package args4c

import org.scalatest.{Matchers, WordSpec}

class RichConfigOpsTest extends WordSpec with Matchers with LowPriorityArgs4cImplicits {

  "RichConfigOps.summary" should {
    "show a flatten summary of a configuration" in {
      val conf = configForArgs(Array("test.foo=bar", "test.password=secret"))
      val entries: List[StringEntry] = conf.summary()
      entries should contain(StringEntry(Nil, "command-line", "test.foo", "bar"))
      entries should contain(StringEntry(Nil, "command-line", "test.password", "**** obscured ****"))

      conf.filter(_.startsWith("test")).summary().mkString("\n") shouldBe
        """test.foo : bar # command-line
          |test.password : **** obscured **** # command-line""".stripMargin
    }
  }
}
