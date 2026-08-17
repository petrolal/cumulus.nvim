ThisBuild / scalaVersion := "3.5.2"
ThisBuild / organization := "io.github.petrolal"
ThisBuild / organizationName := "petrolal"
ThisBuild / organizationHomepage := Some(url("https://github.com/petrolal"))

ThisBuild / scmInfo := Some(
  ScmInfo(
    url("https://github.com/petrolal/cumulus.nvim"),
    "scm:git@github.com:petrolal/cumulus.nvim.git"
  )
)
ThisBuild / developers := List(
  Developer(
    id = "petrolal",
    name = "Petro Lal",
    email = "petrolal@users.noreply.github.com",
    url = url("https://github.com/petrolal")
  )
)

ThisBuild / description := "Polyglot JVM intelligence engine for Neovim"
ThisBuild / licenses := List("Apache-2.0" -> url("https://www.apache.org/licenses/LICENSE-2.0.txt"))
ThisBuild / homepage := Some(url("https://github.com/petrolal/cumulus.nvim"))

// Sonatype Central Portal settings
ThisBuild / sonatypeCredentialHost := "central.sonatype.com"
ThisBuild / sonatypeRepository := "https://central.sonatype.com/api/v1/publisher"
ThisBuild / publishTo := sonatypePublishToBundle.value
ThisBuild / versionScheme := Some("early-semver")

lazy val root = (project in file("."))
  .enablePlugins(BuildInfoPlugin, NativeImagePlugin)
  .settings(
    name := "cumulus-engine",
    Compile / mainClass := Some("cumulus.Main"),
    nativeImageOptions ++= Seq(
      "--no-fallback",
      "--initialize-at-build-time"
    ),

    libraryDependencies ++= Seq(
      "com.lihaoyi" %% "upickle" % "3.1.0",
      "com.lihaoyi" %% "os-lib" % "0.9.3",
      "org.scala-lang.modules" %% "scala-xml" % "2.2.0",
      "org.scalameta" %% "munit" % "1.0.0-M10" % Test
    ),
    buildInfoKeys := Seq[BuildInfoKey](
      name,
      version,
      scalaVersion,
      BuildInfoKey.action("gitCommit") {
        scala.util.Try {
          val result = scala.sys.process.Process("git rev-parse --short HEAD").!!.trim
          if (result.nonEmpty) result else "unknown"
        }.getOrElse("unknown")
      },
      BuildInfoKey.action("buildTime") {
        val sdf = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'")
        sdf.setTimeZone(java.util.TimeZone.getTimeZone("UTC"))
        sdf.format(new java.util.Date())
      }
    ),
    buildInfoPackage := "cumulus"
  )

