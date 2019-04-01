package args4c

import com.typesafe.config.ConfigFactory

import scala.concurrent.duration._

class RichConfigTest extends BaseSpec {

  import scala.collection.JavaConverters._

  "RichConfig.overrideWith" should {
    "provide a config with the config values set" in {
      val config = ConfigFactory.parseString("original =  1\nanInt=3").overrideWith("original = 2")
      config.getInt("anInt") shouldBe 3
      config.getInt("original") shouldBe 2
    }
  }
  "RichConfig.set" should {
    "provide a config with the config values set" in {
      val baseConfig = configForArgs(Array("foo.x.y=1", "test.conf"))
      val conf       = baseConfig.set("foo.x.y", 2).set("aBoolean", true).set("ints", Array(1, 2, 3)).set("strings", Array("four", "five"))
      conf.getInt("foo.x.y") shouldBe 2
      conf.getBoolean("aBoolean") shouldBe true
      conf.getIntList("ints").asScala should contain inOrderOnly (1, 2, 3)
      conf.getStringList("strings").asScala should contain inOrderOnly ("four", "five")
    }
  }
  "RichConfig.summary" should {
    "show a flatten summary of a configuration" in {

      val testConf                  = ConfigFactory.parseString("""
          |test.stringArray : ["one", "two"]
          |test.intArray : [1,2,3]
          |test.objArray : [
          |  {
          |    x : "an object entry"
          |    y : "another object entry"
          |  },
          |  {
          |    foo : false
          |  }
          |]
        """.stripMargin)
      val conf                      = configForArgs(Array("test.foo=bar", "test.password=secret"), fallback = testConf)
      val entries: Seq[StringEntry] = conf.summaryEntries()

      val actual = conf.withPaths("test").summary()

      entries should contain(StringEntry(Nil, "command-line", "test.foo", "bar"))
      entries should contain(StringEntry(Nil, "command-line", "test.password", "**** obscured ****"))

      actual shouldBe
        """test.foo : bar # command-line
          |test.intArray[0] : 1 # String: 3
          |test.intArray[1] : 2 # String: 3
          |test.intArray[2] : 3 # String: 3
          |test.objArray[0].x : an object entry # String: 6
          |test.objArray[0].y : another object entry # String: 7
          |test.objArray[1].foo : false # String: 10
          |test.password : **** obscured **** # command-line
          |test.stringArray[0] : one # String: 2
          |test.stringArray[1] : two # String: 2""".stripMargin
    }
  }
  "RichConfig.asDuration" should {
    "convert an infinite time to a duration" in {
      ConfigFactory.parseString("time = inf").asDuration("time") shouldBe Duration.Inf
    }
    "convert 10s to a finite duration" in {
      ConfigFactory.parseString("time = 10s").asDuration("time") shouldBe 10.seconds
    }
  }
  "RichConfig.withUserArgs" should {
    "treat comma-separated values as lists when overriding string lists" in {
      val actual = listConfig.withUserArgs(Array("stringList=x,y,z"))
      actual.getStringList("stringList").asScala.toList shouldBe List("x", "y", "z")
    }
    "treat comma-separated values as an error when overriding object lists" in {
      val error = intercept[Exception] {
        listConfig.withUserArgs(Array("objectList=x,y,z"))
      }
      error.getMessage shouldBe "Path 'objectList' tried to override an object list with 'x,y,z'"
    }
    "treat comma-separated values as a string when overriding a string" in {
      listConfig.withUserArgs(Array("justAString=x,y,z")).getString("justAString") shouldBe "x,y,z"
    }

    "allow the config to set key/value pairs from user arguments" in {
      val conf   = ConfigFactory.parseString("""thing.value = original1
          | hyphenated-key = original2
          | hyphenated-nested.foo : original3
          | value : original4""".stripMargin)
      val actual = conf.withUserArgs(Array("thing.value=new-one", "hyphenated-key=two"))
      actual.collectAsMap() shouldBe Map(
        "thing.value"           -> "new-one",
        "hyphenated-key"        -> "two",
        "hyphenated-nested.foo" -> "original3",
        "value"                 -> "original4"
      )
    }

  }
  "RichConfig.without" should {
    "return a configuration which excludes the paths from another config" in {
      val remaining = defaultConfig.without(defaultConfig).paths
      remaining shouldBe (empty)
    }
  }
  "RichConfig.pathRoots" should {
    "return only the top-level config roots" in {
      val conf = configForArgs(Array("foo.x.y=1", "foo.flag=true", "bar.name=hi", "bar.bar.bar=1"), ConfigFactory.empty())
      conf.pathRoots should contain only ("bar", "foo")
    }
  }
  "RichConfig.origins" should {
    "return the unique origins from which the configurations were sources" in {
      val conf = configForArgs(Array("foo.x.y=1", "test.conf"))
      conf.origins should contain allOf ("command-line", "environment variable")
      conf.origins.exists(_.contains("test.conf")) shouldBe true
    }
  }
  "RichConfig.asList" should {
    "be able to read both array and string values as a list" in {
      configForArgs(Array("someList=1,2,3")).asList("someList") shouldBe List("1", "2", "3")
      ConfigFactory.parseString("aList : [1,2,3]").asList("aList") shouldBe List("1", "2", "3")
    }
  }
  "RichConfig.collectAsMap" should {
    "collect the string values for a configuration" in {
      val conf = ConfigFactory.parseString(""" thing : {
          |   b : 2
          |   c : 3
          | }
          | bar : true
        """.stripMargin)
      conf.collectAsMap() shouldBe Map("thing.b" -> "2", "thing.c" -> "3", "bar" -> "true")
    }
  }
  "RichConfig.intersect" should {
    "compute the intersection of two configs" in {
      val a = ConfigFactory.parseString(""" thing : {
          |   b : 2
          |   c : 3
          | }
          | bar : y
        """.stripMargin)
      val b = ConfigFactory.parseString(""" thing : {
          |   a : 1
          |   b : 2
          | }
          | foo : x
        """.stripMargin)
      a.intersect(b).paths shouldBe List("thing.b")
    }
  }

  def listConfig = ConfigFactory.parseString("""stringList = [a,b,c]
      |objectList = [{value :1},{value :2}]
      |justAString = original""".stripMargin)
}
