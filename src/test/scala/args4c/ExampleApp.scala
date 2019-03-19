package args4c

import com.typesafe.config.Config

/**
  * Provides an access point to manually test out the ConfigApp
  */
object ExampleApp extends ConfigApp {

  override def run(config: Config): Unit = {
    println(s"""
         |Running with:
         |
         |${config.withoutSystem.root.render()}
       """.stripMargin)
  }
}
