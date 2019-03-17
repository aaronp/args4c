package args4c
import java.nio.file.Files

import com.typesafe.config.{Config, ConfigFactory}
import org.scalatest.{Matchers, WordSpec}

class ConfigAppTest extends WordSpec with Matchers {
  class App extends ConfigApp {
    var shown              = ""
    var lastConfig: Config = ConfigFactory.empty
    override def apply(config: Config): Unit = {
      lastConfig = config
    }
    override protected def show(value: String, config: Config): Unit = {
      shown = value
    }
  }

  "ConfigApp" should {
    "be able to source sensitive config files" in {

      val app = new App
      app.

    }
    "show values when a show is given" in {
      val app = new App
      app.main(Array("ignore.me=ok", "foo.bar.x=123", "foo.bar.y=456", "show=foo.bar"))
      app.shown should include("""foo.bar.x : 123 # command-line
                                 |foo.bar.y : 456 # command-line""".stripMargin)
    }
    "include configurations on the classpath" in {
      val app = new App
      app.main(Array("test.conf"))
      app.lastConfig.getString("description") shouldBe "This is a file on the classpath"
    }
    "honor settings from left to right" in {
      val app = new App
      app.main(Array("source=overridden", "test.conf"))
      app.lastConfig.getString("source") shouldBe "overridden"

      app.main(Array("test.conf", "source=ignored"))
      app.lastConfig.getString("source") shouldBe "classpath file"
    }
    "read files from the file system" in {
      val app = new App

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

}
