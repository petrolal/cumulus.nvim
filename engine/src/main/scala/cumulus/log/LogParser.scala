package cumulus.log

import scala.io.Source
import scala.util.Try
import scala.collection.mutable

/**
 * Parses Maven/Gradle build logs to extract diagnostics.
 * Handles ANSI escape code stripping and diagnostic pattern matching.
 */
object LogParser:

  /**
   * Strip ANSI escape codes from a string.
   * Removes patterns like [1;31m, [0m, (B, etc.
   */
  def stripAnsiCodes(text: String): String =
    // Match ANSI escape sequences: [<numbers>;*m and (B
    text.replaceAll("\\[[0-9;]*m", "").replaceAll("\\(B", "")

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

      // Try Maven pattern first: [ERROR] /path/to/File.java:[line] message
      // or [WARN] /path/to/File.java:[line] message
      val mavenPattern = """\[(ERROR|WARN|WARNING)\]\s*(.+?):(\d+)\s*(.*)""".r
      mavenPattern.findFirstMatchIn(cleanLine) match
        case Some(m) =>
          val severity = if m.group(1) == "WARNING" then "WARN" else m.group(1)
          val file = m.group(2).trim
          val line = m.group(3).toInt
          val message = m.group(4).trim
          diagnostics += BuildDiagnostic(
            file = file,
            line = line,
            col = 1,
            severity = severity,
            message = message
          )
        case None =>
          // Try Gradle pattern: File.java:line:col: error: message
          // or File.java:line: error: message (col optional)
          val gradlePattern = """^(.+?):(\d+):(\d+):\s*(?:error|warning|info):\s*(.*)$""".r
          gradlePattern.findFirstMatchIn(cleanLine) match
            case Some(m) =>
              val file = m.group(1).trim
              val line = m.group(2).toInt
              val col = m.group(3).toInt
              val message = m.group(4).trim
              // Gradle errors default to ERROR severity; can detect from message if needed
              val severity = if cleanLine.toLowerCase.contains("error") then "ERROR" else "WARN"
              diagnostics += BuildDiagnostic(
                file = file,
                line = line,
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
                  val line = m.group(2).toInt
                  val message = m.group(3).trim
                  val severity = if cleanLine.toLowerCase.contains("error") then "ERROR" else "WARN"
                  diagnostics += BuildDiagnostic(
                    file = file,
                    line = line,
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
    val file = new java.io.File(filePath)
    if !file.exists() then
      throw new Exception(s"File not found: $filePath")
    if !file.isFile() then
      throw new java.io.IOException(s"Not a file: $filePath")

    // Check file size limit (100MB) to prevent OOM
    val MAX_FILE_SIZE = 100 * 1024 * 1024
    if file.length() > MAX_FILE_SIZE then
      throw new java.io.IOException(s"File too large: ${file.length()} bytes (max $MAX_FILE_SIZE)")

    val content = try
      scala.io.Source.fromFile(filePath, "UTF-8").mkString
    catch
      case e: java.nio.charset.MalformedInputException =>
        // Fallback to default charset if UTF-8 fails
        scala.io.Source.fromFile(filePath).mkString
      case e: Exception =>
        throw new Exception(s"Error reading file: ${e.getMessage}")

    parseLog(content)
