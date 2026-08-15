package cumulus.code

import scala.io.Source
import java.io.File
import scala.util.Using
import scala.xml.XML

object SpringBootDetector:

  // Compiled regex patterns
  private lazy val springBootAppPattern = """@SpringBootApplication\b""".r
  private lazy val packagePattern = """^\s*package\s+([a-zA-Z_][a-zA-Z0-9_.]*)\s*;""".r
  private lazy val classPattern = """^\s*(?:public\s+)?class\s+([a-zA-Z_][a-zA-Z0-9_]*)\s*""".r
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
    val dir = new File(dirPath)
    if !dir.exists() || !dir.isDirectory() then
      throw new Exception(s"Directory not found: $dirPath")

    // Search for @SpringBootApplication
    val (mainClass, filePath) = findSpringBootApp(dir) match
      case Some((cls, fPath)) => (cls, fPath)
      case None => throw new Exception("No Spring Boot application found")

    // Detect build tool and extract metadata
    val pomFile = new File(dir, "pom.xml")
    val buildGradleFile = new File(dir, "build.gradle")

    val (buildTool, projectName) = if pomFile.exists() then
      ("maven", extractProjectNameFromPom(pomFile.getAbsolutePath()))
    else if buildGradleFile.exists() then
      ("gradle", extractProjectNameFromGradle(buildGradleFile.getAbsolutePath()))
    else
      (null, dir.getName)

    // Extract JVM debug args
    val jvmDebugArgs = if pomFile.exists() then
      extractJvmDebugArgsFromPom(pomFile.getAbsolutePath())
    else if buildGradleFile.exists() then
      extractJvmDebugArgsFromGradle(buildGradleFile.getAbsolutePath())
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
  private def findSpringBootApp(dir: File): Option[(String, String)] =
    val srcMainJava = new File(dir, "src/main/java")
    val srcMainKotlin = new File(dir, "src/main/kotlin")

    val result = if srcMainJava.exists() then
      searchForSpringBootApp(srcMainJava)
    else if srcMainKotlin.exists() then
      searchForSpringBootApp(srcMainKotlin)
    else
      None

    result

  /**
   * Recursively search for @SpringBootApplication in a directory
   * Returns (fully-qualified class name, file path)
   */
  private def searchForSpringBootApp(dir: File): Option[(String, String)] =
    if !dir.isDirectory() then
      return None

    val files = dir.listFiles()
    if files == null then
      return None

    for file <- files do
      if file.isDirectory() then
        val result = searchForSpringBootApp(file)
        if result.isDefined then
          return result
      else if file.getName.endsWith(".java") || file.getName.endsWith(".kt") then
        try
          val (pkg, className, hasSpringBootApp) = parseSourceFile(file.getAbsolutePath())
          if hasSpringBootApp && pkg.nonEmpty && className.nonEmpty then
            return Some((s"$pkg.$className", file.getAbsolutePath()))
        catch
          case _: Exception => // Skip files that can't be parsed

    None

  /**
   * Parse a source file for package, class name, and @SpringBootApplication annotation
   * Returns (package, className, hasSpringBootApp)
   */
  private def parseSourceFile(filePath: String): (String, String, Boolean) =
    try
      val file = new File(filePath)
      val lines = Using(Source.fromFile(file, "UTF-8")) { source =>
        source.getLines().toList
      }.get

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
      val pom = XML.loadFile(new File(pomPath))
      // Try <name> element first
      val nameNodes = pom \\ "name"
      if nameNodes.nonEmpty then
        return nameNodes.head.text.trim

      // Fall back to artifactId
      val artifactNodes = pom \\ "artifactId"
      if artifactNodes.nonEmpty then
        return artifactNodes.head.text.trim

      // Fall back to directory name
      new File(pomPath).getParent match
        case p if p != null => new File(p).getName
        case _ => "unknown"
    catch
      case _: Exception =>
        new File(pomPath).getParent match
          case p if p != null => new File(p).getName
          case _ => "unknown"

  /**
   * Extract project name from build.gradle
   */
  private def extractProjectNameFromGradle(buildGradlePath: String): String =
    try
      val file = new File(buildGradlePath)
      val content = Using(Source.fromFile(file, "UTF-8")) { source =>
        source.mkString
      }.get

      // Try to find rootProject.name
      val rootProjectPattern = """rootProject\.name\s*=\s*['"]([^'"]+)['"]""".r
      rootProjectPattern.findFirstMatchIn(content) match
        case Some(m) => return m.group(1)
        case None => ()

      // Fall back to directory name
      new File(buildGradlePath).getParent match
        case p if p != null => new File(p).getName
        case _ => "unknown"
    catch
      case _: Exception =>
        new File(buildGradlePath).getParent match
          case p if p != null => new File(p).getName
          case _ => "unknown"

  /**
   * Extract JVM debug arguments from pom.xml (maven-surefire or maven-failsafe)
   */
  private def extractJvmDebugArgsFromPom(pomPath: String): Option[String] =
    try
      val pom = XML.loadFile(new File(pomPath))

      // Look for maven-surefire-plugin or maven-failsafe-plugin
      val plugins = pom \\ "plugin"
      for plugin <- plugins do
        val artifactId = (plugin \ "artifactId").text.trim
        if artifactId == "maven-surefire-plugin" || artifactId == "maven-failsafe-plugin" then
          val config = plugin \ "configuration"
          val argLine = (config \ "argLine").text.trim
          if argLine.nonEmpty then
            return Some(argLine)

      None
    catch
      case _: Exception => None

  /**
   * Extract JVM debug arguments from build.gradle
   */
  private def extractJvmDebugArgsFromGradle(buildGradlePath: String): Option[String] =
    try
      val file = new File(buildGradlePath)
      val content = Using(Source.fromFile(file, "UTF-8")) { source =>
        source.mkString
      }.get

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
  private def detectActiveProfiles(dir: File): Seq[String] =
    val profiles = scala.collection.mutable.Set[String]()

    // Check application.yml
    val appYml = new File(dir, "application.yml")
    if appYml.exists() then
      profiles ++= parseProfilesFromFile(appYml.getAbsolutePath())

    // Check application.properties
    val appProps = new File(dir, "application.properties")
    if appProps.exists() then
      profiles ++= parseProfilesFromFile(appProps.getAbsolutePath())

    // Scan for application-{profile}.yml/properties files in root directory
    val allFiles = dir.listFiles()
    if allFiles != null then
      for file <- allFiles if file.isFile && file.getName.startsWith("application-") do
        val name = file.getName
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
      val file = new File(filePath)
      val lines = Using(Source.fromFile(file, "UTF-8")) { source =>
        source.getLines().toList
      }.get

      val result = scala.collection.mutable.ListBuffer[String]()
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
