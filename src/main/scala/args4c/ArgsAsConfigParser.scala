package args4c

import java.io.File

import com.typesafe.config.{Config, ConfigFactory}

import scala.collection.JavaConversions._
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}

/**
 * can convert command-line arguments (a Seq[String]) into an Either[String, Config]
 * representing either a successfully merged configuration (Right(config)) or an error.
 *
 * The arguments can either be classpath or file configuration locations, or in the form -key=value.
 *
 */
object ArgsAsConfigParser {


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
    OnNext(args).right.map(_.withFallback(defaults))
  }

  /**
   * Convenience function to parses the arguments and return a resolved configuration
   * @param args the user arguments to parse
   * @param defaults the default fallback configuration
   */
  def parseArgsAndResolve(args: Seq[String], defaults: Config = ConfigFactory.defaultOverrides()) = {
    parseArgs(args).right.map(_.withFallback(defaults).resolve)
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


  private val flag        = """-?([A-Za-z0-9.-]+)\s*=\s*(.+)\s*""".r
  private val optionalKey = """-?([A-Za-z0-9.-]+)\s*=?""".r
  private val value       = """(^=+)""".r
  private val requiredKey = """-([A-Za-z0-9.-]+)\s*=?""".r
  private val assignment  = """-?([A-Za-z0-9.-]+)\s*=""".r

  // represents the parser state applied across user arguments
  private sealed trait OnNext {
    // called on each 'next' command-line argument
    def apply(next: String): OnNext
  }

  private object OnNext {

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
        case bs: BuildingState                       => Right(bs.asConfig)
        case ErrorState(msg)                         => Left(msg)
        case PendingKey(key, previousState)          => Right(previousState.updated(key, "true").asConfig)
        case UseNextEntryAsValue(key, previousState) => Right(previousState.updated(key, "").asConfig)
        case other                                   => Left(s"Invalid parser state $other for arguments $args")
      }

    }

    private case class ErrorState(msg: String) extends OnNext {
      override def apply(next: String): OnNext = {
        this
      }
    }

    private case class UseNextEntryAsValue(pendingKey: String, previousState: BuildingState) extends OnNext {
      override def apply(next: String): OnNext = next match {
        case flag(_, _)    => ErrorState(s"Value expected for '${pendingKey}' but assignment '${next}' was found")
        case assignment(_) => ErrorState(s"Value expected for '${pendingKey}' but another assignment for '${next}' was found")
        case owt           => previousState.updated(pendingKey, owt)
      }
    }

    private case class PendingKey(pendingKey: String, previousState: BuildingState) extends OnNext {
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

      def asConfig = asConfigs match {
        case Seq()     => ConfigFactory.empty
        case Seq(only) => only
        case configs   => configs.reduce(_ withFallback _)
      }

      def asConfigs = {
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