package args4c

import java.util.Properties

import com.typesafe.config.Config

import scala.util.Try

object Props {

  def format(all: Properties): String = {
    import scala.collection.JavaConverters._
    all
      .keySet()
      .asScala
      .map(_.toString)
      .map { key =>
        val value = all.getProperty(key)
        s"${key} : ${value}"
      }
      .mkString(";\n")
  }

  def propertiesForConfig(config: Config): Properties = {
    import args4c.implicits._
    config
      .collectAsStrings()
      .foldLeft(new java.util.Properties) {
        case (props, (key, AsInteger(value))) =>
          props.put(key, value)
          props
        case (props, (key, value)) =>
          props.put(key, value)
          props
      }
  }

  private object AsInteger {

    def unapply(str: String): Option[Integer] = {
      Try(Integer.valueOf(str.trim)).toOption
    }
  }

}
