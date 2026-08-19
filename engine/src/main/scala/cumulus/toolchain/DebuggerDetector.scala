package cumulus.toolchain

import cumulus.protocol.DebuggerInfo
import os.Path
import scala.util.Try
import scala.sys.process._

/**
 * Discovers available debuggers for various languages.
 * Returns debugger specs for DAP (Debug Adapter Protocol) configuration.
 */
object DebuggerDetector:

  /**
   * Detect all available debuggers for a given language.
   *
   * @param language The target language (java, kotlin, scala, go, rust, python, node)
   * @return Seq[DebuggerInfo] of detected debuggers
   */
  def detectDebuggers(language: String): Seq[DebuggerInfo] =
    language.toLowerCase match
      case "java" => detectJavaDebuggers()
      case "kotlin" => detectKotlinDebuggers()
      case "scala" => detectScalaDebuggers()
      case "go" => detectGoDebuggers()
      case "rust" => detectRustDebuggers()
      case "python" => detectPythonDebuggers()
      case "node" | "typescript" | "javascript" => detectNodeDebuggers()
      case _ => Seq()

  /**
   * Detect Java debuggers (java-debug-adapter, etc.)
   */
  private def detectJavaDebuggers(): Seq[DebuggerInfo] =
    val debuggers = scala.collection.mutable.ListBuffer[DebuggerInfo]()

    // Check if java-debug-adapter is installed via Mason
    if isDebuggerInstalled("java-debug-adapter") || isDebuggerInstalled("java-debug") then
      debuggers += DebuggerInfo(
        name = "java-debug",
        mason_name = "java-debug-adapter",
        version = None,
        status = "available"
      )

    // Check if vscode-java-test is installed
    if isDebuggerInstalled("vscode-java-test") then
      debuggers += DebuggerInfo(
        name = "vscode-java-test",
        mason_name = "vscode-java-test",
        version = None,
        status = "available"
      )

    debuggers.toSeq

  /**
   * Detect Kotlin debuggers (java-debug-adapter, etc.)
   * Kotlin uses the same debuggers as Java.
   */
  private def detectKotlinDebuggers(): Seq[DebuggerInfo] =
    detectJavaDebuggers()

  /**
   * Detect Scala debuggers (java-debug-adapter, scala-debugger, etc.)
   */
  private def detectScalaDebuggers(): Seq[DebuggerInfo] =
    val debuggers = scala.collection.mutable.ListBuffer[DebuggerInfo]()

    // Scala uses Java debuggers
    debuggers ++= detectJavaDebuggers()

    debuggers.toSeq

  /**
   * Detect Go debuggers (Delve, etc.)
   */
  private def detectGoDebuggers(): Seq[DebuggerInfo] =
    val debuggers = scala.collection.mutable.ListBuffer[DebuggerInfo]()

    // Detect Delve (dlv)
    if isToolAvailable("dlv") then
      val version = getToolVersion("dlv", "version")
      debuggers += DebuggerInfo(
        name = "delve",
        mason_name = "delve",
        version = version,
        status = "available"
      )

    debuggers.toSeq

  /**
   * Detect Rust debuggers (LLDB, etc.)
   */
  private def detectRustDebuggers(): Seq[DebuggerInfo] =
    val debuggers = scala.collection.mutable.ListBuffer[DebuggerInfo]()

    // Detect LLDB
    if isToolAvailable("lldb") || isToolAvailable("lldb-mi") then
      val toolName = if isToolAvailable("lldb") then "lldb" else "lldb-mi"
      val version = getToolVersion(toolName, "--version")
      debuggers += DebuggerInfo(
        name = "lldb",
        mason_name = "lldb-vscode",
        version = version,
        status = "available"
      )

    // Detect CodeLLDB (VSCode LLDB debugger)
    if isDebuggerInstalled("codelldb") then
      debuggers += DebuggerInfo(
        name = "codelldb",
        mason_name = "codelldb",
        version = None,
        status = "available"
      )

    debuggers.toSeq

  /**
   * Detect Python debuggers (debugpy, etc.)
   */
  private def detectPythonDebuggers(): Seq[DebuggerInfo] =
    val debuggers = scala.collection.mutable.ListBuffer[DebuggerInfo]()

    // Detect debugpy (commonly used with LSP)
    if isPythonPackageInstalled("debugpy") then
      debuggers += DebuggerInfo(
        name = "debugpy",
        mason_name = "debugpy",
        version = None,
        status = "available"
      )

    debuggers.toSeq

  /**
   * Detect Node.js debuggers (node-debug2, vscode-node-debug, etc.)
   */
  private def detectNodeDebuggers(): Seq[DebuggerInfo] =
    val debuggers = scala.collection.mutable.ListBuffer[DebuggerInfo]()

    // Detect node debugger tools
    if isDebuggerInstalled("node-debug2-adapter") || isDebuggerInstalled("node-debug") then
      debuggers += DebuggerInfo(
        name = "node-debug",
        mason_name = "node-debug2-adapter",
        version = None,
        status = "available"
      )

    // Detect vscode-js-debug
    if isDebuggerInstalled("js-debug-adapter") then
      debuggers += DebuggerInfo(
        name = "js-debug",
        mason_name = "js-debug-adapter",
        version = None,
        status = "available"
      )

    debuggers.toSeq

  /**
   * Check if a debugger is installed via Mason.
   */
  private def isDebuggerInstalled(debugger: String): Boolean =
    Try {
      val masonDataPath = s"${System.getProperty("user.home")}/.local/share/nvim/mason/packages"
      val masonPath = Path(masonDataPath)
      if os.exists(masonPath) && os.isDir(masonPath) then
        os.list(masonPath).exists(_.last.contains(debugger))
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
   * Check if a Python package is installed.
   */
  private def isPythonPackageInstalled(packageName: String): Boolean =
    Try {
      val cmd = s"python3 -m pip show $packageName"
      cmd.!!
      true
    }.isSuccess || Try {
      val cmd = s"python -m pip show $packageName"
      cmd.!!
      true
    }.isSuccess
