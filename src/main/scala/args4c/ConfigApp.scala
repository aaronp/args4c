package args4c

import com.typesafe.config.{ConfigFactory, Config}
import scala.collection.JavaConversions
import scala.util.Try
import language.{implicitConversions, reflectiveCalls}

/**
 * Extends the ArgsAsConfigParser to also offer the usual suspects - 'help' and '-show=<path/to/config>'
 */
object ConfigApp  {

  sealed trait UserInput
  case class ParsedConfig(unresolved : Config) extends UserInput
  case class ShowConfig(conf : Config) extends UserInput
  case object ShowHelp extends UserInput
  case class InvalidInput(args: Seq[String], err : String) extends UserInput

  /**
   * Executes the function against the user input.
   *
   * If the user args contains 'help' or '-help', then 'ShowHelp' is returned
   *
   * If the user args contains '-show=<path>' where <path> is one of:
   *    $ empty or "all" then ShowConfig is passed to 'f' with the full configuration
   *    $ any other path.to.a.configuration.value then ShowConfig is passed to 'f' with the configuration
   *      at the given path
   *
   * Otherwise the function 'f' is called with ParsedConfig(Config)
   *
   * @param args the command-line arguments to parse.
   * @param f the main application which takes the Input and produces a type T
   * @tparam T the return type of the application
   * @return the result of the function
   */
  def runMain[T](args: Seq[String])(f : UserInput => T) : T = {

    def has(str : String) = args.map(_.toLowerCase.trim).contains(str)

    def withConfig(config: Config) : T = {
      if (has("help")) {
        f(ShowHelp)
      } else if (has("show") || config.hasPath("show")) {
        val c: Config = Try(config.getString("show")).getOrElse("") match {
          case "" | "all" => config
          case path => Args4c.configAtPath(config, path)
        }
        f(ShowConfig(c))
      } else {
        f(ParsedConfig(config))
      }
    }

    val filteredArgs = args filterNot(_ == "help")
    val result : T = Args4c.parseArgs(filteredArgs) match {
      case Left(err) => f(InvalidInput(args, err))
      case Right(config) => withConfig(config)
    }

    result
  }

}
