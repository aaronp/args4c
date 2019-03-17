import args4c.RichConfig.LowPriorityImplicits
import com.typesafe.config.{Config, ConfigFactory}

import scala.sys.SystemProperties

package object args4c {

  /** Given the user arguments, produce a loaded configuration which interprets the user-args from left to right as:
    *
    * $ a configuration file on the classpath or file system
    * $ a key=value pair
    *
    * Left-most values take precedence over right
    *
    * @param args
    * @param fallback
    * @return a parsed configuration
    */
  def configForArgs(args: Array[String], fallback: Config = ConfigFactory.load()): Config = {
    import args4c.implicits._
    fallback.withUserArgs(args)
  }

  /**
    * Exposes the entry point for using a RichConfig,
    *
    * mostly for converting user-args into a config
    */
  object implicits extends LowPriorityImplicits

  def env(key: String): Option[String] = sys.env.get(key)

  def prop(key: String): Option[String] = (new SystemProperties).get(key)

  def propOrEnv(key: String): Option[String] = prop(key).orElse(env(key))

  def envOrProp(key: String): Option[String] = env(key).orElse(prop(key))

  def passwordBlacklist = Set("password", "credentials")

  /**
    * Converts environment variables into a configuration in order to more easily override any config setting
    * based on an environment variable.
    *
    * e.g. instead of having the config:
    *
    * {{{
    *   foo.bar.x = 123
    *   foo.bar.x = ${?FOO_BAR_X}
    * }}}
    *
    * repeated for each setting we can just convert all environment variables split on '_' into lower-case configuration values.
    *
    * e.g. 'FOO_BAR_X' would get converted into 'foo.bar.x'.
    *
    *
    */
  def sysEnvAsConfig(env: Map[String, String] = sys.env): Config = {
    val confMap = env.map {
      case (key, value) => envToPath(key) -> value
    }
    import scala.collection.JavaConverters._
    ConfigFactory.parseMap(confMap.asJava)
  }

  def envToPath(str: String) = str.split('_').map(_.toLowerCase).mkString(".")

  def obscurePassword(configPath: String, value: String, blacklist: Set[String] = passwordBlacklist): String = {
    val lc = configPath.toLowerCase
    if (blacklist.exists(lc.contains)) {
      "**** obscured ****"
    } else {
      value
    }
  }
}
