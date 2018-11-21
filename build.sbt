name := "args4c"

organization := "args4c"

val username            = "aaronp"
val scalaEleven         = "2.11.8"
val scalaTwelve         = "2.12.7"
val defaultScalaVersion = scalaTwelve
crossScalaVersions := Seq(scalaEleven, scalaTwelve)

publishMavenStyle := true

libraryDependencies += "com.typesafe" % "config" % "1.2.1" % "provided"

libraryDependencies += "org.scalatest" %% "scalatest" % "3.0.4" % "test"

publishTo := {
  val nexus = "https://oss.sonatype.org/"
  if (version.value.endsWith("SNAPSHOT"))
    Some("snapshots" at nexus + "content/repositories/snapshots")
  else
    Some("releases"  at nexus + "service/local/staging/deploy/maven2")
}

credentials += Credentials(Path.userHome / ".sbt" / ".credentials")

buildInfoKeys := Seq[BuildInfoKey](name, version, scalaVersion, sbtVersion)
buildInfoPackage := "args4c.build"

// see http://www.scalatest.org/user_guide/using_scalatest_with_sbt
testOptions in Test += (Tests.Argument(TestFrameworks.ScalaTest, "-h", s"target/scalatest-reports", "-oN"))

pomExtra := {
  <url>https://github.com/aaronp/args4c</url>
    <licenses>
      <license>
        <name>Apache 2</name>
        <url>http://www.apache.org/licenses/LICENSE-2.0</url>
        <distribution>repo</distribution>
      </license>
    </licenses>
    <scm>
      <url>git@github.com:aaronp/args4c.git</url>
      <connection>scm:git@github.com:aaronp/args4c.git</connection>
    </scm>
    <developers>
      <developer>
        <id>Aaron</id>
        <name>Aaron Pritzlaff</name>
        <url>http://github.com/aaronp</url>
      </developer>
    </developers>
}