package cumulus.toolchain

import cumulus.protocol.FormatterInfo
import os.Path
import scala.util.Try
import scala.sys.process._

/**
 * Discovers available formatters for various languages.
 * Validates formatter CLI availability and returns specs.
 */
object FormatterDetector:

  /**
   * Detect all available formatters for a given language.
   *
   * @param language The target language (java, kotlin, scala, go, rust, python, node)
   * @return Seq[FormatterInfo] of detected formatters
   */
  def detectFormatters(language: String): Seq[FormatterInfo] =
    language.toLowerCase match
      case "java" => detectJavaFormatters()
      case "kotlin" => detectKotlinFormatters()
      case "scala" => detectScalaFormatters()
      case "go" => detectGoFormatters()
      case "rust" => detectRustFormatters()
      case "python" => detectPythonFormatters()
      case "node" | "typescript" | "javascript" => detectNodeFormatters()
      case _ => Seq()

  /**
   * Detect Java formatters (google-java-format, spotless, etc.)
   */
  private def detectJavaFormatters(): Seq[FormatterInfo] =
    val formatters = scala.collection.mutable.ListBuffer[FormatterInfo]()

    // Detect google-java-format
    if isToolAvailable("google-java-format") then
      val version = getToolVersion("google-java-format", "--version")
      formatters += FormatterInfo(
        name = "google-java-format",
        mason_name = "google-java-format",
        version = version,
        cli_args = Seq("--replace")
      )

    formatters.toSeq

  /**
   * Detect Kotlin formatters (ktfmt, ktlint, etc.)
   */
  private def detectKotlinFormatters(): Seq[FormatterInfo] =
    val formatters = scala.collection.mutable.ListBuffer[FormatterInfo]()

    // Detect ktfmt
    if isToolAvailable("ktfmt") then
      val version = getToolVersion("ktfmt", "--version")
      formatters += FormatterInfo(
        name = "ktfmt",
        mason_name = "ktfmt",
        version = version,
        cli_args = Seq()
      )

    // Detect ktlint
    if isToolAvailable("ktlint") then
      val version = getToolVersion("ktlint", "--version")
      formatters += FormatterInfo(
        name = "ktlint",
        mason_name = "ktlint",
        version = version,
        cli_args = Seq("--format")
      )

    formatters.toSeq

  /**
   * Detect Scala formatters (scalafmt, etc.)
   */
  private def detectScalaFormatters(): Seq[FormatterInfo] =
    val formatters = scala.collection.mutable.ListBuffer[FormatterInfo]()

    // Detect scalafmt
    if isToolAvailable("scalafmt") then
      val version = getToolVersion("scalafmt", "--version")
      formatters += FormatterInfo(
        name = "scalafmt",
        mason_name = "scalafmt",
        version = version,
        cli_args = Seq()
      )

    formatters.toSeq

  /**
   * Detect Go formatters (gofmt, goimports, etc.)
   */
  private def detectGoFormatters(): Seq[FormatterInfo] =
    val formatters = scala.collection.mutable.ListBuffer[FormatterInfo]()

    // Detect gofmt (built-in with go)
    if isToolAvailable("go") then
      formatters += FormatterInfo(
        name = "gofmt",
        mason_name = "gofmt",
        version = Some("builtin"),
        cli_args = Seq("-w")
      )

    // Detect goimports
    if isToolAvailable("goimports") then
      val version = getToolVersion("goimports", "-h")
      formatters += FormatterInfo(
        name = "goimports",
        mason_name = "goimports",
        version = version,
        cli_args = Seq("-w")
      )

    formatters.toSeq

  /**
   * Detect Rust formatters (rustfmt, etc.)
   */
  private def detectRustFormatters(): Seq[FormatterInfo] =
    val formatters = scala.collection.mutable.ListBuffer[FormatterInfo]()

    // Detect rustfmt
    if isToolAvailable("rustfmt") then
      val version = getToolVersion("rustfmt", "--version")
      formatters += FormatterInfo(
        name = "rustfmt",
        mason_name = "rustfmt",
        version = version,
        cli_args = Seq()
      )

    formatters.toSeq

  /**
   * Detect Python formatters (black, autopep8, etc.)
   */
  private def detectPythonFormatters(): Seq[FormatterInfo] =
    val formatters = scala.collection.mutable.ListBuffer[FormatterInfo]()

    // Detect black
    if isToolAvailable("black") then
      val version = getToolVersion("black", "--version")
      formatters += FormatterInfo(
        name = "black",
        mason_name = "black",
        version = version,
        cli_args = Seq()
      )

    // Detect autopep8
    if isToolAvailable("autopep8") then
      val version = getToolVersion("autopep8", "--version")
      formatters += FormatterInfo(
        name = "autopep8",
        mason_name = "autopep8",
        version = version,
        cli_args = Seq("-i")
      )

    formatters.toSeq

  /**
   * Detect Node.js formatters (prettier, etc.)
   */
  private def detectNodeFormatters(): Seq[FormatterInfo] =
    val formatters = scala.collection.mutable.ListBuffer[FormatterInfo]()

    // Detect prettier
    if isToolAvailable("prettier") then
      val version = getToolVersion("prettier", "--version")
      formatters += FormatterInfo(
        name = "prettier",
        mason_name = "prettier",
        version = version,
        cli_args = Seq("--write")
      )

    // Detect eslint
    if isToolAvailable("eslint") then
      val version = getToolVersion("eslint", "--version")
      formatters += FormatterInfo(
        name = "eslint",
        mason_name = "eslint",
        version = version,
        cli_args = Seq("--fix")
      )

    formatters.toSeq

  /**
   * Check if a tool is available in PATH.
   */
  private def isToolAvailable(tool: String): Boolean =
    Try {
      s"which $tool".!!
      true
    }.isSuccess

  /**
   * Get tool version by running version query.
   */
  private def getToolVersion(tool: String, flag: String): Option[String] =
    Try {
      val cmd = s"$tool $flag"
      val output = cmd.!!
      Some(output.trim)
    }.getOrElse(None)
