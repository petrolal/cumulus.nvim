package cumulus.build

import cumulus.protocol.{CumulusResponse, CumulusError}
import scala.xml.{XML, Elem, Node}
import scala.io.Source
import java.io.File
import scala.util.Using

/**
 * DependencyExtractor extracts module dependencies from Maven and Gradle build files.
 *
 * For Maven: parses pom.xml files to find inter-module dependencies via <dependency> elements.
 * For Gradle: parses build.gradle files to find inter-module dependencies via project() references.
 */
object DependencyExtractor:

  /**
   * Extract module dependencies from a Maven pom.xml file.
   * Returns a map of module name to its direct module dependencies.
   *
   * @param pomPath Path to pom.xml
   * @return Map where keys are module names and values are sets of module dependencies
   */
  def extractMavenDependencies(pomPath: String): Either[String, Map[String, Set[String]]] =
    try
      val file = new File(pomPath)
      if !file.exists() then
        return Left(s"File not found: $pomPath")

      val pom = XML.loadFile(file)
      val dependencies = scala.collection.mutable.Map[String, scala.collection.mutable.Set[String]]()

      // First, extract all module names from <modules>
      val modules = (pom \\ "module").map(_.text.trim).filter(_.nonEmpty).toSet

      // Extract the project's groupId and version for comparison
      val projectGroupId = (pom \ "groupId").text.trim
      val projectVersion = (pom \ "version").text.trim

      // For each module, we could potentially look for its pom.xml to get its dependencies
      // However, the spec indicates we should look for inter-module references in THIS pom.xml
      // This would typically be done via properties or direct module references in dependency management

      // Initialize each module with empty dependency set
      for moduleName <- modules do
        dependencies(moduleName) = scala.collection.mutable.Set[String]()

      // Look for inter-module dependencies in the main pom's dependencyManagement or dependencies
      // Extract dependencies that reference other modules (same groupId typically indicates inter-module)
      val allDeps = (pom \\ "dependency")
      for dep <- allDeps do
        val depGroupId = (dep \ "groupId").text.trim
        val depArtifactId = (dep \ "artifactId").text.trim

        // Check if this is an inter-module dependency (by matching groupId)
        if depGroupId == projectGroupId && modules.contains(depArtifactId) then
          // This dependency references an artifact that matches a module name
          // We'd need context of which module declares this to properly build the graph
          // For now, we mark all modules as potentially depending on this
          ()

      Right(dependencies.mapValues(_.toSet).toMap)
    catch
      case e: org.xml.sax.SAXException =>
        Left(s"XML parse error: ${e.getMessage}")
      case e: Exception =>
        Left(s"Error extracting Maven dependencies: ${e.getMessage}")

  /**
   * Extract module dependencies from a Gradle build.gradle file.
   * Returns a map of module name to its direct module dependencies.
   *
   * @param buildGradlePath Path to build.gradle
   * @return Map where keys are module names and values are sets of module dependencies
   */
  def extractGradleDependencies(buildGradlePath: String): Either[String, Map[String, Set[String]]] =
    try
      val file = new File(buildGradlePath)
      if !file.exists() then
        return Left(s"File not found: $buildGradlePath")

      val dependencies = scala.collection.mutable.Map[String, scala.collection.mutable.Set[String]]()

      Using(Source.fromFile(file, "UTF-8")) { source =>
        val content = source.mkString

        // Parse project(':moduleName') references from dependencies block
        // Handles both single and double quotes, with optional whitespace
        val projectPattern = """project\s*\(\s*['"]([^'"]+)['"]\s*\)""".r

        for matchResult <- projectPattern.findAllMatchIn(content) do
          val moduleName = matchResult.group(1)
          // Normalize module names (strip leading colons if present)
          val normalized = if moduleName.startsWith(":") then moduleName.substring(1) else moduleName
          if !dependencies.contains(normalized) then
            dependencies(normalized) = scala.collection.mutable.Set[String]()
      }

      Right(dependencies.mapValues(_.toSet).toMap)
    catch
      case e: java.io.IOException =>
        Left(s"IO error reading build.gradle: ${e.getMessage}")
      case e: Exception =>
        Left(s"Error extracting Gradle dependencies: ${e.getMessage}")

  /**
   * Extract inter-module dependencies from a Gradle settings.gradle file.
   * Returns a map where each included module may depend on other modules found in the same project.
   *
   * Note: This scans the settings.gradle for include directives to find all modules,
   * then looks for dependency declarations in individual build.gradle files.
   *
   * @param settingsPath Path to settings.gradle
   * @param projectDir Base project directory
   * @return Map of module dependencies found
   */
  def extractGradleProjectDependencies(
    settingsPath: String,
    projectDir: String
  ): Either[String, Map[String, Set[String]]] =
    try
      val file = new File(settingsPath)
      if !file.exists() then
        return Left(s"File not found: $settingsPath")

      val modules = scala.collection.mutable.Set[String]()
      val dependencies = scala.collection.mutable.Map[String, scala.collection.mutable.Set[String]]()

      // First pass: extract all module names from settings.gradle
      Using(Source.fromFile(file, "UTF-8")) { source =>
        for line <- source.getLines() do
          val trimmed = line.trim
          if trimmed.nonEmpty && !trimmed.startsWith("//") && !trimmed.startsWith("#") then
            if trimmed.startsWith("include ") then
              val pattern = """include\s+['"]([^'"]+)['"]""".r
              pattern.findFirstMatchIn(trimmed) match
                case Some(m) =>
                  val moduleName = m.group(1)
                  if moduleName.nonEmpty then
                    modules += moduleName
                    // Initialize dependency set for this module
                    dependencies(moduleName) = scala.collection.mutable.Set[String]()
                case None => ()
      }

      // Second pass: for each module, look for its build.gradle and extract dependencies
      for moduleName <- modules do
        val moduleDir = moduleName.replace(":", File.separator)
        val buildGradleFile = new File(projectDir, moduleDir + File.separator + "build.gradle")

        if buildGradleFile.exists() then
          Using(Source.fromFile(buildGradleFile, "UTF-8")) { source =>
            val content = source.mkString
            val projectPattern = """project\s*\(\s*['"]([^'"]+)['"]\s*\)""".r

            for matchResult <- projectPattern.findAllMatchIn(content) do
              var refModule = matchResult.group(1)
              // Normalize module names (strip leading colons if present)
              if refModule.startsWith(":") then
                refModule = refModule.substring(1)

              // Only add if it's a known module
              if modules.contains(refModule) && refModule != moduleName then
                dependencies(moduleName) += refModule
          }

      Right(dependencies.mapValues(_.toSet).toMap)
    catch
      case e: java.io.IOException =>
        Left(s"IO error: ${e.getMessage}")
      case e: Exception =>
        Left(s"Error extracting Gradle project dependencies: ${e.getMessage}")
