package args4c

import java.nio.file.{Files, Path, Paths}

import args4c.RichConfig.ParseArg
import args4c.SecretConfig.{defaultSecretConfigPath, readSecretConfig}
import com.typesafe.config.Config

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

  /** launch the application, which will create a typesafe config instance from the user arguments by :
    *
    * $ try to parse the user arguments into a config entry, interpreting them as key=value pairs or locations of config files
    * $ try to load an encrypted 'secret' config if one has been setup to overlay over the other config
    * $ try to map system environment variables as lowercase dot-separated paths so e.g. (FOO_BAR=x) can be used to override foo.bar
    *
    * $ check the args for the special 'show'
    *
    * @param args
    * @param readLine
    */
  protected def runMain(args: Array[String], readLine: String => String): Unit = {
    if (isPasswordSetup(args)) {
      SecretConfig.writeSecretsUsingPrompt(readLine)
    } else {
      val secretConfOpt: Option[Config] = secretConfigForArgs(args, readLine)
      val config                        = secretConfOpt.fold(defaultConfig())(_.withFallback(defaultConfig)).withUserArgs(args, onUnrecognizedUserArg)

      config.show(obscure(secretConfOpt.map(_.paths))) match {
        // 'show' was not specified, let's run our app
        case None               => run(config)
        case Some(specifiedArg) => showValue(specifiedArg, config)
      }
    }
  }

  protected def isPasswordSetup(userArgs: Array[String]): Boolean = {
    userArgs == Array("admin")
  }

  // if we have a 'secret' config, then we should obscure those values
  protected def obscure(secretPathsOpt: Option[List[String]])(configPath: String, value: String): String = {
    secretPathsOpt match {
      case Some(secretPaths) =>
        if (secretPaths.contains(configPath)) {
          defaultObscuredText
        } else {
          value
        }
      case None => obscurePassword(configPath, value)
    }
  }

  /** @return the default config to overlay the user args over.
    */
  protected def defaultConfig(): Config = args4c.defaultConfig()

  /**
    * displays the value for the given config for when the 'show' command-line arg was specified
    *
    * @param value  the value to show
    * @param config the config value at a particular path
    */
  protected def showValue(value: String, config: Config): Unit = println(value)

  /**
    * Instead of 'main', this 'apply' should run w/ a config
    *
    * @param config the configuration to run with
    */
  def run(config: Config): Unit

  protected def onUnrecognizedUserArg(arg: String): Config = {
    ParseArg.Throw(arg)
  }

  protected def secretConfigForArgs(userArgs: Array[String], readLine: String => String): Option[Config] = {

    def defaultSecretConfig(userArgs: Array[String]): Option[String] = {
      Option(defaultSecretConfigPath()).filter(path => Files.exists(Paths.get(path)) && !userArgs.contains(ConfigApp.IgnoreDefaultSecretConfigArg))
    }

    def pathToSecretConfigFromArgs(userArgs: Array[String]): Option[Path] = {

      val filePathOpt = userArgs.collectFirst {
        case KeyValue(ConfigApp.PathToSecretConfigArg, file) => file
      }
      filePathOpt.orElse(defaultSecretConfig(userArgs)).map(Paths.get(_))
    }

    pathToSecretConfigFromArgs(userArgs).map(readSecretConfig(_, readLine))
  }

}

object ConfigApp {

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

}
