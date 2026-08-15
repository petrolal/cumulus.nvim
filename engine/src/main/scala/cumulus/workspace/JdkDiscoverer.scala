package cumulus.workspace

import os.Path
import scala.collection.mutable

object JdkDiscoverer:

  /**
   * Search for JDK installation matching the specified major version.
   * Searches in standard locations:
   * - /usr/lib/jvm/
   * - ~/.sdkman/candidates/java/
   * - /Library/Java/JavaVirtualMachines/
   * - $JAVA_HOME environment variable
   *
   * @param version The major JDK version to search for (e.g., "21")
   * @return Right(JdkInfo) if found, Left(error message) if not found
   */
  def discoverJdk(version: String): Either[String, JdkInfo] =
    try
      val candidates = mutable.ListBuffer[String]()

      // Regex pattern to match JDK versions with vendor prefixes: java-21, jdk-21, temurin-21, zulu-21, graalvm-21, openjdk-21
      val versionPattern = s"""(?i)(?:java|jdk|graalvm|temurin|zulu|openjdk)-?$version(?:[._-]|\b|$$)""".r

      // Check /usr/lib/jvm/
      val jvmDir = Path("/usr/lib/jvm", os.root)
      if os.exists(jvmDir) && os.isDir(jvmDir) then
        try
          for item <- os.list(jvmDir) do
            if os.isDir(item) && versionPattern.findFirstIn(item.last).isDefined then
              candidates += item.toString
        catch
          case _: Exception => ()

      // Check ~/.sdkman/candidates/java/
      val home = Path(System.getProperty("user.home"), os.root)
      val sdkmanDir = home / ".sdkman" / "candidates" / "java"
      if os.exists(sdkmanDir) && os.isDir(sdkmanDir) then
        try
          for item <- os.list(sdkmanDir) do
            if os.isDir(item) && versionPattern.findFirstIn(item.last).isDefined then
              candidates += item.toString
        catch
          case _: Exception => ()

      // Check /Library/Java/JavaVirtualMachines/ (macOS)
      val macJvmDir = Path("/Library/Java/JavaVirtualMachines", os.root)
      if os.exists(macJvmDir) && os.isDir(macJvmDir) then
        try
          for item <- os.list(macJvmDir) do
            if os.isDir(item) && versionPattern.findFirstIn(item.last).isDefined then
              val homePath = item / "Contents" / "Home"
              if os.exists(homePath) && os.isDir(homePath) then
                candidates += homePath.toString
              else
                candidates += item.toString
        catch
          case _: Exception => ()

      // Check $JAVA_HOME environment variable
      Option(System.getenv("JAVA_HOME")).foreach { javaHome =>
        try
          val javaPath = Path(javaHome, os.pwd)
          if os.exists(javaPath) && os.isDir(javaPath) then
            if versionPattern.findFirstIn(javaPath.last).isDefined || versionPattern.findFirstIn(javaPath.toString).isDefined then
              candidates += javaPath.toString
        catch
          case _: Exception => ()
      }

      if candidates.isEmpty then
        return Left(s"JDK version $version not found")

      // Sort candidates and pick the first one (alphabetically)
      val sortedCandidates = candidates.sorted
      val selectedPath = sortedCandidates.head

      // Extract version string from path
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
   *
   * @param path The path to the JDK installation
   * @param version The major version requested
   * @return A version string (e.g., "21.0.3" or "21.0.0")
   */
  private def extractVersionString(path: String, version: String): String =
    try
      val pathLower = path.toLowerCase
      val pattern = s"""(?:java|jdk|graalvm|temurin|zulu|openjdk)-?$version[._]?([0-9.]+)?""".r
      pattern.findFirstMatchIn(pathLower) match
        case Some(m) if m.group(1) != null && m.group(1).nonEmpty =>
          val rest = m.group(1).stripPrefix(".").stripPrefix("-")
          if rest.nonEmpty then s"$version.$rest" else s"$version.0.0"
        case _ => s"$version.0.0"
    catch
      case _: Exception => s"$version.0.0"

