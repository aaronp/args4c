package args4c

import args4c.RichConfig.ParseArg
import com.typesafe.config.{Config, ConfigUtil}

trait LowPriorityArgs4cImplicits {

  implicit class RichString(val str: String) {
    def quoted = ConfigUtil.quoteString(str)
  }
  implicit class RichArgs(val userArgs: Array[String]) {
    def asConfig(fallback: Config = defaultConfig(), onUnrecognizedArg: String => Config = ParseArg.Throw): Config = {
      args4c.configForArgs(userArgs, fallback, onUnrecognizedArg)
    }
  }

  implicit def asRichConfig(c: Config): RichConfig = new RichConfig(c)

}
