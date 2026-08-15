package cumulus.devops

import upickle.default.ReadWriter

/**
 * Represents code coverage data for a single source file parsed from JaCoCo XML.
 *
 * @param file The package-qualified relative path to the source file (e.g. "com/example/App.java")
 * @param covered_lines List of 1-indexed line numbers with executed instructions (ci > 0)
 * @param missed_lines List of 1-indexed line numbers with missed instructions (ci == 0 && mi > 0)
 */
case class CoverageEntry(
  file: String,
  covered_lines: Seq[Int],
  missed_lines: Seq[Int]
) derives ReadWriter

/**
 * Represents a diagnostic issue parsed from a Checkstyle XML audit report.
 *
 * @param file The path to the file containing the violation
 * @param line The 1-indexed line number (defaults to 1 if not specified)
 * @param col The 1-indexed column number, or None if omitted
 * @param severity The severity level in uppercase (e.g. "ERROR", "WARNING", "INFO")
 * @param message The Checkstyle rule violation description
 */
case class CheckstyleDiagnostic(
  file: String,
  line: Int,
  col: Option[Int],
  severity: String,
  message: String
) derives ReadWriter
