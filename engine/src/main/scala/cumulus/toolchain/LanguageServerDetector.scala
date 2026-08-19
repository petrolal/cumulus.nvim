package cumulus.toolchain

import cumulus.protocol.LanguageServerInfo
import cumulus.workspace.JdtlsDetector
import os.Path
import scala.util.Try
import scala.sys.process._

/**
 * Detects installed language servers and validates version compatibility
 * with detected compilers.
 */
object LanguageServerDetector:

  /**
   * Detect all available language servers for a given language.
   *
   * @param language The target language (java, kotlin, scala, go, rust, python, node)
   * @param compilerVersion The version of the detected compiler (for compatibility checks)
   * @return Seq[LanguageServerInfo] of detected LSPs with compatibility status
   */
  def detectLanguageServers(language: String, compilerVersion: Option[String] = None): Seq[LanguageServerInfo] =
    language.toLowerCase match
      case "java" => detectJavaLanguageServers(compilerVersion)
      case "kotlin" => detectKotlinLanguageServers(compilerVersion)
      case "scala" => detectScalaLanguageServers(compilerVersion)
      case "go" => detectGoLanguageServers(compilerVersion)
      case "rust" => detectRustLanguageServers(compilerVersion)
      case "python" => detectPythonLanguageServers(compilerVersion)
      case "node" | "typescript" | "javascript" => detectNodeLanguageServers(compilerVersion)
      case _ => Seq()

  /**
   * Detect Java language servers (JDTLS, etc.)
   */
  private def detectJavaLanguageServers(compilerVersion: Option[String]): Seq[LanguageServerInfo] =
    val servers = scala.collection.mutable.ListBuffer[LanguageServerInfo]()

    // Detect JDTLS via Mason or system path
    if JdtlsDetector.detectJdtls().isDefined then
      val status = compilerVersion match
        case Some(ver) => validateJavaVersion(ver) match
          case true => "compatible"
          case false => "incompatible"
        case None => "unknown"

      val diagnostic = if status == "incompatible" then
        Some(s"JDTLS requires Java >= 11, found $compilerVersion")
      else
        None

      servers += LanguageServerInfo(
        name = "jdtls",
        mason_name = "jdtls",
        min_version = "1.0",
        version = None,
        status = status,
        diagnostic_message = diagnostic
      )

    servers.toSeq

  /**
   * Detect Kotlin language servers (kotlin-language-server, etc.)
   */
  private def detectKotlinLanguageServers(compilerVersion: Option[String]): Seq[LanguageServerInfo] =
    val servers = scala.collection.mutable.ListBuffer[LanguageServerInfo]()

    // Detect kotlin-language-server
    if isLspInstalled("kotlin-language-server") then
      servers += LanguageServerInfo(
        name = "kotlin-language-server",
        mason_name = "kotlin-language-server",
        min_version = "1.0",
        version = getLspVersion("kotlin-language-server"),
        status = "compatible"
      )

    servers.toSeq

  /**
   * Detect Scala language servers (Metals, etc.)
   */
  private def detectScalaLanguageServers(compilerVersion: Option[String]): Seq[LanguageServerInfo] =
    val servers = scala.collection.mutable.ListBuffer[LanguageServerInfo]()

    // Detect Metals
    if isLspInstalled("metals") || isToolAvailable("coursier") then
      servers += LanguageServerInfo(
        name = "metals",
        mason_name = "metals",
        min_version = "0.11",
        version = getLspVersion("metals"),
        status = "compatible"
      )

    servers.toSeq

  /**
   * Detect Go language servers (gopls, etc.)
   */
  private def detectGoLanguageServers(compilerVersion: Option[String]): Seq[LanguageServerInfo] =
    val servers = scala.collection.mutable.ListBuffer[LanguageServerInfo]()

    // Detect gopls
    if isToolAvailable("gopls") then
      val goplsVersion = getToolVersion("gopls", "version")
      val status = compilerVersion match
        case Some(ver) => validateGoVersion(ver) match
          case true => "compatible"
          case false => "incompatible"
        case None => "unknown"

      val diagnostic = if status == "incompatible" then
        Some(s"gopls requires Go >= 1.13, found $compilerVersion")
      else
        None

      servers += LanguageServerInfo(
        name = "gopls",
        mason_name = "gopls",
        min_version = "0.9",
        version = goplsVersion,
        status = status,
        diagnostic_message = diagnostic
      )

    servers.toSeq

  /**
   * Detect Rust language servers (rust-analyzer, etc.)
   */
  private def detectRustLanguageServers(compilerVersion: Option[String]): Seq[LanguageServerInfo] =
    val servers = scala.collection.mutable.ListBuffer[LanguageServerInfo]()

    // Detect rust-analyzer
    if isToolAvailable("rust-analyzer") then
      servers += LanguageServerInfo(
        name = "rust-analyzer",
        mason_name = "rust-analyzer",
        min_version = "2021-01-01",
        version = getToolVersion("rust-analyzer", "--version"),
        status = "compatible"
      )

    servers.toSeq

  /**
   * Detect Python language servers (pylsp, pyright, etc.)
   */
  private def detectPythonLanguageServers(compilerVersion: Option[String]): Seq[LanguageServerInfo] =
    val servers = scala.collection.mutable.ListBuffer[LanguageServerInfo]()

    // Detect pylsp
    if isToolAvailable("pylsp") then
      servers += LanguageServerInfo(
        name = "pylsp",
        mason_name = "pylsp",
        min_version = "1.0",
        version = getToolVersion("pylsp", "--version"),
        status = "compatible"
      )

    // Detect pyright
    if isToolAvailable("pyright") then
      servers += LanguageServerInfo(
        name = "pyright",
        mason_name = "pyright",
        min_version = "1.0",
        version = getToolVersion("pyright", "--version"),
        status = "compatible"
      )

    servers.toSeq

  /**
   * Detect Node.js language servers (typescript-language-server, eslint-language-server, etc.)
   */
  private def detectNodeLanguageServers(compilerVersion: Option[String]): Seq[LanguageServerInfo] =
    val servers = scala.collection.mutable.ListBuffer[LanguageServerInfo]()

    // Detect typescript-language-server
    if isToolAvailable("typescript-language-server") then
      servers += LanguageServerInfo(
        name = "typescript-language-server",
        mason_name = "typescript-language-server",
        min_version = "1.0",
        version = getToolVersion("typescript-language-server", "--version"),
        status = "compatible"
      )

    // Detect eslint-language-server
    if isToolAvailable("eslint_d") || isToolAvailable("eslint") then
      servers += LanguageServerInfo(
        name = "eslint",
        mason_name = "eslint-language-server",
        min_version = "1.0",
        version = getToolVersion("eslint", "--version"),
        status = "compatible"
      )

    servers.toSeq

  /**
   * Validate if Java version meets minimum requirement for JDTLS.
   */
  private def validateJavaVersion(version: String): Boolean =
    val majorVersion = version.split("\\.")(0).toIntOption.getOrElse(0)
    majorVersion >= 11

  /**
   * Validate if Go version meets minimum requirement for gopls.
   */
  private def validateGoVersion(version: String): Boolean =
    val parts = version.split("\\.")
    if parts.length < 2 then return false

    val major = parts(0).toIntOption.getOrElse(0)
    val minor = parts(1).toIntOption.getOrElse(0)

    major > 1 || (major == 1 && minor >= 13)

  /**
   * Check if an LSP is installed (typically in Mason data directory).
   */
  private def isLspInstalled(lsp: String): Boolean =
    Try {
      val masonDataPath = s"${System.getProperty("user.home")}/.local/share/nvim/mason/bin"
      val masonPath = Path(masonDataPath)
      if os.exists(masonPath) && os.isDir(masonPath) then
        os.list(masonPath).exists(_.last.contains(lsp))
      else
        false
    }.getOrElse(false)

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

  /**
   * Get LSP version from Mason metadata or tool version query.
   */
  private def getLspVersion(lsp: String): Option[String] =
    getToolVersion(lsp, "--version")
