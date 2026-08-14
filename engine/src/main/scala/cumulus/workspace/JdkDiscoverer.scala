package cumulus.workspace

import java.io.File
import java.nio.file.Files
import java.nio.file.Paths

object JdkDiscoverer:

  /**
   * Search for JDK installation matching the specified major version.
   * Searches in standard locations:
   * - /usr/lib/jvm/java-<version>*
   * - ~/.sdkman/candidates/java/<version>*
   * - $JAVA_HOME environment variable
   *
   * @param version The major JDK version to search for (e.g., "21")
   * @return Right(JdkInfo) if found, Left(error message) if not found
   */
  def discoverJdk(version: String): Either[String, JdkInfo] =
    try
      // Try standard Unix locations first
      val candidates = scala.collection.mutable.ListBuffer[String]()

      // Check /usr/lib/jvm/
      val jvmFile = new File("/usr/lib/jvm")
      if jvmFile.exists() && jvmFile.isDirectory() then
        try
          for item <- jvmFile.listFiles() if item != null do
            if item.isDirectory() && item.getName.contains(s"java-$version") then
              candidates += item.getAbsolutePath
        catch
          case _: Exception => // Ignore if we can't read the directory

      // Check ~/.sdkman/candidates/java/
      val home = System.getProperty("user.home")
      val sdkmanPath = new File(s"$home/.sdkman/candidates/java")
      if sdkmanPath.exists() && sdkmanPath.isDirectory() then
        try
          for item <- sdkmanPath.listFiles() if item != null do
            if item.isDirectory() && item.getName.contains(version) then
              candidates += item.getAbsolutePath
        catch
          case _: Exception => // Ignore if we can't read the directory

      // Check $JAVA_HOME environment variable
      Option(System.getenv("JAVA_HOME")).foreach { javaHome =>
        val javaFile = new File(javaHome)
        if javaFile.exists() && javaFile.isDirectory() then
          // Extract version from path or environment
          if javaFile.getAbsolutePath.contains(version) then
            candidates += javaFile.getAbsolutePath
      }

      if candidates.isEmpty then
        return Left(s"JDK version $version not found")

      // Sort candidates and pick the first one (alphabetically)
      val sortedCandidates = candidates.sorted
      val selectedPath = sortedCandidates.head

      // Extract version string from path or use generic pattern
      val versionString = extractVersionString(selectedPath, version)

      Right(JdkInfo(
        java_home = selectedPath,
        version = versionString
      ))
    catch
      case e: Exception =>
        Left(s"Error discovering JDK: ${e.getMessage}")

  /**
   * Extract version string from JDK path.
   * Falls back to provided version with common patch suffixes.
   *
   * @param path The path to the JDK installation
   * @param version The major version requested
   * @return A version string (e.g., "21.0.3" or "21")
   */
  private def extractVersionString(path: String, version: String): String =
    try
      // Try to read version from the path (e.g., java-21-openjdk, openjdk-21.0.3)
      val pathLower = path.toLowerCase

      // Pattern: java-21-openjdk-something or similar
      val pattern = s"""java-?$version[._]?([0-9.]+)?""".r
      pattern.findFirstMatchIn(pathLower) match
        case Some(m) if m.group(1) != null => s"$version.${m.group(1)}"
        case _ => s"$version.0.0"
    catch
      case _: Exception => s"$version.0.0"
