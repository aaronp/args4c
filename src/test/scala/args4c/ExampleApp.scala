package args4c

import com.typesafe.config.Config

/**
  * Provides an access point to manually test out the ConfigApp
  */
object ExampleApp extends ConfigApp:
  type Result = Config

  override def run(config: Config) =
    val s: String = config.withOnlyPath("foo").summary()
    println(s"""
         |Running with:
         |
         |${s}
       """.stripMargin)

    config
