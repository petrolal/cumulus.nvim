package cumulus.build

import os.Path
import cumulus.protocol.{CumulusResponse, Module, ModuleTree}

object GradleParser:

  // Regex patterns for tasks
  private lazy val headerPattern = """^([A-Z][a-zA-Z0-9\s]+)\s+tasks$""".r
  private lazy val separatorPattern = """^-+$""".r
  private lazy val taskPattern = """^([a-zA-Z0-9_:-]+)\s+-\s+(.+)$""".r

  // Regex for include / includeBuild directives
  private lazy val stringLiteralPattern = """['"]([^'"]+)['"]""".r

  /**
   * Parse tasks from Gradle task output text.
   * Extracts task names from ./gradlew tasks output.
   *
   * @param input The gradle tasks output text. If null or empty, returns empty response.
   * @return CumulusResponse containing parsed tasks grouped by group name
   */
  def parseTasks(input: String): CumulusResponse[ParseGradleTasksResponse] =
    try
      if input == null || input.trim.isEmpty then
        return CumulusResponse(
          success = true,
          data = Some(ParseGradleTasksResponse(Seq())),
          error = None,
          error_code = None
        )

      val tasks = scala.collection.mutable.Set[String]()
      val lines = input.split("\n")

      for line <- lines do
        val trimmed = line.trim
        if trimmed.nonEmpty && !trimmed.endsWith("-") && !trimmed.startsWith("//") && !trimmed.startsWith("#") && trimmed.contains(" - ") then
          val parts = trimmed.split(" - ", 2)
          if parts.length > 0 then
            val taskName = parts(0).trim
            if taskName.nonEmpty then
              tasks += taskName

      val sortedTasks = tasks.toSeq.sorted.map(GradleTask(_))

      CumulusResponse(
        success = true,
        data = Some(ParseGradleTasksResponse(sortedTasks)),
        error = None,
        error_code = None
      )

    catch
      case e: Exception =>
        CumulusResponse(
          success = false,
          data = None,
          error = Some(s"Error parsing Gradle tasks: ${e.getMessage}"),
          error_code = Some("PARSE_ERROR")
        )


  /**
   * Parse submodules from a settings.gradle or settings.gradle.kts file.
   *
   * @param settingsFilePath Path to the settings.gradle / settings.gradle.kts file
   * @return CumulusResponse containing the list of parsed submodules
   */
  def parseModules(settingsFilePath: String): CumulusResponse[ParseGradleModulesResponse] =
    try
      val p = Path(settingsFilePath, os.pwd)
      if !os.exists(p) || !os.isFile(p) then
        return CumulusResponse(
          success = false,
          data = None,
          error = Some(s"Settings file not found: $settingsFilePath"),
          error_code = Some("FILE_NOT_FOUND")
        )

      val modules = scala.collection.mutable.ListBuffer[GradleModule]()
      val lines = os.read.lines(p, charSet = java.nio.charset.StandardCharsets.UTF_8)

      for line <- lines do
        val trimmed = line.trim
        // Skip empty lines and comments
        if trimmed.nonEmpty && !trimmed.startsWith("//") && !trimmed.startsWith("#") && !trimmed.startsWith("/*") then
          // Parse include and includeBuild directives
          if trimmed.startsWith("include ") || trimmed.startsWith("include(") ||
             trimmed.startsWith("includeBuild ") || trimmed.startsWith("includeBuild(") then
            for m <- stringLiteralPattern.findAllMatchIn(trimmed) do
              val rawName = m.group(1).trim
              if rawName.nonEmpty then
                val moduleName = rawName.stripPrefix(":")
                val modulePath = moduleName.replace(":", "/")
                modules += GradleModule(rawName, modulePath)

      CumulusResponse(
        success = true,
        data = Some(ParseGradleModulesResponse(modules.toSeq)),
        error = None,
        error_code = None
      )
    catch
      case e: Exception =>
        CumulusResponse(
          success = false,
          data = None,
          error = Some(s"Error parsing Gradle modules: ${e.getMessage}"),
          error_code = Some("INTERNAL_ERROR")
        )

  /**
   * Resolve the module tree from a Gradle settings.gradle file.
   * Extracts include and includeBuild directives and returns them as a flat ModuleTree.
   * Only processes single-level module hierarchy from include directives.
   */
  def resolveModuleTree(rootDir: String): CumulusResponse[ModuleTree] =
    try
      val rootPath = Path(rootDir, os.pwd)
      if !os.exists(rootPath) || !os.isDir(rootPath) then
        return CumulusResponse(
          success = false,
          data = None,
          error = Some(s"Directory not found or not a directory: $rootDir"),
          error_code = Some("FILE_NOT_FOUND")
        )

      // Look for settings.gradle or settings.gradle.kts
      val settingsFile = rootPath / "settings.gradle"
      val settingsKtsFile = rootPath / "settings.gradle.kts"

      val settingsPath = if os.exists(settingsFile) && os.isFile(settingsFile) then
        settingsFile
      else if os.exists(settingsKtsFile) && os.isFile(settingsKtsFile) then
        settingsKtsFile
      else
        // No settings file found, treat as single-module project
        return CumulusResponse(
          success = true,
          data = Some(ModuleTree(
            root = rootPath.toString,
            modules = Seq(),
            dependencies = None
          )),
          error = None,
          error_code = None
        )

      val modules = scala.collection.mutable.ListBuffer[Module]()
      val lines = os.read.lines(settingsPath, charSet = java.nio.charset.StandardCharsets.UTF_8)

      for line <- lines do
        val trimmed = line.trim
        // Skip empty lines and comments
        if trimmed.nonEmpty && !trimmed.startsWith("//") && !trimmed.startsWith("#") && !trimmed.startsWith("/*") then
          // Parse include and includeBuild directives
          if trimmed.startsWith("include ") || trimmed.startsWith("include(") ||
             trimmed.startsWith("includeBuild ") || trimmed.startsWith("includeBuild(") then
            for m <- stringLiteralPattern.findAllMatchIn(trimmed) do
              val rawName = Option(m.group(1)).map(_.trim).getOrElse("")
              if rawName.nonEmpty then
                val moduleName = rawName.stripPrefix(":").stripPrefix("/")
                if moduleName.nonEmpty then
                  val modulePath = moduleName.replace(":", "/")

                  // Validate that the module directory exists
                  val moduleDir = rootPath / modulePath
                  if os.exists(moduleDir) && os.isDir(moduleDir) then
                    modules += Module(
                      name = moduleName,
                      path = modulePath,
                      relativePath = Some(modulePath)
                    )

      CumulusResponse(
        success = true,
        data = Some(ModuleTree(
          root = rootPath.toString,
          modules = modules.toSeq,
          dependencies = None
        )),
        error = None,
        error_code = None
      )
    catch
      case e: Exception =>
        CumulusResponse(
          success = false,
          data = None,
          error = Some(s"Error resolving Gradle module tree: ${e.getMessage}"),
          error_code = Some("INTERNAL_ERROR")
        )
