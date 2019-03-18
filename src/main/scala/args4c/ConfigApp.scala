package args4c
import java.nio.file.{Files, Path}

import args4c.RichConfig.ParseArg
import com.typesafe.config.{Config, ConfigFactory}

import scala.io.StdIn

/**
  * A convenience mix-in utility for a main entry point.
  *
  * It parsed the user arguments using the default config (which is ConfigFactory.load() but w/ system environment variables overlaid)
  *
  * And, if the config has a 'show=<path>' in it, then that path will be printed out and the program with return.
  *
  * e.g. MyAppWhichExtendsConfigApp show=myapp.database.url
  *
  * will display the value of myapp.database.url
  */
trait ConfigApp extends LowPriorityArgs4cImplicits {

  def main(args: Array[String]): Unit = {
    runMain(args, StdIn.readLine(_))
  }

  protected def runMain(args: Array[String], readLine: String => String): Unit = {

    if (args.size == 1 && args(0) == "admin") {
      SecretConfig.writeSecretsUsingPrompt(readLine)
    } else {

      val secretConfOpt = SecretConfig.getSecretConfig(args, readLine)
      val config        = secretConfOpt.fold(defaultConfig())(_.withFallback(defaultConfig)).withUserArgs(args, onUnrecognizedUserArg)

      // if we have a 'secret' config, then we should obscure those values
      val obscureFnc: (String, String) => String = {
        secretConfOpt match {
          case Some(secretConf) =>
            val secretPaths: List[String] = secretConf.paths
            def isSecret(configPath: String, value: String) = {
              if (secretPaths.contains(configPath)) {
                defaultObscuredText
              } else {
                value
              }
            }
            isSecret(_, _)
          case None => obscurePassword(_, _)
        }
      }

      config.show(obscureFnc) match {
        case None => // show not specified, let's run our app
          apply(config)
        case Some(specifiedArg) => show(specifiedArg, config)
      }
    }
  }

  protected def defaultConfig(): Config = args4c.defaultConfig()

  def configFrom(file: Path, pwd: Array[Byte]): Config = {
    val configBytes = Files.readAllBytes(file)
    val configText  = Encryption.decryptAES(pwd, configBytes, configBytes.length)
    Encryption.clear(pwd)
    ConfigFactory.parseString(configText)
  }

  protected def show(value: String, config: Config): Unit = println(value)

  /**
    * Instead of 'main', this 'apply' should run w/ a config
    *
    * @param config the configuration to run with
    */
  def apply(config: Config): Unit

  protected def onUnrecognizedUserArg(arg: String): Config = {
    ParseArg.Throw(arg)
  }

}
