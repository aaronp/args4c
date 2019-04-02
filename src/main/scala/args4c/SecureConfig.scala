package args4c

import java.nio.file.attribute.PosixFilePermissions
import java.nio.file.{Files, Path, Paths, StandardOpenOption}

import com.typesafe.config._
import javax.crypto.BadPaddingException

import scala.annotation.tailrec
import scala.compat.Platform
import scala.util.Properties
import scala.util.control.NoStackTrace

/**
  * Makes available a means to initialize a sensitive, encrypted config file via [[SecureConfig.setupSecureConfig]] and [[ConfigApp.secretConfigForArgs]]
  *
  * The idea is that a (service) user-only readable, password-protected AES encrypted config file can be set up via reading entries from
  * standard input, and an application an use those configuration entries thereafter by taking the password from standard input.
  */
object SecureConfig {

  // format: off

  /**
    * The environment variable which, if set, will be used to decrypt an encrypted config file (e.g. "--secure" for the default or "--secure=password.conf" for specifying one)
    */
  val SecretEnvVariableName = "CONFIG_SECRET"


  private[args4c] val defaultPermissions = "rwx------"

  def defaultSecretConfigPath(workDir: String = Properties.userDir): String = {
    Paths.get(workDir).relativize(Paths.get(s"${workDir}/.config/secure.conf")).toString
  }


  def readConfigAtPath(path: Path, pwd: Array[Byte]): Config = {
    require(Files.exists(path), s"$path does not exist")
    val bytes = Files.readAllBytes(path)
    val configText = try {
      Encryption.decryptAES(pwd, bytes, bytes.length)
    } catch {
      case _ : BadPaddingException => throw new IllegalAccessException(s"Invalid password for ${path.getFileName}") with NoStackTrace
    }

    ConfigFactory.parseString(configText, ConfigParseOptions.defaults.setOriginDescription(path.getFileName.toString))
  }
}

case class SecureConfig(promptForInput: Reader) {

  import SecureConfig._

  /** @param defaultSecretConfigFilePath the default path to store the configuration in, either from the --secure=x/y/z user arg, and env variable or default
    * @return the path to the secure config
    */
  def setupSecureConfig(defaultSecretConfigFilePath : Path): Path = {
    val configPath = readSecretConfigPath(defaultSecretConfigFilePath)
    updateSecureConfig(configPath)
  }

  /**
    * prompt for and set some secure values
    *
    * @return the path to the encrypted configuration file
    */
  def updateSecureConfig(configPath : Path): Path = {
    val permissions = readPermissions()

    if (configPath.getParent != null && !Files.exists(configPath.getParent)) {
      Files.createDirectories(configPath.getParent)
    }

    var previousConfigPassword: Option[Array[Byte]] = None

    val config = {
      val newConfig = readSecretConfig()
      val existingConfig = if (Files.exists(configPath)) {
        val pwd = promptForInput(PromptForExistingPassword(configPath)).getBytes("UTF-8")
        previousConfigPassword = Option(pwd)

        readConfigAtPath(configPath, pwd)
      } else {
        ConfigFactory.empty
      }
      val options = ConfigParseOptions.defaults()
        .setSyntax(ConfigSyntax.CONF)
        .setOriginDescription("sensitive")
        .setAllowMissing(false)

      ConfigFactory.parseString(newConfig, options).withFallback(existingConfig)
    }

    val pwd: Array[Byte] = {
      val configPasswordPrompt = if (previousConfigPassword.isEmpty) PromptForPassword else PromptForUpdatedPassword
      promptForInput(configPasswordPrompt) match {
        case "" if previousConfigPassword.nonEmpty => previousConfigPassword.get
        case password => password.getBytes("UTF-8")
      }
    }

    import args4c.implicits._
    val encrypted = config.encrypt(pwd)
    Encryption.clear(pwd)

    import StandardOpenOption._
    
    if (!Files.exists(configPath)) {
      // touch the file to set the permissions
      Files.write(configPath, Array[Byte](), TRUNCATE_EXISTING, CREATE, CREATE_NEW, SYNC)
    }
    Files.setPosixFilePermissions(configPath, permissions)

    // now create the actual file
    Files.write(configPath, encrypted, TRUNCATE_EXISTING, SYNC)
  }

  /**
    * read the configuration from the given path, prompting for the password via 'promptForInput' should the  [[SecureConfig.SecretEnvVariableName]]
    * environment variable not be set
    *
    * @param pathToEncryptedConfig the path pointing at the encrypted config
    * @return a configuration if the file exists
    */
  def readSecretConfig(pathToEncryptedConfig: Path): Option[Config] = {
    if (Files.exists(pathToEncryptedConfig)) {
      val pwd = readConfigPassword()
      val conf = readConfigAtPath(pathToEncryptedConfig, pwd)
      Encryption.clear(pwd)
      Option(conf)
    } else {
      None
    }
  }

  /** @return the application config password used to encrypt the config
    */
  protected def readConfigPassword(): Array[Byte] = {
    envOrProp(SecretEnvVariableName).getOrElse(promptForInput(PromptForPassword)).getBytes("UTF-8")
  }

  private def readSecretConfig(): String = {
    import implicits._
    readNext(Map.empty, ReadNextKeyValuePair).map {
      case (key, value) => s"$key = ${value.quoted}"
    }.mkString(Platform.EOL)
  }

  @tailrec
  private def readNext(entries: Map[String, String], nextPrompt : Prompt): Map[String, String] = {
    promptForInput(nextPrompt).trim match {
      case "" => entries
      case KeyValue(key, value) => readNext(entries.updated(key, value), ReadNextKeyValuePair)
      case other => readNext(entries, ReadNextKeyValuePairAfterError(other))
    }
  }

  private def readPermissions() = {
    val permString = promptForInput(PromptForConfigFilePermissions) match {
      case "" => defaultPermissions
      case other => other
    }

    PosixFilePermissions.fromString(permString)
  }

  /** ask where we should save the config
    */
  private def readSecretConfigPath(pathToSecretConfigFile : Path): Path = {
    promptForInput(SaveSecretPrompt(pathToSecretConfigFile)) match {
      case "" => pathToSecretConfigFile
      case path => Paths.get(path)
    }
  }

  // format: on
}
