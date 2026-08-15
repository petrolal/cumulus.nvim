package cumulus.log

import upickle.default.ReadWriter

/**
 * Represents a diagnostic extracted from a build log (e.g., compiler error).
 *
 * @param file The file path where the diagnostic occurred
 * @param line The line number (1-indexed)
 * @param col The column number (1-indexed), defaults to 1
 * @param severity The severity level (e.g., "ERROR", "WARN")
 * @param message The diagnostic message
 */
case class BuildDiagnostic(
  file: String,
  line: Int,
  col: Int,
  severity: String,
  message: String
) derives ReadWriter

/**
 * Represents a stack frame parsed from a stacktrace.
 *
 * @param className The fully qualified class name
 * @param methodName The method name
 * @param file The source file name
 * @param line The line number in the source file (1-indexed)
 */
case class StackFrame(
  className: String,
  methodName: String,
  file: String,
  line: Int
) derives ReadWriter

/**
 * Represents an indexed log entry (line with severity match).
 *
 * @param lineNumber The line number in the log file (1-indexed)
 * @param severity The severity level (e.g., "ERROR", "WARN", "FATAL", "SEVERE")
 * @param timestamp Optional ISO 8601 timestamp extracted from the line
 * @param message The log message or the entire line
 */
case class LogIndexEntry(
  lineNumber: Int,
  severity: String,
  timestamp: Option[String],
  message: String
) derives ReadWriter
