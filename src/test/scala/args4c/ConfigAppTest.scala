package args4c
import java.nio.file.Files

import com.typesafe.config.{Config, ConfigFactory}

class ConfigAppTest extends BaseSpec {

  "ConfigApp.main" should {
    "error if told to run with a --secret which doesn't exist" in {
      val app = new ConfigApp {
        type Result = Config
        var lastConfig: Config = ConfigFactory.empty
        override def run(config: Config) = {
          lastConfig = config
          config
        }
      }

      val bang = intercept[IllegalStateException] {
        app.main(Array("--secret=some/invalid/path"))
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
      val app = new ConfigApp {
        type Result = Config
        var lastConfig: Config = ConfigFactory.empty
        override def run(config: Config) = {
          lastConfig = config
          config
        }
      }

      val configFile = "./target/ConfigAppTest.conf"

      try {
        // set up a secret config
        app.runMain(Array("--setup", s"--secret=$configFile"), SecureConfig(SecureConfigTest.testInput(configFile, Iterator("my.password=test"))))

        // run our app w/ that config
        app.runMain(Array(s"--secret=$configFile"), SecureConfig(SecureConfigTest.testInput(configFile, Iterator())))

        app.lastConfig.getString("my.password") shouldBe "test"

      } finally {
        deleteFile(configFile)
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

    override protected def secretConfigForArgs(userArgs: Array[String],
                                               secureConfig : SecureConfig,
                                               ignoreDefaultSecretConfigArg: String,
                                               pathToSecretConfigArg: String): SecretConfigResult = {
      SecretConfigNotSpecified
    }

  }

}
