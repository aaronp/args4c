package args4c

import com.typesafe.config.Config

/**
  * Provides an access point to manually test out the ConfigApp
  */
object ExampleApp extends ConfigApp {
  type Result = Config

  override def run(config: Config) = {
    println(s"""
         |Running with:
         |
         |${config.withoutSystem.root.render()}
       """.stripMargin)
    config
  }
}
