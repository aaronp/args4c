package args4c
import java.nio.file.attribute.PosixFilePermissions
import java.nio.file.{Files, Path, Paths, StandardOpenOption}

import com.typesafe.config.{Config, ConfigFactory}

import scala.annotation.tailrec
import scala.compat.Platform
import scala.util.Properties

/**
  * Makes available a means to initialize a secret config
  */
object Initialize {

  // format: off
  type Prompt = String
  private val KeyValueR = "(.*)=(.*)".r

  def getSecretConfig(args: Array[String], readLine: String => String): Option[Config] = {
    val pathOpt: Option[Path] = {
      val filePathOpt = args.collectFirst {
        case KeyValueR("prompt", file) => file
      }
      filePathOpt.orElse {
          Option(defaultSecretConfigPath).filter(_ => args.contains("prompt"))
        }.map(Paths.get(_))
    }

    pathOpt.map(readConfigAtPath(_, "Password:", readLine))
  }

  def readConfigAtPath(path: Path, prompt : String, readLine: String => String): Config = {
    val bytes      = Files.readAllBytes(path)
    val pwd        = readLine(prompt).getBytes("UTF-8")
    val configText = Encryption.decryptAES(pwd, bytes, bytes.length)
    Encryption.clear(pwd)
    ConfigFactory.parseString(configText)
  }

  /**
    * prompt for and set some secret values
    *
    * @param readLine
    * @return
    */
  def setSecrets(readLine: Prompt => String): Path = {
    val configPath  = readSecretConfigPath(readLine)
    val permissions = readPermissions(readLine)

    if (!Files.exists(configPath.getParent)) {
//      Files.createDirectories(configPath.getParent, PosixFilePermissions.asFileAttribute(permissions))
      Files.createDirectories(configPath.getParent)
    }

    val config      = {
      val newConfig = readSecretConfig(readLine)
      val existingConfig = if (Files.exists(configPath)) {
        readConfigAtPath(configPath, "Password:", readLine)
      } else {
        ConfigFactory.empty
      }
      ConfigFactory.parseString(newConfig).withFallback(existingConfig)
    }

    val pwd            = readLine("Config Password:").getBytes("UTF-8")
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
    Files.write(configPath, encrypted, TRUNCATE_EXISTING, CREATE, CREATE_NEW, SYNC)
  }

  @tailrec
  private def readNext(entries: Map[String, String], readLine: Prompt => String): Map[String, String] = {
    readLine("Next sensitive config path:") match {
      case KeyValueR(key, value) =>
        readNext(entries.updated(key, value), readLine)
      case "" => entries
      case _ =>
        System.err.println("Invalid key=value pair. Entries should be in the for <path.to.config.entry>=some sensitive value")
        entries
    }
  }

  def readSecretConfig(readLine: Prompt => String): String = {
    readNext(Map.empty, readLine).map {
        case (key, value) => s"$key : $value"
    }.mkString(Platform.EOL)
  }

  def readPermissions(readLine: Prompt => String) = {
      val default = "rw-------"
      val permString = readLine(s"Config Permissions (defaults to $default):") match {
        case ""    => default
        case other => other
      }
      
    PosixFilePermissions.fromString(permString)
  }

  def readSecretConfigPath(readLine: Prompt => String): Path = {
    val path = readLine(s"Save secret config to (defaults to ${defaultSecretConfigPath}):") match {
      case ""   => defaultSecretConfigPath
      case path => path
    }
    Paths.get(path)
  }

  def defaultSecretConfigPath = s"${Properties.userDir}/.config/secret.conf"

  // format: on
}
