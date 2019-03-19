package args4c

import args4c.SecretConfig.defaultPermissions

class SecretConfigTest extends BaseSpec {

  import SecretConfigTest._

  val testConfigFile = s"./target/${getClass.getName}/config.cfg"

  // our 'readLine' function to supply for this test
  def testConfigEntries: Iterator[String] = Iterator(
    "mongo.password=secret",
    "anEntry.which.contains.an.equals.sign=abc=123",
    "credentials=don't tell"
  )

  "SecretConfig.writeSecretsUsingPrompt" should {
    "allow secret passwords to be set up" in {
      // call the method under test to write 'testConfigFile'
      val pathToConfig = SecretConfig.writeSecretsUsingPrompt(testConfigFile, testInput(testConfigFile, testConfigEntries))

      // prove we can read back the config
      val Some(readBack) = SecretConfig.readSecretConfig(pathToConfig, testInput(testConfigFile, testConfigEntries))
      readBack.getString("mongo.password") shouldBe "secret"
      readBack.getString("anEntry.which.contains.an.equals.sign") shouldBe "abc=123"
      readBack.getString("credentials") shouldBe "don't tell"
    }
  }

  override def beforeAll(): Unit = {
    super.beforeAll()
    deleteFile(testConfigFile)
  }

  override def afterAll(): Unit = {
    super.afterAll()
    deleteFile(testConfigFile)
  }
}

object SecretConfigTest {

  def testInput(pathToConfigFile: String, testConfigEntries: Iterator[String])(prompt: String): String = {
    val Permissions = s"Config Permissions: [$defaultPermissions]"
    val PathPrompt  = SecretConfig.saveSecretPrompt(pathToConfigFile)
    val userInput = prompt match {
      case PathPrompt                       => pathToConfigFile
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

    userInput
  }
}
