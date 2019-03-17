package args4c
import eie.io._
import org.scalatest.{Matchers, WordSpec}
class InitializeTest extends WordSpec with Matchers {

  "Initialize" should {
    "allow secret passwords to be set up" in {
      val responses = Map("" -> "")
      def testInput(prompt: String) = {
        responses.getOrElse(prompt, sys.error(s"test setup encountered unrecognized prompt for '$prompt'"))
      }
      val pathToConfig = Initialize.setSecrets(testInput)
      val x            = pathToConfig.text
      println(x)
    }
  }

}
