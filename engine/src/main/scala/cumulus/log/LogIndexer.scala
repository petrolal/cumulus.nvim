package cumulus.log

import scala.io.Source
import scala.util.Try
import scala.collection.mutable
import java.io.File

/**
 * Indexes large log files for severity-level lines.
 * Efficiently scans for ERROR, WARN, FATAL, SEVERE keywords.
 */
object LogIndexer:

  // Severity keywords to match with priority (higher priority = more severe)
  private val SEVERITY_KEYWORDS = Set("ERROR", "WARN", "FATAL", "SEVERE")
  private val SEVERITY_PRIORITY = Map("ERROR" -> 4, "FATAL" -> 3, "SEVERE" -> 2, "WARN" -> 1)

  /**
   * Extract a timestamp from a log line if present.
   * Looks for ISO 8601 format (e.g., 2026-08-14T10:30:45Z) or common syslog formats.
   * Gracefully handles invalid timestamps by returning None.
   *
   * @param line The log line
   * @return Option of extracted timestamp string
   */
  def extractTimestamp(line: String): Option[String] =
    try
      // ISO 8601 pattern: YYYY-MM-DDTHH:MM:SSZ or YYYY-MM-DDTHH:MM:SS+/-HH:MM
      val iso8601Pattern = """(\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:Z|[+-]\d{2}:\d{2})?)""".r
      iso8601Pattern.findFirstMatchIn(line).map(_.group(1))
    catch
      case e: Exception =>
        // Gracefully skip timestamp extraction on error
        None

  /**
   * Check if a line contains a severity keyword.
   * Looks for ERROR, WARN, FATAL, SEVERE as whole words.
   * When multiple severities are present, returns the one that appears first,
   * with severity priority as a tiebreaker.
   *
   * @param line The log line
   * @return Option of the matched severity (first occurrence, prioritized: ERROR > FATAL > SEVERE > WARN)
   */
  def extractSeverity(line: String): Option[String] =
    // Find all matches and return the one that appears first in the line
    val matches = SEVERITY_KEYWORDS.flatMap { severity =>
      val pattern = s"(?i)\\b$severity\\b".r
      pattern.findFirstMatchIn(line).map { m =>
        (m.start, severity.toUpperCase, SEVERITY_PRIORITY.getOrElse(severity.toUpperCase, 0))
      }
    }

    if matches.isEmpty then None
    else
      // Sort by position first (earliest appearance), then by priority level as tiebreaker
      Some(matches.minBy(t => (t._1, -t._3))._2)

  /**
   * Index a log file for severity-level lines.
   * Returns all lines containing ERROR, WARN, FATAL, or SEVERE.
   *
   * @param filePath The path to the log file
   * @return Sequence of LogIndexEntry, or throws exception if file not found
   */
  def indexLogFile(filePath: String): Seq[LogIndexEntry] =
    val file = new File(filePath)
    if !file.exists() then
      throw new Exception(s"File not found: $filePath")

    // Check file size limit (100MB) to prevent OOM
    val MAX_FILE_SIZE = 100 * 1024 * 1024
    if file.length() > MAX_FILE_SIZE then
      throw new java.io.IOException(s"File too large: ${file.length()} bytes (max $MAX_FILE_SIZE)")

    val entries = mutable.Buffer[LogIndexEntry]()
    val source = Source.fromFile(filePath, "UTF-8")

    try
      var lineNumber = 0
      for (line <- source.getLines()) {
        lineNumber += 1
        extractSeverity(line) match
          case Some(severity) =>
            val timestamp = extractTimestamp(line)
            entries += LogIndexEntry(
              lineNumber = lineNumber,
              severity = severity.toUpperCase,
              timestamp = timestamp,
              message = line
            )
          case None =>
            // Not a matching severity line, skip
      }
    finally
      source.close()

    entries.toSeq

  /**
   * Index log content from a string (stdin input).
   *
   * @param content The full log content as a string
   * @return Sequence of LogIndexEntry
   */
  def indexLogContent(content: String): Seq[LogIndexEntry] =
    val lines = content.split("(?:\r\n|\r|\n)")
    val entries = mutable.Buffer[LogIndexEntry]()

    lines.zipWithIndex.foreach { case (line, idx) =>
      val lineNumber = idx + 1
      extractSeverity(line) match
        case Some(severity) =>
          val timestamp = extractTimestamp(line)
          entries += LogIndexEntry(
            lineNumber = lineNumber,
            severity = severity.toUpperCase,
            timestamp = timestamp,
            message = line
          )
        case None =>
          // Not a matching severity line, skip
    }

    entries.toSeq
