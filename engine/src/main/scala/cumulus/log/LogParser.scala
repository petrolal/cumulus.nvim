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
   * Parse SBT log format: [error] File.scala:123: message or [warn] File.scala:123: message
   * SBT does not include column numbers in standard output, so default to 1.
   */
  private def parseSbtLog(line: String): Option[BuildDiagnostic] =
    val sbtPattern = """\[(error|warn|warning)\]\s*(.+?):(\d+):\s*(.*)""".r
    sbtPattern.findFirstMatchIn(line) match
      case Some(m) =>
        val severity = if m.group(1) == "warn" || m.group(1) == "warning" then "WARN" else "ERROR"
        val file = m.group(2).trim
        if file.isEmpty then return None
        try
          val lineNum = m.group(3).toInt
          if lineNum <= 0 then return None
          val message = m.group(4).trim
          Some(BuildDiagnostic(
            file = file,
            line = lineNum,
            col = 1,
            severity = severity,
            message = message
          ))
        catch
          case _: NumberFormatException => None
      case None => None

  /**
   * Parse kotlinc log format: File.kt:123:45: error: message
   * Kotlinc uses the same format as Gradle for file:line:col: severity: message
   */
  private def parseKotlinLog(line: String): Option[BuildDiagnostic] =
    val kotlincPattern = """^(.+?\.kt):(\d+):(\d+):\s*(?:error|warning|info):\s*(.*)$""".r
    kotlincPattern.findFirstMatchIn(line) match
      case Some(m) =>
        val file = m.group(1).trim
        if file.isEmpty then return None
        try
          val lineNum = m.group(2).toInt
          val col = m.group(3).toInt
          if lineNum <= 0 || col <= 0 then return None
          val message = m.group(4).trim
          val severity = if line.toLowerCase.contains("error") then "ERROR" else "WARN"
          Some(BuildDiagnostic(
            file = file,
            line = lineNum,
            col = col,
            severity = severity,
            message = message
          ))
        catch
          case _: NumberFormatException => None
      case None => None

  /**
   * Parse scalac log format: File.scala:123: error: message
   * Scalac typically only includes line number, not column; default column to 1.
   */
  private def parseScalaLog(line: String): Option[BuildDiagnostic] =
    val scalacPattern = """^(.+?\.scala):(\d+):\s*(?:error|warning|info):\s*(.*)$""".r
    scalacPattern.findFirstMatchIn(line) match
      case Some(m) =>
        val file = m.group(1).trim
        if file.isEmpty then return None
        try
          val lineNum = m.group(2).toInt
          if lineNum <= 0 then return None
          val message = m.group(3).trim
          val severity = if line.toLowerCase.contains("error") then "ERROR" else "WARN"
          Some(BuildDiagnostic(
            file = file,
            line = lineNum,
            col = 1,
            severity = severity,
            message = message
          ))
        catch
          case _: NumberFormatException => None
      case None => None

  /**
   * Parse Cargo (Rust) log format. Cargo outputs text-based errors as:
   * error[E0425]: cannot find value in this scope
   * --> src/main.rs:10:5
   *
   * This function looks for lines with error[Ennn]: pattern and extracts
   * file/line/col from the following context line (-->).
   * Returns None if no match; context extraction happens in parseLog().
   */
  private def parseCargoLogError(line: String): Option[String] =
    val cargoErrorPattern = """^error\[E\d+\]:\s*(.*)$""".r
    cargoErrorPattern.findFirstMatchIn(line).map(m => m.group(1))

  /**
   * Parse Cargo context line to extract file, line, col: --> path/file.rs:10:5
   */
  private def parseCargoContextLine(line: String): Option[(String, Int, Int)] =
    val contextPattern = """--> (.+?):(\d+):(\d+)""".r
    contextPattern.findFirstMatchIn(line).map(m =>
      (m.group(1).trim, m.group(2).toInt, m.group(3).toInt)
    )

  /**
   * Parse a build log (Maven, Gradle, SBT, kotlinc, scalac, Cargo) and extract diagnostics.
   * Uses language-agnostic auto-detection by scanning pattern signatures.
   * Single pass through lines, testing each against all patterns in priority order.
   *
   * @param logContent The full log content as a string
   * @return Sequence of BuildDiagnostic entries
   */
  def parseLog(logContent: String): Seq[BuildDiagnostic] =
    val lines = logContent.split("(?:\r\n|\r|\n)")
    val diagnostics = mutable.Buffer[BuildDiagnostic]()

    var cargoErrorMessage: Option[String] = None
    var cargoErrorLineIdx = -1

    lines.zipWithIndex.foreach { case (line, idx) =>
      val cleanLine = stripAnsiCodes(line)

      // Clear orphaned Cargo error at EOF if no context line found
      if idx > 0 && cargoErrorMessage.isDefined && idx > cargoErrorLineIdx + 1 then
        cargoErrorMessage = None

      // Try Maven bracket patterns (highest priority):
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
              // Try SBT pattern: [error] File.scala:123: message
              parseSbtLog(cleanLine) match
                case Some(diag) =>
                  diagnostics += diag
                case None =>
                  // Try Gradle/Kotlin pattern: File.java:line:col: error: message
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
                          // Try Cargo error pattern: error[E0425]: message
                          parseCargoLogError(cleanLine) match
                            case Some(msg) =>
                              cargoErrorMessage = Some(msg)
                              cargoErrorLineIdx = idx
                            case None =>
                              // Try Cargo context line: --> path/file.rs:10:5
                              if cargoErrorMessage.isDefined && idx == cargoErrorLineIdx + 1 then
                                parseCargoContextLine(cleanLine) match
                                  case Some((file, lineNum, col)) =>
                                    diagnostics += BuildDiagnostic(
                                      file = file,
                                      line = lineNum,
                                      col = col,
                                      severity = "ERROR",
                                      message = cargoErrorMessage.get
                                    )
                                    cargoErrorMessage = None
                                  case None =>
                                    cargoErrorMessage = None
                              else
                                cargoErrorMessage = None
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

