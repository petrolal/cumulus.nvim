package cumulus

import cumulus.protocol.{CumulusResponse, CumulusError}
import cumulus.build.{MavenParser, GradleParser, ParsePomResponse, ParseGradleTasksResponse, ParseModulesResponse, ParseGradleModulesResponse, ComputeBuildOrderResponse, ModuleBuildStep, DagSolver, DependencyExtractor}
import cumulus.workspace.{JdkDiscoverer, BuildToolDetector, WorkspaceScanner, JdkInfo, BuildToolInfo, WorkspaceInfo}
import cumulus.code.{CodeLensExtractor, CodeLensItem, CodeLensResponse, SpringBootDetector, BeanGraphAnalyzer, SpringBootApp, SpringBeansResponse, EndpointScanner, Endpoint, EndpointsResponse, ImportOptimizer, ImportsResponse, JavaHeaderGenerator, JavaHeader}
import cumulus.testing.{TestContextDetector, TestOutputParser, TestCommandAssembler, TestContext, TestResult, TestCommand}
import cumulus.log.{LogParser, LogIndexer, StacktraceResolver, BuildDiagnostic, LogIndexEntry, StackFrame}
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
            val stdinInput = Source.fromInputStream(System.in, "UTF-8").mkString
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

        case "discover-jdk" =>
          given ReadWriter[JdkInfo] = upickle.default.macroRW
          val argMap = parseArgs(args.slice(1, args.length))
          val result = argMap.get("version") match
            case None =>
              errorEnvelope[JdkInfo]("Missing --version argument")
            case Some(version) =>
              JdkDiscoverer.discoverJdk(version) match
                case Right(jdkInfo) =>
                  successEnvelope[JdkInfo](Some(jdkInfo))
                case Left(error) =>
                  errorEnvelope[JdkInfo](error)
          serializeResponse(result)

        case "discover-build-tool" =>
          given ReadWriter[BuildToolInfo] = upickle.default.macroRW
          val argMap = parseArgs(args.slice(1, args.length))
          val result = argMap.get("dir") match
            case None =>
              errorEnvelope[BuildToolInfo]("Missing --dir argument")
            case Some(dirPath) =>
              BuildToolDetector.detectBuildTool(dirPath) match
                case Right(toolInfo) =>
                  successEnvelope[BuildToolInfo](Some(toolInfo))
                case Left(error) =>
                  errorEnvelope[BuildToolInfo](error)
          serializeResponse(result)

        case "discover-workspace" =>
          given ReadWriter[WorkspaceInfo] = upickle.default.macroRW
          val argMap = parseArgs(args.slice(1, args.length))
          val result = argMap.get("dir") match
            case None =>
              errorEnvelope[WorkspaceInfo]("Missing --dir argument")
            case Some(dirPath) =>
              WorkspaceScanner.discoverWorkspace(dirPath) match
                case Right(wsInfo) =>
                  successEnvelope[WorkspaceInfo](Some(wsInfo))
                case Left(error) =>
                  errorEnvelope[WorkspaceInfo](error)
          serializeResponse(result)

        case "extract-codelens" =>
          given ReadWriter[CodeLensResponse] = upickle.default.macroRW
          val argMap = parseArgs(args.slice(1, args.length))
          val result = argMap.get("file") match
            case None =>
              errorEnvelope[CodeLensResponse]("Missing --file argument")
            case Some(filePath) =>
              try
                val items = CodeLensExtractor.extractCodeLens(filePath)
                successEnvelope[CodeLensResponse](Some(CodeLensResponse(items = items)))
              catch
                case e: Exception =>
                  if e.getMessage != null && e.getMessage.contains("not found") then
                    errorEnvelope[CodeLensResponse](e.getMessage, CumulusError.FILE_NOT_FOUND)
                  else if e.getMessage != null && e.getMessage.contains("Unsupported file type") then
                    errorEnvelope[CodeLensResponse](e.getMessage, CumulusError.INVALID_INPUT)
                  else
                    errorEnvelope[CodeLensResponse](s"Error extracting CodeLens: ${e.getMessage}")
          serializeResponse(result)

        case "detect-springboot-app" =>
          given ReadWriter[SpringBootApp] = upickle.default.macroRW
          val argMap = parseArgs(args.slice(1, args.length))
          val result = argMap.get("dir") match
            case None =>
              errorEnvelope[SpringBootApp]("Missing --dir argument")
            case Some(dirPath) =>
              try
                val app = SpringBootDetector.detectSpringBootApp(dirPath)
                successEnvelope[SpringBootApp](Some(app))
              catch
                case e: Exception =>
                  if e.getMessage != null && e.getMessage.contains("No Spring Boot application found") then
                    errorEnvelope[SpringBootApp](e.getMessage, CumulusError.INVALID_INPUT)
                  else if e.getMessage != null && e.getMessage.contains("Directory not found") then
                    errorEnvelope[SpringBootApp](e.getMessage, CumulusError.FILE_NOT_FOUND)
                  else
                    errorEnvelope[SpringBootApp](s"Error detecting Spring Boot app: ${e.getMessage}")
          serializeResponse(result)

        case "parse-spring-beans" =>
          given ReadWriter[SpringBeansResponse] = upickle.default.macroRW
          val argMap = parseArgs(args.slice(1, args.length))
          val result = argMap.get("dir") match
            case None =>
              errorEnvelope[SpringBeansResponse]("Missing --dir argument")
            case Some(dirPath) =>
              try
                val beans = BeanGraphAnalyzer.parseSpringBeans(dirPath)
                successEnvelope[SpringBeansResponse](Some(SpringBeansResponse(beans = beans)))
              catch
                case e: Exception =>
                  if e.getMessage != null && e.getMessage.contains("Directory not found") then
                    errorEnvelope[SpringBeansResponse](e.getMessage, CumulusError.FILE_NOT_FOUND)
                  else
                    errorEnvelope[SpringBeansResponse](s"Error parsing Spring beans: ${e.getMessage}")
          serializeResponse(result)

        case "extract-endpoints" =>
          given ReadWriter[EndpointsResponse] = upickle.default.macroRW
          val argMap = parseArgs(args.slice(1, args.length))
          val result = argMap.get("dir") match
            case None =>
              errorEnvelope[EndpointsResponse]("Missing --dir argument")
            case Some(dirPath) =>
              try
                val endpoints = EndpointScanner.scanEndpoints(dirPath)
                successEnvelope[EndpointsResponse](Some(EndpointsResponse(endpoints = endpoints)))
              catch
                case e: Exception =>
                  if e.getMessage != null && e.getMessage.contains("Directory not found") then
                    errorEnvelope[EndpointsResponse](e.getMessage, CumulusError.FILE_NOT_FOUND)
                  else
                    errorEnvelope[EndpointsResponse](s"Error extracting endpoints: ${e.getMessage}")
          serializeResponse(result)

        case "optimize-imports" =>
          given ReadWriter[ImportsResponse] = upickle.default.macroRW
          try
            val stdinInput = Source.fromInputStream(System.in, "UTF-8").mkString
            val imports = ImportOptimizer.optimizeImports(stdinInput)
            serializeResponse(successEnvelope[ImportsResponse](Some(ImportsResponse(imports = imports))))
          catch
            case e: java.io.IOException =>
              serializeResponse(CumulusResponse(
                success = false,
                data = None,
                error = Some(s"IO error reading stdin: ${e.getMessage}"),
                error_code = Some("IO_ERROR")
              ))

        case "generate-java-header" =>
          given ReadWriter[JavaHeader] = upickle.default.macroRW
          val argMap = parseArgs(args.slice(1, args.length))
          val result = argMap.get("file") match
            case None =>
              errorEnvelope[JavaHeader]("Missing --file argument")
            case Some(filePath) =>
              try
                val header = JavaHeaderGenerator.generateHeader(filePath)
                successEnvelope[JavaHeader](Some(header))
              catch
                case e: Exception =>
                  if e.getMessage != null && e.getMessage.contains("File not found") then
                    errorEnvelope[JavaHeader](e.getMessage, CumulusError.FILE_NOT_FOUND)
                  else
                    errorEnvelope[JavaHeader](s"Error generating Java header: ${e.getMessage}")
          serializeResponse(result)

        case "detect-test-context" =>
          given ReadWriter[TestContext] = upickle.default.macroRW
          val argMap = parseArgs(args.slice(1, args.length))
          val result = (argMap.get("file"), argMap.get("line")) match
            case (None, _) =>
              errorEnvelope[TestContext]("Missing --file argument")
            case (_, None) =>
              errorEnvelope[TestContext]("Missing --line argument")
            case (Some(filePath), Some(lineStr)) =>
              try
                val lineNumber = lineStr.toInt
                TestContextDetector.detectTestContext(filePath, lineNumber) match
                  case Right(context) =>
                    successEnvelope[TestContext](Some(context))
                  case Left(error) =>
                    errorEnvelope[TestContext](error, CumulusError.INVALID_INPUT)
              catch
                case e: NumberFormatException =>
                  errorEnvelope[TestContext](s"Invalid line number: $lineStr")
                case e: Exception =>
                  errorEnvelope[TestContext](s"Error detecting test context: ${e.getMessage}")
          serializeResponse(result)

        case "parse-test-output" =>
          try
            val stdinInput = Source.fromInputStream(System.in, "UTF-8").mkString
            val result = TestOutputParser.parseTestOutput(stdinInput) match
              case Right(results) =>
                val jsonResults = results.map { r =>
                  ujson.Obj(
                    "class_name" -> r.class_name,
                    "method_name" -> r.method_name,
                    "status" -> r.status,
                    "message" -> r.message.map(ujson.Str(_)).getOrElse(ujson.Null)
                  )
                }
                ujson.Obj(
                  "success" -> true,
                  "data" -> ujson.Arr(jsonResults*),
                  "error" -> ujson.Null,
                  "error_code" -> ujson.Null
                )
              case Left(error) =>
                ujson.Obj(
                  "success" -> false,
                  "data" -> ujson.Null,
                  "error" -> error,
                  "error_code" -> CumulusError.PARSE_ERROR.toString
                )
            println(ujson.write(result))
          catch
            case e: Exception =>
              val errorResponse = ujson.Obj(
                "success" -> false,
                "data" -> ujson.Null,
                "error" -> s"Error reading or parsing test output: ${e.getMessage}",
                "error_code" -> CumulusError.INTERNAL_ERROR.toString
              )
              println(ujson.write(errorResponse))

        case "assemble-test-command" =>
          given ReadWriter[TestCommand] = upickle.default.macroRW
          val argMap = parseArgs(args.slice(1, args.length))
          val result = (argMap.get("tool"), argMap.get("class"), argMap.get("method"), argMap.get("dir")) match
            case (None, _, _, _) =>
              errorEnvelope[TestCommand]("Missing --tool argument")
            case (_, None, _, _) =>
              errorEnvelope[TestCommand]("Missing --class argument")
            case (_, _, None, _) =>
              errorEnvelope[TestCommand]("Missing --method argument")
            case (_, _, _, None) =>
              errorEnvelope[TestCommand]("Missing --dir argument")
            case (Some(tool), Some(className), Some(methodName), Some(dirPath)) =>
              TestCommandAssembler.assembleTestCommand(tool, className, methodName, dirPath) match
                case Right(command) =>
                  successEnvelope[TestCommand](Some(command))
                case Left(error) =>
                  errorEnvelope[TestCommand](error, CumulusError.INVALID_INPUT)
          serializeResponse(result)

        case "parse-build-log" =>
          val argMap = parseArgs(args.slice(1, args.length))
          try
            val result = argMap.get("file") match
              case Some(filePath) =>
                try
                  val diagnostics = LogParser.parseFromFile(filePath)
                  val jsonDiagnostics = diagnostics.map { d =>
                    ujson.Obj(
                      "file" -> d.file,
                      "line" -> d.line,
                      "col" -> d.col,
                      "severity" -> d.severity,
                      "message" -> d.message
                    )
                  }
                  ujson.Obj(
                    "success" -> true,
                    "data" -> ujson.Arr(jsonDiagnostics*),
                    "error" -> ujson.Null,
                    "error_code" -> ujson.Null
                  )
                catch
                  case e: Exception if e.getMessage.contains("not found") =>
                    ujson.Obj(
                      "success" -> false,
                      "data" -> ujson.Null,
                      "error" -> e.getMessage,
                      "error_code" -> CumulusError.FILE_NOT_FOUND.toString
                    )
                  case e: Exception =>
                    ujson.Obj(
                      "success" -> false,
                      "data" -> ujson.Null,
                      "error" -> s"Error parsing log: ${e.getMessage}",
                      "error_code" -> CumulusError.INTERNAL_ERROR.toString
                    )
              case None =>
                try
                  val stdinInput = Source.fromInputStream(System.in, "UTF-8").mkString
                  val diagnostics = LogParser.parseFromStdin(stdinInput)
                  val jsonDiagnostics = diagnostics.map { d =>
                    ujson.Obj(
                      "file" -> d.file,
                      "line" -> d.line,
                      "col" -> d.col,
                      "severity" -> d.severity,
                      "message" -> d.message
                    )
                  }
                  ujson.Obj(
                    "success" -> true,
                    "data" -> ujson.Arr(jsonDiagnostics*),
                    "error" -> ujson.Null,
                    "error_code" -> ujson.Null
                  )
                catch
                  case e: java.io.IOException =>
                    ujson.Obj(
                      "success" -> false,
                      "data" -> ujson.Null,
                      "error" -> s"IO error reading stdin: ${e.getMessage}",
                      "error_code" -> CumulusError.INTERNAL_ERROR.toString
                    )
            println(ujson.write(result))
          catch
            case e: Exception =>
              val errorResponse = ujson.Obj(
                "success" -> false,
                "data" -> ujson.Null,
                "error" -> s"Error: ${e.getMessage}",
                "error_code" -> CumulusError.INTERNAL_ERROR.toString
              )
              println(ujson.write(errorResponse))

        case "resolve-stacktrace-symbol" =>
          val argMap = parseArgs(args.slice(1, args.length))
          val result = (argMap.get("stacktrace"), argMap.get("dir")) match
            case (None, _) =>
              ujson.Obj(
                "success" -> false,
                "data" -> ujson.Null,
                "error" -> "Missing --stacktrace argument",
                "error_code" -> CumulusError.INVALID_INPUT.toString
              )
            case (_, None) =>
              ujson.Obj(
                "success" -> false,
                "data" -> ujson.Null,
                "error" -> "Missing --dir argument (workspace directory)",
                "error_code" -> CumulusError.INVALID_INPUT.toString
              )
            case (Some(stacktraceStr), Some(workspaceDir)) =>
              try
                StacktraceResolver.resolveStacktrace(stacktraceStr, workspaceDir) match
                  case Right(resolved) =>
                    val jsonMap = ujson.Obj()
                    resolved.foreach { case (k, v) =>
                      jsonMap(k) = v
                    }
                    ujson.Obj(
                      "success" -> true,
                      "data" -> jsonMap,
                      "error" -> ujson.Null,
                      "error_code" -> ujson.Null
                    )
                  case Left(error) =>
                    val errorCode = if error.contains("not found") then CumulusError.FILE_NOT_FOUND.toString else CumulusError.INTERNAL_ERROR.toString
                    ujson.Obj(
                      "success" -> false,
                      "data" -> ujson.Null,
                      "error" -> error,
                      "error_code" -> errorCode
                    )
              catch
                case e: Exception =>
                  ujson.Obj(
                    "success" -> false,
                    "data" -> ujson.Null,
                    "error" -> s"Error resolving stacktrace: ${e.getMessage}",
                    "error_code" -> CumulusError.INTERNAL_ERROR.toString
                  )
          println(ujson.write(result))

        case "index-log" =>
          val argMap = parseArgs(args.slice(1, args.length))
          try
            val result = argMap.get("file") match
              case Some(filePath) =>
                try
                  val entries = LogIndexer.indexLogFile(filePath)
                  val jsonEntries = entries.map { e =>
                    ujson.Obj(
                      "lineNumber" -> e.lineNumber,
                      "severity" -> e.severity,
                      "timestamp" -> e.timestamp.map(ujson.Str(_)).getOrElse(ujson.Null),
                      "message" -> e.message
                    )
                  }
                  ujson.Obj(
                    "success" -> true,
                    "data" -> ujson.Arr(jsonEntries*),
                    "error" -> ujson.Null,
                    "error_code" -> ujson.Null
                  )
                catch
                  case e: Exception if e.getMessage.contains("not found") =>
                    ujson.Obj(
                      "success" -> false,
                      "data" -> ujson.Null,
                      "error" -> e.getMessage,
                      "error_code" -> CumulusError.FILE_NOT_FOUND.toString
                    )
                  case e: Exception =>
                    ujson.Obj(
                      "success" -> false,
                      "data" -> ujson.Null,
                      "error" -> s"Error indexing log: ${e.getMessage}",
                      "error_code" -> CumulusError.INTERNAL_ERROR.toString
                    )
              case None =>
                ujson.Obj(
                  "success" -> false,
                  "data" -> ujson.Null,
                  "error" -> "Missing --file argument",
                  "error_code" -> CumulusError.INVALID_INPUT.toString
                )
            println(ujson.write(result))
          catch
            case e: Exception =>
              val errorResponse = ujson.Obj(
                "success" -> false,
                "data" -> ujson.Null,
                "error" -> s"Error: ${e.getMessage}",
                "error_code" -> CumulusError.INTERNAL_ERROR.toString
              )
              println(ujson.write(errorResponse))

        case _ =>
          given ReadWriter[Unit] = upickle.default.readwriter[String].bimap[Unit](
            _ => "null",
            _ => ()
          )
          serializeResponse(errorEnvelope[Unit]("Unknown subcommand"))
