package cumulus.workspace

import java.io.File
import java.nio.file.Files
import java.nio.file.Paths

object WorkspaceScanner:

  /**
   * Scan upward from the given directory to find the workspace root.
   * Stops at the first directory containing build files (pom.xml, build.gradle, build.gradle.kts, settings.gradle, build.sbt, .mvn, .gradle).
   * If no build files are found, returns the input directory.
   *
   * @param dirPath The directory path to start scanning from
   * @return Right(WorkspaceInfo) with root path and metadata
   */
  def discoverWorkspace(dirPath: String): Either[String, WorkspaceInfo] =
    try
      val startDir = new File(dirPath)

      if !startDir.exists() || !startDir.isDirectory() then
        return Left(s"Directory not found or not a directory: $dirPath")

      // Scan upward for workspace root
      val (root, buildFiles) = scanUpwardForRoot(startDir)

      // Check if multi-module
      val isMultiModule = detectMultiModule(root, buildFiles)

      Right(WorkspaceInfo(
        root = root.getAbsolutePath(),
        build_files = buildFiles.sorted.toList,
        is_multi_module = isMultiModule
      ))
    catch
      case e: Exception =>
        Left(s"Error discovering workspace: ${e.getMessage}")

  /**
   * Scan upward from the given directory to find the project root.
   * Returns the root path and list of detected build files.
   *
   * @param startDir The directory to start scanning from
   * @return Tuple of (root path, list of build files found)
   */
  private def scanUpwardForRoot(startDir: File): (File, Seq[String]) =
    val buildFileNames = Seq("pom.xml", "build.gradle", "build.gradle.kts", "settings.gradle", "build.sbt")
    val buildDirNames = Seq(".mvn", ".gradle")

    def scan(current: File): (File, Seq[String]) =
      // Check for build files in current directory
      val detectedFiles = scala.collection.mutable.ListBuffer[String]()

      for fileName <- buildFileNames do
        if new File(current, fileName).exists() then
          detectedFiles += fileName

      // Check for build directories
      for dirName <- buildDirNames do
        val dirFile = new File(current, dirName)
        if dirFile.exists() && dirFile.isDirectory() then
          detectedFiles += dirName

      // If build files found, this is the root
      if detectedFiles.nonEmpty then
        (current, detectedFiles.toSeq)
      else
        // Move to parent directory
        val parent = current.getParentFile()
        if parent == null || parent == current then
          // Reached filesystem root without finding build files
          // Return the original directory
          (startDir, Seq())
        else
          scan(parent)

    scan(startDir)

  /**
   * Detect if the workspace contains multiple modules.
   * Heuristics:
   * - Maven: presence of modules section in pom.xml
   * - Gradle: presence of include directives in settings.gradle
   * - SBT: multiple subdirectories with build.sbt
   *
   * @param root The workspace root directory
   * @param buildFiles The detected build files
   * @return true if multi-module, false otherwise
   */
  private def detectMultiModule(root: File, buildFiles: Seq[String]): Boolean =
    try
      // Check for Maven modules: look for <modules> tag with at least one <module>
      if buildFiles.contains("pom.xml") then
        val pomPath = new File(root, "pom.xml")
        if pomPath.exists() then
          val content = new String(Files.readAllBytes(pomPath.toPath()))
          // More robust: check for <modules> pattern with nested <module> tags (allow whitespace)
          val modulesPattern = """(?s)<modules>.*?<module>.*?</module>.*?</modules>""".r
          if modulesPattern.findFirstIn(content).isDefined then
            return true

      // Check for Gradle settings.gradle with includes
      if buildFiles.contains("settings.gradle") then
        val settingsPath = new File(root, "settings.gradle")
        if settingsPath.exists() then
          val content = new String(Files.readAllBytes(settingsPath.toPath()))
          // Look for include directives with module references
          if content.matches("""(?s).*\binclude\s+['"]?[\w:/-]+['"]?.*""") then
            return true

      // Check settings.gradle.kts as well
      if buildFiles.contains("build.gradle.kts") then
        val settingsKtsPath = new File(root, "settings.gradle.kts")
        if settingsKtsPath.exists() then
          val content = new String(Files.readAllBytes(settingsKtsPath.toPath()))
          if content.matches("""(?s).*\binclude\s+\(?\s*['"]?[\w:/-]+['"]?.*""") then
            return true

      false
    catch
      case _: Exception => false
