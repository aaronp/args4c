import org.scoverage.coveralls.Imports.CoverallsKeys._
import eie.io._

name := "args4c"

organization := "com.github.aaronp"

enablePlugins(GhpagesPlugin)
enablePlugins(ParadoxPlugin)
enablePlugins(SiteScaladocPlugin)
enablePlugins(ParadoxMaterialThemePlugin) // see https://jonas.github.io/paradox-material-theme/getting-started.html

//ParadoxMaterialThemePlugin.paradoxMaterialThemeSettings(Paradox)

val username     = "aaronp"
val dottyVersion = "3.0.0-RC1"
scalaVersion := dottyVersion
crossScalaVersions := Seq(dottyVersion)

paradoxProperties += ("project.url" -> "https://aaronp.github.io/args4c/docs/current/")

Compile / paradoxMaterialTheme ~= {
  _.withLanguage(java.util.Locale.ENGLISH)
    .withColor("blue", "grey")
    .withLogoIcon("cloud")
    .withRepository(uri("https://github.com/aaronp/args4c"))
    .withSocial(uri("https://github.com/aaronp"))
    .withoutSearch()
}

//scalacOptions += Seq("-encoding", "UTF-8")

siteSourceDirectory := target.value / "paradox" / "site" / "main"

siteSubdirName in SiteScaladoc := "api/latest"

libraryDependencies += "com.typesafe" % "config" % "1.3.4" % "provided"

libraryDependencies ++= List(
  "org.scalatest"        %% "scalatest"               % "3.2.6"  % "test",
  "org.pegdown"          % "pegdown"                  % "1.6.0"  % "test",
  "junit"                % "junit"                    % "4.13.2" % "test",
  "com.vladsch.flexmark" % "flexmark-profile-pegdown" % "0.36.8" % Test,
  "javax.xml.bind"       % "jaxb-api"                 % "2.3.0"  % "provided"
)

publishMavenStyle := true
releaseCrossBuild := true
coverageMinimum := 90
coverageFailOnMinimum := true
git.remoteRepo := s"git@github.com:$username/args4c.git"
ghpagesNoJekyll := true
releasePublishArtifactsAction := PgpKeys.publishSigned.value
publishConfiguration := publishConfiguration.value.withOverwrite(true)
publishLocalConfiguration := publishLocalConfiguration.value.withOverwrite(true)

test in assembly := {}
publishTo := {
  val nexus = "https://oss.sonatype.org/"
  if (version.value.endsWith("SNAPSHOT"))
    Some("snapshots" at nexus + "content/repositories/snapshots")
  else
    Some("releases" at nexus + "service/local/staging/deploy/maven2")
}

credentials += Credentials(Path.userHome / ".sbt" / ".credentials")

// https://coveralls.io/github/aaronp/args4c
// https://github.com/scoverage/sbt-coveralls#specifying-your-repo-token
coverallsTokenFile := Option((Path.userHome / ".sbt" / ".coveralls.args4c").asPath.toString)

buildInfoKeys := Seq[BuildInfoKey](name, version, scalaVersion, sbtVersion)
buildInfoPackage := "args4c.build"

// see http://scalameta.org/scalafmt/
ThisBuild / scalafmtOnCompile := true
ThisBuild / scalafmtVersion := "1.4.0"

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
    <developers>
      <developer>
        <id>Aaron</id>
        <name>Aaron Pritzlaff</name>
        <url>http://github.com/aaronp</url>
      </developer>
    </developers>
}
