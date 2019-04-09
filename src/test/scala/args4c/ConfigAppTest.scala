package args4c
import java.nio.file.{Files, Paths}
import java.util.concurrent.atomic.AtomicInteger

import com.typesafe.config.{Config, ConfigFactory}

import scala.collection.mutable.ListBuffer

class ConfigAppTest extends BaseSpec {

  "ConfigApp.main" should {
    "prompt the user if there are any missing required entries" in withConfigFile { configFile =>
      val prompts = ListBuffer[(Prompt, String)]()
      val suppliedValues = Map(
        "foo"  -> "the foo value",
        "bar"  -> "bar= isComplicated",
        "fizz" -> "\" fizz is quoted \""
      )
      val otherInputs = Iterator("meh=unprompted")
      def inputs(prompt: Prompt) = {
        val answer = prompt match {
          case ReadNextKeyValuePair(key, _) if suppliedValues.contains(key) => suppliedValues(key)
          case _                                                            => SecureConfigTest.testInput(configFile, otherInputs)(prompt)
        }
        prompts += (prompt -> answer)
        answer
      }
      val app = new PromptingTestApp(SecureConfig(inputs))
      app.main(Array("args4c.requiredConfigPaths=foo,bar,fizz,isAlreadySet", "isAlreadySet=yes"))

      // verify we were prompted for the required values
      suppliedValues.foreach {
        case (key, value) =>
          withClue(s"we should've been prompted for $key") {
            val found = prompts.find {
              case (_, v) => v == value
            }
            found should not be (empty)
          }
          app.lastConfig.getString(key) shouldBe value
      }
      app.lastConfig.getString("isAlreadySet") shouldBe "yes"
      prompts.size shouldBe suppliedValues.size
    }
    "prompt the user if there are any missing required entries as well as any additional entries when setup is run" in withConfigFile { configFile =>
      val testPassword = "whatever"

      val prompts = ListBuffer[(Prompt, String)]()
      val suppliedValues = Map(
        "foo"  -> "the foo value",
        "bar"  -> "bar= isComplicated",
        "fizz" -> "\" fizz is quoted \""
      )
      val otherInputs = Iterator("meh=unprompted")
      def inputs(prompt: Prompt): String = {
        val answer = prompt match {
          case ReadNextKeyValuePair(key, _) if suppliedValues.contains(key) => suppliedValues(key)
          case _                                                            => SecureConfigTest.testInput(configFile, otherInputs, testPassword)(prompt)
        }
        prompts += (prompt -> answer)
        answer
      }

      val app = new PromptingTestApp(SecureConfig(inputs))
      app.main(Array("args4c.requiredConfigPaths=foo,bar,fizz", "--setup"))

      // read back our fancy new populated config
      val newlySetupConfig: Config = SecureConfig.readConfigAtPath(Paths.get(configFile), testPassword.getBytes("UTF-8"))

      // verify we were prompted for the required values
      suppliedValues.foreach {
        case (key, value) =>
          withClue(s"we should've been prompted for $key") {
            val found = prompts.find {
              case (_, v) => v == value
            }
            found should not be (empty)
          }
          newlySetupConfig.getString(key) shouldBe value
      }
      newlySetupConfig.getString("meh") shouldBe "unprompted"
    }
    "error if told to run with a --secure which doesn't exist" in {
      val app = new ConfigApp {
        type Result = Config
        var lastConfig: Config = ConfigFactory.empty
        override def run(config: Config) = {
          lastConfig = config
          config
        }
      }

      val bang = intercept[IllegalStateException] {
        app.main(Array("--secure=some/invalid/path"))
      }
      bang.getMessage should include("Configuration at 'some/invalid/path' doesn't exist")
    }
    "invoke the correct function with 'onUnrecognizedUserArg'" in {
      val app = new ConfigApp {
        type Result = Config
        var lastConfig: Config = ConfigFactory.empty
        override def run(config: Config) = {
          lastConfig = config
          config
        }
      }

      val bang = intercept[Exception] {
        app.main(Array("someRawString"))
      }
      bang.getMessage should include("Unrecognized user arg 'someRawString'")
    }
    "be able to source sensitive config files" in {

      withConfigFile { configFile =>
        // set up a secret config
        val app = new PromptingTestApp(SecureConfig(SecureConfigTest.testInput(configFile, Iterator("my.password=test"))))
        app.runMain(Array("--setup", s"--secure=$configFile"))

        // run our app w/ that config
        app.cfg = SecureConfig(SecureConfigTest.testInput(configFile, Iterator()))
        app.runMain(Array(s"--secure=$configFile"))

        app.lastConfig.getString("my.password") shouldBe "test"
      }
    }
    "show values when a show is given" in {
      val app = new TestApp
      app.main(Array("foo.bar.password=secret!", "ignore.me=ok", "foo.bar.x=123", "foo.bar.y=456", "show=foo.bar"))
      app.lastConfig.getInt("foo.bar.x") shouldBe 123
      app.lastConfig.getInt("foo.bar.y") shouldBe 456
      app.shown should not include ("""ignore.me""")
      app.shown.lines.toList should contain only ("foo.bar.password : **** obscured **** # command-line",
      """foo.bar.x : 123 # command-line""",
      """foo.bar.y : 456 # command-line""")
    }
    "include configurations on the classpath" in {
      val app = new TestApp
      app.main(Array("test.conf"))
      app.lastConfig.getString("description") shouldBe "This is a file on the classpath"
    }
    "honor settings from left to right" in {
      val app = new TestApp
      app.main(Array("source=overridden", "test.conf"))
      app.lastConfig.getString("source") shouldBe "overridden"

      app.main(Array("test.conf", "source=ignored"))
      app.lastConfig.getString("source") shouldBe "classpath file"
    }
    "read files from the file system" in {
      val app = new TestApp

      val file = Files.createTempFile("temporary", ".conf")
      Files.write(file, "source=absolutePath".getBytes)
      try {
        app.main(Array(file.toAbsolutePath.toString))
        app.lastConfig.getString("source") shouldBe "absolutePath"

      } finally {
        Files.delete(file)
      }
    }
  }

  private val counter = new AtomicInteger(0)
  def withConfigFile(test: String => Unit) = {
    val configFile = s"./target/ConfigAppTest-${counter.incrementAndGet}.conf"
    try {
      test(configFile)
    } finally {
      deleteFile(configFile)
    }
  }

  class PromptingTestApp(initial: SecureConfig) extends ConfigApp {
    var cfg: SecureConfig                   = initial
    override def secureConfig: SecureConfig = cfg

    type Result = Config
    var lastConfig: Config = ConfigFactory.empty
    override def run(config: Config): Result = {
      lastConfig = config
      config
    }
  }
  class TestApp extends ConfigApp {
    type Result = Config
    var shown              = ""
    var lastConfig: Config = ConfigFactory.empty

    override def run(config: Config) = {
      lastConfig = config
      config
    }

    override protected def showValue(value: String, config: Config): Unit = {
      lastConfig = config
      shown = value
    }

    override protected def secureConfigForArgs(userArgs: Array[String],
                                               ignoreDefaultSecureConfigArg: String,
                                               pathToSecureConfigArg: String): SecureConfigState = {
      SecureConfigNotSpecified
    }
  }
}
