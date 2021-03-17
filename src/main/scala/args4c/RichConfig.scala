package args4c

import com.typesafe.config.impl.ConfigImpl
import com.typesafe.config.{Config, ConfigFactory}
import scala.language.dynamics

/**
  * Adds some scala utility around a typesafe config
  *
  * @param config
  */
class RichConfig(override val config: Config) extends RichConfigOps

object RichConfig {

  /**
    * Contains functions detailing what to do with user command-line input
    * which doesn't match either a file path, resource or key=value pair
    */
  object ParseArg {
    val Throw  = (a: String) => sys.error(s"Unrecognized user arg '$a'")
    val Ignore = (_: String) => ConfigFactory.empty()
  }

  private[args4c] def asConfig(key: String, value: Any, originDesc: String = "command-line"): Config = {
    import scala.collection.JavaConverters._
    ConfigImpl.fromPathMap(Map(key -> value).asJava, originDesc).toConfig
  }

  private[args4c] object FilePathConfig {
    def unapply(path: String): Option[Config] =
      pathAsFile(path).map { file =>
        ConfigFactory.parseFileAnySyntax(file.toFile)
      }
  }

  private[args4c] object UrlPathConfig {
    def unapply(path: String): Option[Config] = pathAsUrl(path).map(ConfigFactory.parseURL)
  }

}
