package args4c

import java.nio.file.{Files, Paths}

import com.typesafe.config.impl.ConfigImpl
import com.typesafe.config.{Config, ConfigFactory, ConfigUtil}

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
    val Ignore = (a: String) => ConfigFactory.empty()

    /**
      * Treats orphaned args as on/off boolean flags
      * e.g. Main foo bar=bazz x.y.z
      * will have an entry for foo=true, bar set to 'bazz', and 'x.y.z' set to true
      */
    val AsBooleanFlag = (a: String) => asConfig(ConfigUtil.quoteString(a), true.toString)
  }

  private[args4c] def asConfig(key: String, value: Any, originDesc: String = "command-line"): Config = {
    import scala.collection.JavaConverters._
    ConfigImpl.fromPathMap(Map(key -> value).asJava, originDesc).toConfig
  }

  private[args4c] object FilePathConfig {
    def unapply(path: String): Option[Config] =
      Option(Paths.get(path))
        .filter(p => Files.exists(p))
        .map(_.toFile)
        .map { file =>
          ConfigFactory.parseFileAnySyntax(file)
        }
  }

  private[args4c] object UrlPathConfig {
    def unapply(path: String): Option[Config] = {
      val url = getClass.getClassLoader.getResource(path)
      Option(url).map(ConfigFactory.parseURL)
    }
  }

  private[args4c] val KeyValue = "(.*)=(.*)".r

}
