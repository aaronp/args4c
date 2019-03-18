package args4c

import com.typesafe.config.{Config, ConfigUtil}

trait LowPriorityArgs4cImplicits {

  implicit class RichString(val str: String) {
    def quoted = ConfigUtil.quoteString(str)
  }

  implicit def asRichConfig(c: Config): RichConfig = new RichConfig(c)

}
