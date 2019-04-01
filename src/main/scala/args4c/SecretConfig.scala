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
  * Makes available a means to initialize a sensitive, encrypted config file via [[SecretConfig.writeSecretsUsingPrompt]] and [[ConfigApp.secretConfigForArgs]]
  *
  * The idea is that a (service) user-only readable, password-protected AES encrypted config file can be set up via reading entries from
  * standard input, and an application an use those configuration entries thereafter by taking the password from standard input.
  */
object SecretConfig {

  // format: off


  /**
    * The environment variable which, if set, will be used to decrypt an encrypted config file (e.g. "--secret" for the default or "--secret=password.conf" for specifying one)
    */
  val SecretEnvVariableName = "CONFIG_SECRET"

  /**
    * prompt for and set some secret values
    *
    * @param readLine a means to accept user input for a particular prompt
    * @return the path to the encrypted configuration file
    */
  def writeSecretsUsingPrompt(secretConfigFilePath : String, readLine: Reader): Path = {
    val configPath = readSecretConfigPath(secretConfigFilePath, readLine)
    val permissions = readPermissions(readLine)

    if (!Files.exists(configPath.getParent)) {
      Files.createDirectories(configPath.getParent)
    }

    var previousConfigPassword: Option[Array[Byte]] = None

    val config = {
      val newConfig = readSecretConfig(readLine)
      val existingConfig = if (Files.exists(configPath)) {
        val pwd = readLine(PromptForExistingPassword(configPath)).getBytes("UTF-8")
        previousConfigPassword = Option(pwd)

        readConfigAtPath(configPath, pwd, readLine)
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
      readLine(configPasswordPrompt) match {
        case "" if previousConfigPassword.nonEmpty =>
          val same = previousConfigPassword.get
          previousConfigPassword.foreach(Encryption.clear)
          same
        case password => password.getBytes("UTF-8")
      }
    }

    import args4c.implicits._
    val encrypted = config.encrypt(pwd)
    Encryption.clear(pwd)

    import StandardOpenOption._
    // touch the file to set the permissions
    Files.write(configPath, Array[Byte](), TRUNCATE_EXISTING, CREATE, CREATE_NEW, SYNC)
    Files.setPosixFilePermissions(configPath, permissions)

    // now create the actual file
    Files.write(configPath, encrypted, APPEND, SYNC)
  }

  /**
    * read the configuration from the given path, prompting for the password via 'readLine' should the  [[SecretEnvVariableName]]
    * environment variable not be set
    *
    * @param pathToEncryptedConfig the path pointing at the encrypted config
    * @param readLine the readline function to get user input
    * @return a configuration if the file exists
    */
  def readSecretConfig(pathToEncryptedConfig: Path, readLine: Reader): Option[Config] = {
    if (Files.exists(pathToEncryptedConfig)) {
      val pwd = readConfigPassword(readLine)
      val conf = readConfigAtPath(pathToEncryptedConfig, pwd, readLine)
      Encryption.clear(pwd)
      Option(conf)
    } else {
      None
    }
  }

  /** @param readLine the standard-in read function
    * @return the application config password used to encrypt the config
    */
  protected def readConfigPassword(readLine: Reader): Array[Byte] = {
    envOrProp(SecretEnvVariableName).getOrElse(readLine(PromptForPassword)).getBytes("UTF-8")
  }

  private def readConfigAtPath(path: Path, pwd: Array[Byte], readLine: Reader): Config = {
    require(Files.exists(path), s"$path does not exist")
    val bytes = Files.readAllBytes(path)
    val configText = try {
      Encryption.decryptAES(pwd, bytes, bytes.length)
    } catch {
      case _ : BadPaddingException => throw new IllegalAccessException(s"Invalid password for ${path.getFileName}") with NoStackTrace
    }
    ConfigFactory.parseString(configText, ConfigParseOptions.defaults.setOriginDescription(path.getFileName.toString))
  }

  private def readSecretConfig(readLine: Reader): String = {
    import implicits._
    readNext(Map.empty, ReadNextKeyValuePair, readLine).map {
      case (key, value) => s"$key = ${value.quoted}"
    }.mkString(Platform.EOL)
  }

  @tailrec
  private def readNext(entries: Map[String, String], nextPrompt : Prompt, readLine: Reader): Map[String, String] = {
    readLine(nextPrompt).trim match {
      case "" => entries
      case KeyValue(key, value) => readNext(entries.updated(key, value), ReadNextKeyValuePair, readLine)
      case other => readNext(entries, ReadNextKeyValuePairAfterError(other), readLine)
    }
  }

  private def readPermissions(readLine: Reader) = {
    val permString = readLine(PromptForConfigFilePermissions) match {
      case "" => defaultPermissions
      case other => other
    }

    PosixFilePermissions.fromString(permString)
  }

  private def readSecretConfigPath(pathToSecretConfigFile : String, readLine: Reader): Path = {
    val path = readLine(SaveSecretPrompt(pathToSecretConfigFile)) match {
      case "" => pathToSecretConfigFile
      case path => path
    }
    Paths.get(path)
  }

  private[args4c] val defaultPermissions = "rwx------"

  def defaultSecretConfigPath(workDir: String = Properties.userDir): String = {
    Paths.get(workDir).relativize(Paths.get(s"${workDir}/.config/secret.conf")).toString
  }

  // format: on
}
