package cumulus

import cumulus.protocol.{CumulusResponse, CumulusError}
import cumulus.build.{MavenParser, GradleParser, ParsePomResponse, ParseGradleTasksResponse, ParseModulesResponse, ParseGradleModulesResponse, ComputeBuildOrderResponse, ModuleBuildStep, DagSolver, DependencyExtractor, CommandIntent, CommandAssemblyResult, CommandAssembler}
import cumulus.workspace.{JdkDiscoverer, BuildToolDetector, WorkspaceScanner, JdkInfo, BuildToolInfo, WorkspaceInfo, DistroInstaller, InstallResult, WorkspaceClassifier, WorkspaceTopology, ProjectSubmodule, DevopsRoots, DevopsRootDiscoverer}
import cumulus.code.{CodeLensExtractor, CodeLensItem, CodeLensResponse, SpringBootDetector, BeanGraphAnalyzer, SpringBootApp, SpringBeansResponse, EndpointScanner, Endpoint, EndpointsResponse, ImportOptimizer, ImportsResponse, JavaHeaderGenerator, JavaHeader, DapConfiguration, DapConfigResult, DapConfigGenerator}
import cumulus.testing.{TestContextDetector, TestOutputParser, TestCommandAssembler, TestContext, TestResult, TestCommand}
import cumulus.log.{LogParser, LogIndexer, StacktraceResolver, BuildDiagnostic, LogIndexEntry, StackFrame}
import cumulus.devops.{CoverageParser, CheckstyleParser, CoverageEntry, CheckstyleDiagnostic, MigrationValidator, K8sValidator, MigrationIssue, K8sValidationIssue, SessionSanitizeResult, GradleWrapperStatus, NetworkStatus, SyncStatus, DependencyInfo, DependencyLens, ThemeState, CfnSamParser, CfnTemplateInfo, CfnValidationIssue, AnsibleParser, AnsiblePlaybookInfo, AnsibleValidationIssue, AnsibleInventoryGroup, TerraformParser, TfSecurityParser, TerraformInfo, SecurityFinding, DockerParser, DockerValidator, DockerImage, ContainerValidationIssue, HelmParser, HelmChartInfo}
import cumulus.git.{ConflictParser, ConflictBlock}
import cumulus.util.{SessionSanitizer, NetworkChecker}
import cumulus.gradle.WrapperVerifier
import cumulus.workspace.JdtlsSyncChecker
import cumulus.dep.{DepResolver, DepLens}
import cumulus.theme.ThemeManager
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
      val p = os.Path(dirPath, os.pwd)
      if !os.exists(p) || !os.isDir(p) then
        return CumulusResponse(
          success = false,
          data = None,
          error = Some(s"Directory not found or not a directory: $dirPath"),
          error_code = Some("FILE_NOT_FOUND")
        )

      val pomFile = p / "pom.xml"
      val settingsFile = p / "settings.gradle"
      val settingsKtsFile = p / "settings.gradle.kts"
      val buildFile = p / "build.gradle"
      val buildKtsFile = p / "build.gradle.kts"

      // Detect project type and extract modules + dependencies
      if os.exists(pomFile) && os.isFile(pomFile) then
        // Maven project
        val modulesResult = MavenParser.parseModules(pomFile.toString)
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

        // For Maven, we assume modules are listed in declaration order with warning
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
      else if os.exists(settingsFile) || os.exists(settingsKtsFile) || os.exists(buildFile) || os.exists(buildKtsFile) then
        // Gradle project
        val settingsPath = if os.exists(settingsFile) && os.isFile(settingsFile) then
          settingsFile.toString
        else if os.exists(settingsKtsFile) && os.isFile(settingsKtsFile) then
          settingsKtsFile.toString
        else if os.exists(buildFile) && os.isFile(buildFile) then
          buildFile.toString
        else
          buildKtsFile.toString


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
          val path = nameToPath.getOrElse(name, name)
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
      import CumulusResponse.unitRW
      serializeResponse(errorEnvelope[Unit]("No subcommand provided"))
    else
      import CumulusResponse.unitRW
      args(0) match
        case "ping" =>
          case class PingData(status: String, version: String, scala: String, commit: String, built: String) derives ReadWriter
          val pingInfo = PingData(
            status = "ok",
            version = BuildInfo.version,
            scala = BuildInfo.scalaVersion,
            commit = BuildInfo.gitCommit,
            built = BuildInfo.buildTime
          )
          serializeResponse(successEnvelope[PingData](Some(pingInfo)))

        case "version" | "--version" | "-v" =>
          case class EngineVersion(version: String, commit: String, build_time: String, scala_version: String) derives ReadWriter
          val info = EngineVersion(
            version = BuildInfo.version,
            commit = BuildInfo.gitCommit,
            build_time = BuildInfo.buildTime,
            scala_version = BuildInfo.scalaVersion
          )
          serializeResponse(successEnvelope[EngineVersion](Some(info)))


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
                  errorEnvelope[JdkInfo](error, CumulusError.INVALID_INPUT)
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

        case "classify-workspace" | "--classify-workspace" =>
          given ReadWriter[WorkspaceTopology] = upickle.default.macroRW
          val argMap = parseArgs(args.slice(1, args.length))
          val dirPath = argMap.get("dir").orElse(args.drop(1).find(!_.startsWith("--"))).getOrElse(".")
          val result = WorkspaceClassifier.classifyWorkspace(dirPath)
          serializeResponse(result)

        case "discover-devops-roots" | "--discover-devops-roots" =>
          given ReadWriter[DevopsRoots] = upickle.default.macroRW
          val argMap = parseArgs(args.slice(1, args.length))
          val path = argMap.get("path")
            .orElse(argMap.get("dir"))
            .orElse(argMap.get("file"))
            .orElse(args.drop(1).find(!_.startsWith("--")))
            .getOrElse(".")
          val result = DevopsRootDiscoverer.discoverDevopsRoots(path)
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
            case e: Exception =>
              serializeResponse(errorEnvelope[ImportsResponse](s"Error optimizing imports: ${e.getMessage}"))


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
          given ReadWriter[TestResult] = upickle.default.macroRW
          try
            val stdinInput = Source.fromInputStream(System.in, "UTF-8").mkString
            val result = TestOutputParser.parseTestOutput(stdinInput) match
              case Right(results) =>
                successEnvelope[Seq[TestResult]](Some(results))
              case Left(error) =>
                errorEnvelope[Seq[TestResult]](error, CumulusError.PARSE_ERROR)
            serializeResponse(result)
          catch
            case e: Exception =>
              serializeResponse(errorEnvelope[Seq[TestResult]](s"Error reading or parsing test output: ${e.getMessage}"))

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
          given ReadWriter[BuildDiagnostic] = upickle.default.macroRW
          val argMap = parseArgs(args.slice(1, args.length))
          val result = try
            argMap.get("file") match
              case Some(filePath) =>
                try
                  val diagnostics = LogParser.parseFromFile(filePath)
                  successEnvelope[Seq[BuildDiagnostic]](Some(diagnostics))
                catch
                  case e: Exception if e.getMessage.contains("not found") || e.getMessage.contains("No such file") =>
                    errorEnvelope[Seq[BuildDiagnostic]](e.getMessage, CumulusError.FILE_NOT_FOUND)
                  case e: Exception =>
                    errorEnvelope[Seq[BuildDiagnostic]](s"Error parsing log: ${e.getMessage}")
              case None =>
                try
                  val stdinInput = Source.fromInputStream(System.in, "UTF-8").mkString
                  val diagnostics = LogParser.parseFromStdin(stdinInput)
                  successEnvelope[Seq[BuildDiagnostic]](Some(diagnostics))
                catch
                  case e: Exception =>
                    errorEnvelope[Seq[BuildDiagnostic]](s"Error reading stdin: ${e.getMessage}")
          catch
            case e: Exception =>
              errorEnvelope[Seq[BuildDiagnostic]](s"Error: ${e.getMessage}")
          serializeResponse(result)

        case "resolve-stacktrace-symbol" =>
          val argMap = parseArgs(args.slice(1, args.length))
          val stacktraceOpt = argMap.get("stacktrace")
          val workspaceDir = argMap.getOrElse("dir", ".")

          val result = stacktraceOpt match
            case None =>
              errorEnvelope[Map[String, String]]("Missing --stacktrace argument", CumulusError.INVALID_INPUT)
            case Some(stacktraceStr) =>
              try
                StacktraceResolver.resolveStacktrace(stacktraceStr, workspaceDir) match
                  case Right(resolved) =>
                    successEnvelope[Map[String, String]](Some(resolved))
                  case Left(error) =>
                    val errCode = if error.contains("not found") then CumulusError.FILE_NOT_FOUND else CumulusError.INTERNAL_ERROR
                    errorEnvelope[Map[String, String]](error, errCode)
              catch
                case e: Exception =>
                  errorEnvelope[Map[String, String]](s"Error resolving stacktrace: ${e.getMessage}")
          serializeResponse(result)

        case "index-log" =>
          given ReadWriter[LogIndexEntry] = upickle.default.macroRW
          val argMap = parseArgs(args.slice(1, args.length))
          val result = argMap.get("file") match
            case Some(filePath) =>
              try
                val entries = LogIndexer.indexLogFile(filePath)
                successEnvelope[Seq[LogIndexEntry]](Some(entries))
              catch
                case e: Exception if e.getMessage.contains("not found") || e.getMessage.contains("No such file") =>
                  errorEnvelope[Seq[LogIndexEntry]](e.getMessage, CumulusError.FILE_NOT_FOUND)
                case e: Exception =>
                  errorEnvelope[Seq[LogIndexEntry]](s"Error indexing log: ${e.getMessage}")
            case None =>
              errorEnvelope[Seq[LogIndexEntry]]("Missing --file argument", CumulusError.INVALID_INPUT)
          serializeResponse(result)

        case "parse-coverage" =>
          val argMap = parseArgs(args.slice(1, args.length))
          val result = argMap.get("file") match
            case Some(filePath) =>
              CoverageParser.parseCoverage(filePath)
            case None =>
              errorEnvelope[Seq[CoverageEntry]]("Missing --file argument", CumulusError.INVALID_INPUT)
          serializeResponse(result)

        case "parse-checkstyle" =>
          val argMap = parseArgs(args.slice(1, args.length))
          val result = argMap.get("file") match
            case Some(filePath) =>
              CheckstyleParser.parseCheckstyleFile(filePath)
            case None =>
              try
                val stdinInput = Source.fromInputStream(System.in, "UTF-8").mkString
                CheckstyleParser.parseCheckstyle(stdinInput)
              catch
                case e: Exception =>
                  errorEnvelope[Seq[CheckstyleDiagnostic]](s"Error reading stdin: ${e.getMessage}", CumulusError.INTERNAL_ERROR)
          serializeResponse(result)

        case "validate-migrations" =>
          val argMap = parseArgs(args.slice(1, args.length))
          val dir = argMap.getOrElse("dir", ".")
          val result = MigrationValidator.validateMigrationsDir(dir)
          serializeResponse(result)

        case "validate-k8s-manifest" =>
          val argMap = parseArgs(args.slice(1, args.length))
          val result = argMap.get("file") match
            case Some(filePath) =>
              K8sValidator.validateK8sManifestFile(filePath)
            case None =>
              try
                val stdinInput = Source.fromInputStream(System.in, "UTF-8").mkString
                K8sValidator.validateK8sManifest(stdinInput)
              catch
                case e: Exception =>
                  errorEnvelope[Seq[K8sValidationIssue]](s"Error reading stdin: ${e.getMessage}", CumulusError.INTERNAL_ERROR)
          serializeResponse(result)

        case "parse-git-conflicts" =>
          val argMap = parseArgs(args.slice(1, args.length))
          val result = argMap.get("file") match
            case Some(filePath) =>
              ConflictParser.parseGitConflictsFile(filePath)
            case None =>
              try
                val stdinInput = Source.fromInputStream(System.in, "UTF-8").mkString
                ConflictParser.parseGitConflicts(stdinInput)
              catch
                case e: Exception =>
                  errorEnvelope[Seq[ConflictBlock]](s"Error reading stdin: ${e.getMessage}", CumulusError.INTERNAL_ERROR)
          serializeResponse(result)

        case "session-sanitize" =>
          val argMap = parseArgs(args.slice(1, args.length))
          val result = argMap.get("file") match
            case Some(filePath) =>
              SessionSanitizer.sanitizeSession(filePath)
            case None =>
              errorEnvelope[SessionSanitizeResult]("Missing --file argument", CumulusError.INVALID_INPUT)
          serializeResponse(result)

        case "verify-gradle-wrapper" =>
          val argMap = parseArgs(args.slice(1, args.length))
          val dir = argMap.getOrElse("dir", ".")
          val result = WrapperVerifier.verifyGradleWrapper(dir)
          serializeResponse(result)

        case "check-network" =>
          val argMap = parseArgs(args.slice(1, args.length))
          val result = argMap.get("host") match
            case Some(host) =>
              val timeout = argMap.get("timeout").flatMap(_.toLongOption).getOrElse(3000L)
              NetworkChecker.checkNetwork(host, timeout)
            case None =>
              errorEnvelope[NetworkStatus]("Missing --host argument", CumulusError.INVALID_INPUT)
          serializeResponse(result)

        case "check-jdtls-sync" =>
          val argMap = parseArgs(args.slice(1, args.length))
          val dir = argMap.getOrElse("dir", ".")
          val startTime = argMap.get("start-time").flatMap(_.toLongOption).getOrElse(0L)
          val result = JdtlsSyncChecker.checkJdtlsSync(dir, startTime)
          serializeResponse(result)

        case "resolve-deps" =>
          val argMap = parseArgs(args.slice(1, args.length))
          val result = argMap.get("file") match
            case Some(filePath) =>
              DepResolver.resolveDependencies(filePath)
            case None =>
              errorEnvelope[Seq[DependencyInfo]]("Missing --file argument", CumulusError.INVALID_INPUT)
          serializeResponse(result)

        case "check-dep-versions" =>
          val argMap = parseArgs(args.slice(1, args.length))
          val result = argMap.get("file") match
            case Some(filePath) =>
              DepLens.checkDepVersions(filePath)
            case None =>
              errorEnvelope[Seq[DependencyLens]]("Missing --file argument", CumulusError.INVALID_INPUT)
          serializeResponse(result)

        case "manage-theme" =>
          val argMap = parseArgs(args.slice(1, args.length))
          val action = argMap.getOrElse("action", "get").toLowerCase
          val fileOpt = argMap.get("file")
          val result = action match
            case "get" =>
              ThemeManager.getTheme(fileOpt)
            case "set" =>
              argMap.get("theme") match
                case Some(theme) =>
                  val variantOpt = argMap.get("variant")
                  ThemeManager.setTheme(theme, variantOpt, fileOpt)
                case None =>
                  errorEnvelope[ThemeState]("Missing --theme argument for set action", CumulusError.INVALID_INPUT)
            case other =>
              errorEnvelope[ThemeState](s"Unknown action '$other'. Expected 'get' or 'set'", CumulusError.INVALID_INPUT)
          serializeResponse(result)

        case "cfn-inspect" | "--cfn-inspect" =>
          val argMap = parseArgs(args.slice(1, args.length))
          val filePath = argMap.get("file").orElse(args.lift(1).filter(!_.startsWith("--")))
          val result = filePath match
            case Some(path) =>
              CfnSamParser.inspectTemplateFile(path)
            case None =>
              try
                val stdinInput = Source.fromInputStream(System.in, "UTF-8").mkString
                successEnvelope(Some(CfnSamParser.inspectTemplate(stdinInput)))
              catch
                case e: Exception =>
                  errorEnvelope[CfnTemplateInfo](s"Error reading stdin: ${e.getMessage}", CumulusError.INTERNAL_ERROR)
          serializeResponse(result)

        case "cfn-validate" | "--cfn-validate" =>
          val argMap = parseArgs(args.slice(1, args.length))
          val filePath = argMap.get("file").orElse(args.lift(1).filter(!_.startsWith("--")))
          val result = filePath match
            case Some(path) =>
              CfnSamParser.validateTemplateFile(path)
            case None =>
              try
                val stdinInput = Source.fromInputStream(System.in, "UTF-8").mkString
                successEnvelope(Some(CfnSamParser.validateTemplate(stdinInput)))
              catch
                case e: Exception =>
                  errorEnvelope[Seq[CfnValidationIssue]](s"Error reading stdin: ${e.getMessage}", CumulusError.INTERNAL_ERROR)
          serializeResponse(result)

        case "ansible-inspect" | "--ansible-inspect" =>
          val argMap = parseArgs(args.slice(1, args.length))
          val filePath = argMap.get("file").orElse(args.lift(1).filter(!_.startsWith("--")))
          val result = filePath match
            case Some(path) =>
              AnsibleParser.inspectPlaybookFile(path)
            case None =>
              try
                val stdinInput = Source.fromInputStream(System.in, "UTF-8").mkString
                successEnvelope(Some(AnsibleParser.inspectPlaybook(stdinInput)))
              catch
                case e: Exception =>
                  errorEnvelope[AnsiblePlaybookInfo](s"Error reading stdin: ${e.getMessage}", CumulusError.INTERNAL_ERROR)
          serializeResponse(result)

        case "ansible-validate" | "--ansible-validate" =>
          val argMap = parseArgs(args.slice(1, args.length))
          val filePath = argMap.get("file").orElse(args.lift(1).filter(!_.startsWith("--")))
          val result = filePath match
            case Some(path) =>
              AnsibleParser.validatePlaybookFile(path)
            case None =>
              try
                val stdinInput = Source.fromInputStream(System.in, "UTF-8").mkString
                successEnvelope(Some(AnsibleParser.validatePlaybook(stdinInput)))
              catch
                case e: Exception =>
                  errorEnvelope[Seq[AnsibleValidationIssue]](s"Error reading stdin: ${e.getMessage}", CumulusError.INTERNAL_ERROR)
          serializeResponse(result)

        case "ansible-inventory-parse" | "--ansible-inventory-parse" =>
          val argMap = parseArgs(args.slice(1, args.length))
          val filePath = argMap.get("file").orElse(args.lift(1).filter(!_.startsWith("--")))
          val result = filePath match
            case Some(path) =>
              AnsibleParser.parseInventoryFile(path)
            case None =>
              try
                val stdinInput = Source.fromInputStream(System.in, "UTF-8").mkString
                successEnvelope(Some(AnsibleParser.parseInventory(stdinInput)))
              catch
                case e: Exception =>
                  errorEnvelope[Seq[AnsibleInventoryGroup]](s"Error reading stdin: ${e.getMessage}", CumulusError.INTERNAL_ERROR)
          serializeResponse(result)

        case "tf-inspect" | "--tf-inspect" =>
          val argMap = parseArgs(args.slice(1, args.length))
          val filePath = argMap.get("file").orElse(argMap.get("dir")).orElse(args.lift(1).filter(!_.startsWith("--")))
          val result = filePath match
            case Some(path) =>
              TerraformParser.inspectPath(path)
            case None =>
              try
                val stdinInput = Source.fromInputStream(System.in, "UTF-8").mkString
                successEnvelope(Some(TerraformParser.inspectContent(stdinInput)))
              catch
                case e: Exception =>
                  errorEnvelope[TerraformInfo](s"Error reading stdin: ${e.getMessage}", CumulusError.INTERNAL_ERROR)
          serializeResponse(result)

        case "tf-security-parse" | "--tf-security-parse" =>
          val argMap = parseArgs(args.slice(1, args.length))
          val filePath = argMap.get("file").orElse(args.lift(1).filter(!_.startsWith("--")))
          val result = filePath match
            case Some(path) =>
              TfSecurityParser.parseSecurityFile(path)
            case None =>
              try
                val stdinInput = Source.fromInputStream(System.in, "UTF-8").mkString
                TfSecurityParser.parseSecurityJson(stdinInput) match
                  case Right(findings) =>
                    successEnvelope(Some(findings))
                  case Left(err) =>
                    errorEnvelope[Seq[SecurityFinding]](err, CumulusError.PARSE_ERROR)
              catch
                case e: Exception =>
                  errorEnvelope[Seq[SecurityFinding]](s"Error reading stdin: ${e.getMessage}", CumulusError.INTERNAL_ERROR)
          serializeResponse(result)

        case "docker-inspect" | "--docker-inspect" =>
          val argMap = parseArgs(args.slice(1, args.length))
          val filePath = argMap.get("file").orElse(args.lift(1).filter(!_.startsWith("--")))
          val result = filePath match
            case Some(path) =>
              DockerParser.parseFile(path)
            case None =>
              try
                val stdinInput = Source.fromInputStream(System.in, "UTF-8").mkString
                CumulusResponse(
                  success = true,
                  data = Some(DockerParser.parseContent(stdinInput)),
                  error = None,
                  error_code = None
                )
              catch
                case e: Exception =>
                  errorEnvelope[DockerImage](s"Error reading stdin: ${e.getMessage}", CumulusError.INTERNAL_ERROR)
          serializeResponse(result)

        case "docker-validate" | "--docker-validate" =>
          val argMap = parseArgs(args.slice(1, args.length))
          val filePath = argMap.get("file").orElse(args.lift(1).filter(!_.startsWith("--")))
          val result = filePath match
            case Some(path) =>
              DockerValidator.validateFile(path)
            case None =>
              try
                val stdinInput = Source.fromInputStream(System.in, "UTF-8").mkString
                CumulusResponse(
                  success = true,
                  data = Some(DockerValidator.validateContent(stdinInput)),
                  error = None,
                  error_code = None
                )
              catch
                case e: Exception =>
                  errorEnvelope[Seq[ContainerValidationIssue]](s"Error reading stdin: ${e.getMessage}", CumulusError.INTERNAL_ERROR)
          serializeResponse(result)

        case "helm-inspect" | "--helm-inspect" =>
          val argMap = parseArgs(args.slice(1, args.length))
          val filePath = argMap.get("file").orElse(argMap.get("dir")).orElse(args.drop(1).find(!_.startsWith("--")))
          val result = filePath match
            case Some(path) =>
              HelmParser.inspectChart(path)
            case None =>
              try
                val stdinInput = Source.fromInputStream(System.in, "UTF-8").mkString
                successEnvelope(Some(HelmParser.inspectChartContent(stdinInput)))
              catch
                case e: Exception =>
                  errorEnvelope[HelmChartInfo](s"Error reading stdin: ${e.getMessage}", CumulusError.INTERNAL_ERROR)
          serializeResponse(result)

        case "assemble-command" | "--assemble-command" =>
          given ReadWriter[CommandAssemblyResult] = upickle.default.macroRW
          val argMap = parseArgs(args.slice(1, args.length))
          val result: CumulusResponse[CommandAssemblyResult] = if argMap.contains("tool") || argMap.contains("action") then
            val tool = argMap.getOrElse("tool", "")
            val action = argMap.getOrElse("action", "build")
            val target = argMap.get("target")
            val dir = argMap.get("dir")
            val flags = argMap.get("flags").map { f =>
              if f.contains(",") then f.split(",").map(_.trim).filter(_.nonEmpty).toSeq
              else f.split("\\s+").map(_.trim).filter(_.nonEmpty).toSeq
            }.getOrElse(Seq.empty)
            val intent = CommandIntent(
              tool = tool,
              action = action,
              target = target,
              dir = dir,
              flags = flags
            )
            CommandAssembler.assemble(intent) match
              case Right(res) => successEnvelope(Some(res))
              case Left(err) => errorEnvelope[CommandAssemblyResult](err, CumulusError.INVALID_INPUT)
          else
            try
              val stdinInput = Source.fromInputStream(System.in, "UTF-8").mkString.trim
              if stdinInput.isEmpty then
                errorEnvelope[CommandAssemblyResult]("Missing required parameters: tool and action must be specified via flags or JSON stdin", CumulusError.INVALID_INPUT)
              else
                try
                  val intent = upickle.default.read[CommandIntent](stdinInput)
                  CommandAssembler.assemble(intent) match
                    case Right(res) => successEnvelope(Some(res))
                    case Left(err) => errorEnvelope[CommandAssemblyResult](err, CumulusError.INVALID_INPUT)
                catch
                  case e: Exception =>
                    errorEnvelope[CommandAssemblyResult](s"Invalid JSON input for CommandIntent: ${e.getMessage}", CumulusError.INVALID_INPUT)
            catch
              case e: Exception =>
                errorEnvelope[CommandAssemblyResult](s"Error reading stdin: ${e.getMessage}", CumulusError.INTERNAL_ERROR)
          serializeResponse(result)

        case "generate-dap-config" | "--generate-dap-config" =>
          given ReadWriter[DapConfigResult] = upickle.default.macroRW
          val argMap = parseArgs(args.slice(1, args.length))
          val dirOpt = argMap.get("dir").orElse(args.drop(1).find(!_.startsWith("--")))
          val dirPath = dirOpt.getOrElse(os.pwd.toString)
          val result = DapConfigGenerator.generateDapConfig(dirPath)
          serializeResponse(result)

        case "install" | "bootstrap" =>
          given ReadWriter[InstallResult] = upickle.default.macroRW
          val argMap = parseArgs(args.slice(1, args.length))
          val targetDirOpt = argMap.get("target").orElse(argMap.get("dir"))
          val appnameOpt = argMap.get("appname")
          val repoUrlOpt = argMap.get("repo")
          val skipSys = argMap.contains("skip-system-packages") || argMap.contains("no-sudo")
          DistroInstaller.runInstall(targetDirOpt, appnameOpt, repoUrlOpt, skipSys) match
            case Right(res) =>
              serializeResponse(successEnvelope[InstallResult](Some(res)))
            case Left(err) =>
              serializeResponse(errorEnvelope[InstallResult](err, CumulusError.INTERNAL_ERROR))

        case _ =>
          import CumulusResponse.unitRW
          serializeResponse(errorEnvelope[Unit]("Unknown subcommand"))

