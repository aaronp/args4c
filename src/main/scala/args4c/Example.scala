package args4c

import com.typesafe.config.ConfigFactory


object Example {

  def main(a : Array[String]) {
    println(s"${a.size} args:")
    a.foreach(println)



    val conf = ArgsAsConfigParser.parseArgs(a, ConfigFactory.empty)

    println("AsConfig:")
    println(conf.right.map(_.root.render))
  }
}
