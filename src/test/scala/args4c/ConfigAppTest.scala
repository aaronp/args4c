package args4c

import args4c.ConfigApp.ParsedConfig
import com.typesafe.config.ConfigFactory
import org.scalatest._

class ConfigAppTest extends FunSpec {
  val config = ConfigFactory.parseString("""
                                      | int = 1
                                      | array = [1,2,3]
                                      | obj {
                                      |   nested {
                                      |     bool = true
                                      |
                                      |   }
                                      | }
                                    """.stripMargin)

  describe("ConfigApp.runMain") {
    it("should invoke the a parsed config if the user input is valid") {
      ConfigApp.runMain(Seq("a=b")) {
        case ParsedConfig(conf) => assert(conf.getString("a") === "b")
        case other => require(false, s"Expected a parsed config: $other")
      }
    }
    it("should invoke the a parsed config with InvalidInput when given an invalid config") {
      ConfigApp.runMain(Seq("a=b")) {
        case ParsedConfig(conf) => assert(conf.getString("a") === "b")
        case other => require(false, s"Expected a parsed config: $other")
      }
    }
  }
  describe("ConfigApp.configAtPath") {
    it("should return the default unfound value non-existent paths") {
      val notFoundString = "some not found string"
      val invalidPath = "obj.invalid.path"
      val conf = ConfigApp.configAtPath(config, invalidPath, notFoundString)
      assert(conf.getString(invalidPath) === notFoundString)
    }
    it("should return the nested configuration at the given path") {
      val path = "obj"
      val conf = ConfigApp.configAtPath(config, path)

      assert(config.getConfig(path) === conf)
    }
  }
}
