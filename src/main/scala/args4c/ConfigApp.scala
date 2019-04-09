package args4c

import java.nio.file.{Files, Path, Paths}

import args4c.RichConfig.ParseArg
import args4c.SecureConfig.defaultSecureConfigPath
import com.typesafe.config.{Config, ConfigFactory}

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
  * Subsequent runs of your application would then use '--secure=path/to/encrypted.conf' to load that encrypted configuration and either
  * take the password from an environment variable or standard input.
  *
  *
  * For example, running 'MyApp --setup' would then prompt like this:
  * {{{
  * Save secure config to (/opt/etc/myapp/.config/secure.conf):config/secure.conf
  * Config Permissions (defaults to rwx------): rwxrw----
  * Add config path in the form <key>=<value> (leave blank when finished):myapp.secure.password=hi
  * Add config path in the form <key>=<value> (leave blank when finished):myapp.another.config.entry=123
  * Add config path in the form <key>=<value> (leave blank when finished):
  * Config Password:password
  * }}}
  *
  * Then, running 'MyApp --secure=config/secure.conf -myapp.whocares=visible -show=myapp'
  *
  * would prompt for the password from standard input, then produce the following, hiding the values which were present in the secure config:
  *
  * {{{
  * myapp.another.config.entry : **** obscured **** # secure.conf: 1
  * myapp.secure.password : **** obscured **** # secure.conf: 1
  * myapp.whocares : visible # command-line
  * }}}
  *
  * NOTE: even though the summary obscures the values, they ARE present as PLAIN TEXT STRINGS in the configuration, so take
  * care in limiting the scope of where the configuration is used, either by filtering back out those values, otherwise
  * separating the secure config from the remaining config, or just ensuring to limit the scope of the config itself.
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
    runMain(args)
  }

  /** @return a means to read/write a secure (encrypted) config
    */
  protected def secureConfig: SecureConfig = SecureConfig(Prompt.stdIn())

  /** @param resolvedConfig the configuration we are to run with
    *                       @return any paths for invalid/missing configurations (e.g. a 'password' field is left empty, or a hostPort field)
    */
  def missingRequiredConfigEntriesForConfig(resolvedConfig: Config): Seq[String] = {
    if (resolvedConfig.hasPath(configKeyForRequiredEntries)) {
      resolvedConfig.asList(configKeyForRequiredEntries).filterNot(resolvedConfig.hasValue)
    } else {
      Nil
    }
  }
  protected val configKeyForRequiredEntries = "args4c.requiredConfigPaths"

  /**
    * Exposes a run function which checks the parsedConfig for a 'show' user setting to display the config,
    * otherwise invokes 'run' with the parsed config.
    *
    * This method exposes access to the secure config parse result should the application need to do something with it
    *
    * @param userArgs the original user args
    * @param pathToSecureConfig the path where the secure config should be stored
    * @param secureConfigState the result of the secure config user arguments
    * @param parsedConfig the total configuration, potentially including the secure config
    */
  protected def runWithConfig(userArgs: Array[String], pathToSecureConfig: Path, secureConfigState: SecureConfigState, parsedConfig: Config): Option[Result] = {
    val resolvedConfig = parsedConfig.resolve()
    resolvedConfig.showIfSpecified(obscure(secureConfigState.configOpt.map(_.paths))) match {
      // 'show' was not specified, let's run our app
      case None =>
        val missingRequiredConfigEntries = missingRequiredConfigEntriesForConfig(resolvedConfig)

        val appConfig = if (missingRequiredConfigEntries.nonEmpty) {
          missingRequiredConfigEntries.foldLeft(resolvedConfig) {
            case (config, missingPath) =>
              val value = secureConfig.promptForInput(ReadNextKeyValuePair(missingPath, config))
              config.set(missingPath, value)
          }
        } else {
          resolvedConfig
        }
        val mainAppResult = run(appConfig)

        Option(mainAppResult)
      case Some(specifiedArg) =>
        showValue(specifiedArg, resolvedConfig)
        None
    }
  }

  /** launch the application, which will create a typesafe config instance from the user arguments by :
    *
    * $ try to parse the user arguments into a config entry, interpreting them as key=value pairs or locations of config files
    * $ try to load an encrypted 'secure' config if one has been setup to overlay over the other config
    * $ try to map system environment variables as lowercase dot-separated paths so e.g. (FOO_BAR=x) can be used to override foo.bar
    *
    * In addition to providing a configuration from the user arguments and environment variables, the user arguments are also checked
    * for one of three special arguments:
    *
    * $ The argument 'show=<key substring>' flag, in which case the configuration matching <key substring> is shown.
    * This can be especially convenient to verify the right config values are picked up if there are multiple arguments,
    * such as alternative property files, key=value pairs, etc.
    *
    * $ The argument '--setup' in order to populate a password-encrypted secure config file from standard input.
    * For example, running "MyMainEntryPoint --setup" will proceed to prompt the user for config entries which will be saved
    * in a password-encrypted file with restricted permissions. Subsequent runs of the application will check for this file,
    * either in the default location or from -secure=<path/to/encrypted/config>
    *
    * If either the default or specified encrypted files are found, then the password is taken either from the CONFIG_SECRET if set, or else it prompted for from standard input
    *
    * @param userArgs the user arguments
    * @param setupUserArgFlag the argument to check for in order to run the secure config setup
    * @param ignoreDefaultSecureConfigArg the argument which, if 'userArgs' contains this string, then we will NOT try
    * @param pathToSecureConfigArgFlag the value for the key in the form <key>=<path to secure password config> (e.g. defaults to "--secure", as in --secure=/etc/passwords.conf)
    */
  def runMain(userArgs: Array[String],
              setupUserArgFlag: String = defaultSetupUserArgFlag,
              ignoreDefaultSecureConfigArg: String = defaultIgnoreDefaultSecureConfigArg,
              pathToSecureConfigArgFlag: String = defaultSecureConfigArgFlag): Option[Result] = {

    val pathToSecureConfig: Path = {
      val path = pathToSecureConfigFromArgs(userArgs, pathToSecureConfigArgFlag).getOrElse(SecureConfig.defaultSecureConfigPath())
      Paths.get(path)
    }

    val handledArgs = Set(setupUserArgFlag, ignoreDefaultSecureConfigArg, pathToSecureConfigArgFlag)

    /** Has the user explicitly passed the '--setup' flag?
      */
    if (isSetupSpecified(userArgs, setupUserArgFlag)) {
      val configSoFar                  = defaultConfig().withUserArgs(userArgs, onUnrecognizedUserArg(handledArgs))
      val missingRequiredConfigEntries = missingRequiredConfigEntriesForConfig(configSoFar)
      secureConfig.setupSecureConfig(pathToSecureConfig, missingRequiredConfigEntries.sorted)
      None
    } else {
      val secureConfigState: SecureConfigState = secureConfigForArgs(userArgs, ignoreDefaultSecureConfigArg, pathToSecureConfigArgFlag)
      val parsedConfig = {
        val baseConfig = secureConfigState match {
          case SecureConfigDoesntExist(path) => throw new IllegalStateException(s"Configuration at '$path' doesn't exist")
          case other                         => other.configOpt.fold(defaultConfig())(_.withFallback(defaultConfig()))
        }
        baseConfig.withUserArgs(userArgs, onUnrecognizedUserArg(handledArgs))
      }

      runWithConfig(userArgs, pathToSecureConfig, secureConfigState, parsedConfig)
    }
  }

  protected def isSetupSpecified(userArgs: Array[String], setupArg: String): Boolean = userArgs.contains(setupArg)

  // if we have a 'secure' config, then we should obscure those values
  protected def obscure(securePathsOpt: Option[Seq[String]])(configPath: String, value: String): String = {
    securePathsOpt match {
      case Some(securePaths) =>
        if (securePaths.contains(configPath)) {
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

  /**
    * Represents the state of the '--secure' config
    *
    * @param configOpt
    */
  protected sealed abstract class SecureConfigState(val configOpt: Option[Config])
  protected case class SecureConfigDoesntExist(path: Path)            extends SecureConfigState(None)
  protected case class SecureConfigParsed(path: Path, config: Config) extends SecureConfigState(Some(config))
  protected case object SecureConfigNotSpecified                      extends SecureConfigState(None)

  protected def pathToSecureConfigFromArgs(userArgs: Array[String], pathToSecureConfigArg: String): Option[String] = {
    // our KeyValue regex trims leading '-' characters, so if our 'pathToSecureConfigArgFlag' flag is e.g. '--secure' (i.e., the default),
    // then we need to drop those leading dashes
    val trimmedArg = pathToSecureConfigArg.dropWhile(_ == '-')
    userArgs.collectFirst {
      case KeyValue(`trimmedArg`, file) => file
    }
  }

  protected def secureConfigForArgs(userArgs: Array[String], ignoreDefaultSecureConfigArg: String, pathToSecureConfigArg: String): SecureConfigState = {

    def defaultSecureConfig(userArgs: Array[String]): Option[String] = {
      Option(defaultSecureConfigPath()).filter(path => Files.exists(Paths.get(path)) && !userArgs.contains(ignoreDefaultSecureConfigArg))
    }

    pathToSecureConfigFromArgs(userArgs, pathToSecureConfigArg)
      .orElse(defaultSecureConfig(userArgs))
      .map(Paths.get(_))
      .map { path =>
        secureConfig.readSecureConfigAtPath(path) match {
          case Some(config) => SecureConfigParsed(path, config)
          case None         => SecureConfigDoesntExist(path)
        }
      }
      .getOrElse(SecureConfigNotSpecified)
  }

  /** @return he command-line argument flag which tells the application NOT to load the default secure config file if it exists.
    * e.g., try running the app without the secure config.
    */
  protected def defaultIgnoreDefaultSecureConfigArg: String = envOrProp("IgnoreDefaultSecureConfigArg").getOrElse("--ignoreSecureConfig")

  /** @return the flag which should indicate that we should prompt to setup secure configurations
    */
  protected def defaultSetupUserArgFlag: String = envOrProp("DefaultSetupUserArgFlag").getOrElse("--setup")

  /** @return the command-line argument to specify the path to an encrypted secure config file (e.g. MyApp --secure=.passwords.conf)
    */
  protected def defaultSecureConfigArgFlag: String = envOrProp("DefaultSecureArgFlag").getOrElse("--secure")

}
