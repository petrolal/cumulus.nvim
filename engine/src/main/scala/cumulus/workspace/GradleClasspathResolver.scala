package cumulus.workspace

import os.Path
import scala.io.Source
import scala.collection.mutable
import scala.util.Using

/**
 * Resolves Gradle project classpath by scanning ~/.gradle/caches/modules-2/.
 */
object GradleClasspathResolver:

  /**
   * Resolve classpath for a Gradle project.
   * Scans ~/.gradle/caches/modules-2/ for downloaded dependency jars.
   *
   * @param projectRoot The root directory of the Gradle project
   * @return Seq[String] of jar file paths
   */
  def resolveClasspath(projectRoot: String): Seq[String] =
    try
      val rootPath = Path(projectRoot)

      // Check if it's a Gradle project
      val buildGradle = rootPath / "build.gradle"
      val buildGradleKts = rootPath / "build.gradle.kts"

      if (!os.exists(buildGradle) && !os.exists(buildGradleKts)) then
        return Seq.empty

      // Scan ~/.gradle/caches/modules-2 for jars
      val home = sys.env.getOrElse("HOME", System.getProperty("user.home"))
      val gradleCache = Path(home) / ".gradle" / "caches" / "modules-2"

      if !os.exists(gradleCache) || !os.isDir(gradleCache) then
        return Seq.empty

      // Recursively find all .jar files in the cache
      val jars = mutable.ListBuffer[String]()
      val visited = mutable.Set[String]()
      scanForJars(gradleCache, jars, visited)

      jars.toSeq
    catch
      case _: Exception => Seq.empty

  /**
   * Recursively scan a directory for jar files, tracking visited paths to prevent symlink cycles.
   *
   * @param dir The directory to scan
   * @param jars Buffer to accumulate found jar files
   * @param visited Set of absolute paths already visited (for symlink cycle detection)
   */
  private def scanForJars(dir: Path, jars: mutable.ListBuffer[String], visited: mutable.Set[String]): Unit =
    try
      val dirAbsPath = try
        dir.toNIO.toRealPath().toString
      catch
        case _: Exception => dir.toString

      // Check if we've already visited this directory (prevents symlink cycles)
      if visited.contains(dirAbsPath) then
        return

      visited += dirAbsPath

      val entries = os.list(dir)
      for entry <- entries do
        if os.isFile(entry) && entry.last.endsWith(".jar") then
          jars += entry.toString
        else if os.isDir(entry) then
          // Avoid scanning certain directories that are known not to contain jars
          val dirName = entry.last
          if !dirName.startsWith(".") && dirName != "metadata-2.1" then
            scanForJars(entry, jars, visited)
    catch
      case _: Exception => ()

  /**
   * Extract dependency info from build.gradle.
   * Very basic parsing - looks for patterns like "group:artifact:version"
   */
  private def extractDependencies(buildFilePath: String): Seq[(String, String, String)] =
    try
      val deps = mutable.ListBuffer[(String, String, String)]()

      Using.resource(Source.fromFile(buildFilePath, "UTF-8")) { source =>
        val content = source.getLines().mkString("\n")

        // Simple regex to find dependencies: "group:artifact:version" or implementation/api/runtimeOnly blocks
        val depPattern = """(?:group\s*[:=]\s*["']([^"']+)["'].*?name\s*[:=]\s*["']([^"']+)["'].*?version\s*[:=]\s*["']([^"']+)["'])""".r
        val shortPattern = """["']([^":]+):([^":]+):([^"]+)["']""".r

        // Find all dependency declarations
        val allMatches = depPattern.findAllMatchIn(content)
        for m <- allMatches do
          val groupId = m.group(1)
          val artifactId = m.group(2)
          val version = m.group(3)
          if groupId.nonEmpty && artifactId.nonEmpty && version.nonEmpty then
            deps += ((groupId, artifactId, version))

        // Also try short format
        val shortMatches = shortPattern.findAllMatchIn(content)
        for m <- shortMatches do
          val groupId = m.group(1)
          val artifactId = m.group(2)
          val version = m.group(3)
          if !version.contains(":") && groupId.nonEmpty && artifactId.nonEmpty && version.nonEmpty then
            deps += ((groupId, artifactId, version))
      }

      deps.toSeq
    catch
      case _: Exception => Seq.empty
