package cumulus.code

import os.Path
import cumulus.protocol.{CumulusResponse, CumulusError}
import cumulus.workspace.BuildToolDetector
import scala.xml.XML
import scala.collection.mutable

object DapConfigGenerator:

  private lazy val javaMainPattern = """public\s+static\s+void\s+main\s*\(\s*String""".r
  private lazy val kotlinJvmMainPattern = """@JvmStatic\s+fun\s+main""".r
  private lazy val kotlinFunMainPattern = """fun\s+main\s*\(""".r
  private lazy val scalaMainDefPattern = """@main\s+def\s+([a-zA-Z_][a-zA-Z0-9_]*)""".r
  private lazy val scalaAppPattern = """(?:object|class)\s+([a-zA-Z_][a-zA-Z0-9_]*)\s+extends\s+App\b""".r
  private lazy val packagePattern = """^\s*package\s+([a-zA-Z_][a-zA-Z0-9_.]*)\s*;?""".r
  private lazy val classPattern = """(?:public|protected|private|final|open|abstract|\s)*\b(?:class|object)\s+([a-zA-Z_][a-zA-Z0-9_]*)\s*""".r

  /**
   * Generates complete ready-to-run DAP debug configurations for Spring Boot or JVM projects.
   *
   * @param dirPath The project root directory
   * @return CumulusResponse containing DapConfigResult with launch and attach targets
   */
  def generateDapConfig(dirPath: String): CumulusResponse[DapConfigResult] =
    try
      val dir = Path(dirPath, os.pwd)
      if !os.exists(dir) || !os.isDir(dir) then
        return CumulusResponse(
          success = false,
          data = None,
          error = Some(s"Directory not found: $dirPath"),
          error_code = Some("FILE_NOT_FOUND")
        )

      // 1. Check for Spring Boot project first
      val maybeSpringBootApp = try
        Some(SpringBootDetector.detectSpringBootApp(dir.toString))
      catch
        case _: Exception => None

      val (projectName, mainClassOpt, buildToolOpt, activeProfiles, customVmArgs) = maybeSpringBootApp match
        case Some(sb) =>
          val pName = if sb.project_name.nonEmpty then sb.project_name else dir.last
          val mClass = if sb.main_class.nonEmpty then Some(sb.main_class) else None
          (pName, mClass, sb.build_tool, sb.active_profiles, sb.jvm_debug_args)

        case None =>
          // 2. Generic JVM Project detection
          val pName = extractGenericProjectName(dir)
          val bTool = detectBuildTool(dir)
          val mClass = findGenericMainClass(dir)
          (pName, mClass, bTool, Seq.empty[String], None)

      // Determine pre-launch task based on build tool
      val preLaunchTask = buildToolOpt.map(_.toLowerCase) match
        case Some("maven") => Some("maven: clean package")
        case Some("gradle") => Some("gradle: clean build")
        case Some("sbt") => Some("sbt: compile")
        case _ => None

      // Determine VM debug args / JDWP parameters
      val vmArgs = customVmArgs.orElse(Some("-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005"))

      // Determine Spring profiles active environment variable
      val env = if activeProfiles.nonEmpty then
        Map("SPRING_PROFILES_ACTIVE" -> activeProfiles.mkString(","))
      else
        Map.empty[String, String]

      val isSpring = maybeSpringBootApp.isDefined
      val launchName = if isSpring then s"Spring Boot: $projectName" else s"Launch: $projectName"
      val attachName = s"Attach to Process ($projectName:5005)"

      val launchConfig = DapConfiguration(
        `type` = "java",
        name = launchName,
        request = "launch",
        mainClass = mainClassOpt,
        projectName = Some(projectName),
        cwd = dir.toString,
        console = "integratedTerminal",
        preLaunchTask = preLaunchTask,
        vmArgs = vmArgs,
        args = None,
        env = env,
        hostName = None,
        port = None
      )

      val attachConfig = DapConfiguration(
        `type` = "java",
        name = attachName,
        request = "attach",
        mainClass = None,
        projectName = Some(projectName),
        cwd = dir.toString,
        console = "integratedTerminal",
        preLaunchTask = None,
        vmArgs = None,
        args = None,
        env = Map.empty,
        hostName = Some("localhost"),
        port = Some(5005)
      )

      val result = DapConfigResult(
        launch = launchConfig,
        attach = attachConfig,
        configurations = Seq(launchConfig, attachConfig)
      )

      CumulusResponse(
        success = true,
        data = Some(result),
        error = None,
        error_code = None
      )
    catch
      case e: Exception =>
        CumulusResponse(
          success = false,
          data = None,
          error = Some(s"Error generating DAP configuration: ${e.getMessage}"),
          error_code = Some("INTERNAL_ERROR")
        )

  private def detectBuildTool(dir: Path): Option[String] =
    val pomFile = dir / "pom.xml"
    val buildGradle = dir / "build.gradle"
    val buildGradleKts = dir / "build.gradle.kts"
    val settingsGradle = dir / "settings.gradle"
    val settingsGradleKts = dir / "settings.gradle.kts"
    val buildSbt = dir / "build.sbt"

    if os.exists(pomFile) && os.isFile(pomFile) then Some("maven")
    else if (os.exists(buildGradle) && os.isFile(buildGradle)) ||
            (os.exists(buildGradleKts) && os.isFile(buildGradleKts)) ||
            (os.exists(settingsGradle) && os.isFile(settingsGradle)) ||
            (os.exists(settingsGradleKts) && os.isFile(settingsGradleKts)) then Some("gradle")
    else if os.exists(buildSbt) && os.isFile(buildSbt) then Some("sbt")
    else
      BuildToolDetector.detectBuildTool(dir.toString).toOption.map(_.build_tool)

  private def extractGenericProjectName(dir: Path): String =
    try
      val pomFile = dir / "pom.xml"
      if os.exists(pomFile) && os.isFile(pomFile) then
        val pom = XML.loadString(os.read(pomFile))
        val nameNodes = pom \\ "name"
        if nameNodes.nonEmpty && nameNodes.head.text.trim.nonEmpty then
          nameNodes.head.text.trim
        else
          val artifactNodes = pom \\ "artifactId"
          if artifactNodes.nonEmpty && artifactNodes.head.text.trim.nonEmpty then
            artifactNodes.head.text.trim
          else
            dir.last
      else
        val rootProjectPattern = """rootProject\.name\s*=\s*['"]([^'"]+)['"]""".r
        val sbtNamePattern = """name\s*:=\s*['"]([^'"]+)['"]""".r

        val buildGradle = dir / "build.gradle"
        val buildGradleKts = dir / "build.gradle.kts"
        val settingsGradle = dir / "settings.gradle"
        val settingsGradleKts = dir / "settings.gradle.kts"
        val buildSbt = dir / "build.sbt"

        val gradleFiles = Seq(buildGradle, buildGradleKts, settingsGradle, settingsGradleKts)
        val gradleMatch = gradleFiles.find(f => os.exists(f) && os.isFile(f)).flatMap { f =>
          rootProjectPattern.findFirstMatchIn(os.read(f)).map(_.group(1))
        }

        gradleMatch.orElse {
          if os.exists(buildSbt) && os.isFile(buildSbt) then
            sbtNamePattern.findFirstMatchIn(os.read(buildSbt)).map(_.group(1))
          else None
        }.getOrElse(dir.last)
    catch
      case _: Exception => dir.last

  private def findGenericMainClass(dir: Path): Option[String] =
    val candidateDirs = Seq(
      dir / "src" / "main" / "java",
      dir / "src" / "main" / "kotlin",
      dir / "src" / "main" / "scala",
      dir / "src"
    ).filter(d => os.exists(d) && os.isDir(d))

    val searchDirs = if candidateDirs.nonEmpty then candidateDirs else Seq(dir)

    searchDirs.iterator.flatMap { sDir =>
      val files = try
        os.walk(sDir, skip = p => {
          val n = p.last
          n == "target" || n == "build" || n == ".git" || n == "node_modules" || n == ".gradle"
        }).filter(f => os.isFile(f) && (f.last.endsWith(".java") || f.last.endsWith(".kt") || f.last.endsWith(".scala")))
      catch
        case _: Exception => Seq.empty

      files.iterator.flatMap(file => parseMainClassFromFile(file))
    }.nextOption()

  private def parseMainClassFromFile(file: Path): Option[String] =
    try
      val lines = os.read.lines(file, charSet = java.nio.charset.StandardCharsets.UTF_8).toList
      var pkg = ""
      var className = ""
      var hasMain = false

      for line <- lines if line != null do
        if pkg.isEmpty then
          packagePattern.findFirstMatchIn(line) foreach { m =>
            pkg = m.group(1)
          }

        if className.isEmpty then
          classPattern.findFirstMatchIn(line) foreach { m =>
            className = m.group(1)
          }

        if javaMainPattern.findFirstIn(line).isDefined ||
           kotlinJvmMainPattern.findFirstIn(line).isDefined ||
           kotlinFunMainPattern.findFirstIn(line).isDefined then
          hasMain = true

        scalaMainDefPattern.findFirstMatchIn(line) foreach { m =>
          hasMain = true
          if className.isEmpty then className = m.group(1)
        }

        scalaAppPattern.findFirstMatchIn(line) foreach { m =>
          hasMain = true
          if className.isEmpty then className = m.group(1)
        }

      if hasMain then
        val effectiveClass = if className.nonEmpty then className else file.last.replaceAll("\\.[^.]+$", "")
        if pkg.nonEmpty then Some(s"$pkg.$effectiveClass") else Some(effectiveClass)
      else
        None
    catch
      case _: Exception => None

