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

  def defaultSecretConfigPath = s"${Properties.userDir}/.config/secret.conf"

  type Prompt = String
  private val KeyValueR = "(.*)=(.*)".r

  def getSecretConfig(args: Array[String], readLine: String => String): Option[Config] = {
    val pathOpt = {
      val filePathOpt = args.collectFirst {
        case KeyValueR("prompt", file) => file
      }
      filePathOpt.orElse {
        Option(defaultSecretConfigPath).filter(_ => args.contains("prompt"))
      }
    }
    pathOpt.map(Files.readAllBytes(_)).map { bytes =>
      val pwd        = readLine("Password:").getBytes("UTF-8")
      val configText = Encryption.decryptAES(pwd, bytes, bytes.length)
      Encryption.clear(pwd)
      ConfigFactory.parseString(configText)
    }
  }

  def setSecrets(readLine: Prompt => String): Path = {
    val config      = readSecretConfig(readLine)
    val configPath  = readSecretConfigPath(readLine)
    val permissions = readPermissions(readLine)

    if (!Files.exists(configPath.getParent)) {
      Files.createDirectories(configPath.getParent, PosixFilePermissions.asFileAttribute(permissions))
    }

    val pwd            = readLine("Config Password:").getBytes("UTF-8")
    val (_, encrypted) = Encryption.encryptAES(pwd, config)
    Encryption.clear(pwd)

    import StandardOpenOption._

    // touch the file to set the permissions
    Files.write(configPath, Array[Byte](), TRUNCATE_EXISTING, CREATE, CREATE_NEW, SYNC)
    Files.setPosixFilePermissions(configPath, permissions)

    // now create the actual file
    Files.write(configPath, encrypted, TRUNCATE_EXISTING, CREATE, CREATE_NEW, SYNC)
  }

  // format: off
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
      val default = "r--------"
      val permString = readLine(s"Config Permissions (defaults to $default):") match {
        case ""    => default
        case other => other
      }
      
    PosixFilePermissions.fromString(permString)
  }

  def readSecretConfigPath(readLine: Prompt => String): Path = {
    val path = readLine(s"Save secret config to (defaults to ${defaultSecretConfigPath}):") match {
      case ""   => default
      case path => path
    }
    Paths.get(path)
  }

  // format: on
}
