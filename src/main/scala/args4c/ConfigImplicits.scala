package args4c

import com.typesafe.config.{ConfigException, Config}

import scala.collection.JavaConverters

trait ConfigImplicits {

  private[this] val WithBrackets = """\[(.*)\]""".r

  implicit def asRichConfig(conf : Config) = new {
    /**
     * @throws NumberFormatException if any values cannot be converted to an int
     * @param path the config path
     * @return the integer values at the given path
     */
    def getIntSeq(path : String) : Seq[Int] = {
      def toInt(x : String) = try {
        x.toInt
      } catch {
        case nfe : NumberFormatException =>
          throw new NumberFormatException(s"Couldn't convert '${x}' to an integer at config path $path")
      }
      getStringSeq(path).map(toInt)
    }

    /**
     * @param path the configuration path to retrieve
     * @return the configuration value as a sequence of non-empty string values
     */
    def getStringSeq(path : String) : Seq[String] = {
      import JavaConverters._

      try {
        conf.getStringList(path).asScala.toSeq
      } catch {
        case ce : ConfigException => tryAsSplitString(path)
      }
    }

    private[this] def tryAsSplitString(path : String): Seq[String] = {
      try {
        val string = conf.getString(path).trim match {
          case WithBrackets(ohneBrackets) => ohneBrackets
          case other => other
        }

        string.split(",").map(_.trim).filterNot(_.isEmpty).toSeq
      } catch {
        case ce : ConfigException => Nil
      }
    }
  }
}

object ConfigImplicits extends ConfigImplicits
