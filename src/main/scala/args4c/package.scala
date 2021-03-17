import java.net.URL
import java.nio.file.{Files, Path, Paths}

import args4c.RichConfig.ParseArg
import com.typesafe.config._

import scala.sys.SystemProperties

/**
  * Args4c (arguments for configuration) is intended to add some helpers and utilities in obtaining a typesafe
  * configuration from user arguments.
  *
  * The core is simply to convert an Array[String] to a Config where the arguments are either paths to configuration resources
  * or simple key=value pairs via [[args4c.configForArgs]]
  *
  * Left-most arguments take precedence. In this example, we assume 'prod.conf' is a resource on the classpath:
  *
  * {{{
  *   MyApp foo.x=bar foo.x=ignored /opt/etc/overrides.conf prod.conf
  * }}}
  *
  * In addition to this core concept, this library also provides some additional configuration utilities via [[args4c.RichConfigOps]]
  * which can be made available by extending [[args4c.LowPriorityArgs4cImplicits]] or importing [[args4c.implicits]]:
  *
  * {{{
  *   import args4c.implicits._
  *   object MyApp {
  *      override def main(args : Array[String]) : Unit = {
  *        val config = args.asConfig()
  *        println("Starting MyApp with config:")
  *
  *        // let's "log" our app's config on startup:
  *        val flatSummary : String = config.filter(_.startsWith("myapp")).summary()
  *        println(flatSummary) // "logging" our config
  *      }
  *   }
  * }}}
  *
  * Where the 'summary' will produce sorted [[args4c.StringEntry]] values with potentially sensitive entries (e.g. passwords)
  * obscured and a source comment for some sanity as to where each entry comes from:
  *
  * {{{
  * myapp.foo : bar # command-line
  * myapp.password : **** obscured **** # command-line
  * myapp.saveTo : afile # file:/opt/etc/myapp/test.conf@3
  * }}}
  *
  * Also, when extracting user arguments into a configuration, an additional 'fallback' config is specified.
  * Typically this would just be the ConfigFactory.load() configuration, but args4c uses the [[args4c.defaultConfig]],
  * which is essentially just the system environment variables converted from snake-caes to dotted lowercase values
  * first, then falling back on ConfigFactory.load().
  *
  * Applications can elect to not have this behaviour and provide their own fallback configs when parsing args, but
  * the default provides a convenience for system environment variables to override e.g. 'foo.bar.x=default' by specifying
  *
  * {{{
  *   FOO_BAR_X=override
  * }}}
  *
  * as a system environment variable. Otherwise you may end up having to repeat this sort of thing all over you config:
  * {{{
  *   foo.bar=default
  *   foo.bar=$${?FOO_BAR}
  *
  *   foo.bazz=default2
  *   foo.bazz=$${?FOO_BAZZ}
  *
  *   ...
  * }}}
  *
  *
  * Finally, args4c also provides a [[args4c.ConfigApp]] which provides some additional functionality to configuration-based
  * applications.
  *
  */
package object args4c {

  /** A means to get a values from user prompts in order to set up a secure configuration
    */
  type UserInput = Prompt => String

  private val UnquoteR = "\\s*\"(.*)\"\\s*".r

  /**
    * trims and unquotes a string (the single quotes is mine - added to demonstrate the full text):
    *
    * {{{
    *  '"quoted"'     becomes: 'quoted'
    *  '  "quoted"  ' becomes: 'quoted'
    *  'quoted"  '    is unchanged: 'quoted"  '
    *  '"quoted '     is unchanged: 'quoted"  '
    * }}}
    *
    * @param str the string to unquote
    * @return either the string unchanged or the single quotes removed as trimming whitespace around the quotes
    */
  def unquote(str: String) = str match {
    case UnquoteR(middle) => middle
    case _                => str
  }

  /** Given the user arguments, produce a loaded configuration which interprets the user-args from left to right as:
    *
    * $ a configuration file on the classpath or file system
    * $ a key=value pair
    *
    * Left-most values take precedence over right
    *
    * @param args the user command-line arguments
    * @param fallback the default configuration to fall back to
    * @param onUnrecognizedArg the handler for unrecognized user arguments
    * @return a parsed configuration
    */
  def configForArgs(args: Array[String], fallback: Config = defaultConfig(), onUnrecognizedArg: String => Config = ParseArg.Throw): Config = {
    import args4c.implicits._
    fallback.withUserArgs(args, onUnrecognizedArg)
  }

  /** @return ConfigFactory.load()
    */
  def defaultConfig(): Config = ConfigFactory.load()

  def env(key: String): Option[String] = sys.env.get(key)

  def prop(key: String): Option[String] = (new SystemProperties).get(key)

  def envOrProp(key: String): Option[String] = env(key).orElse(prop(key))

  def passwordBlacklist = Set("password", "credentials")

  /** @param confMap
    * @return a config for this map
    */
  def configForMap(confMap: Map[String, String]): Config = {
    import scala.collection.JavaConverters._
    try {
      val trimmed = {
        val keys: Set[String] = confMap.keySet
        confMap.view.filter {
          case (k, _) => prefixNotInSet(keys, k)
        }
      }
      ConfigFactory.parseMap(trimmed.toMap.asJava, "environment variable")
    } catch {
      case exp: ConfigException => throw new IllegalStateException(s"couldn't parse: ${confMap.mkString("\n")}", exp)
    }
  }

  private[args4c] def prefixNotInSet(keys: Set[String], value: String) = {
    val otherKeys = keys - value
    !otherKeys.exists(_.startsWith(value))
  }

  private[args4c] def envToPath(str: String) = {
    val TrimDotsR = """\.*(.*)\.*""".r

    str.split('_').map(_.toLowerCase).mkString(".").dropWhile(_ == '.') match {
      case TrimDotsR(trimmed) => trimmed.replaceAll("\\.+", ".")
      case other              => other
    }
  }

  val defaultObscuredText = "**** obscured ****"

  /**
    * @param configPath the config key (e.g. foo.bar.bazz)
    * @param value the config value, as a string
    * @param blacklist a 'blacklist' which, if any of the entries are found anywhere in the configPath, then the value will be obscured
    * @return the
    */
  def obscurePassword(configPath: String, value: String, blacklist: Set[String] = passwordBlacklist, obscuredValue: String = defaultObscuredText): String = {
    val lc = configPath.toLowerCase
    if (blacklist.exists(lc.contains)) {
      obscuredValue
    } else {
      value
    }
  }

  def pathAsFile(path: String): Option[Path] = Option(Paths.get(path)).filter(p => Files.exists(p))

  def pathAsUrl(path: String): Option[URL] = {
    Option(getClass.getClassLoader.getResource(path)).orElse {
      Option(Thread.currentThread().getContextClassLoader.getResource(path))
    }
  }

  private[args4c] val KeyValue = "-{0,2}(.*?)=(.*)".r
}
