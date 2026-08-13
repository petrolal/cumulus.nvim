package cumulus.build

import cumulus.protocol.{CumulusResponse, CumulusError}
import upickle.default.ReadWriter
import scala.xml.{XML, Elem, Node}
import scala.io.Source
import java.io.File

object MavenParser:

  /**
   * Parse Maven lifecycle and plugin goals from a pom.xml file.
   * Returns goals from:
   * - Standard Maven lifecycle: clean, compile, test, package, install
   * - Plugin-specific goals: Spring Boot, Quarkus, Surefire, Exec
   */
  def parseGoals(pomPath: String): CumulusResponse[ParsePomResponse] =
    try
      val file = new File(pomPath)
      if !file.exists() then
        return CumulusResponse(
          success = false,
          data = None,
          error = Some(s"File not found: $pomPath"),
          error_code = Some("FILE_NOT_FOUND")
        )

      val pom = XML.loadFile(file)
      val goals = scala.collection.mutable.ListBuffer[PomGoal]()

      // Add standard lifecycle goals
      goals += PomGoal("clean", "lifecycle")
      goals += PomGoal("compile", "lifecycle")
      goals += PomGoal("test", "lifecycle")
      goals += PomGoal("package", "lifecycle")
      goals += PomGoal("install", "lifecycle")

      // Extract plugin-specific goals
      extractPluginGoals(pom) foreach { goal =>
        goals += goal
      }

      CumulusResponse(
        success = true,
        data = Some(ParsePomResponse(goals.toSeq)),
        error = None,
        error_code = None
      )
    catch
      case e: org.xml.sax.SAXException =>
        CumulusResponse(
          success = false,
          data = None,
          error = Some(s"XML parse error: ${e.getMessage}"),
          error_code = Some("PARSE_ERROR")
        )
      case e: Exception =>
        CumulusResponse(
          success = false,
          data = None,
          error = Some(s"Error parsing POM: ${e.getMessage}"),
          error_code = Some("INTERNAL_ERROR")
        )

  /**
   * Parse Maven submodules from a pom.xml file.
   * Extracts module names and relative paths from <modules> block.
   */
  def parseModules(pomPath: String): CumulusResponse[ParseModulesResponse] =
    try
      val file = new File(pomPath)
      if !file.exists() then
        return CumulusResponse(
          success = false,
          data = None,
          error = Some(s"File not found: $pomPath"),
          error_code = Some("FILE_NOT_FOUND")
        )

      val pom = XML.loadFile(file)
      val modules = scala.collection.mutable.ListBuffer[PomModule]()

      // Look for modules - try both with and without namespace
      val modulesNodes = (pom \\ "module")

      for moduleNode <- modulesNodes do
        val moduleName = moduleNode.text.trim
        if moduleName.nonEmpty then
          // Normalize path: if it doesn't start with ./ or /, add ./
          val modulePath = if moduleName.startsWith("./") || moduleName.startsWith("/") then
            moduleName
          else
            s"./$moduleName"
          modules += PomModule(moduleName, modulePath)

      CumulusResponse(
        success = true,
        data = Some(ParseModulesResponse(modules.toSeq)),
        error = None,
        error_code = None
      )
    catch
      case e: org.xml.sax.SAXException =>
        CumulusResponse(
          success = false,
          data = None,
          error = Some(s"XML parse error: ${e.getMessage}"),
          error_code = Some("PARSE_ERROR")
        )
      case e: Exception =>
        CumulusResponse(
          success = false,
          data = None,
          error = Some(s"Error parsing POM: ${e.getMessage}"),
          error_code = Some("INTERNAL_ERROR")
        )

  /**
   * Extract plugin-specific goals from the POM's build/plugins section.
   */
  private def extractPluginGoals(pom: Elem): Seq[PomGoal] =
    val goals = scala.collection.mutable.ListBuffer[PomGoal]()

    // Get all plugin elements
    val pluginNodes = (pom \\ "plugin")

    for pluginNode <- pluginNodes do
      val groupId = (pluginNode \ "groupId").text.trim
      val artifactId = (pluginNode \ "artifactId").text.trim

      // Map known plugins to their goals
      if artifactId == "spring-boot-maven-plugin" then
        goals += PomGoal("spring-boot:run", "plugin")
        goals += PomGoal("spring-boot:build-image", "plugin")
      else if artifactId == "quarkus-maven-plugin" then
        goals += PomGoal("quarkus:dev", "plugin")
        goals += PomGoal("quarkus:build", "plugin")
      else if artifactId == "maven-surefire-plugin" then
        goals += PomGoal("surefire:test", "plugin")
      else if artifactId == "exec-maven-plugin" then
        goals += PomGoal("exec:java", "plugin")
        goals += PomGoal("exec:exec", "plugin")

    goals.toSeq
