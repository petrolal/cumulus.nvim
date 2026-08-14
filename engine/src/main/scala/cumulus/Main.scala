package cumulus

import cumulus.protocol.{CumulusResponse, CumulusError}
import cumulus.build.{MavenParser, GradleParser, ParsePomResponse, ParseGradleTasksResponse, ParseModulesResponse, ParseGradleModulesResponse, ComputeBuildOrderResponse, ModuleBuildStep, DagSolver, DependencyExtractor}
import upickle.default.ReadWriter
import scala.io.Source
import java.io.File

object Main:
  // Helper functions to construct response envelopes
  def successEnvelope[T](data: Option[T]): CumulusResponse[T] =
    CumulusResponse(
      success = true,
      data = data,
      error = None,
      error_code = None
    )

  def errorEnvelope[T](message: String, code: CumulusError = CumulusError.INVALID_INPUT): CumulusResponse[T] =
    CumulusResponse(
      success = false,
      data = None,
      error = Some(message),
      error_code = Some(code.toString)
    )

  /**
   * Parse command-line arguments as key-value pairs.
   * Example: ["--file", "pom.xml", "--tool", "maven"] becomes Map("file" -> "pom.xml", "tool" -> "maven")
   */
  def parseArgs(args: Seq[String]): Map[String, String] =
    args.sliding(2, 1).collect {
      case Seq(key, value) if key.startsWith("--") => (key.substring(2), value)
    }.toMap

  def serializeResponse[T: ReadWriter](response: CumulusResponse[T]): Unit =
    try
      println(ujson.write(CumulusResponse.toJson(response)))
    catch
      case e: Exception =>
        // On serialization error, return an error envelope to stdout (not a stack trace)
        val errorResponse = CumulusResponse[Unit](
          success = false,
          data = None,
          error = Some(s"Serialization error: ${e.getMessage}"),
          error_code = Some("INTERNAL_ERROR")
        )
        given ReadWriter[Unit] = upickle.default.readwriter[String].bimap[Unit](
          _ => "null",
          _ => ()
        )
        println(ujson.write(CumulusResponse.toJson(errorResponse)))

  /**
   * Compute build order for a multi-module project by detecting tool (Maven/Gradle) and solving the DAG.
   */
  def computeBuildOrderForDirectory(dirPath: String): CumulusResponse[ComputeBuildOrderResponse] =
    try
      val dir = new File(dirPath)
      if !dir.exists() || !dir.isDirectory() then
        return CumulusResponse(
          success = false,
          data = None,
          error = Some(s"Directory not found or not a directory: $dirPath"),
          error_code = Some("FILE_NOT_FOUND")
        )

      val pomFile = new File(dir, "pom.xml")
      val settingsFile = new File(dir, "settings.gradle")
      val buildFile = new File(dir, "build.gradle")

      // Detect project type and extract modules + dependencies
      if pomFile.exists() then
        // Maven project
        val modulesResult = MavenParser.parseModules(pomFile.getAbsolutePath())
        if !modulesResult.success then
          return CumulusResponse(
            success = false,
            data = None,
            error = modulesResult.error,
            error_code = modulesResult.error_code
          )

        if modulesResult.data.isEmpty then
          return CumulusResponse(
            success = false,
            data = None,
            error = Some("Maven parser succeeded but returned no data"),
            error_code = Some("PARSE_ERROR")
          )

        val modules = modulesResult.data.get.modules
        if modules.isEmpty then
          // Single module project
          return CumulusResponse(
            success = true,
            data = Some(ComputeBuildOrderResponse(
              modules = Seq(ModuleBuildStep(step = 1, name = ".", path = ".", buildCommand = "mvn")),
              warnings = None
            )),
            error = None,
            error_code = None
          )

        // For Maven, we need to extract dependencies from individual module pom.xml files
        // For now, we'll assume modules are listed in declaration order and build accordingly
        val moduleNames = modules.map(_.name)
        val modulePaths = modules.map(_.path).map { p =>
          if p.startsWith("./") && p.length > 2 then p.substring(2) else p
        }

        // Build steps with sequential ordering (safe fallback)
        val steps = moduleNames.zipWithIndex.map { case (name, idx) =>
          val path = modulePaths(idx)
          ModuleBuildStep(
            step = idx + 1,
            name = name,
            path = path,
            buildCommand = "mvn"
          )
        }

        CumulusResponse(
          success = true,
          data = Some(ComputeBuildOrderResponse(
            modules = steps,
            warnings = Some(List("Maven inter-module dependency detection not yet implemented; using declaration order"))
          )),
          error = None,
          error_code = None
        )
      else if settingsFile.exists() || buildFile.exists() then
        // Gradle project
        val settingsPath = if settingsFile.exists() then
          settingsFile.getAbsolutePath()
        else if buildFile.exists() then
          buildFile.getAbsolutePath()
        else
          return CumulusResponse(
            success = false,
            data = None,
            error = Some("Gradle project detected but neither settings.gradle nor build.gradle found"),
            error_code = Some("FILE_NOT_FOUND")
          )

        val modulesResult = GradleParser.parseModules(settingsPath)
        if !modulesResult.success then
          return CumulusResponse(
            success = false,
            data = None,
            error = modulesResult.error,
            error_code = modulesResult.error_code
          )

        if modulesResult.data.isEmpty then
          return CumulusResponse(
            success = false,
            data = None,
            error = Some("Gradle parser succeeded but returned no data"),
            error_code = Some("PARSE_ERROR")
          )

        val modules = modulesResult.data.get.modules
        if modules.isEmpty then
          // Single module project
          return CumulusResponse(
            success = true,
            data = Some(ComputeBuildOrderResponse(
              modules = Seq(ModuleBuildStep(step = 1, name = ".", path = ".", buildCommand = "gradle")),
              warnings = None
            )),
            error = None,
            error_code = None
          )

        val moduleNames = modules.map(_.name)
        val modulePaths = modules.map(_.path)

        // Extract dependencies from Gradle project
        val dependenciesResult = DependencyExtractor.extractGradleProjectDependencies(
          settingsPath,
          dirPath
        )

        val (dependencies, warnings) = dependenciesResult match
          case Left(err) =>
            // If dependency extraction fails, fall back to declaration order with warning
            (Map[String, Set[String]](), Some(List(s"Failed to extract Gradle dependencies: $err; using declaration order")))
          case Right(deps) =>
            (deps, None)

        // Compute build order using topological sort
        val (orderedNames, cycleWarnings) = DagSolver.computeBuildOrder(moduleNames, dependencies)

        // Validate that all modules are present in ordered output
        if orderedNames.length != moduleNames.length || !orderedNames.toSet.equals(moduleNames.toSet) then
          return CumulusResponse(
            success = false,
            data = None,
            error = Some(s"Build order computation returned inconsistent module set; expected ${moduleNames.length} modules, got ${orderedNames.length}"),
            error_code = Some("INTERNAL_ERROR")
          )

        // Map ordered names back to paths
        val nameToPath = modules.map(m => m.name -> m.path).toMap
        val steps = orderedNames.zipWithIndex.map { case (name, idx) =>
          val path = nameToPath.get(name) match
            case Some(p) => p
            case None =>
              return CumulusResponse(
                success = false,
                data = None,
                error = Some(s"Module $name from build order not found in module list"),
                error_code = Some("INTERNAL_ERROR")
              )
          ModuleBuildStep(
            step = idx + 1,
            name = name,
            path = path,
            buildCommand = "gradle"
          )
        }

        // Combine warnings
        val finalWarnings = (warnings.toList.flatten ++ cycleWarnings.toList.flatten) match
          case ws if ws.nonEmpty => Some(ws)
          case _ => None

        CumulusResponse(
          success = true,
          data = Some(ComputeBuildOrderResponse(
            modules = steps,
            warnings = finalWarnings
          )),
          error = None,
          error_code = None
        )
      else
        // No Maven or Gradle project found
        CumulusResponse(
          success = false,
          data = None,
          error = Some(s"No pom.xml or Gradle build files found in $dirPath"),
          error_code = Some("FILE_NOT_FOUND")
        )
    catch
      case e: Exception =>
        CumulusResponse(
          success = false,
          data = None,
          error = Some(s"Error computing build order: ${e.getMessage}"),
          error_code = Some("INTERNAL_ERROR")
        )

  def main(args: Array[String]): Unit =
    if args.isEmpty then
      given ReadWriter[Unit] = upickle.default.readwriter[String].bimap[Unit](
        _ => "null",
        _ => ()
      )
      serializeResponse(errorEnvelope[Unit]("No subcommand provided"))
    else
      args(0) match
        case "ping" =>
          given ReadWriter[Unit] = upickle.default.readwriter[String].bimap[Unit](
            _ => "null",
            _ => ()
          )
          serializeResponse(successEnvelope[Unit](None))

        case "parse-pom" =>
          val argMap = parseArgs(args.slice(1, args.length))
          given ReadWriter[ParsePomResponse] = upickle.default.macroRW
          val result = argMap.get("file") match
            case None =>
              errorEnvelope[ParsePomResponse]("Missing --file argument")
            case Some(filePath) =>
              MavenParser.parseGoals(filePath)
          serializeResponse(result)

        case "parse-gradle-tasks" =>
          given ReadWriter[ParseGradleTasksResponse] = upickle.default.macroRW
          try
            val stdinInput = Source.fromInputStream(System.in).mkString
            val result = GradleParser.parseTasks(stdinInput)
            serializeResponse(result)
          catch
            case e: java.io.IOException =>
              serializeResponse(CumulusResponse(
                success = false,
                data = None,
                error = Some(s"IO error reading stdin: ${e.getMessage}"),
                error_code = Some("IO_ERROR")
              ))

        case "parse-modules" =>
          val argMap = parseArgs(args.slice(1, args.length))
          (argMap.get("tool"), argMap.get("file")) match
            case (None, _) =>
              given ReadWriter[ParseModulesResponse] = upickle.default.macroRW
              serializeResponse(errorEnvelope[ParseModulesResponse]("Missing --tool argument"))
            case (_, None) =>
              given ReadWriter[ParseModulesResponse] = upickle.default.macroRW
              serializeResponse(errorEnvelope[ParseModulesResponse]("Missing --file argument"))
            case (Some("maven"), Some(filePath)) =>
              given ReadWriter[ParseModulesResponse] = upickle.default.macroRW
              val result = MavenParser.parseModules(filePath)
              serializeResponse(result)
            case (Some("gradle"), Some(filePath)) =>
              given ReadWriter[ParseGradleModulesResponse] = upickle.default.macroRW
              val result = GradleParser.parseModules(filePath)
              serializeResponse(result)
            case (Some(tool), _) =>
              given ReadWriter[ParseModulesResponse] = upickle.default.macroRW
              serializeResponse(errorEnvelope[ParseModulesResponse](s"Unsupported tool: $tool"))

        case "compute-build-order" =>
          given ReadWriter[ComputeBuildOrderResponse] = upickle.default.macroRW
          val argMap = parseArgs(args.slice(1, args.length))
          val result = argMap.get("dir") match
            case None =>
              errorEnvelope[ComputeBuildOrderResponse]("Missing --dir argument")
            case Some(dirPath) =>
              computeBuildOrderForDirectory(dirPath)
          serializeResponse(result)

        case _ =>
          given ReadWriter[Unit] = upickle.default.readwriter[String].bimap[Unit](
            _ => "null",
            _ => ()
          )
          serializeResponse(errorEnvelope[Unit]("Unknown subcommand"))
