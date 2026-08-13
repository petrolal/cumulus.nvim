ThisBuild / scalaVersion := "3.4.2"
ThisBuild / organization := "dev.cumulus"

lazy val root = (project in file("."))
  .enablePlugins(BuildInfoPlugin)
  .settings(
    name := "cumulus-engine",
    version := "0.1.0",
    Compile / mainClass := Some("cumulus.Main"),
    // GraalVM native-image plugin configuration
    // Commented out until sbt-native-packager availability is resolved
    // .enablePlugins(GraalVMNativeImagePlugin)
    // graalVMNativeImageOptions ++= Seq(
    //   "--no-fallback",
    //   "--initialize-at-build-time=cumulus"
    // ),
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
        new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'")
          .format(new java.util.Date())
      }
    ),
    buildInfoPackage := "cumulus"
  )
