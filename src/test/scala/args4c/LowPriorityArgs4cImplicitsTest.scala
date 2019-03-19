package args4c

class LowPriorityArgs4cImplicitsTest extends BaseSpec {

  "userargs.asConfig" should {
    "produce a configuration for the arguments" in {
      val config = Array("myapp.foo=bar", "myapp.password=secret", "test.conf").asConfig()
      config.getString("myapp.foo") shouldBe "bar"
      config.filter(_.startsWith("myapp")).summary().foreach(println)
    }
  }

}
