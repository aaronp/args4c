package agora.config

import com.typesafe.config.ConfigFactory
import org.scalatest.{Matchers, WordSpec}

class ConfigPackageTest extends WordSpec with Matchers {

  "configForArgs" should {
    "evaluate values from the command line which are referenced from the config file" in {

      val fallback = ConfigFactory.parseString("""
          |foo=defaultValue
          |some.other.value=${foo}
        """.stripMargin)
      val conf     = configForArgs(Array("foo=bar"), fallback).resolve

      conf.getString("some.other.value") shouldBe "bar"
    }
  }
}
