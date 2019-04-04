package args4c

import com.typesafe.config._

import scala.language.dynamics

/**
  * An object returned from a dynamic select
  */
case class Selected(value: ConfigValue) extends Dynamic {

  def selectDynamic(path: String): RichConfig = asRichConfig.selectDynamic(path)

  def asRichConfig: RichConfig = {
    value match {
      case c: ConfigObject => new RichConfig(c.toConfig)
      case other =>
        sys.error(s"$other for ${value.valueType()} is not a config: $value")
    }
  }
}

object Selected {
  implicit def selectedAsConfig(s: Selected): RichConfig = s.asRichConfig
}
