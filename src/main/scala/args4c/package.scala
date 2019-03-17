import com.typesafe.config.{Config, ConfigException, ConfigFactory}

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
  def configForArgs(args: Array[String], fallback: Config = defaultConfig()): Config = {
    import args4c.implicits._
    fallback.withUserArgs(args)
  }

  /**
    * @return essentially ConfigFactory.load() but with [[sysEnvAsConfig]] layered over the default application
    */
  def defaultConfig(): Config = ConfigFactory.load(sysEnvAsConfig().withFallback(ConfigFactory.defaultApplication()))

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
    val confMap = env
      .map {
        case (key, value) => envToPath(key) -> value
      }
      .filterKeys(_.nonEmpty)
    configForMap(confMap)
  }

  /** @param confMap
    * @return a config for this map
    */
  def configForMap(confMap: Map[String, String]): Config = {
    import scala.collection.JavaConverters._
    try {
      val trimmed = {
        val keys: Set[String] = confMap.keySet
        confMap.filterKeys(prefixNotInSet(keys, _))
      }
      ConfigFactory.parseMap(trimmed.asJava, "environment variable")
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

  def obscurePassword(configPath: String, value: String, blacklist: Set[String] = passwordBlacklist): String = {
    val lc = configPath.toLowerCase
    if (blacklist.exists(lc.contains)) {
      "**** obscured ****"
    } else {
      value
    }
  }
}
