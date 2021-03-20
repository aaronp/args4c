package args4c

import com.typesafe.config._

import java.nio.file.attribute.PosixFilePermissions
import java.nio.file.{Files, Path, Paths, StandardOpenOption}
import javax.crypto.BadPaddingException
import scala.annotation.tailrec
import scala.compat.Platform
import scala.util.Properties
import scala.util.control.NoStackTrace

/**
  * Makes available a means to initialize a sensitive, encrypted config file via [[SecureConfig.setupSecureConfig]] and [[ConfigApp.secureConfigForArgs]]
  *
  * The idea is that a (service) user-only readable, password-protected AES encrypted config file can be set up via reading entries from
  * standard input, and an application an use those configuration entries thereafter by taking the password from standard input.
  */
object SecureConfig:

  // format: off

  /**
    * The environment variable which, if set, will be used to decrypt an encrypted config file (e.g. "--secure" for the default or "--secure=password.conf" for specifying one)
    */
  val SecureEnvVariableName = "CONFIG_SECRET"


  private[args4c] val defaultPermissions = "rwx------"

  def defaultSecureConfigPath(workDir: String = Properties.userDir): String =
    Paths.get(workDir).relativize(Paths.get(s"${workDir}/.config/secure.conf")).toString


  def readConfigAtPath(path: Path, pwd: Array[Byte]): Config =
    require(Files.exists(path), s"$path does not exist")
    val bytes = Files.readAllBytes(path)
    val configText = try
      Encryption.decryptAES(pwd, bytes, bytes.length)
    catch
      case _ : BadPaddingException => throw new IllegalAccessException(s"Invalid password for ${path.getFileName}") with NoStackTrace

    ConfigFactory.parseString(configText, ConfigParseOptions.defaults.setOriginDescription(path.getFileName.toString))

case class SecureConfig(promptForInput: UserInput):

  import SecureConfig._

  /** @param defaultSecureConfigFilePath the default path to store the configuration in, either from the --secure=x/y/z user arg, and env variable or default
    * @param requiredPaths a potentially empty list of configuration paths which need to have values specified
    * @return the path to the secure config
    */
  def setupSecureConfig(defaultSecureConfigFilePath : Path, requiredPaths : Seq[String] = Nil): Path =
    val configPath = readSecureConfigPath(defaultSecureConfigFilePath)
    updateSecureConfig(configPath, requiredPaths)

  /**
    * prompt for and set some secure values
    *
    * @param configPath the path where the secure config is written
    * @param requiredPaths a potentially empty list of configuration paths which need to have values specified
    * @return the path to the encrypted configuration file
    */
  def updateSecureConfig(configPath : Path, requiredPaths : Seq[String]): Path =
    val permissions = readPermissions()

    if configPath.getParent != null && !Files.exists(configPath.getParent) then
      Files.createDirectories(configPath.getParent)

    val (previousConfigPassword, config) = readSecureConfig(configPath, requiredPaths.distinct)

    val pwd: Array[Byte] =
      val configPasswordPrompt = if previousConfigPassword.isEmpty then PromptForPassword else PromptForUpdatedPassword
      promptForInput(configPasswordPrompt) match
        case "" if previousConfigPassword.nonEmpty => previousConfigPassword.get
        case password => password.getBytes("UTF-8")

    import args4c.implicits._
    val encrypted = config.encrypt(pwd)
    Encryption.clear(pwd)

    import StandardOpenOption._
    
    if !Files.exists(configPath) then
      // touch the file to set the permissions
      Files.write(configPath, Array[Byte](), TRUNCATE_EXISTING, CREATE, CREATE_NEW, SYNC)
    Files.setPosixFilePermissions(configPath, permissions)

    // now create the actual file
    Files.write(configPath, encrypted, TRUNCATE_EXISTING, SYNC)

  /**
    * read the configuration from the given path, prompting for the password via 'promptForInput' should the  [[SecureConfig.SecureEnvVariableName]]
    * environment variable not be set
    *
    * @param pathToEncryptedConfig the path pointing at the encrypted config
    * @return a configuration if the file exists
    */
  def readSecureConfigAtPath(pathToEncryptedConfig: Path): Option[Config] =
    if Files.exists(pathToEncryptedConfig) then
      val pwd = readConfigPassword()
      val conf = readConfigAtPath(pathToEncryptedConfig, pwd)
      Encryption.clear(pwd)
      Option(conf)
    else
      None

  /** @return the application config password used to encrypt the config
    */
  protected def readConfigPassword(): Array[Byte] =
    envOrProp(SecureEnvVariableName).getOrElse(promptForInput(PromptForPassword)).getBytes("UTF-8")

  /** @return the user-supplied key/value pairs as a parse-able block of text
    */
  private def readSecureConfig(configPath : Path, requiredPaths : Seq[String])  =
    import implicits._

    var previousConfigPassword: Option[Array[Byte]] = None

    val existingConfig: Config = if Files.exists(configPath) then
      val pwd = promptForInput(PromptForExistingPassword(configPath)).getBytes("UTF-8")
      previousConfigPassword = Option(pwd)

      readConfigAtPath(configPath, pwd)
    else
      ConfigFactory.empty

    val filteredRequired = requiredPaths.filterNot(existingConfig.hasValue)
    previousConfigPassword -> readNextRecursive(existingConfig, filteredRequired.headOption, ReadNextKeyValuePair(filteredRequired.headOption.getOrElse(""), existingConfig), filteredRequired)

  @tailrec
  private def readNextRecursive(wipConfig: Config, promptedForConfigPath : Option[String], nextPrompt : Prompt, requiredPaths : Seq[String]): Config =

    import implicits._
    promptForInput(nextPrompt).trim match
      case ""  =>
        promptedForConfigPath match
            //
            // they were prompted for a value but intentionally left it blank
            //
          case Some(key) =>
            val newRequired = requiredPaths.filterNot(_ == key)
            readNextRecursive(wipConfig, newRequired.headOption, ReadNextKeyValuePair(newRequired.headOption.getOrElse(""), wipConfig), newRequired)

            //
            // they were unprompted, and so are done entering values
            //
          case None =>
            wipConfig

        //
        // The case where the user was NOT prompted to supply an explicit entry (the previousKey is None) and so supplied the text 'foo.path=bar'
        //
      case KeyValue(key, value) if promptedForConfigPath.isEmpty =>
        val updated = wipConfig.set(key.trim, value.trim)
        val newRequired = requiredPaths.filterNot(updated.hasValue)
        readNextRecursive(updated, newRequired.headOption, ReadNextKeyValuePair(newRequired.headOption.getOrElse(""), updated), newRequired)
      case other =>
        promptedForConfigPath match
          case None =>
            val newRequired = requiredPaths.filterNot(wipConfig.hasValue)
            readNextRecursive(wipConfig, newRequired.headOption, ReadNextKeyValuePairAfterError(other), requiredPaths)
          case Some(promptedForKey) =>
            val updated = wipConfig.set(promptedForKey, other)
            val newRequired = requiredPaths.filterNot(updated.hasValue)
            readNextRecursive(updated, newRequired.headOption, ReadNextKeyValuePair(newRequired.headOption.getOrElse(""), updated), newRequired)

  private def readPermissions() =
    val permString = promptForInput(PromptForConfigFilePermissions) match
      case "" => defaultPermissions
      case other => other

    PosixFilePermissions.fromString(permString)

  /** ask where we should save the config
    */
  private def readSecureConfigPath(pathToSecureConfigFile : Path): Path =
    promptForInput(SaveSecretPrompt(pathToSecureConfigFile)) match
      case "" => pathToSecureConfigFile
      case path => Paths.get(path)

  // format: on
