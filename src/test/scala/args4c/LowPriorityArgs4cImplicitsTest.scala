package args4c

class LowPriorityArgs4cImplicitsTest extends BaseSpec {

  "userargs.asConfig" should {
    "produce a configuration for the arguments" in {
      val config = Array("myapp.foo=bar", "myapp.password=secret", "test.conf").asConfig()
      config.getString("myapp.foo") shouldBe "bar"
      val actual = config.withPaths("myapp").summaryEntries()
      actual.map(e => e.key -> e.value) should contain inOrderOnly (
        ("myapp.foo", "bar"),
        ("myapp.password", "**** obscured ****"),
        ("myapp.saveTo", "afile")
      )
    }
  }

}
