package cumulus.workspace

import java.io.File

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
      val dir = new File(dirPath)

      if !dir.exists() || !dir.isDirectory() then
        return Left(s"Directory not found or not a directory: $dirPath")

      // Check for Maven
      val pomExists = new File(dir, "pom.xml").exists()
      val mvnwExists = new File(dir, "mvnw").exists()
      if pomExists || mvnwExists then
        val wrapperPath = if mvnwExists then Some(s"$dirPath/mvnw") else None
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
      val buildGradleExists = new File(dir, "build.gradle").exists()
      val buildGradleKtsExists = new File(dir, "build.gradle.kts").exists()
      val gradlewExists = new File(dir, "gradlew").exists()
      if buildGradleExists || buildGradleKtsExists || gradlewExists then
        val wrapperPath = if gradlewExists then Some(s"$dirPath/gradlew") else None
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
      val buildSbtExists = new File(dir, "build.sbt").exists()
      val projectDir = new File(dir, "project")
      val projectDirExists = projectDir.exists() && projectDir.isDirectory()
      if buildSbtExists || (projectDirExists && new File(projectDir, "build.properties").exists()) then
        return Right(BuildToolInfo(
          build_tool = "sbt",
          wrapper = None,
          executable = None,
          recommendation = None
        ))

      // No build tool found
      Left(s"No build tool detected in $dirPath (looking for pom.xml, build.gradle, build.gradle.kts, build.sbt)")
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
