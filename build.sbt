name := "args4c"

organization := "com.github.aaronp"

enablePlugins(TutPlugin)

val username            = "aaronp"
val scalaEleven         = "2.11.8"
val scalaTwelve         = "2.12.7"
val defaultScalaVersion = scalaTwelve
crossScalaVersions := Seq(scalaEleven, scalaTwelve)

libraryDependencies += "com.typesafe" % "config" % "1.3.0" % "provided"
libraryDependencies ++= List(
  "com.github.aaronp" %% "eie"       % "0.0.3" % "test",
  "org.scalactic"     %% "scalactic" % "3.0.4" % "test",
  "org.scalatest"     %% "scalatest" % "3.0.4" % "test",
  "org.pegdown"       % "pegdown"    % "1.6.0" % "test",
  "junit"             % "junit"      % "4.12"  % "test"
)

publishMavenStyle := true

releaseCrossBuild := true

publishTo := {
  val nexus = "https://oss.sonatype.org/"
  if (version.value.endsWith("SNAPSHOT"))
    Some("snapshots" at nexus + "content/repositories/snapshots")
  else
    Some("releases" at nexus + "service/local/staging/deploy/maven2")
}

credentials += Credentials(Path.userHome / ".sbt" / ".credentials")

buildInfoKeys := Seq[BuildInfoKey](name, version, scalaVersion, sbtVersion)
buildInfoPackage := "args4c.build"

// see http://scalameta.org/scalafmt/
scalafmtOnCompile in ThisBuild := true
scalafmtVersion in ThisBuild := "1.4.0"

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
