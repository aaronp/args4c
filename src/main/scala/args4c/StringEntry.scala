package args4c

case class StringEntry(comments: List[String], origin: String, key: String, value: String) {
  override def toString: String = s"""$key : ${value} # $origin""".stripMargin

  def withPrefix(prefix: String) = copy(key = s"$prefix$key")
}
