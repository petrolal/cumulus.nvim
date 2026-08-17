package cumulus.code

import os.Path
import scala.collection.mutable
import scala.xml.XML

object SpringBootDetector:

  // Compiled regex patterns
  private lazy val springBootAppPattern = """@SpringBootApplication\b""".r
  private lazy val packagePattern = """^\s*package\s+([a-zA-Z_][a-zA-Z0-9_.]*)\s*;?""".r
  private lazy val classPattern = """(?:public|protected|private|final|open|abstract|\s)*\bclass\s+([a-zA-Z_][a-zA-Z0-9_]*)\s*""".r
  private lazy val profilesActivePropertiesPattern = """spring\.profiles\.active\s*=\s*([^\n#]+)""".r
  private lazy val profilesActiveYamlPattern = """active\s*:\s*([^\n#]+)""".r
  private lazy val jvmArgsPattern = """jvmArgs\s*=\s*\[([^\]]+)\]""".r

  /**
   * Detect Spring Boot application in a directory.
   * Scans src/main/java and src/main/kotlin for @SpringBootApplication.
   *
   * @param dirPath The root directory of the project
   * @return SpringBootApp with detected metadata, or error details
   */
  def detectSpringBootApp(dirPath: String): SpringBootApp =
    val dir = Path(dirPath, os.pwd)
    if !os.exists(dir) || !os.isDir(dir) then
      throw new Exception(s"Directory not found: $dirPath")

    // Search for @SpringBootApplication
    val (mainClass, filePath) = findSpringBootApp(dir) match
      case Some((cls, fPath)) => (cls, fPath)
      case None => throw new Exception("No Spring Boot application found")

    // Detect build tool and extract metadata
    val pomFile = dir / "pom.xml"
    val buildGradleFile = dir / "build.gradle"
    val buildGradleKtsFile = dir / "build.gradle.kts"

    val (buildTool, projectName) = if os.exists(pomFile) then
      ("maven", extractProjectNameFromPom(pomFile.toString))
    else if os.exists(buildGradleFile) then
      ("gradle", extractProjectNameFromGradle(buildGradleFile.toString))
    else if os.exists(buildGradleKtsFile) then
      ("gradle", extractProjectNameFromGradle(buildGradleKtsFile.toString))
    else
      (null, dir.last)

    // Extract JVM debug args
    val jvmDebugArgs = if os.exists(pomFile) then
      extractJvmDebugArgsFromPom(pomFile.toString)
    else if os.exists(buildGradleFile) then
      extractJvmDebugArgsFromGradle(buildGradleFile.toString)
    else if os.exists(buildGradleKtsFile) then
      extractJvmDebugArgsFromGradle(buildGradleKtsFile.toString)
    else
      None

    // Parse active profiles
    val activeProfiles = detectActiveProfiles(dir)

    SpringBootApp(
      main_class = mainClass,
      project_name = projectName,
      build_tool = Option(buildTool),
      jvm_debug_args = jvmDebugArgs,
      active_profiles = activeProfiles
    )

  /**
   * Recursively search for @SpringBootApplication in src/main/java and src/main/kotlin
   */
  private def findSpringBootApp(dir: Path): Option[(String, String)] =
    val candidateDirs = Seq(dir / "src" / "main" / "java", dir / "src" / "main" / "kotlin")
    candidateDirs.filter(d => os.exists(d) && os.isDir(d)).flatMap(searchForSpringBootApp).headOption

  /**
   * Recursively search for @SpringBootApplication in a directory
   * Returns (fully-qualified class name, file path)
   */
  private def searchForSpringBootApp(dir: Path): Option[(String, String)] =
    if !os.isDir(dir) then None
    else
      val files = os.walk(dir).filter(f => os.isFile(f) && (f.last.endsWith(".java") || f.last.endsWith(".kt")))
      files.iterator.map { file =>
        val (pkg, className, hasSpringBootApp) = parseSourceFile(file.toString)
        (pkg, className, hasSpringBootApp, file)
      }.collectFirst {
        case (pkg, className, true, file) if pkg.nonEmpty && className.nonEmpty =>
          (s"$pkg.$className", file.toString)
      }

  /**
   * Parse a source file for package, class name, and @SpringBootApplication annotation
   * Returns (package, className, hasSpringBootApp)
   */
  private def parseSourceFile(filePath: String): (String, String, Boolean) =
    try
      val p = Path(filePath, os.pwd)
      val lines = os.read.lines(p, charSet = java.nio.charset.StandardCharsets.UTF_8).toList

      var pkg = ""
      var className = ""
      var hasSpringBootApp = false

      for line <- lines do
        if line != null then
          // Extract package
          if pkg.isEmpty then
            packagePattern.findFirstMatchIn(line) foreach { m =>
              pkg = m.group(1)
            }

          // Check for @SpringBootApplication
          if springBootAppPattern.findFirstIn(line).isDefined then
            hasSpringBootApp = true

          // Extract class name
          if className.isEmpty && hasSpringBootApp then
            classPattern.findFirstMatchIn(line) foreach { m =>
              className = m.group(1)
            }

      (pkg, className, hasSpringBootApp)
    catch
      case _: Exception => ("", "", false)

  /**
   * Extract project name from pom.xml
   */
  private def extractProjectNameFromPom(pomPath: String): String =
    try
      val p = Path(pomPath, os.pwd)
      val pom = XML.loadString(os.read(p))
      // Try <name> element first
      val nameNodes = pom \\ "name"
      if nameNodes.nonEmpty && nameNodes.head.text.trim.nonEmpty then
        return nameNodes.head.text.trim

      // Fall back to artifactId
      val artifactNodes = pom \\ "artifactId"
      if artifactNodes.nonEmpty && artifactNodes.head.text.trim.nonEmpty then
        return artifactNodes.head.text.trim

      // Fall back to directory name
      val parent = p / os.up
      parent.last
    catch
      case _: Exception =>
        val p = Path(pomPath, os.pwd)
        (p / os.up).last

  /**
   * Extract project name from build.gradle
   */
  private def extractProjectNameFromGradle(buildGradlePath: String): String =
    try
      val p = Path(buildGradlePath, os.pwd)
      val content = os.read(p)

      // Try to find rootProject.name
      val rootProjectPattern = """rootProject\.name\s*=\s*['"]([^'"]+)['"]""".r
      rootProjectPattern.findFirstMatchIn(content) match
        case Some(m) => return m.group(1)
        case None => ()

      // Fall back to directory name
      (p / os.up).last
    catch
      case _: Exception =>
        val p = Path(buildGradlePath, os.pwd)
        (p / os.up).last

  /**
   * Extract JVM debug arguments from pom.xml (maven-surefire or maven-failsafe)
   */
  private def extractJvmDebugArgsFromPom(pomPath: String): Option[String] =
    try
      val p = Path(pomPath, os.pwd)
      val pom = XML.loadString(os.read(p))

      // Look for maven-surefire-plugin or maven-failsafe-plugin
      val plugins = pom \\ "plugin"
      plugins.iterator.map { plugin =>
        val artifactId = (plugin \ "artifactId").text.trim
        val argLine = ((plugin \ "configuration") \ "argLine").text.trim
        (artifactId, argLine)
      }.collectFirst {
        case (artifactId, argLine) if (artifactId == "maven-surefire-plugin" || artifactId == "maven-failsafe-plugin") && argLine.nonEmpty =>
          argLine
      }
    catch
      case _: Exception => None

  /**
   * Extract JVM debug arguments from build.gradle
   */
  private def extractJvmDebugArgsFromGradle(buildGradlePath: String): Option[String] =
    try
      val p = Path(buildGradlePath, os.pwd)
      val content = os.read(p)

      // Look for test { jvmArgs = [...] }
      jvmArgsPattern.findFirstMatchIn(content) match
        case Some(m) =>
          val argsStr = m.group(1).trim
          // Clean up the arguments string
          Some(argsStr.replaceAll("['\"]", "").trim)
        case None => None
    catch
      case _: Exception => None

  /**
   * Detect active profiles from application.yml and application.properties
   */
  private def detectActiveProfiles(dir: Path): Seq[String] =
    val profiles = mutable.Set[String]()

    // Check config directories (root and src/main/resources)
    val configDirs = Seq(dir, dir / "src" / "main" / "resources").filter(os.exists)

    for cDir <- configDirs if os.isDir(cDir) do
      val appYml = cDir / "application.yml"
      if os.exists(appYml) && os.isFile(appYml) then
        profiles ++= parseProfilesFromFile(appYml.toString)

      val appProps = cDir / "application.properties"
      if os.exists(appProps) && os.isFile(appProps) then
        profiles ++= parseProfilesFromFile(appProps.toString)

      // Scan for application-{profile}.yml/properties files in config directory
      val allFiles = os.list(cDir).filter(f => os.isFile(f) && f.last.startsWith("application-"))
      for file <- allFiles do
        val name = file.last
        if name.endsWith(".yml") || name.endsWith(".yaml") || name.endsWith(".properties") then
          val profile = name
            .replaceAll("^application-", "")
            .replaceAll("\\.(yml|yaml|properties)$", "")
          if profile.nonEmpty then
            profiles += profile

    profiles.toSeq

  /**
   * Parse spring.profiles.active from a configuration file
   */
  private def parseProfilesFromFile(filePath: String): Seq[String] =
    try
      val p = Path(filePath, os.pwd)
      val lines = os.read.lines(p, charSet = java.nio.charset.StandardCharsets.UTF_8).toList

      val result = mutable.ListBuffer[String]()
      for line <- lines do
        if line != null then
          // Match properties format: spring.profiles.active=...
          if line.contains("spring.profiles.active") then
            profilesActivePropertiesPattern.findFirstMatchIn(line) foreach { m =>
              val profilesStr = m.group(1).trim
              // Split by comma and clean up
              val profiles = profilesStr.split(",").map(_.trim).filter(_.nonEmpty)
              result ++= profiles
            }
          // Match YAML format: active: ...
          else if line.contains("active:") then
            profilesActiveYamlPattern.findFirstMatchIn(line) foreach { m =>
              val profilesStr = m.group(1).trim
              // Split by comma and clean up
              val profiles = profilesStr.split(",").map(_.trim).filter(_.nonEmpty)
              result ++= profiles
            }
      result.toSeq
    catch
      case _: Exception => Seq()
