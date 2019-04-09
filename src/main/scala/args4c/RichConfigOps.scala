package args4c

import java.util.concurrent.TimeUnit

import com.typesafe.config._

import scala.compat.Platform
import scala.concurrent.duration._
import scala.language.{dynamics, implicitConversions, postfixOps}
import scala.util.Try

/**
  * Exposes new operations on a 'config'
  */
trait RichConfigOps extends Dynamic with LowPriorityArgs4cImplicits {

  /** @return the configuration for which we're providing additional functionality
    */
  def config: Config

  def defaultRenderOptions = ConfigRenderOptions.concise.setJson(false)

  def selectDynamic(path: String): Selected = Selected(config.getValue(path))

  /**
    *
    * @param password
    * @return the encrypted configuration
    */
  def encrypt(password: Array[Byte]): Array[Byte] = Encryption.encryptAES(password, asJson)._2

  import RichConfig._

  /** @param key the configuration path
    * @return the value at the given key as a scala duration
    */
  def asDuration(key: String): Duration = {
    config.getString(key).toLowerCase() match {
      case "inf" | "infinite" => Duration.Inf
      case _                  => asFiniteDuration(key)
    }
  }

  def asFiniteDuration(key: String): FiniteDuration = {
    config.getDuration(key, TimeUnit.MILLISECONDS).millis
  }

  /**
    * If 'show=X' is specified, configuration values which contain X in their path will be displayed with the values matching 'obscure' obscured.
    *
    * If 'X' is 'all' or 'root', then the entire configuration is rendered.
    *
    * This can be useful to debug other command-line args (to ensure they take the desired effect)
    * or to validate the environment variable replacement
    *
    * @param obscure a function which takes a dotted configuration path and string value and returns the value to display
    * @return the optional value of what's pointed to if 'show=<path>' is specified
    */
  def showIfSpecified(obscure: (String, String) => String = obscurePassword(_, _)): Option[String] = {
    if (config.hasPath("show")) {
      val filteredConf = config.getString("show") match {
        case "all" | "" | "root" => config
        case path =>
          val lcPath = path.toLowerCase
          config.filter(_.toLowerCase.contains(lcPath))
      }
      Option(filteredConf.summary(obscure))
    } else {
      None
    }
  }

  /** Overlay the given arguments over this configuration, where the arguments are taken to be in the form:
    *
    * $ the path to a configuration file, either on the classpath or file system
    * $ a <key>=<value> pair where the key is a 'path.to.a.configuration.entry'
    *
    * @param args            the user arguments in the form <key>=<value>, <filePath> or <fileOnTheClasspath>
    * @param unrecognizedArg what to do with malformed user input
    * @return a configuration with the given user-argument overrides applied over top
    */
  def withUserArgs(args: Array[String], unrecognizedArg: String => Config = ParseArg.Throw): Config = {
    def isSimpleList(key: String) = {
      def isList = Try(config.getStringList(key)).isSuccess

      config.hasPath(key) && isList
    }

    def isObjectList(key: String) = {
      def isList = Try(config.getObjectList(key)).isSuccess

      config.hasPath(key) && isList
    }

    val configs: Array[Config] = args.map {
      case KeyValue(k, v) if isSimpleList(k) =>
        asConfig(k, java.util.Arrays.asList(v.split(",", -1): _*))
      case KeyValue(k, v) if isObjectList(k) =>
        sys.error(s"Path '$k' tried to override an object list with '$v'")
      case KeyValue(k, v)    => asConfig(k, v)
      case FilePathConfig(c) => c
      case UrlPathConfig(c)  => c
      case other             => unrecognizedArg(other)
    }

    (configs :+ config).reduce(_ withFallback _)
  }

  /**
    * produces a scala list, either from a StringList or a comma-separated string value
    *
    * @param separator if specified, the value at the given path will be parsed if it is a string and not a stringlist
    * @param path      the config path
    */
  def asList(path: String, separator: Option[String] = Option(",")): List[String] = {
    import collection.JavaConverters._
    try {
      config.getStringList(path).asScala.toList
    } catch {
      case e: ConfigException.WrongType =>
        separator.fold(throw e) { sep =>
          config.getString(path).split(sep, -1).toList
        }
    }
  }

  /** @param path the config path
    * @return true if the config path is set in this config to a non-empty value. This will error if the path specified is an object or a list
    */
  def hasValue(path: String): Boolean = config.hasPath(path) && config.getString(path).nonEmpty

  /** @param overrideConfig the configuration (as a string) which should override this config -- essentially the inverse of 'withFallback'
    * @return a new configuration based on 'configString' with our config as a fallback
    */
  def overrideWith(overrideConfig: Config): Config = overrideConfig.withFallback(config)

  /** @param configString the configuration (as a string) which should override this config -- essentially the inverse of 'withFallback'
    * @return a new configuration based on 'configString' with our config as a fallback
    */
  def overrideWith(configString: String): Config = overrideWith(ConfigFactory.parseString(configString))

  /** @param key the config path
    * @param value the value to set
    * @return a new configuration based on 'configString' with our config as a fallback
    */
  def set(key: String, value: Long): Config = set(Map(key -> value))

  def set(key: String, value: String): Config = set(Map(key -> value))

  def set(key: String, value: Boolean): Config = set(Map(key -> value))

  def setArray[T](key: String, firstValue: T, secondValue: T, theRest: T*): Config = {
    setArray(key, firstValue +: secondValue +: theRest.toSeq)
  }

  def setArray[T](key: String, value: Seq[T], originDesc: String = null): Config = {
    import scala.collection.JavaConverters._
    set(Map(key -> value.asJava), originDesc)
  }

  def set(values: Map[String, Any], originDesc: String = "override"): Config = {
    import scala.collection.JavaConverters._
    overrideWith(ConfigFactory.parseMap(values.asJava, originDesc))
  }

  /** @param other the configuration to remove from this config
    * @return a new configuration with all values from 'other' removed
    */
  def without(other: Config): Config = without(configAsRichConfig(other).paths)

  /** @param firstPath the first path to remove
    * @param theRest the remaining paths to remove
    * @return a new configuration with the given paths removed
    */
  def without(firstPath: String, theRest: String*): Config = without(firstPath +: theRest)

  /** @param configPaths the paths to remove
    * @return a new configuration with the given paths removed
    */
  def without(configPaths: TraversableOnce[String]): Config = {
    configPaths.foldLeft(config)(_ withoutPath _)
  }

  /** @param pathFilter a predicate used to determine if the configuration path should be kept
    * @return a new configuration which just keeps the paths which include the provided path predicate
    */
  def filter(pathFilter: String => Boolean): Config = filterNot(pathFilter.andThen(_.unary_!))

  /** @param pathFilter a predicate used to determine if the configuration path should be kept
    * @return a new configuration which just keeps the paths which do NOT include the provided path predicate
    */
  def filterNot(pathFilter: String => Boolean): Config = without(paths.filter(pathFilter))

  /** @return the configuration as a json string
    */
  def asJson: String = config.root.render(ConfigRenderOptions.concise().setJson(true))

  /** @return all the unique paths for this configuration
    */
  def paths: Seq[String] = {
    entries.map(_._1).toSeq.sorted
  }

  /** @return the configuration entries as a set of entries
    */
  def entries: Set[(String, ConfigValue)] = {
    import scala.collection.JavaConverters._

    def prepend(prefix: String, cv: ConfigValue): Set[(String, ConfigValue)] = {
      cv match {
        case obj: ConfigObject =>
          obj.toConfig.entries.map {
            case (path, cv) => s"${prefix}.$path" -> cv
          }
        case list: ConfigList =>
          import scala.collection.JavaConverters._
          val all = list
            .listIterator()
            .asScala
            .zipWithIndex
            .flatMap {
              case (value: ConfigValue, i) => prepend(s"$prefix[$i]", value)
            }
            .toSet
          // format :on
          if (all.isEmpty) {
            Set(prefix -> list)
          } else {
            all
          }
        case _ => Set(prefix -> cv)
      }
    }

    val all = config.entrySet().asScala.flatMap { e =>
      val key = e.getKey
      e.getValue match {
        case list: ConfigList =>
          import scala.collection.JavaConverters._
          val all = list.listIterator().asScala.zipWithIndex.flatMap {
            case (value: ConfigValue, i) => prepend(s"$key[$i]", value)
          }
          if (all.isEmpty) {
            Set(key -> list)
          } else {
            all
          }
        case cv => Set(key -> cv)
      }
    }
    all.toSet
  }

  /** @return the config as a map
    */
  def toMap = entries.toMap

  /** @return a sorted list of the origins from when the config values come
    */
  def origins: List[String] = {
    val urls = entries.flatMap {
      case (_, e) =>
        val origin = e.origin()
        Option(origin.url). //
        orElse(Option(origin.filename)). //
        orElse(Option(origin.resource)). //
        orElse(Option(origin.description)). //
        map(_.toString)
    }
    urls.toList.distinct.sorted
  }

  /**
    * Return a property-like summary of the config using the pathFilter to trim property entries
    *
    * @param obscure a function which will 'safely' replace any config values with an obscured value
    * @return a summary of the configuration
    */
  def summary(obscure: (String, String) => String = obscurePassword(_, _)): String = {
    summaryEntries(obscure).mkString(Platform.EOL)
  }

  /**
    * Return a property-like summary of the config using the 'obscure' function to mask sensitive entries
    *
    * @param obscure a function which will 'safely' replace any config values with an obscured value
    */
  def summaryEntries(obscure: (String, String) => String = obscurePassword(_, _)): Seq[StringEntry] = {
    val cro = defaultRenderOptions
    entries
      .collect {
        case (key, value) =>
          val stringValue = obscure(key, value.render(cro))
          val originString = {
            val o       = value.origin
            def resOpt  = Option(o.resource)
            def descOpt = Option(o.description)
            def line    = Option(o.lineNumber()).filterNot(_ < 0).map(": " + _).getOrElse("")
            Option(o.url()).map(_.toString).orElse(Option(o.filename)).orElse(resOpt).map(_ + line).orElse(descOpt).getOrElse("unknown origin")
          }
          import scala.collection.JavaConverters._
          val comments = value.origin().comments().asScala.toList
          StringEntry(comments, originString, key, unquote(stringValue))
      }
      .toSeq
      .sortBy(_.key)
  }

  /** The available config roots.
    *
    * e.g. of a config has
    * {{{
    *   foo.bar.x = 1
    *   java.home = /etc/java
    *   bar.enabled = true
    *   bar.user = root
    * }}}
    *
    * The 'pathRoots' would return a [bar, foo, java]
    *
    * @return a sorted list of the root entries to the config.
    */
  def pathRoots: Seq[String] = paths.map { p =>
    ConfigUtil.splitPath(p).get(0)
  }

  /** @return the configuration as a set of key/value tuples
    */
  def collectAsStrings(options: ConfigRenderOptions = defaultRenderOptions): Seq[(String, String)] =
    entries
      .map {
        case (key, value) => (key, unquote(value.render(options)))
      }
      .toSeq
      .sorted

  /** @return the configuration as a map
    */
  def collectAsMap(options: ConfigRenderOptions = defaultRenderOptions): Predef.Map[String, String] = {
    collectAsStrings(options).toMap
  }

  /** @param other
    * @return the configuration representing the intersection of the two configuration entries
    */
  def intersect(other: Config): Config = {
    withPaths(other.paths)
  }

  /** @param first   the first path to include (keep)
    * @param theRest any other paths to keep
    * @return this configuration which only contains the specified paths
    */
  def withPaths(first: String, theRest: String*): Config = withPaths(first :: theRest.toList)

  /** @param paths
    * @return this configuration which only contains the specified paths
    */
  def withPaths(paths: Seq[String]): Config = {
    paths.map(config.withOnlyPath).reduce(_ withFallback _)
  }
}
