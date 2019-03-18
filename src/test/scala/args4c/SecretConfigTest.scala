package args4c

import args4c.SecretConfig.defaultPermissions
import eie.io._
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpec}

class SecretConfigTest extends WordSpec with Matchers with BeforeAndAfterAll {

  import SecretConfigTest._

  "SecretConfig.writeSecretsUsingPrompt" should {
    "allow secret passwords to be set up" in {
      // call the method under test to write 'testConfigFile'
      val pathToConfig = SecretConfig.writeSecretsUsingPrompt(testInput)

      // prove we can read back the config
      val readBack = SecretConfig.readSecretConfig(pathToConfig, testInput)
      readBack.getString("mongo.password") shouldBe "secret"
      readBack.getString("anEntry.which.contains.an.equals.sign") shouldBe "abc=123"
      readBack.getString("credentials") shouldBe "don't tell"
    }
  }

  override def beforeAll(): Unit = {
    if (testConfigFile.asPath.exists()) {
      testConfigFile.asPath.delete()
    }
  }

  override def afterAll(): Unit = {
    testConfigFile.asPath.delete()
  }
}

object SecretConfigTest {

  val testConfigFile = s"./target/${getClass.getName}/config.cfg"

  // our 'readLine' function to supply for this test
  val testConfigEntries = Iterator(
    "mongo.password=secret",
    "anEntry.which.contains.an.equals.sign=abc=123",
    "credentials=don't tell"
  )

  def testInput(prompt: String): String = {
    val Permissions = s"Config Permissions (defaults to $defaultPermissions):"
    val PathPrompt  = SecretConfig.saveSecretPrompt()
    prompt match {
      case PathPrompt                       => testConfigFile
      case Permissions                      => SecretConfig.defaultPermissions
      case _ if prompt.contains("Password") => "sEcre3t"
      case "Add config path in the form <key>=<value> (leave blank when finished):" =>
        if (testConfigEntries.hasNext) {
          testConfigEntries.next()
        } else {
          ""
        }
      case _ => sys.error(s"test setup encountered unrecognized prompt for '$prompt'")
    }
  }
}
