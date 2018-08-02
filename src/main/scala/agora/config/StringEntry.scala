package agora.config

case class StringEntry(comments: List[String], origin: String, key: String, value: String) {
  override def toString = {
    s"""#$origin
       |$key : ${value}""".stripMargin
  }
}
