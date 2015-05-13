name := "args4c"

version := "0.0.0-SNAPSHOT"

organization := "args4c"

scalaVersion := "2.11.6"

crossScalaVersions := Seq("2.10.4", "2.11.6")

publishMavenStyle := true

libraryDependencies += "com.typesafe" % "config" % "1.2.1" % "provided"

libraryDependencies += "org.scalatest" %% "scalatest" % "2.2.1" % "test"

publishTo := {
  val nexus = "https://oss.sonatype.org/"
  if (version.value.endsWith("SNAPSHOT"))
    Some("snapshots" at nexus + "content/repositories/snapshots")
  else
    Some("releases"  at nexus + "service/local/staging/deploy/maven2")
}


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