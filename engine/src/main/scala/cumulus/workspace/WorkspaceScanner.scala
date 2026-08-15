package cumulus.workspace

import os.Path

object WorkspaceScanner:

  /**
   * Scan upward from the given directory to find the project root.
   *
   * @param startDir The directory to start scanning from
   * @return Right(WorkspaceInfo) if workspace root found, Left(error message) if not
   */
  def discoverWorkspace(startDir: String): Either[String, WorkspaceInfo] =
    try
      var current = Path(startDir, os.pwd)

      if !os.exists(current) || !os.isDir(current) then
        return Left(s"Starting directory not found or not a directory: $startDir")

      var foundRoot: Option[Path] = None
      var keepScanning = true

      while keepScanning && current.segmentCount >= 0 do
        val buildFiles = getBuildFilesInDir(current)

        if buildFiles.nonEmpty then
          foundRoot = Some(current)
          keepScanning = false
        else
          // Stop at root directory
          if current.segmentCount == 0 then
            keepScanning = false
          else
            val parent = current / os.up
            if parent == current then
              keepScanning = false
            else
              current = parent

      foundRoot match
        case Some(root) =>
          val buildFiles = getBuildFilesInDir(root)
          val isMultiModule = detectMultiModule(root, buildFiles)

          Right(WorkspaceInfo(
            root = root.toString,
            build_files = buildFiles.sorted.toList,
            is_multi_module = isMultiModule
          ))

        case None =>
          Left(s"No workspace root found scanning upward from $startDir (looking for pom.xml, build.gradle, build.gradle.kts, build.sbt, .mvn, .gradle)")
    catch
      case e: Exception =>
        Left(s"Error scanning workspace: ${e.getMessage}")

  /**
   * Check for known build files in a directory.
   */
  private def getBuildFilesInDir(dir: Path): Seq[String] =
    val knownFiles = Seq(
      "pom.xml",
      "build.gradle",
      "build.gradle.kts",
      "settings.gradle",
      "settings.gradle.kts",
      "build.sbt",
      ".mvn",
      ".gradle"
    )

    val found = scala.collection.mutable.ListBuffer[String]()
    for fileName <- knownFiles do
      val file = dir / os.RelPath(fileName)
      if os.exists(file) then
        found += fileName

    found.toSeq

  /**
   * Detect if a workspace is multi-module.
   */
  private def detectMultiModule(root: Path, buildFiles: Seq[String]): Boolean =
    try
      // Check for Maven modules: look for <modules> tag with at least one <module>
      if buildFiles.contains("pom.xml") then
        val pomPath = root / "pom.xml"
        if os.exists(pomPath) && os.isFile(pomPath) then
          val content = os.read(pomPath)
          val modulesPattern = """(?s)<modules>.*?<module>.*?</module>.*?</modules>""".r
          if modulesPattern.findFirstIn(content).isDefined then
            return true

      // Check for Gradle settings.gradle with includes
      if buildFiles.contains("settings.gradle") then
        val settingsPath = root / "settings.gradle"
        if os.exists(settingsPath) && os.isFile(settingsPath) then
          val content = os.read(settingsPath)
          if content.matches("""(?s).*\binclude\s+['"]?[\w:/-]+['"]?.*""") then
            return true

      // Check settings.gradle.kts
      if buildFiles.contains("settings.gradle.kts") || os.exists(root / "settings.gradle.kts") then
        val settingsKtsPath = root / "settings.gradle.kts"
        if os.exists(settingsKtsPath) && os.isFile(settingsKtsPath) then
          val content = os.read(settingsKtsPath)
          if content.matches("""(?s).*\binclude\s*\(\s*['"]?[\w:/-]+['"]?.*""") || content.matches("""(?s).*\binclude\s+['"]?[\w:/-]+['"]?.*""") then
            return true

      false
    catch
      case _: Exception => false
