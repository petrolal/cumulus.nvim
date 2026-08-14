package cumulus.build

import cumulus.protocol.{CumulusResponse, CumulusError}
import scala.io.Source
import java.io.File
import scala.util.Using

object GradleParser:

  /**
   * Parse Gradle task output from stdin or provided text.
   * Extracts task names from ./gradlew tasks output.
   * Expected format:
   *   Build tasks
   *   -----------
   *   assemble - Assemble main and test classes
   *   build - Assemble and test this project
   *
   * @param input The gradle tasks output text. If null or empty, returns empty response.
   * @return CumulusResponse containing parsed tasks or error information
   */
  def parseTasks(input: String): CumulusResponse[ParseGradleTasksResponse] =
    try
      // Validate input: return empty response if null or empty
      if input == null || input.trim.isEmpty then
        return CumulusResponse(
          success = true,
          data = Some(ParseGradleTasksResponse(Seq())),
          error = None,
          error_code = None
        )

      val tasks = scala.collection.mutable.Set[String]()
      val lines = input.split("\n")

      // Parse each line looking for task definitions
      // Format: "taskName - description"
      // Lines with " - " indicate a task
      for line <- lines do
        val trimmed = line.trim
        // Skip empty lines, section headers (lines ending with dashes), and comments
        if trimmed.nonEmpty && !trimmed.endsWith("-") && !trimmed.startsWith("//") && !trimmed.startsWith("#") && trimmed.contains(" - ") then
          // Extract task name (everything before " - ")
          val parts = trimmed.split(" - ", 2)
          if parts.length > 0 then
            val taskName = parts(0).trim
            // Filter out empty task names and add non-empty ones
            if taskName.nonEmpty && !taskName.contains(":") then
              // Basic task name (no namespace prefix)
              tasks += taskName
            else if taskName.nonEmpty && taskName.contains(":") then
              // Handle namespaced tasks like "spring-boot:run"
              tasks += taskName

      val sortedTasks = tasks.toSeq.sorted.map(GradleTask(_))

      CumulusResponse(
        success = true,
        data = Some(ParseGradleTasksResponse(sortedTasks)),
        error = None,
        error_code = None
      )
    catch
      case e: java.io.IOException =>
        CumulusResponse(
          success = false,
          data = None,
          error = Some(s"IO error reading Gradle tasks: ${e.getMessage}"),
          error_code = Some("INTERNAL_ERROR")
        )
      case e: Exception =>
        CumulusResponse(
          success = false,
          data = None,
          error = Some(s"Error parsing Gradle tasks: ${e.getMessage}"),
          error_code = Some("PARSE_ERROR")
        )

  /**
   * Parse Gradle modules from a settings.gradle file.
   * Extracts module names and paths from include '...' directives.
   * Example:
   *   include 'core'
   *   include 'web:api'
   *
   * Nested modules use colon separators which are converted to paths:
   *   include 'web:api' -> module name "web:api", path "web/api"
   *
   * @param settingsPath Path to the settings.gradle file
   * @return CumulusResponse containing parsed modules or error information
   */
  def parseModules(settingsPath: String): CumulusResponse[ParseGradleModulesResponse] =
    try
      val file = new File(settingsPath)
      if !file.exists() then
        return CumulusResponse(
          success = false,
          data = None,
          error = Some(s"File not found: $settingsPath"),
          error_code = Some("FILE_NOT_FOUND")
        )

      val modules = scala.collection.mutable.ListBuffer[GradleModule]()

      Using(Source.fromFile(file, "UTF-8")) { source =>
        for line <- source.getLines() do
          val trimmed = line.trim
          // Skip empty lines and comments
          if trimmed.nonEmpty && !trimmed.startsWith("//") && !trimmed.startsWith("#") then
            // Parse include directives: include 'moduleName' or include "moduleName"
            if trimmed.startsWith("include ") then
              // Extract the module name from quotes
              val pattern = """include\s+['"]([^'"]+)['"]""".r
              pattern.findFirstMatchIn(trimmed) match
                case Some(m) =>
                  val moduleName = m.group(1)
                  if moduleName.nonEmpty then
                    // Convert nested module names (colons) to paths (slashes)
                    val modulePath = moduleName.replace(":", "/")
                    modules += GradleModule(moduleName, modulePath)
                case None => () // Skip malformed lines
      }

      CumulusResponse(
        success = true,
        data = Some(ParseGradleModulesResponse(modules.toSeq)),
        error = None,
        error_code = None
      )
    catch
      case e: java.io.IOException =>
        CumulusResponse(
          success = false,
          data = None,
          error = Some(s"IO error reading settings.gradle: ${e.getMessage}"),
          error_code = Some("INTERNAL_ERROR")
        )
      case e: Exception =>
        CumulusResponse(
          success = false,
          data = None,
          error = Some(s"Error parsing Gradle modules: ${e.getMessage}"),
          error_code = Some("INTERNAL_ERROR")
        )
