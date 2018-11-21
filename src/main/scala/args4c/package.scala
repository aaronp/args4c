import args4c.RichConfig.LowPriorityImplicits
import com.typesafe.config.{Config, ConfigFactory}

import scala.sys.SystemProperties

package object args4c {

  def configForArgs(args: Array[String], fallback: Config = ConfigFactory.empty): Config = {
    import args4c.implicits._
    fallback.withUserArgs(args)
  }

  /**
    * Exposes the entry point for using a RichConfig,
    *
    * mostly for converting user-args into a config
    */
  object implicits extends LowPriorityImplicits

  def env(key: String): Option[String]       = sys.env.get(key)
  def prop(key: String): Option[String]      = (new SystemProperties).get(key)
  def propOrEnv(key: String): Option[String] = prop(key).orElse(env(key))
  def envOrProp(key: String): Option[String] = env(key).orElse(prop(key))
}
