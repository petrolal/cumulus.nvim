package cumulus.format

import cumulus.protocol.{FormatterSpec, FormatterArg}
import os.Path
import scala.xml.XML

/**
 * Detects and configures Spotless formatter from pom.xml or build.gradle
 */
object SpotlessResolver:

  /**
   * Resolves Spotless from Maven pom.xml
   */
  def resolveMaven(rootPath: Path): Option[FormatterSpec] =
    try
      val pomFile = rootPath / "pom.xml"
      if !os.exists(pomFile) || !os.isFile(pomFile) then
        return None

      val xmlContent = os.read(pomFile)
      // Check if spotless-maven-plugin is present
      if !xmlContent.contains("spotless-maven-plugin") &&
         !xmlContent.contains("com.diffplug.spotless") then
        return None

      val args = scala.collection.mutable.Buffer[FormatterArg]()
      args += FormatterArg("goal", "spotless:apply")

      Some(FormatterSpec(
        formatter = "mvn",
        config_file = Some("pom.xml"),
        args = args.toSeq,
        stdin_mode = false,
        notes = Some(Seq("Spotless configuration detected in pom.xml"))
      ))
    catch
      case _: Exception => None

  /**
   * Resolves Spotless from Gradle build.gradle or build.gradle.kts
   */
  def resolveGradle(rootPath: Path): Option[FormatterSpec] =
    try
      val buildGradle = rootPath / "build.gradle"
      val buildGradleKts = rootPath / "build.gradle.kts"

      val hasSpotlessGradle =
        if os.exists(buildGradle) && os.isFile(buildGradle) then
          os.read(buildGradle).contains("spotless")
        else
          false

      val hasSpotlessKts =
        if os.exists(buildGradleKts) && os.isFile(buildGradleKts) then
          os.read(buildGradleKts).contains("spotless")
        else
          false

      if !hasSpotlessGradle && !hasSpotlessKts then
        return None

      val args = scala.collection.mutable.Buffer[FormatterArg]()
      args += FormatterArg("task", "spotlessApply")

      Some(FormatterSpec(
        formatter = "gradle",
        config_file = if hasSpotlessKts then Some("build.gradle.kts") else Some("build.gradle"),
        args = args.toSeq,
        stdin_mode = false,
        notes = Some(Seq("Spotless configuration detected in Gradle"))
      ))
    catch
      case _: Exception => None
