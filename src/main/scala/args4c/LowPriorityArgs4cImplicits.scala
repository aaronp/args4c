package args4c

import args4c.RichConfig.ParseArg
import com.typesafe.config.{Config, ConfigFactory, ConfigUtil}

trait LowPriorityArgs4cImplicits {

  implicit class RichString(val str: String) {
    def quoted = ConfigUtil.quoteString(str)
  }

  implicit def asRichConfig(c: Config): RichConfig = new RichConfig(c)

  implicit class RichArgs(val args: Array[String]) {
    def asConfig(unrecognizedArg: String => Config = ParseArg.Throw): Config = {
      ConfigFactory.empty().withUserArgs(args, unrecognizedArg)
    }
  }

  implicit class RichMap(val map: Map[String, String]) {
    def asConfig: Config = {
      import scala.collection.JavaConverters._
      ConfigFactory.parseMap(map.asJava)
    }
  }

}
