package cumulus.testing

import os.Path

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
      val dir = Path(dirPath, os.pwd)
      if !os.exists(dir) || !os.isDir(dir) then
        return Left(s"Directory not found or not a directory: $dirPath")

      val projectRoot = findProjectRoot(dir)
      val projectRootPath = projectRoot.toString

      val command = tool.toLowerCase() match
        case "maven" =>
          val executable = if hasWrapper(projectRoot, "mvnw") then "./mvnw" else "mvn"
          val modulePath = detectMavenModule(dir, projectRoot)
          if modulePath.nonEmpty && modulePath != "." then
            s"$executable -pl :$modulePath test -Dtest=$className#$methodName"
          else
            s"$executable test -Dtest=$className#$methodName"

        case "gradle" =>
          val executable = if hasWrapper(projectRoot, "gradlew") then "./gradlew" else "gradle"
          val modulePath = detectGradleModule(dir, projectRoot)
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
   * Find the project root by walking up the directory tree looking for root build files.
   */
  private def findProjectRoot(startDir: Path): Path =
    var current: Option[Path] = Some(startDir)
    var rootCandidate = startDir

    while current.isDefined do
      val p = current.get
      if os.exists(p / "pom.xml") || os.exists(p / "settings.gradle") || os.exists(p / "settings.gradle.kts") || os.exists(p / "build.sbt") then
        rootCandidate = p
      current = if p == os.root then None else Some(p / os.up)

    // If rootCandidate is still startDir, check if build.gradle exists
    if rootCandidate == startDir && os.exists(startDir / "build.gradle") then
      startDir
    else
      rootCandidate

  /**
   * Check if a wrapper executable exists in the directory.
   */
  private def hasWrapper(dir: Path, wrapperName: String): Boolean =
    val wrapper = dir / wrapperName
    os.exists(wrapper) && os.isFile(wrapper)

  /**
   * Detect which Maven module contains the given directory path.
   * Returns the module name or "." for single-module projects.
   */
  private def detectMavenModule(dir: Path, projectRoot: Path): String =
    if dir == projectRoot then
      return "."

    var current: Option[Path] = Some(dir)
    while current.isDefined && current.get.startsWith(projectRoot) do
      val pomFile = current.get / "pom.xml"
      if os.exists(pomFile) && os.isFile(pomFile) then
        if current.get == projectRoot then
          return "."
        else
          val rel = current.get.relativeTo(projectRoot).toString
          return rel.replace('\\', '/').stripPrefix("/")
      current = if current.get == projectRoot || current.get == os.root then None else Some(current.get / os.up)

    "."

  /**
   * Detect which Gradle module contains the given directory path.
   * Returns the module name or "." for single-module projects.
   */
  private def detectGradleModule(dir: Path, projectRoot: Path): String =
    if dir == projectRoot then
      return "."

    // Walk up looking for a build.gradle or build.gradle.kts until projectRoot
    var current: Option[Path] = Some(dir)
    var submodulePath: Option[Path] = None

    while current.isDefined && current.get.startsWith(projectRoot) do
      val p = current.get
      if p != projectRoot && (os.exists(p / "build.gradle") || os.exists(p / "build.gradle.kts") || os.exists(p / "pom.xml")) then
        submodulePath = Some(p)
      current = if p == projectRoot || p == os.root then None else Some(p / os.up)

    val targetDir = submodulePath.getOrElse(dir)
    if targetDir == projectRoot then
      "."
    else
      val rel = targetDir.relativeTo(projectRoot).toString.replace('\\', '/').stripPrefix("/")
      val moduleNotation = rel.replace('/', ':')
      if moduleNotation.isEmpty then "." else moduleNotation

