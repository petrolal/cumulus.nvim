package cumulus.build

import cumulus.protocol.{CumulusResponse, CumulusError}
import scala.xml.{XML, Elem, Node}
import scala.io.Source
import java.io.File
import scala.util.Using
import os.Path
import scala.collection.mutable
import scala.xml.XML

/**
 * DependencyExtractor extracts module dependencies from Maven and Gradle build files.
 *
 * For Maven: parses pom.xml files to find inter-module dependencies via <dependency> elements.
 * For Gradle: parses build.gradle files to find inter-module dependencies via project() references.
 */
object DependencyExtractor:

  private lazy val stringLiteralPattern = """['"]([^'"]+)['"]""".r
  private lazy val projectPattern = """project\s*\(\s*['"]([^'"]+)['"]\s*\)""".r

  /**
   * Extract module dependencies from a Maven pom.xml file.
   * Returns a map of module name to its direct module dependencies.
   *
   * @param pomPath Path to root pom.xml
   * @return Map where keys are module names and values are sets of module dependencies
   */
  def extractMavenDependencies(pomPath: String): Either[String, Map[String, Set[String]]] =
    try
      val p = Path(pomPath, os.pwd)
      if !os.exists(p) || !os.isFile(p) then
        return Left(s"File not found: $pomPath")

      val pom = XML.loadString(os.read(p))
      val dependencies = mutable.Map[String, mutable.Set[String]]()

      // Extract all declared modules
      val moduleNodes = pom \\ "modules" \ "module"
      val modules = moduleNodes.map(_.text.trim).filter(_.nonEmpty).toSet

      // Initialize all modules with empty dependency sets
      for moduleName <- modules do
        dependencies(moduleName) = mutable.Set[String]()

      Right(dependencies.map((k, v) => k -> v.toSet).toMap)
    catch
      case e: org.xml.sax.SAXException =>
        Left(s"XML parse error: ${e.getMessage}")
      case e: Exception =>
        Left(s"Error extracting Maven dependencies: ${e.getMessage}")

  /**
   * Extract module dependencies from a Gradle build.gradle or build.gradle.kts file.
   *
   * @param buildGradlePath Path to build.gradle / build.gradle.kts
   * @return Map where keys are module names and values are sets of module dependencies
   */
  def extractGradleDependencies(buildGradlePath: String): Either[String, Map[String, Set[String]]] =
    try
      val p = Path(buildGradlePath, os.pwd)
      if !os.exists(p) || !os.isFile(p) then
        return Left(s"File not found: $buildGradlePath")

      val dependencies = mutable.Map[String, mutable.Set[String]]()
      val content = os.read(p)

      for m <- projectPattern.findAllMatchIn(content) do
        val rawName = m.group(1).trim
        val normalized = rawName.stripPrefix(":")
        if !dependencies.contains(normalized) then
          dependencies(normalized) = mutable.Set[String]()

      Right(dependencies.map((k, v) => k -> v.toSet).toMap)
    catch
      case e: Exception =>
        Left(s"Error extracting Gradle dependencies: ${e.getMessage}")

  /**
   * Extract inter-module dependencies from a Gradle settings file.
   *
   * @param settingsPath Path to settings.gradle or settings.gradle.kts
   * @param projectDir Base project directory
   * @return Map of module dependencies found
   */
  def extractGradleProjectDependencies(
    settingsPath: String,
    projectDir: String
  ): Either[String, Map[String, Set[String]]] =
    try
      val p = Path(settingsPath, os.pwd)
      val baseDir = Path(projectDir, os.pwd)
      if !os.exists(p) || !os.isFile(p) then
        return Left(s"File not found: $settingsPath")

      val modules = mutable.Set[String]()
      val dependencies = mutable.Map[String, mutable.Set[String]]()

      val lines = os.read.lines(p, charSet = java.nio.charset.StandardCharsets.UTF_8)
      for line <- lines do
        val trimmed = line.trim
        if trimmed.nonEmpty && !trimmed.startsWith("//") && !trimmed.startsWith("#") && !trimmed.startsWith("/*") then
          if trimmed.startsWith("include ") || trimmed.startsWith("include(") ||
             trimmed.startsWith("includeBuild ") || trimmed.startsWith("includeBuild(") then
            for m <- stringLiteralPattern.findAllMatchIn(trimmed) do
              val rawName = m.group(1).trim
              val normalized = rawName.stripPrefix(":")
              if normalized.nonEmpty then
                modules += normalized
                dependencies(normalized) = mutable.Set[String]()

      // For each module, look for its build.gradle or build.gradle.kts and extract project(':...') deps
      for moduleName <- modules do
        val moduleRelPath = moduleName.replace(":", "/")
        val subDir = baseDir / os.RelPath(moduleRelPath)
        val buildGradle = subDir / "build.gradle"
        val buildGradleKts = subDir / "build.gradle.kts"

        val targetFile = if os.exists(buildGradle) && os.isFile(buildGradle) then Some(buildGradle)
        else if os.exists(buildGradleKts) && os.isFile(buildGradleKts) then Some(buildGradleKts)
        else None

        targetFile.foreach { bf =>
          val content = os.read(bf)
          for m <- projectPattern.findAllMatchIn(content) do
            val refRaw = m.group(1).trim
            val refModule = refRaw.stripPrefix(":")
            if modules.contains(refModule) && refModule != moduleName then
              dependencies(moduleName) += refModule
        }

      Right(dependencies.map((k, v) => k -> v.toSet).toMap)
    catch
      case e: Exception =>
        Left(s"Error extracting Gradle project dependencies: ${e.getMessage}")
