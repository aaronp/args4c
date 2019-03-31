package args4c

import java.nio.file.{Files, Path, Paths}

import args4c.RichConfig.ParseArg
import args4c.SecretConfig.{Prompt, Reader, defaultSecretConfigPath, readSecretConfig}
import com.typesafe.config.{Config, ConfigFactory}

import scala.io.StdIn

/**
  * A convenience mix-in utility for a main entry point.
  *
  * It parsed the user arguments using the default config (which is ConfigFactory.load() but w/ system environment variables overlaid)
  *
  * If the config has a 'show=<path>' in it, then that path will be printed out and the program with return.
  *
  * e.g. MyAppWhichExtendsConfigApp show=myapp.database.url
  *
  * will display the value of myapp.database.url
  *
  * It also interprets a single '--setup' to enable the configuration of sensitive configuration entries into a locally encrypted file.
  *
  * Subsequent runs of your application would then use '--secret=path/to/encrypted.conf' to load that encrypted configuration and either
  * take the password from an environment variable or standard input.
  *
  *
  * For example, running 'MyApp --setup' would then prompt like this:
  * {{{
  * Save secret config to (/opt/etc/myapp/.config/secret.conf):config/secret.conf
  * Config Permissions (defaults to rwx------): rwxrw----
  * Add config path in the form <key>=<value> (leave blank when finished):myapp.secret.password=hi
  * Add config path in the form <key>=<value> (leave blank when finished):myapp.another.config.entry=123
  * Add config path in the form <key>=<value> (leave blank when finished):
  * Config Password:password
  * }}}
  *
  * Then, running 'MyApp --secret=config/secret.conf -myapp.whocares=visible -show=myapp'
  *
  * would prompt for the password from standard input, then produce the following, hiding the values which were present in the secret config:
  *
  * {{{
  * myapp.another.config.entry : **** obscured **** # secret.conf: 1
  * myapp.secret.password : **** obscured **** # secret.conf: 1
  * myapp.whocares : visible # command-line
  * }}}
  *
  * NOTE: even though the summary obscures the values, they ARE present as PLAIN TEXT STRINGS in the configuration, so take
  * care in limiting the scope of where the configuration is used, either by filtering back out those values, otherwise
  * separating the secret config from the remaining config, or just ensuring to limit the scope of the config itself.
  */
trait ConfigApp extends LowPriorityArgs4cImplicits {

  /**
    * The result of running this application
    */
  type Result

  /**
    * exposes a main entry point which will then:
    *
    * 1) parse the user args as a configuration
    * 2) check the user args if we should just 'show' a particular configuration setting (obscuring sensitive entries)
    * 3) check the user args if we should run 'setup' to configure an encrypted configuration
    *
    * @param args the user arguments
    */
  def main(args: Array[String]): Unit = {
    runMain(args, Prompt.stdIn)
  }

  /**
    * Exposes a run function which checks the parsedConfig for a 'show' user setting to display the config,
    * otherwise invokes 'run' with the parsed config.
    *
    * This method exposes access to the secret config parse result should the application need to do something with it
    *
    * @param userArgs the original user args
    * @param pathToSecretConfig the path where the secret config should be stored
    * @param secretConfig the result of the secret config user arguments
    * @param parsedConfig the total configuration, potentially including the secret config
    */
  protected def runWithConfig(userArgs: Array[String],
                              pathToSecretConfig : String,
                              secretConfig: SecretConfigResult,
                              parsedConfig: Config): Option[Result] = {
    parsedConfig.showIfSpecified(obscure(secretConfig.configOpt.map(_.paths))) match {
      // 'show' was not specified, let's run our app
      case None => Option(run(parsedConfig))
      case Some(specifiedArg) =>
        showValue(specifiedArg, parsedConfig)
        None
    }
  }

  /** launch the application, which will create a typesafe config instance from the user arguments by :
    *
    * $ try to parse the user arguments into a config entry, interpreting them as key=value pairs or locations of config files
    * $ try to load an encrypted 'secret' config if one has been setup to overlay over the other config
    * $ try to map system environment variables as lowercase dot-separated paths so e.g. (FOO_BAR=x) can be used to override foo.bar
    *
    * In addition to providing a configuration from the user arguments and environment variables, the user arguments are also checked
    * for one of three special arguments:
    *
    * $ The argument 'show=<key substring>' flag, in which case the configuration matching <key substring> is shown.
    * This can be especially convenient to verify the right config values are picked up if there are multiple arguments,
    * such as alternative property files, key=value pairs, etc.
    *
    * $ The argument '--setup' in order to populate a password-encrypted secret config file from standard input.
    * For example, running "MyMainEntryPoint --setup" will proceed to prompt the user for config entries which will be saved
    * in a password-encrypted file with restricted permissions. Subsequent runs of the application will check for this file,
    * either in the default location or from -secret=<path/to/encrypted/config>
    *
    * If either the default or specified encrypted files are found, then the password is taken either from the CONFIG_SECRET if set, or else it prompted for from standard input
    *
    * @param userArgs the user arguments
    * @param readLine a function to read in the user input
    * @param setupUserArgFlag the argument to check for in order to run the secret config setup
    * @param ignoreDefaultSecretConfigArg the argument which, if 'userArgs' contains this string, then we will NOT try
    * @param pathToSecretConfigArg the value for the key in the form <key>=<path to secret password config> (e.g. defaults to "--secret", as in --secret=/etc/passwords.conf)
    */
  def runMain(userArgs: Array[String],
              readLine: Reader,
              setupUserArgFlag: String = defaultSetupUserArgFlag,
              ignoreDefaultSecretConfigArg: String = defaultIgnoreDefaultSecretConfigArg,
              pathToSecretConfigArg: String = defaultSecretConfigArg): Option[Result] = {

    val pathToSecretConfig: String = pathToSecretConfigFromArgs(userArgs, pathToSecretConfigArg).getOrElse(SecretConfig.defaultSecretConfigPath())

    /**
      * should we configure the local passwords?
      */
    if (isPasswordSetup(userArgs, setupUserArgFlag)) {
      SecretConfig.writeSecretsUsingPrompt(pathToSecretConfig, readLine)
      None
    } else {
      val secretConfig: SecretConfigResult = secretConfigForArgs(userArgs, readLine, ignoreDefaultSecretConfigArg, pathToSecretConfigArg)
      val parsedConfig = {
        val handledArgs = Set(setupUserArgFlag, ignoreDefaultSecretConfigArg, pathToSecretConfigArg)
        val baseConfig  = secretConfig.configOpt.fold(defaultConfig())(_.withFallback(defaultConfig()))
        baseConfig.withUserArgs(userArgs, onUnrecognizedUserArg(handledArgs))
      }

      runWithConfig(userArgs, pathToSecretConfig, secretConfig, parsedConfig)
    }
  }

  protected def isPasswordSetup(userArgs: Array[String], setupArg: String): Boolean = userArgs.contains(setupArg)

  // if we have a 'secret' config, then we should obscure those values
  protected def obscure(secretPathsOpt: Option[Seq[String]])(configPath: String, value: String): String = {
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
  def defaultConfig(): Config = args4c.defaultConfig()

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
  def run(config: Config): Result

  protected def onUnrecognizedUserArg(allowedArgs: Set[String])(arg: String): Config = {
    if (allowedArgs.contains(arg)) {
      ConfigFactory.empty
    } else {
      ParseArg.Throw(arg)
    }
  }

  protected sealed abstract class SecretConfigResult(val configOpt: Option[Config])
  protected case class SecretConfigDoesntExist(path: Path)            extends SecretConfigResult(None)
  protected case class SecretConfigParsed(path: Path, config: Config) extends SecretConfigResult(Some(config))
  protected case object SecretConfigNotSpecified                      extends SecretConfigResult(None)

  protected def pathToSecretConfigFromArgs(userArgs: Array[String], pathToSecretConfigArg: String): Option[String] = {
    // our KeyValue regex trims leading '-' characters, so if our 'pathToSecretConfigArg' flag is e.g. '--secret' (i.e., the default),
    // then we need to drop those leading dashes
    val trimmedArg = pathToSecretConfigArg.dropWhile(_ == '-')
    userArgs.collectFirst {
      case KeyValue(`trimmedArg`, file) => file
    }
  }

  protected def secretConfigForArgs(userArgs: Array[String],
                                    readLine: String => String,
                                    ignoreDefaultSecretConfigArg: String,
                                    pathToSecretConfigArg: String): SecretConfigResult = {

    def defaultSecretConfig(userArgs: Array[String]): Option[String] = {
      Option(defaultSecretConfigPath()).filter(path => Files.exists(Paths.get(path)) && !userArgs.contains(ignoreDefaultSecretConfigArg))
    }

    pathToSecretConfigFromArgs(userArgs, pathToSecretConfigArg)
      .orElse(defaultSecretConfig(userArgs))
      .map(Paths.get(_))
      .map { path =>
        readSecretConfig(path, readLine) match {
          case Some(config) => SecretConfigParsed(path, config)
          case None         => SecretConfigDoesntExist(path)
        }
      }
      .getOrElse(SecretConfigNotSpecified)
  }

  /** @return he command-line argument flag which tells the application NOT to load the default secret config file if it exists.
    * e.g., try running the app without the secret config.
    */
  protected def defaultIgnoreDefaultSecretConfigArg: String = envOrProp("IgnoreDefaultSecretConfigArg").getOrElse("--ignoreSecretConfig")

  /** @return the flag which should indicate that we should prompt to setup secret configurations
    */
  protected def defaultSetupUserArgFlag: String = envOrProp("DefaultSetupUserArgFlag").getOrElse("--setup")

  /** @return the command-line argument to specify the path to an encrypted secret config file (e.g. MyApp -secret=.passwords.conf)
    */
  protected def defaultSecretConfigArg: String = envOrProp("DefaultSecretArgFlag").getOrElse("--secret")

}
