package args4c


import java.io.File

import args4c.io.IoImplicits._
import com.typesafe.config.{Config, ConfigFactory}
import org.scalatest._

import scala.util.Try

class Args4cTest extends FunSpec {

  import Args4cTest._

  describe("AsConfig.unapply") {
    it("should not consider empty files valid configuration files") {
      withTempFile("delete", "me") { file =>
        assert(Args4c.AsConfig.unapply(file.getPath).isEmpty, "Empty configuration file")
      }
    }
    it("should parse absolute file paths") {
      withTempFile("delete", "me") { file =>
        file.append("x : y")
        file.getPath match {
          case Args4c.AsConfig(c) => assert(c.getString("x") === "y")
          case _ => require(false, "AsConfig didn't match an absolute file location")
        }
      }
    }
    it("should not parse invalid urls or file locations") {
      assert(Args4c.AsConfig.unapply("thisDoesntExist").isEmpty)
    }
  }
  describe("Args4c.configAtPath") {
    it("should return the default unfound value non-existent paths") {
      val notFoundString = "some not found string"
      val invalidPath = "obj.invalid.path"
      val conf = Args4c.configAtPath(ConfigAppTest.config, invalidPath, notFoundString)
      assert(conf.getString(invalidPath) === notFoundString)
    }
    it("should return the nested configuration at the given path") {
      val path = "obj"
      val conf = Args4c.configAtPath(ConfigAppTest.config, path)

      assert(ConfigAppTest.config.getConfig(path) === conf)
    }
  }
  describe("ArgsAsConfigParser.parseArgs") {
    it("should parse a single user argument as a boolean flag") {
      assert(parseSuccess("-flag").getBoolean("flag") === true)
      assert(parseSuccess("flag").getBoolean("flag") === true)
    }
    it("should parse the last pending argument as a boolean flag") {
      def verify(args: String*) = {
        val conf = parseSuccess(args: _*)
        assert(conf.getBoolean("flag") === true)
        assert(conf.getString("foo") === "bar")
      }
      verify("foo", "=", "bar", "-flag")
      verify("foo", "=", "bar", "flag")
    }

    it("should not parse unexpected '=' signs as input") {
      def verifyFails(args : String*) = {
        val either = Args4c.parseArgs(args, ConfigFactory.empty)
        assert(either.isLeft, s"Expected failure due to equal sign in $args but got ${either.right.map{_.root.render}}")
      }

      verifyFails("=")
      verifyFails("a=b", "=", "b=c")
      verifyFails("-a", "b", "=")

    }
    it("should consider 'a=  b=c' as invalid user input for key 'a'") {
      def verifyFails(args : String*) = {
        val either = Args4c.parseArgs(args, ConfigFactory.empty)
        assert(either.isLeft, s"Expected an error for the hanging key '-a= in $args but got ${either.right.map{_.root.render}}")
      }

      verifyFails("-a=", "b=c")
      verifyFails("a=", "b=c")
      verifyFails("a", "=", "b=c")
    }
    it("should parse relative paths to configuration files") {
      verifyABConfig(_.getName)
    }
    it("should parse absolute paths to configuration files") {
      verifyABConfig(_.getAbsolutePath)
    }
    it("should always give preference to all key/value command-line args over configuration file args") {
      withLocalFile { file =>
        file.text =
          """
            | file : true
            | a : b
          """.stripMargin

        def verfiyConfig(args: String*) = {
          val Right(config) = Args4c.parseArgs(args.toSeq, ConfigFactory.empty)
          assert(config.getString("a") === "overridden")
          assert(config.getBoolean("file"))
        }

        verfiyConfig("-a", "overridden", file.getName)
        verfiyConfig(file.getName, "-a", "overridden")
      }
    }
    it("should be able to resolve references between different configuration files") {
      withLocalFile { alpha =>
        alpha.text = "name : alpha"

        withLocalFile { beta =>
          beta.text =
            """
              |resolved : ${name}
              | """.stripMargin

          val alphaFirst = parseSuccess(alpha.getName, beta.getName).resolve
          val betaFirst = parseSuccess(beta.getName, alpha.getName).resolve

          assert(alphaFirst.getString("resolved") === "alpha")
          assert(betaFirst.getString("resolved") === "alpha")
        }
      }
    }
    it("should give preference to configuration files from left to right") {
      withLocalFile { alpha =>
        alpha.text =
          """
            | name : alpha
            | some : thing
          """.stripMargin

        withLocalFile { beta =>
          beta.text =
            """
              | name : beta
              | foo : bar
            """.stripMargin

          val alphaFirst = parseSuccess(alpha.getName, beta.getName)
          assert(alphaFirst.getString("name") === "alpha")
          assert(alphaFirst.getString("some") === "thing")
          assert(alphaFirst.getString("foo") === "bar")


          val betaFirst = parseSuccess(beta.getName, alpha.getName)
          assert(betaFirst.getString("name") === "beta")
          assert(betaFirst.getString("some") === "thing")
          assert(betaFirst.getString("foo") === "bar")
        }
      }
    }
    it("should parse '-a b -c d' as a=b, c=d") {
      val Right(config) = Args4c.parseArgs(Seq("-a", "b", "-c", "d"), ConfigFactory.empty)
      assert(config.getString("a") === "b")
      assert(config.getString("c") === "d")
    }
    it("should parse '-a=b -c=d' as a=b, c=d") {
      val config = parseSuccess("-a=b", "-c=d")
      assert(config.getString("a") === "b")
      assert(config.getString("c") === "d")
    }
    it("should parse '-singleFlag -anotherFlag foo' where the singleFlag is interpretted as a boolean") {
      val config = parseSuccess("-singleFlag", "-anotherFlag", "foo")
      assert(config.getBoolean("singleFlag"))
      assert(config.getString("anotherFlag") === "foo")
    }
    it("should parse the nested args 'a.b.c=xyz' ") {
      val config = parseSuccess("a.b.c", "xyz")
      assert(config.getConfig("a.b").getString("c") === "xyz")
    }
    it("should parse spaced args '-a = b  c = d'") {
      val config = parseSuccess("-a", "=", "b", "c", "=", "d")
      assert(config.getString("a") === "b")
      assert(config.getString("c") === "d")
    }
  }
}

object Args4cTest {


  // convenience function to call the method under test (ArgsAsConfigParser.parseArgs) with an empty default config
  def parseSuccess(args: String*): Config = {
    val resultEither = Args4c.parseArgs(args.toSeq, ConfigFactory.empty)

    resultEither match {
      case Right(conf) => conf
      case Left(err) =>
        assert(resultEither.isRight, s"Error parsing '$args' : $err")
        ???
    }
  }

  private def withTempFile(name : String, suffix: String)(f : File => Unit) = {
    val file = File.createTempFile(name, suffix)
    try {
      f(file)
    } finally {
      Try(file.delete())
    }
  }


  def withLocalFile(test: File => Unit) = {

    def find(i: Int = 0): File = {
      val f = i match {
        case 0 => new java.io.File("ArgsAsConfigParser.parseArgs_test.tmp")
        case n => new java.io.File(s"ArgsAsConfigParser.parseArgs_test-$n.tmp")
      }
      if (f.exists) {
        find(i + 1)
      } else {
        f
      }
    }
    // set up a related temp config file
    val file = find()
    try {
      require(!file.exists && file.createNewFile(), s"Couldn't create ${file.getPath}")
      test(file)

    } finally {
      file.delete()
    }
  }

  def verifyABConfig(filePath: File => String) = {
    withLocalFile { file =>
      file.text = "a : b"
      val Right(config) = Args4c.parseArgs(Seq(filePath(file)), ConfigFactory.empty)
      new Assertions {
        assert(config.getString("a") === "b")
      }
    }
  }
}