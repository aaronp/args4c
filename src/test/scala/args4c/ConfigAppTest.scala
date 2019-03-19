package args4c
import java.nio.file.Files

import com.typesafe.config.{Config, ConfigFactory}

class ConfigAppTest extends BaseSpec {

  "ConfigApp" should {
    "be able to source sensitive config files" in {
      val app = new ConfigApp {
        var lastConfig: Config = ConfigFactory.empty
        override def run(config: Config): Unit = {
          lastConfig = config
        }
      }

      val configFile = "./target/ConfigAppTest.conf"

      try {
        // set up a secret config
        app.runMain(Array("--setup"), SecretConfigTest.testInput(configFile, Iterator("my.password=test")))

        // run our app w/ that config
        app.runMain(Array("--secret=" + configFile), SecretConfigTest.testInput(configFile, Iterator("my.password=test")))

        app.lastConfig.getString("my.password") shouldBe "test"

      } finally {
        import eie.io._
        configFile.asPath.delete()
      }
    }
    "show values when a show is given" in {
      val app = new TestApp
      app.main(Array("ignore.me=ok", "foo.bar.x=123", "foo.bar.y=456", "show=foo.bar"))
      app.shown should include("""foo.bar.x : 123 # command-line
                                 |foo.bar.y : 456 # command-line""".stripMargin)
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
    var shown              = ""
    var lastConfig: Config = ConfigFactory.empty

    override def run(config: Config): Unit = {
      lastConfig = config
    }

    override protected def showValue(value: String, config: Config): Unit = {
      shown = value
    }

    // don't try and load a secret config
    override protected def secretConfigForArgs(userArgs: Array[String],
                                               readLine: String => String,
                                               ignoreDefaultSecretConfigArg: String,
                                               pathToSecretConfigArg: String) = SecretConfigNotSpecified
  }

}
