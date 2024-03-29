package args4c

import args4c.RichConfig.ParseArg
import com.typesafe.config.{Config, ConfigUtil}
import scala.language.implicitConversions

trait LowPriorityArgs4cImplicits:

  extension(str: String)
    def quoted = ConfigUtil.quoteString(str)

  extension(userArgs: Array[String])
    def asRichConfig(fallback: Config = defaultConfig(), onUnrecognizedArg: String => Config = ParseArg.Throw): RichConfig =
      configAsRichConfig(asConfig(fallback, onUnrecognizedArg))

    def asConfig(fallback: Config = defaultConfig(), onUnrecognizedArg: String => Config = ParseArg.Throw): Config =
      args4c.configForArgs(userArgs, fallback, onUnrecognizedArg)

  implicit def configAsRichConfig(c: Config): RichConfig = new RichConfig(c)

