package cumulus.log

import scala.collection.mutable
import os.Path

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
      val baseDir = Path(workspaceDir, os.pwd)
      if !os.exists(baseDir) || !os.isDir(baseDir) then
        return Left(s"Workspace directory not found: $workspaceDir")

      // Standard source directories to search, ordered by preference
      val sourceDirs = Seq(
        "src/main/java",
        "src/main/kotlin",
        "src/test/java",
        "src/test/kotlin"
      )

      // Extract the class name and convert to relative path segments
      // For inner classes (Outer$Inner), strip the inner class part
      val classNameNoInner = frame.className.split("\\$")(0)
      val classRelPath = classNameNoInner.replace('.', '/')

      // Collect all matching paths (to detect matches)
      val matchedPaths = mutable.Buffer[String]()

      // Search for the file in all source directories by class path
      for (sourceDir <- sourceDirs) {
        val sourcePath = baseDir / os.RelPath(sourceDir)
        if os.exists(sourcePath) && os.isDir(sourcePath) then
          val possibleJava = sourcePath / os.RelPath(classRelPath + ".java")
          if os.exists(possibleJava) && os.isFile(possibleJava) then
            matchedPaths += possibleJava.toString

          val possibleKt = sourcePath / os.RelPath(classRelPath + ".kt")
          if os.exists(possibleKt) && os.isFile(possibleKt) then
            matchedPaths += possibleKt.toString
      }

      // If found by class path, prefer src/main over src/test
      if matchedPaths.nonEmpty then
        val mainPath = matchedPaths.find(_.contains("src/main"))
        return Right(mainPath.getOrElse(matchedPaths(0)))

      // If not found by class path, try searching by simple file name
      val fileName = frame.file // e.g., "Service.java"
      val fileMatches = mutable.Buffer[String]()
      for (sourceDir <- sourceDirs) {
        val sourcePath = baseDir / os.RelPath(sourceDir)
        if os.exists(sourcePath) && os.isDir(sourcePath) then
          val found = os.walk(sourcePath).filter(p => os.isFile(p) && p.last == fileName)
          fileMatches ++= found.map(_.toString)
      }

      if fileMatches.nonEmpty then
        // Prefer src/main over src/test when multiple matches
        val mainPath = fileMatches.find(_.contains("src/main"))
        Right(mainPath.getOrElse(fileMatches(0)))
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

