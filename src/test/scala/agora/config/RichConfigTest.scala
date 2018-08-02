package agora.config

import com.typesafe.config.ConfigFactory
import org.scalatest.{Matchers, WordSpec}

class RichConfigTest extends WordSpec with Matchers {

  import agora.config.implicits._

  import scala.collection.JavaConverters._

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
      actual.collectAsMap shouldBe Map(
        "thing.value"           -> "new-one",
        "hyphenated-key"        -> "two",
        "hyphenated-nested.foo" -> "original3",
        "value"                 -> "original4"
      )
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
      conf.collectAsMap shouldBe Map("thing.b" -> "2", "thing.c" -> "3", "bar" -> "true")
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
