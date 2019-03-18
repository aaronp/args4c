package args4c
import com.typesafe.config.Config

object TestApp extends ConfigApp {


  override def run(config: Config): Unit = {
    println(s"""
         |Running with:
         |
         |${config.withoutSystem.root.render()}
       """.stripMargin)
  }
}
