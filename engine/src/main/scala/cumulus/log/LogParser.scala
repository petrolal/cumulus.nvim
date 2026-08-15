package cumulus.log

import scala.collection.mutable
import os.Path

/**
 * Parses Maven/Gradle build logs to extract diagnostics.
 * Handles ANSI escape code stripping and diagnostic pattern matching.
 */
object LogParser:

  /**
   * Strip ANSI escape codes from a string.
   * Removes patterns like \u001b[1;31m, \u001b[0m, \u001b(B, and unescaped brackets.
   */
  def stripAnsiCodes(text: String): String =
    text
      .replaceAll("\u001b\\[[0-9;]*[a-zA-Z]", "")
      .replaceAll("\u001b\\(B", "")
      .replaceAll("\\[[0-9;]*m", "")
      .replaceAll("\\(B", "")

  /**
   * Parse a build log (Maven or Gradle) and extract diagnostics.
   * Reads from the provided text content (already split into lines).
   *
   * @param logContent The full log content as a string
   * @return Sequence of BuildDiagnostic entries
   */
  def parseLog(logContent: String): Seq[BuildDiagnostic] =
    val lines = logContent.split("(?:\r\n|\r|\n)")
    val diagnostics = mutable.Buffer[BuildDiagnostic]()

    lines.zipWithIndex.foreach { case (line, _) =>
      val cleanLine = stripAnsiCodes(line)

      // Try Maven patterns:
      // 1. [ERROR] /path/to/File.java:[123,45] message or [ERROR] /path/to/File.java:[123] message
      // 2. [ERROR] /path/to/File.java:123 message
      val mavenBracketPattern = """\[(ERROR|WARN|WARNING)\]\s*(.+?):\[(\d+)(?:,(\d+))?\]\s*(.*)""".r
      val mavenSimplePattern = """\[(ERROR|WARN|WARNING)\]\s*(.+?):(\d+)\s*(.*)""".r

      mavenBracketPattern.findFirstMatchIn(cleanLine) match
        case Some(m) =>
          val severity = if m.group(1) == "WARNING" then "WARN" else m.group(1)
          val file = m.group(2).trim
          val lineNum = m.group(3).toInt
          val col = Option(m.group(4)).map(_.toInt).getOrElse(1)
          val message = m.group(5).trim
          diagnostics += BuildDiagnostic(
            file = file,
            line = lineNum,
            col = col,
            severity = severity,
            message = message
          )
        case None =>
          mavenSimplePattern.findFirstMatchIn(cleanLine) match
            case Some(m) =>
              val severity = if m.group(1) == "WARNING" then "WARN" else m.group(1)
              val file = m.group(2).trim
              val lineNum = m.group(3).toInt
              val message = m.group(4).trim
              diagnostics += BuildDiagnostic(
                file = file,
                line = lineNum,
                col = 1,
                severity = severity,
                message = message
              )
            case None =>
              // Try Gradle pattern: File.java:line:col: error: message
              val gradlePattern = """^(.+?):(\d+):(\d+):\s*(?:error|warning|info):\s*(.*)$""".r
              gradlePattern.findFirstMatchIn(cleanLine) match
                case Some(m) =>
                  val file = m.group(1).trim
                  val lineNum = m.group(2).toInt
                  val col = m.group(3).toInt
                  val message = m.group(4).trim
                  val severity = if cleanLine.toLowerCase.contains("error") then "ERROR" else "WARN"
                  diagnostics += BuildDiagnostic(
                    file = file,
                    line = lineNum,
                    col = col,
                    severity = severity,
                    message = message
                  )
                case None =>
                  // Try Gradle pattern without column: File.java:line: error: message
                  val gradlePatternNoCol = """^(.+?):(\d+):\s*(?:error|warning|info):\s*(.*)$""".r
                  gradlePatternNoCol.findFirstMatchIn(cleanLine) match
                    case Some(m) =>
                      val file = m.group(1).trim
                      val lineNum = m.group(2).toInt
                      val message = m.group(3).trim
                      val severity = if cleanLine.toLowerCase.contains("error") then "ERROR" else "WARN"
                      diagnostics += BuildDiagnostic(
                        file = file,
                        line = lineNum,
                        col = 1,
                        severity = severity,
                        message = message
                      )
                    case None =>
                      // No match, skip line
    }

    diagnostics.toSeq

  /**
   * Parse a build log from stdin input.
   *
   * @param stdinInput The full log content read from stdin
   * @return Sequence of BuildDiagnostic entries
   */
  def parseFromStdin(stdinInput: String): Seq[BuildDiagnostic] =
    parseLog(stdinInput)

  /**
   * Parse a build log from a file.
   *
   * @param filePath The path to the log file
   * @return Sequence of BuildDiagnostic entries, or throws exception if file not found
   */
  def parseFromFile(filePath: String): Seq[BuildDiagnostic] =
    val p = Path(filePath, os.pwd)
    if !os.exists(p) then
      throw new Exception(s"File not found: $filePath")
    if !os.isFile(p) then
      throw new java.io.IOException(s"Not a file: $filePath")

    // Check file size limit (100MB) to prevent OOM
    val MAX_FILE_SIZE = 100 * 1024 * 1024
    val fileSize = os.size(p)
    if fileSize > MAX_FILE_SIZE then
      throw new java.io.IOException(s"File too large: $fileSize bytes (max $MAX_FILE_SIZE)")

    val content = try
      os.read(p, charSet = java.nio.charset.StandardCharsets.UTF_8)
    catch
      case e: java.nio.charset.MalformedInputException =>
        // Fallback to default charset if UTF-8 fails
        os.read(p, charSet = java.nio.charset.Charset.defaultCharset())
      case e: Exception =>
        throw new Exception(s"Error reading file: ${e.getMessage}")

    parseLog(content)

