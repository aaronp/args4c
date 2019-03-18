package args4c

import java.nio.file.attribute.PosixFilePermissions
import java.nio.file.{Files, Path, Paths, StandardOpenOption}

import com.typesafe.config._

import scala.annotation.tailrec
import scala.compat.Platform
import scala.util.Properties

/**
  * Makes available a means to initialize a sensitive, encrypted config file via [[SecretConfig.writeSecretsUsingPrompt]] and [[SecretConfig.getSecretConfig]]
  *
  * The idea is that a (service) user-only readable, password-protected AES encrypted config file can be set up via reading entries from
  * standard input, and an application an use those configuration entries thereafter by taking the password from standard input.
  */
object SecretConfig {

  // format: off
  type Prompt = String

  /**
    * The command-line argument to specify the path to an encrypted secret config file
    * (e.g. MyApp -secret=.passwords.conf)
    */
  val PathToSecretConfigArg = "-secret"

  /**
    * The command-line argument flag which tells the application NOT to load the default secret config file if it exists.
    * e.g., try running the app without the secret config.
    */
  val IgnoreDefaultSecretConfigArg = sys.env.getOrElse("IgnoreDefaultSecretConfigArg", "--ignoreSecretConfig")

  /**
    * prompt for and set some secret values
    *
    * @param readLine a means to accept user input for a particular prompt
    * @return the path to the encrypted configuration file
    */
  def writeSecretsUsingPrompt(readLine: Prompt => String): Path = {
    val configPath = readSecretConfigPath(readLine)
    val permissions = readPermissions(readLine)

    if (!Files.exists(configPath.getParent)) {
      Files.createDirectories(configPath.getParent, PosixFilePermissions.asFileAttribute(permissions))
      Files.createDirectories(configPath.getParent)
    }

    var previousConfigPassword: Option[Array[Byte]] = None

    val config = {
      val newConfig = readSecretConfig(readLine)
      val existingConfig = if (Files.exists(configPath)) {
        val pwd = readLine(s"A config already exists at $configPath, enter password:").getBytes("UTF-8")
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

    val pwd = {
      val configPasswordPrompt = if (previousConfigPassword.isEmpty) "Config Password:" else "New Config Password (or blank to reuse the existing one):"
      readLine(configPasswordPrompt) match {
        case "" if previousConfigPassword.nonEmpty =>
          val same = previousConfigPassword.get
          previousConfigPassword.foreach(Encryption.clear)
          same
        case password => password.getBytes("UTF-8")
      }
    }
    val configString = {
      import args4c.implicits._
      config.asJson
    }
    val (_, encrypted) = Encryption.encryptAES(pwd, configString)
    Encryption.clear(pwd)

    import StandardOpenOption._

    // touch the file to set the permissions
    Files.write(configPath, Array[Byte](), TRUNCATE_EXISTING, CREATE, CREATE_NEW, SYNC)
    Files.setPosixFilePermissions(configPath, permissions)

    // now create the actual file
    Files.write(configPath, encrypted, APPEND, SYNC)
  }

  def pathToSecretConfigFromArgs(args: Array[String]): Option[Path] = {
      val filePathOpt = args.collectFirst {
        case KeyValue(PathToSecretConfigArg, file) => file
      }
      filePathOpt.orElse {
        Option(defaultSecretConfigPath()).filter(path => Files.exists(Paths.get(path)) && !args.contains(IgnoreDefaultSecretConfigArg))
      }.map(Paths.get(_))
  }

  def getSecretConfig(args: Array[String], readLine: String => String): Option[Config] = {
    pathToSecretConfigFromArgs(args).map(readSecretConfig(_, readLine))
  }

  def readSecretConfig(path: Path, readLine: String => String) = {
    val pwd = readLine("Config Password:").getBytes("UTF-8")
    val conf = readConfigAtPath(path, pwd, readLine)
    Encryption.clear(pwd)
    conf
  }

  private def readConfigAtPath(path: Path, pwd: Array[Byte], readLine: String => String): Config = {
    val bytes = Files.readAllBytes(path)
    val configText = Encryption.decryptAES(pwd, bytes, bytes.length)
    ConfigFactory.parseString(configText)
  }

  private def readSecretConfig(readLine: Prompt => String): String = {
    readNext(Map.empty, readLine).map {
      case (key, value) => s"$key = ${ConfigUtil.quoteString(value)}"
    }.mkString(Platform.EOL)
  }

  @tailrec
  private def readNext(entries: Map[String, String], readLine: Prompt => String): Map[String, String] = {
    readLine("Add config path in the form <key>=<value> (leave blank when finished):").trim match {
      case KeyValue(key, value) =>
        readNext(entries.updated(key, value), readLine)
      case "" => entries
      case _ =>
        System.err.println("Invalid key=value pair. Entries should be in the for <path.to.config.entry>=some sensitive value")
        entries
    }
  }

  private def readPermissions(readLine: Prompt => String) = {
    val permString = readLine(s"Config Permissions (defaults to $defaultPermissions):") match {
      case "" => defaultPermissions
      case other => other
    }

    PosixFilePermissions.fromString(permString)
  }

  private def readSecretConfigPath(readLine: Prompt => String): Path = {
    val path = readLine(saveSecretPrompt()) match {
      case "" => defaultSecretConfigPath()
      case path => path
    }
    Paths.get(path)
  }

  private[args4c] def defaultPermissions = "rwx------"

  private[args4c] def saveSecretPrompt(workDir: String = Properties.userDir) = s"Save secret config to (defaults to ${defaultSecretConfigPath(workDir)}):"

  private[args4c] def defaultSecretConfigPath(workDir: String = Properties.userDir): String = s"${workDir}/.config/secret.conf"

  // format: on
}
