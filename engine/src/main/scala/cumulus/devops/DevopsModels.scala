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

/**
 * Represents an issue found when validating Flyway database migrations.
 *
 * @param file The filename of the migration script
 * @param line Optional 1-indexed line number where the issue occurs
 * @param severity Severity of the issue ("ERROR" | "WARN")
 * @param message Description of the validation issue
 */
case class MigrationIssue(
  file: String,
  line: Option[Int] = None,
  severity: String,
  message: String
) derives ReadWriter

/**
 * Represents a validation issue in a Kubernetes YAML manifest.
 *
 * @param line 1-indexed line number of the issue
 * @param col Optional 1-indexed column number
 * @param severity Severity level (defaults to "ERROR")
 * @param message Description of the validation issue
 */
case class K8sValidationIssue(
  line: Int,
  col: Option[Int] = None,
  severity: String = "ERROR",
  message: String
) derives ReadWriter

/**
 * Result of sanitizing a .vim session file.
 */
case class SessionSanitizeResult(
  success: Boolean,
  cleaned_lines: Int,
  total_lines: Int
) derives ReadWriter

/**
 * Status of Gradle wrapper and CI configuration verification.
 */
case class GradleWrapperStatus(
  local_version: Option[String],
  ci_version: Option[String],
  sha256_configured: Boolean,
  sha256_valid: Boolean,
  issues: Seq[String]
) derives ReadWriter

/**
 * Network socket connectivity status.
 */
case class NetworkStatus(
  connected: Boolean,
  host: String,
  port: Int,
  elapsed_ms: Long
) derives ReadWriter

/**
 * JDTLS classpath synchronization status.
 */
case class SyncStatus(
  sync_needed: Boolean,
  modified_file: Option[String]
) derives ReadWriter

/**
 * Project dependency information.
 */
case class DependencyInfo(
  group: String,
  artifact: String,
  version: String,
  scope: String
) derives ReadWriter

/**
 * Dependency version lens information for inlay/virtual text hints.
 */
case class DependencyLens(
  group: String,
  artifact: String,
  current_version: String,
  latest_version: String,
  line: Int,
  age_status: String
) derives ReadWriter


