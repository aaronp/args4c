package args4c

import com.typesafe.config.ConfigFactory

import scala.concurrent.duration._

class RichConfigTest extends BaseSpec {

  import scala.collection.JavaConverters._

  "RichConfig.dynamic select" should {
    "be able to traverse the config usign dynamic values" in {
      val config = Array("a.b.c.d.e=1,2,3").asRichConfig()
      config.a.b.c.d.asList("e").map(_.toInt) shouldBe List(1, 2, 3)
    }
    "error if it tries to access invalid paths" in {
      val config = Array("a.int=1").asRichConfig()
      val bang = intercept[Exception] {
        config.a.int.asList("bang")
      }
      bang.getMessage should include("is not a config")
    }
  }
  "RichConfig.overrideWith" should {
    "provide a config with the config values set" in {
      val config = ConfigFactory.parseString("original =  1\nanInt=3").overrideWith("original = 2")
      config.getInt("anInt") shouldBe 3
      config.getInt("original") shouldBe 2
    }
  }
  "RichConfig.setArray" should {
    "be able to set an array" in {
      val baseConfig = configForArgs(Array("array=[1,2]"))
      baseConfig.setArray("array", 4, 5, 6).asList("array") should contain only ("4", "5", "6")
    }
  }
  "RichConfig.set" should {
    "set string values" in {
      val config = configForArgs(Array("str=original")).set("str", "updated").set("foo", "bar")
      config.getString("str") shouldBe ("updated")
      config.getString("foo") shouldBe ("bar")
    }
    "provide a config with the config values set" in {
      val baseConfig = configForArgs(Array("foo.x.y=1", "test.conf"))
      val conf       = baseConfig.set("foo.x.y", 2).set("aBoolean", true).setArray("ints", Array(1, 2, 3)).setArray[String]("strings", Array("four", "five"))
      conf.getInt("foo.x.y") shouldBe 2
      conf.getBoolean("aBoolean") shouldBe true
      conf.getIntList("ints").asScala should contain inOrderOnly (1, 2, 3)
      conf.getStringList("strings").asScala should contain inOrderOnly ("four", "five")
    }
  }
  "RichConfig.summary" should {
    "show a flatten summary of a configuration" in {

      val testConf = ConfigFactory.parseString("""
          |test.stringArray : ["one", "two"]
          |test.intArray : [1,2,3]
          |test.emptyArray : []
          |test.nested: [
          |   [ { id :  1}, {id : 2 } ],
          |   [ { id :  3, array : ["yes"] } ],
          |   [],
          |   [[[{deep : "very"}]]]
          |]
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
      val conf     = configForArgs(Array("test.foo=bar", "test.password=secret"), fallback = testConf)
      val entries  = conf.summaryEntries().map(e => (e.key, e.value))

      val actual = conf.withPaths("test").summary()

      entries should contain("test.foo"         -> "bar")
      entries should contain("test.intArray[0]" -> "1")
      entries should contain("test.intArray[1]" -> "2")
      entries should contain("test.intArray[2]" -> "3")
      entries should contain("test.emptyArray"  -> "[]")
      entries should contain("test.password"    -> "**** obscured ****")

      actual shouldBe
        """test.emptyArray : [] # String: 4
          |test.foo : bar # command-line
          |test.intArray[0] : 1 # String: 3
          |test.intArray[1] : 2 # String: 3
          |test.intArray[2] : 3 # String: 3
          |test.nested[0][0].id : 1 # String: 6
          |test.nested[0][1].id : 2 # String: 6
          |test.nested[1][0].array[0] : yes # String: 7
          |test.nested[1][0].id : 3 # String: 7
          |test.nested[2] : [] # String: 8
          |test.nested[3][0][0][0].deep : very # String: 9
          |test.objArray[0].x : an object entry # String: 13
          |test.objArray[0].y : another object entry # String: 14
          |test.objArray[1].foo : false # String: 17
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
      val remaining = defaultConfig.without(defaultConfig).paths()
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
      conf.origins should contain("command-line")
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
      a.intersect(b).paths() shouldBe List("thing.b")
    }
  }

  def listConfig = ConfigFactory.parseString("""stringList = [a,b,c]
      |objectList = [{value :1},{value :2}]
      |justAString = original""".stripMargin)
}
