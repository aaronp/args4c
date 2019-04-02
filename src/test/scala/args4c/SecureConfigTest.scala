package args4c

import java.nio.file.Paths

class SecureConfigTest extends BaseSpec {

  import SecureConfigTest._

  def testConfigFile(name: String) = s"./target/${getClass.getName}/config-$name.cfg"

  // our 'readLine' function to supply for this test
  def testConfigEntries: Iterator[String] = Iterator(
    "mongo.password=secret",
    "anEntry.which.contains.an.equals.sign=abc=123",
    "credentials=don't tell"
  )

  "SecretConfig.setupSecureConfig" should {
    "allow entries in an existing secure config to be updated" in {
      val configPath = testConfigFile("setupSecureConfig")

      // call the method under test to write 'testConfigFile'
      val pathToConfig = SecureConfig(
        testInput(configPath,
                  Iterator(
                    "conf.original.one=first",
                    "conf.original.two=second"
                  ))).setupSecureConfig(Paths.get(configPath))

      // run the app again with different values
      val updated = SecureConfig(
        testInput(configPath,
                  Iterator(
                    "conf.original.one=first",
                    "conf.original.two=changed",
                    "conf.updated=new"
                  ))).setupSecureConfig(Paths.get(configPath))

      updated shouldBe pathToConfig

      val readBack = SecureConfig.readConfigAtPath(updated, "sEcre3t".getBytes("UTF-8"))
      readBack.getString("conf.original.one") shouldBe "first"
      readBack.getString("conf.original.two") shouldBe "changed"
      readBack.getString("conf.updated") shouldBe "new"
    }
    "be able to change the password for the secure config" in {
      val configPath = testConfigFile("updatepwd")

      // call the method under test to write 'testConfigFile'
      val pathToConfig = SecureConfig(
        testInput(configPath,
                  Iterator(
                    "conf.original.one=first",
                    "conf.original.two=leftAlone"
                  ))).setupSecureConfig(Paths.get(configPath))

      // run the app again with different values
      val updated = {
        val original: Prompt => String = testInput(configPath, Iterator("conf.original.one=changed"), "newPassword") _
        def newInput(prompt: Prompt) = {
          prompt match {
            case PromptForExistingPassword(_) => "sEcre3t"
            case PromptForUpdatedPassword     => "newPassword"
            case p                            => original(p)
          }
        }
        SecureConfig(newInput).setupSecureConfig(Paths.get(configPath))
      }

      updated shouldBe pathToConfig

      val cannotReadBack = intercept[Exception] {
        SecureConfig.readConfigAtPath(updated, "sEcre3t".getBytes("UTF-8"))
      }
      cannotReadBack.getMessage shouldBe "Invalid password for config-updatepwd.cfg"

      val readBack = SecureConfig.readConfigAtPath(updated, "newPassword".getBytes("UTF-8"))
      readBack.getString("conf.original.one") shouldBe "changed"
      readBack.getString("conf.original.two") shouldBe "leftAlone"
    }
    "allow secret passwords to be set up" in {
      val configPath = testConfigFile("vanilla")
      def underTest  = SecureConfig(testInput(configPath, testConfigEntries))

      // call the method under test to write 'testConfigFile'
      val pathToConfig = underTest.setupSecureConfig(Paths.get(configPath))

      // prove we can read back the config
      val Some(readBack) = underTest.readSecretConfig(pathToConfig)
      readBack.getString("mongo.password") shouldBe "secret"
      readBack.getString("anEntry.which.contains.an.equals.sign") shouldBe "abc=123"
      readBack.getString("credentials") shouldBe "don't tell"
    }
  }

  override def beforeAll(): Unit = {
    super.beforeAll()
    deleteConfigFiles()
  }

  override def afterAll(): Unit = {
    super.afterAll()
    deleteConfigFiles()
  }

  def deleteConfigFiles() = {
    val dir = Paths.get(s"./target/${getClass.getName}")
    require(dir != null, "null dir")
    require(dir.toFile != null, "null dir file")
    Option(dir.toFile.listFiles).toList.flatten.map(_.toPath).foreach(deleteFile)
  }
}

object SecureConfigTest {

  def testInput(pathToConfigFile: String, testConfigEntries: Iterator[String], password: String = "sEcre3t")(prompt: Prompt): String = {
    prompt match {
      case SaveSecretPrompt(_)            => pathToConfigFile
      case PromptForConfigFilePermissions => SecureConfig.defaultPermissions
      case PromptForPassword              => password
      case PromptForUpdatedPassword       => password
      case PromptForExistingPassword(_)   => password
      case ReadNextKeyValuePair =>
        if (testConfigEntries.hasNext) {
          testConfigEntries.next()
        } else {
          ""
        }
      case ReadNextKeyValuePairAfterError(_) =>
        if (testConfigEntries.hasNext) {
          testConfigEntries.next()
        } else {
          ""
        }
    }
  }
}
