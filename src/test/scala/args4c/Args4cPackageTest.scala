package args4c

import java.nio.file.{Files, Paths}
import java.util.UUID

import args4c.RichConfig.ParseArg
import com.typesafe.config.{Config, ConfigFactory}

class Args4cPackageTest extends BaseSpec {

  sys.env.get("JAVA_HOME").foreach { _ =>
    "sysEnvAsConfig" should {
      "convert JAVA_HOME variables into a configuration" in {
        sysEnvAsConfig().getString("java.home") should not be (empty)
      }
    }
  }

  "unquote" should {
    List(
      "foo"              -> "foo",
      "foo\" "           -> "foo\" ",
      "\" foo \" "       -> " foo ",
      "some \" foo \" "  -> "some \" foo \" ",
      "\" foo \" suffix" -> "\" foo \" suffix"
    ).foreach {
      case (input, expected) =>
        s"Unquote >$input< as $expected" in {
          unquote(input) shouldBe expected
        }
    }
  }

  "prefixNotInSet" should {
    "return true if the set does not contain a prefix of the value" in {
      prefixNotInSet(Set("foo.bar"), "foo") shouldBe false
      prefixNotInSet(Set("foo.bar"), "bar") shouldBe true
      prefixNotInSet(Set("foo.bar"), "other") shouldBe true
    }
  }
  "configForMap" should {
    "discard values in favour of longer paths" in {
      val map  = Map("someroot" -> "short", "someroot.value" -> "long")
      val conf = configForMap(map)
      conf.toMap.mapValues(_.unwrapped) shouldBe Map("someroot.value" -> "long")
    }
  }

  "envToPath" should {
    "convert UPPER_SNAKE_CASE into dotted lowercase strings" in {
      envToPath("foo") shouldBe "foo"
      envToPath("foo_") shouldBe "foo"
      envToPath("_foo") shouldBe "foo"
      envToPath("FOO") shouldBe "foo"
      envToPath("FOO_") shouldBe "foo"
      envToPath("_FOO") shouldBe "foo"

      envToPath("A_BC_D") shouldBe "a.bc.d"
      envToPath("A_Bc_D") shouldBe "a.bc.d"
      envToPath("_Bc___D") shouldBe "bc.d"
    }
  }
  "configForArgs" should {
    "handled colons in keys" in {
      val config: Config = configForArgs(Array("affinity:container=hello"))
      config.getString("\"affinity:container\"") shouldBe "hello"
    }
    "ignore malformed arguments when when ParseArg.Ignore is specified" in {
      val conf = configForArgs(Array("invalid", "file.doesn't.exist", "ok=true"), onUnrecognizedArg = ParseArg.Ignore).resolve
      conf.getBoolean("ok") shouldBe true
    }
    "error when ParseArg.Throw is specified" in {
      val err = intercept[Exception] {
        configForArgs(Array("invalid", "file.doesn't.exist", "ok=true"), onUnrecognizedArg = ParseArg.Throw).resolve
      }
      err.getMessage should include("Unrecognized user arg 'invalid'")
    }
    "load configurations from the filesystem and classpath" in {

      val fooConf = Paths.get(s"./target/foo-${UUID.randomUUID}.conf")
      Files.write(fooConf, """fromFile = true""".stripMargin.getBytes("UTF-8"))
      try {
        val config = configForArgs(Array(fooConf.toAbsolutePath.toString, "test.conf"), ConfigFactory.empty)
        config.getBoolean("fromFile") shouldBe true
        config.getString("source") shouldBe "classpath file"
      } finally {
        Files.delete(fooConf)
      }
    }
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
