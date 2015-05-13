package args4c

import java.io.File

import com.typesafe.config.{Config, ConfigFactory}

import scala.collection.JavaConversions
import scala.collection.JavaConversions._
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}

/**
 * can convert command-line arguments (a Seq[String]) into an Either[String, Config]
 * representing either a successfully merged configuration (Right(config)) or an error.
 *
 * The user arguments can be:
 *  - the relative path to a config file 
 *  - the absolute path to a config file
 *  - the path to a config file on the classpath
 *  - a key/value pair to a configuration value in the form:
 *    -  <key> = <value>
 *    - <key>=<value>
 *    - -<key> = <value>
 *    - -<key>=<value>
 *
 * The values are resolved left-to right, with any key/value settings taking precidence over any
 * file settings.
 *
 * {{{
 * MyApp foo=bar myConf.conf x.y.z=1,2,3 anotherConf.properties
 * }}}
 *
 * would use the configuration value 'bar' for property 'foo' and "1,2,3" for property x.y.z over any
 * properties in myConf.conf or anotherConf.properties.
 *
 * Then any values in myConf.conf would have its configuration settings take precidence over anotherConf.properties
 */
object Args4c {

  /**
   *
   */
  def parseUnresolved(args: Seq[String]): Either[String, Config] = OnNext(args)

  /**
   * parses the string arguments as a configuration.
   *
   * Any arguments in the form "-key=value" will override any other values.
   *
   * The arguments otherwise are expected to be paths to configuration files, either on the classpath or as
   * absolute paths.
   *
   * Left-most configurations are used in preference over ones further to the right.
   *
   * @param args the arguments to parse
   * @return Either a Left with an error message or a Right with an unresolved, parsed configuration
   */
  def parseArgs(args: Seq[String], defaults: Config = ConfigFactory.defaultOverrides): Either[String, Config] = {
    parseUnresolved(args).right.map(_.withFallback(defaults))
  }

  /**
   * Convenience function to parses the arguments and return a resolved configuration
   * @param args the user arguments to parse
   * @param defaults the default fallback configuration
   */
  def parseArgsAndResolve(args: Seq[String], defaults: Config = ConfigFactory.defaultOverrides) = {
    parseUnresolved(args).right.map(_.withFallback(defaults).resolve)
  }



  /**
   * Given a configuration and a path, return the configuration at that path.
   *
   * If the path is invalid or points to a value which is NOT a configuration, a "synthetic" configuration will be
   * created for the path to either hold the value at the path or the default value
   * @param c the configuration to parse
   * @param path the configuration path
   * @param defaultValue the value to display for an invalid path
   * @return the sub-configuration for the given path
   */
  def configAtPath(c: Config, path: String, defaultValue : String = "<invalid path>"): Config = {
    implicit def asRichC(obj: Any) = new {
      def asConf = obj match {
        case javaC: java.util.Collection[_] =>
          import JavaConversions._
          ConfigFactory.parseString( s"""${path}="${javaC.mkString(",")}" """)
        case _ =>
          ConfigFactory.parseString( s"""${path}="${obj}" """)
      }
    }

    val usualSuspects = Seq(
      (_: Config).getConfig(path),
      (_: Config).getString(path).asConf,
      (_: Config).getStringList(path).asConf,
      (_: Config).getList(path).asConf,
      (_: Config).getObject(path).asConf)
    val valueOpt: Option[Config] = usualSuspects collectFirst {
      case f if Try(f(c)).isSuccess => f(c)
    }
    valueOpt getOrElse {
      ConfigFactory.parseString( s"""${path}="${defaultValue}" """)
    }
  }

  private[args4c] object AsConfig {
    def unapply(url: String): Option[Config] = parse(url).toOption

    object UrlRes {
      def unapply(str: String) = try {
        Option(getClass.getClassLoader.getResource(str))
      } catch {
        case NonFatal(_) => None
      }
    }

    def parse(url: String): Try[Config] = {
      val conf = Try[Config] {
        url match {
          case UrlRes(res) => ConfigFactory.parseURL(res)
          case _           => ConfigFactory.parseResourcesAnySyntax(url)
        }
      }

      conf match {
        case Success(c) if c.isEmpty =>
          if (new File(url).exists) {
            Try(ConfigFactory.parseFile(new File(url)).ensuring(!_.isEmpty, s"The file config at '${url}' is empty"))
          } else {
            Failure(new IllegalArgumentException(s"Empty configuration found at $url"))
          }
        case ok@Success(x)           => ok
        case _                       => conf
      }
    }
  }


  private[this] val flag        = """-?([A-Za-z0-9.-]+)\s*=\s*(.+)\s*""".r
  private[this] val optionalKey = """-?([A-Za-z0-9.-]+)\s*=?""".r
  private[this] val value       = """(^=+)""".r
  private[this] val requiredKey = """-([A-Za-z0-9.-]+)\s*=?""".r
  private[this] val assignment  = """-?([A-Za-z0-9.-]+)\s*=""".r

  // represents the parser state applied across user arguments
  private[this] sealed trait OnNext {
    // called on each 'next' command-line argument
    def apply(next: String): OnNext
  }

  private[this] object OnNext {

    /**
     * Convenience methods for applying the parser across the given user args
     * @param args the user arguments
     * @return either an error message (Left) or the parsed configuration (Right)
     */
    def apply(args: Seq[String]): Either[String, Config] = {
      val res = args.foldLeft(BuildingState(args, Map.empty, Nil): OnNext) {
        case (acc, next) => acc(next)
      }

      res match {
        case bs: BuildingState                       => bs.asConfig
        case ErrorState(msg)                         => Left(msg)
        case PendingKey(key, previousState)          => previousState.updated(key, "true").asConfig
        case UseNextEntryAsValue(key, previousState) => previousState.updated(key, "").asConfig
        case other                                   => Left(s"Invalid parser state $other for arguments $args")
      }

    }

    private[this] case class ErrorState(msg: String) extends OnNext {
      override def apply(next: String): OnNext = {
        this
      }
    }

    private[this] case class UseNextEntryAsValue(pendingKey: String, previousState: BuildingState) extends OnNext {
      override def apply(next: String): OnNext = next match {
        case flag(_, _)    => ErrorState(s"Value expected for '${pendingKey}' but assignment '${next}' was found")
        case assignment(_) => ErrorState(s"Value expected for '${pendingKey}' but another assignment for '${next}' was found")
        case owt           => previousState.updated(pendingKey, owt)
      }
    }

    private[this] case class PendingKey(pendingKey: String, previousState: BuildingState) extends OnNext {
      override def apply(next: String): OnNext = next match {
        case requiredKey(k) => PendingKey(k, previousState.updated(pendingKey, "true"))
        case "="            => UseNextEntryAsValue(pendingKey, previousState)
        case value(v)       => previousState.updated(pendingKey, v)
        case flag(k, v)     => previousState.updated(k, v).updated(pendingKey, "true")
        case AsConfig(file) => previousState.copy(configs = previousState.configs :+ file)
        case assignment(k)  => ErrorState(s"Value expected for '${pendingKey}' but another assignment for '${next}' was found")
        case value          => previousState.updated(pendingKey, value)
      }
    }

    private case class BuildingState(args: Seq[String], map: Map[String, String], configs: Seq[Config]) extends OnNext {

      def asConfig : Either[String, Config] = {
        val parsed = try {
          Right(asConfigs)
        } catch {
          case NonFatal(e) => Left(e.getMessage)
        }
        parsed.right.map {
          case Seq()     => ConfigFactory.empty
          case Seq(only) => only
          case configs   => configs.reduce(_ withFallback _)
        }
      }

      private[this] def asConfigs = {
        if (map.isEmpty) {
          configs
        }
        else {
          ConfigFactory.parseMap(map) +: configs
        }
      }

      def updated(key: String, value: String) = BuildingState(args, map.updated(key, value), configs)

      override def apply(next: String): OnNext = {
        next match {
          case flag(key, value)       => updated(key, value)
          case assignment(pendingKey) => UseNextEntryAsValue(pendingKey, this)
          case optionalKey(k)         => PendingKey(k, this)
          case "="                    => ErrorState(s"Unexpected hanging '=' found")
          case AsConfig(file)         => BuildingState(args, map, configs :+ file)
          case other                  =>
            val err = AsConfig.parse(other).failed.get.getMessage
            ErrorState(s"Error parsing '$other' for user arguments '${args.mkString(",")}' : $err")
        }
      }
    }
  }
}