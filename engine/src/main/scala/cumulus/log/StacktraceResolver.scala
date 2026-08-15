package cumulus.log

import scala.io.Source
import scala.util.Try
import scala.collection.mutable
import java.io.File
import java.nio.file.{Files, Paths}

/**
 * Resolves stacktrace frames to actual workspace file paths.
 * Parses JVM stacktrace format and maps to source files.
 */
object StacktraceResolver:

  /**
   * Parse a stacktrace frame in JVM format.
   * Example: "at com.example.Service.method(Service.java:123)"
   * Validates that the parsed frame matches the expected pattern with all required components.
   *
   * @param frameStr The stacktrace frame string
   * @return Option of parsed StackFrame
   */
  def parseStackFrame(frameStr: String): Option[StackFrame] =
    val trimmed = frameStr.trim
    // Match pattern: "at com.pkg.Class.method(File.java:123)" or "at com.pkg.Outer$Inner.method(File.java:123)"
    val pattern = """at\s+(.+?)\.([^.(]+)\(([^:]+):(\d+)\)""".r
    pattern.findFirstMatchIn(trimmed) match
      case Some(m) =>
        val fullClassPath = m.group(1) // e.g., "com.example.Service" or "com.example.Outer$Inner"
        val methodName = m.group(2)
        val fileName = m.group(3) // e.g., "Service.java"
        val lineNumberStr = m.group(4)

        // Validate that all components are present and non-empty
        if fullClassPath.nonEmpty && methodName.nonEmpty && fileName.nonEmpty && lineNumberStr.nonEmpty then
          try
            val lineNumber = lineNumberStr.toInt
            Some(StackFrame(
              className = fullClassPath,
              methodName = methodName,
              file = fileName,
              line = lineNumber
            ))
          catch
            case e: NumberFormatException => None
        else
          None
      case None => None

  /**
   * Resolve a StackFrame to an actual file path in the workspace.
   * Scans standard source directories: src/main/java, src/main/kotlin, src/test/java, src/test/kotlin
   * Prefers src/main over src/test when ambiguous matches exist.
   *
   * @param frame The StackFrame to resolve
   * @param workspaceDir The workspace root directory
   * @return Either a resolved file path or an error message
   */
  def resolveStackFrame(frame: StackFrame, workspaceDir: String): Either[String, String] =
    try
      val baseDir = new File(workspaceDir)
      if !baseDir.exists() || !baseDir.isDirectory() then
        return Left(s"Workspace directory not found: $workspaceDir")

      // Standard source directories to search, ordered by preference
      val sourceDirs = Seq(
        "src/main/java",
        "src/main/kotlin",
        "src/test/java",
        "src/test/kotlin"
      )

      // Extract the class name and convert to file path
      // For inner classes (Outer$Inner), we need to strip the inner class part
      val className = frame.className.replace(".", File.separator)
      val classNameNoInner = className.split("\\$")(0) // Strip $Inner if present

      // Collect all matching paths (to detect ambiguous matches)
      val matchedPaths = mutable.Buffer[String]()

      // Search for the file in all source directories by class path
      for (sourceDir <- sourceDirs) {
        val sourcePath = new File(baseDir, sourceDir)
        if sourcePath.exists() && sourcePath.isDirectory() then
          // Try to find the file
          val possiblePath = new File(sourcePath, classNameNoInner + ".java")
          if possiblePath.exists() then
            matchedPaths += possiblePath.getAbsolutePath()

          // Also try .kt extension
          val possibleKtPath = new File(sourcePath, classNameNoInner + ".kt")
          if possibleKtPath.exists() then
            matchedPaths += possibleKtPath.getAbsolutePath()
      }

      // If found by class path, prefer src/main over src/test
      if matchedPaths.nonEmpty then
        val mainPath = matchedPaths.find(_.contains("src/main"))
        return Right(mainPath.getOrElse(matchedPaths(0)))

      // If not found by class path, try a simple file search
      val fileName = frame.file // e.g., "Service.java"
      val fileMatches = mutable.Buffer[String]()
      for (sourceDir <- sourceDirs) {
        val sourcePath = new File(baseDir, sourceDir)
        if sourcePath.exists() && sourcePath.isDirectory() then
          val possiblePath = new File(sourcePath, fileName)
          if possiblePath.exists() then
            fileMatches += possiblePath.getAbsolutePath()
      }

      if fileMatches.nonEmpty then
        // Prefer src/main over src/test when multiple matches
        val mainPath = fileMatches.find(_.contains("src/main"))
        return Right(mainPath.getOrElse(fileMatches(0)))

      // Check if multiple ambiguous matches were found
      if matchedPaths.length > 1 || fileMatches.length > 1 then
        Left(s"Ambiguous file matches for ${frame.file}: ${(matchedPaths ++ fileMatches).mkString(", ")}")
      else
        Left(s"File not found: ${frame.file}")
    catch
      case e: Exception =>
        Left(s"Error resolving stacktrace: ${e.getMessage}")

  /**
   * Resolve a full stacktrace string to a map of frames and their resolved paths.
   *
   * @param stacktraceStr The full stacktrace as a multi-line string
   * @param workspaceDir The workspace root directory
   * @return Either a map of frame -> resolved path, or an error message
   */
  def resolveStacktrace(stacktraceStr: String, workspaceDir: String): Either[String, Map[String, String]] =
    try
      val lines = stacktraceStr.split("(?:\r\n|\r|\n)")
      val results = mutable.Map[String, String]()
      var hasError = false
      var errorMsg = ""

      lines.foreach { line =>
        parseStackFrame(line) match
          case Some(frame) =>
            resolveStackFrame(frame, workspaceDir) match
              case Right(path) =>
                results(line) = path
              case Left(err) =>
                // Only record the first error
                if !hasError then
                  hasError = true
                  errorMsg = err
          case None =>
            // Skip non-stacktrace lines
      }

      if hasError && results.isEmpty then
        Left(errorMsg)
      else
        Right(results.toMap)
    catch
      case e: Exception =>
        Left(s"Error parsing stacktrace: ${e.getMessage}")
