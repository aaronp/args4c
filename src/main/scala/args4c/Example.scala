package args4c

import com.typesafe.config.ConfigFactory


object Example {

  def main(a : Array[String]) {
    println(s"${a.size} args:")
    a.zipWithIndex.foreach {
      case (a,i) => println(s"${i.toString.padTo(3, ' ')}: $a")
    }

    val conf = Args4c.parseArgs(a, ConfigFactory.empty)

    println("-" * 80)
    println("AsConfig:")
    println(conf.right.map(_.root.render))
  }
}
