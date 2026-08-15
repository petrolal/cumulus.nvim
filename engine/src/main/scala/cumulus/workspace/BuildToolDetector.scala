package cumulus.workspace

import os.Path

object BuildToolDetector:

  /**
   * Detect build tool in the given directory.
   * Scans for build files and wrappers; prioritizes Maven > Gradle > SBT.
   *
   * @param dirPath The directory path to scan
   * @return Right(BuildToolInfo) if a build tool is detected, Left(error message) if not
   */
  def detectBuildTool(dirPath: String): Either[String, BuildToolInfo] =
    try
      val dir = Path(dirPath, os.pwd)

      if !os.exists(dir) || !os.isDir(dir) then
        return Left(s"Directory not found or not a directory: $dirPath")

      // Check for Maven
      val pomExists = os.exists(dir / "pom.xml") && os.isFile(dir / "pom.xml")
      val mvnwFile = dir / "mvnw"
      val mvnwExists = os.exists(mvnwFile) && os.isFile(mvnwFile)
      if pomExists || mvnwExists then
        val wrapperPath = if mvnwExists then Some(mvnwFile.toString) else None
        val executableFlag = wrapperPath.map(isExecutable)
        val recommendation = wrapperPath.flatMap { wp =>
          if !executableFlag.getOrElse(true) then
            Some(s"chmod +x $wp")
          else
            None
        }

        return Right(BuildToolInfo(
          build_tool = "maven",
          wrapper = wrapperPath,
          executable = executableFlag,
          recommendation = recommendation
        ))

      // Check for Gradle
      val buildGradleExists = os.exists(dir / "build.gradle") && os.isFile(dir / "build.gradle")
      val buildGradleKtsExists = os.exists(dir / "build.gradle.kts") && os.isFile(dir / "build.gradle.kts")
      val settingsGradleExists = os.exists(dir / "settings.gradle") && os.isFile(dir / "settings.gradle")
      val settingsGradleKtsExists = os.exists(dir / "settings.gradle.kts") && os.isFile(dir / "settings.gradle.kts")
      val gradlewFile = dir / "gradlew"
      val gradlewExists = os.exists(gradlewFile) && os.isFile(gradlewFile)
      if buildGradleExists || buildGradleKtsExists || settingsGradleExists || settingsGradleKtsExists || gradlewExists then
        val wrapperPath = if gradlewExists then Some(gradlewFile.toString) else None
        val executableFlag = wrapperPath.map(isExecutable)
        val recommendation = wrapperPath.flatMap { wp =>
          if !executableFlag.getOrElse(true) then
            Some(s"chmod +x $wp")
          else
            None
        }

        return Right(BuildToolInfo(
          build_tool = "gradle",
          wrapper = wrapperPath,
          executable = executableFlag,
          recommendation = recommendation
        ))

      // Check for SBT
      val buildSbtExists = os.exists(dir / "build.sbt") && os.isFile(dir / "build.sbt")
      val projectDir = dir / "project"
      val projectDirExists = os.exists(projectDir) && os.isDir(projectDir)
      if buildSbtExists || (projectDirExists && os.exists(projectDir / "build.properties")) then
        return Right(BuildToolInfo(
          build_tool = "sbt",
          wrapper = None,
          executable = None,
          recommendation = None
        ))

      // No build tool found
      Left(s"No build tool detected in $dirPath (looking for pom.xml, build.gradle, build.gradle.kts, settings.gradle, settings.gradle.kts, build.sbt)")
    catch
      case e: Exception =>
        Left(s"Error detecting build tool: ${e.getMessage}")

  /**
   * Check if a file is executable.
   *
   * @param filePath The path to the file
   * @return true if executable, false otherwise
   */
  private def isExecutable(filePath: String): Boolean =
    try
      val file = new java.io.File(filePath)
      file.canExecute()
    catch
      case _: Exception => false

