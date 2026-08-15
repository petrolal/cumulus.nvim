package cumulus.testing

import java.io.File

object TestCommandAssembler:

  /**
   * Assemble a platform-correct test CLI command for Maven, Gradle, or SBT.
   *
   * @param tool The build tool: "maven", "gradle", or "sbt"
   * @param className The test class name (e.g., "FooTest")
   * @param methodName The test method name (e.g., "testBar")
   * @param dirPath The project directory path
   * @return Either an error message or the TestCommand with command string and cwd
   */
  def assembleTestCommand(
    tool: String,
    className: String,
    methodName: String,
    dirPath: String
  ): Either[String, TestCommand] =
    try
      val dir = new File(dirPath)
      if !dir.exists() || !dir.isDirectory() then
        return Left(s"Directory not found or not a directory: $dirPath")

      val projectRoot = findProjectRoot(dir)
      val projectRootPath = projectRoot.getAbsolutePath()

      val command = tool.toLowerCase() match
        case "maven" =>
          val executable = if hasWrapper(projectRoot, "mvnw") then "./mvnw" else "mvn"
          val modulePath = detectMavenModule(dirPath, projectRootPath)
          if modulePath.nonEmpty && modulePath != "." then
            s"$executable -pl :$modulePath test -Dtest=$className#$methodName"
          else
            s"$executable test -Dtest=$className#$methodName"

        case "gradle" =>
          val executable = if hasWrapper(projectRoot, "gradlew") then "./gradlew" else "gradle"
          val modulePath = detectGradleModule(dirPath, projectRootPath)
          if modulePath.nonEmpty && modulePath != "." then
            s"$executable :$modulePath:test --tests $className.$methodName"
          else
            s"$executable test --tests $className.$methodName"

        case "sbt" =>
          s"""sbt "testOnly *$className -- -t $methodName""""

        case other =>
          return Left(s"Unsupported build tool: $other")

      Right(TestCommand(
        command = command,
        cwd = projectRootPath
      ))

    catch
      case e: Exception =>
        Left(s"Error assembling test command: ${e.getMessage}")

  /**
   * Find the project root by walking up the directory tree looking for build files.
   */
  private def findProjectRoot(startDir: File): File =
    var current = startDir
    while current != null do
      if hasBuildFile(current) then
        return current
      current = current.getParentFile()

    startDir // Fallback to starting directory

  /**
   * Check if a directory contains Maven or Gradle build files.
   */
  private def hasBuildFile(dir: File): Boolean =
    val pomFile = new File(dir, "pom.xml")
    val settingsFile = new File(dir, "settings.gradle")
    val buildFile = new File(dir, "build.gradle")
    pomFile.exists() || settingsFile.exists() || buildFile.exists()

  /**
   * Check if a wrapper executable exists in the directory.
   */
  private def hasWrapper(dir: File, wrapperName: String): Boolean =
    val wrapper = new File(dir, wrapperName)
    wrapper.exists() && wrapper.isFile()

  /**
   * Detect which Maven module contains the given directory path.
   * Returns the module name or "." for single-module projects.
   */
  private def detectMavenModule(dirPath: String, projectRoot: String): String =
    val dir = new File(dirPath)
    val normalizedDirPath = dir.getAbsolutePath()
    val normalizedProjectRoot = new File(projectRoot).getAbsolutePath()

    if normalizedDirPath == normalizedProjectRoot then
      return "."

    // Check if there's a pom.xml in the given directory or its parents
    var current = Option(dir)
    while current.isDefined && current.get.getAbsolutePath().startsWith(normalizedProjectRoot) do
      val pomFile = new File(current.get, "pom.xml")
      if pomFile.exists() then
        // Extract module name from relative path
        val relativePath = current.get.getAbsolutePath().substring(normalizedProjectRoot.length)
        val cleanPath = if relativePath.startsWith("/") then relativePath.substring(1) else relativePath
        if cleanPath.nonEmpty then
          return cleanPath.replace("\\", "/")
        else
          return "."
      current = Option(current.get.getParentFile())

    "."

  /**
   * Detect which Gradle module contains the given directory path.
   * Returns the module name or "." for single-module projects.
   */
  private def detectGradleModule(dirPath: String, projectRoot: String): String =
    val dir = new File(dirPath)
    val normalizedDirPath = dir.getAbsolutePath()
    val normalizedProjectRoot = new File(projectRoot).getAbsolutePath()

    if normalizedDirPath == normalizedProjectRoot then
      return "."

    // Extract relative path and convert to Gradle module notation
    val relativePath = normalizedDirPath.substring(normalizedProjectRoot.length)
    val cleanPath = if relativePath.startsWith("/") then relativePath.substring(1) else relativePath
    if cleanPath.nonEmpty then
      val modulePath = cleanPath.replace("\\", "/").replace("/", ":")
      if modulePath.isEmpty then "." else modulePath
    else
      "."
