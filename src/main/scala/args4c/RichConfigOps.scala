package args4c

import java.util.Map
import java.util.concurrent.TimeUnit

import com.typesafe.config.ConfigRenderOptions._
import com.typesafe.config._

import scala.collection.mutable
import scala.concurrent.duration._
import scala.language.{implicitConversions, postfixOps}
import scala.util.Try

/**
  * Provider operations on a 'config'
  */
trait RichConfigOps extends RichConfig.LowPriorityImplicits {

  def config: Config

  def encrypt(keyBytes: Array[Byte]) = {
    val input: String = config.root.render(ConfigRenderOptions.concise())
    val (len, bytes) = Encryption.encryptAES(keyBytes, input)
    (len, new String(bytes, "UTF-8"))
  }

  import ConfigFactory._
  import RichConfig._

  /** @param key the configuration path
    * @return the value at the given key as a scala duration
    */
  def asDuration(key: String): Duration = {
    config.getString(key).toLowerCase() match {
      case "inf" | "infinite" => Duration.Inf
      case _ => asFiniteDuration(key)
    }
  }

  def asFiniteDuration(key: String): FiniteDuration = {
    config.getDuration(key, TimeUnit.MILLISECONDS).millis
  }

  /**
    * If 'show=X' specified, configuration values which contain X in their path.
    *
    * If 'X' is 'all' or 'root', then the entire configuration is rendered.
    *
    * This can be useful to debug other command-line args (to ensure they take the desired effect)
    * or to validate the environment variable replacement
    *
    * @return the optional value of what's pointed to if 'show=<path>' is specified
    */
  def show(options: ConfigRenderOptions = ConfigRenderOptions.concise().setFormatted(true)): Option[String] = {
    if (config.hasPath("show")) {
      val filteredConf = config.getString("show") match {
        case "all" | "" | "root" => config.root.render()
        case path =>
          val lcPath = path.toLowerCase
          config.summary(_.toLowerCase.contains(lcPath)).mkString("\n\n")
      }
      Option(filteredConf)
    } else {
      None
    }
  }

  /**
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
      case KeyValue(k, v) => asConfig(k, v)
      case FilePathConfig(c) => c
      case UrlPathConfig(c) => c
      case other => unrecognizedArg(other)
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

  /** And example which uses most of the below stuff to showcase what this is for
    * Note : writing a 'diff' using this would be pretty straight forward
    */
  def uniquePaths: List[String] = unique.paths.toList.sorted

  def unique = withoutSystem.filterNot(_.startsWith("akka"))

  /** this config w/o the system props and stuff */
  def withoutSystem: Config = without(systemEnvironment.or(systemProperties).paths)

  def or(other: Config) = config.withFallback(other)

  def without(other: Config): Config = without(asRichConfig(other).paths)

  def without(firstPath: String, theRest: String*): Config = without(firstPath +: theRest)

  def without(paths: TraversableOnce[String]): Config = paths.foldLeft(config)(_ withoutPath _)

  def filterNot(path: String => Boolean) = without(paths.filter(path))

  def describe(implicit opts: ConfigRenderOptions = concise().setFormatted(true)) =
    config.root.render(opts)

  def json = config.root.render(ConfigRenderOptions.concise().setJson(true))

  def paths: List[String] = entries.map(_.getKey).toList.sorted

  def entries: mutable.Set[Map.Entry[String, ConfigValue]] = {
    import scala.collection.JavaConverters._
    config.entrySet().asScala
  }

  def entryPairs = entries.map { entry =>
    (entry.getKey, entry.getValue)
  }

  /** @return a sorted list of the origins from when the config values come
    */
  def origins: List[String] = {
    val urls = entries.flatMap { e =>
      val origin = e.getValue.origin()
      Option(origin.url).orElse(Option(origin.filename)).orElse(Option(origin.resource)).map(_.toString)
    }
    urls.toList.distinct.sorted
  }

  /**
    * Return a property-like summary of the config using the pathFilter to trim property entries
    *
    * @param pathFilter
    */
  def summary(pathFilter: String => Boolean = _ => true): List[StringEntry] = {
    collectAsStrings.collect {
      case (key, stringValue) if pathFilter(key) =>
        val value = config.getValue(key)
        val origin = s"${value.origin.url()}@${value.origin().lineNumber()}"
        import scala.collection.JavaConverters._
        val comments = value.origin().comments().asScala.toList
        StringEntry(comments, origin, key, stringValue)
    }
  }

  def pathRoots = paths.map { p =>
    ConfigUtil.splitPath(p).get(0)
  }

  def collectAsStrings: List[(String, String)] = paths.flatMap { key =>
    Try(config.getString(key)).toOption.map(key ->)
  }

  def collectAsMap = collectAsStrings.toMap

  def intersect(other: Config): Config = {
    withPaths(other.paths)
  }

  def withPaths(first: String, theRest: String*): Config = withPaths(first :: theRest.toList)

  def withPaths(paths: List[String]): Config = {
    paths.map(config.withOnlyPath).reduce(_ withFallback _)
  }
}
