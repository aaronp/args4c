package args4c

import org.scalatest.FunSpec

class ConfigImplicitsTest extends FunSpec {

  describe("ConfigImplicits.getIntSeq") {

    import Args4cTest._
    import ConfigImplicits._

    it("should parse x=[1,2,3] as a list") {
      val config = parseSuccess("x=", "[1,2,3]")
      assert(config.getIntSeq("x") === Seq(1, 2, 3))
    }
    it("should parse x=1,2,3 as a list") {
      val config = parseSuccess("x=", "1,2,3")
      assert(config.getIntSeq("x") === Seq(1, 2, 3))
    }
    it("should parse x=1,,3 as a list") {
      val config = parseSuccess("x=", "1,,3")
      assert(config.getIntSeq("x") === Seq(1, 3))
    }
  }
}
