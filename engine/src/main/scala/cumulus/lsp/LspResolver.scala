package cumulus.lsp

import cumulus.protocol.{CumulusResponse, CumulusError, LspSpec, LspCapability, TreeSitterParserSpec, MasonPackageSpec}
import cumulus.toolchain.LanguageServerDetector
import scala.sys.process._

/**
 * Orchestrates unified LSP and TreeSitter configuration detection.
 * Detects installed language servers, resolves capabilities, discovers TreeSitter parsers,
 * and returns complete LSP specification for Lua bridge integration.
 */
object LspResolver:

  /**
   * Resolve complete LSP configuration for a given language.
   *
   * @param language The target language (java, kotlin, scala, groovy)
   * @return CumulusResponse[LspSpec] with all LSP and TreeSitter data
   */
  def handle(language: String): CumulusResponse[LspSpec] =
    try
      // Guard against null/empty inputs
      if language == null || language.trim.isEmpty then
        return CumulusResponse(
          success = false,
          data = None,
          error = Some("Language argument is required"),
          error_code = Some("INVALID_INPUT")
        )

      val normalizedLang = language.trim.toLowerCase

      // Validate language support
      if !LspLanguageConfig.isSupported(normalizedLang) then
        return CumulusResponse(
          success = false,
          data = None,
          error = Some(s"Language '$normalizedLang' is not currently supported"),
          error_code = Some("UNSUPPORTED_LANGUAGE")
        )

      // 1. Detect installed language servers
      val detectedServers = LanguageServerDetector.detectLanguageServers(normalizedLang)

      // 2. Pick the first detected server (or None if not found)
      val (lspName, lspPath) = detectedServers.headOption match
        case Some(server) =>
          (Some(server.name), Some(detectLspPath(server.name)))
        case None =>
          (None, None)

      // 3. Detect LSP capabilities
      val capabilities = lspName match
        case Some(name) =>
          LspCapabilityDetector.detectCapabilities(name, normalizedLang)
        case None =>
          // Use default capabilities for the language if no LSP is detected
          LspCapabilityDetector.getDefaultCapabilities(normalizedLang)

      // 4. Discover TreeSitter parsers
      val treeSitterParsers = TreeSitterResolver.discoverParsers(normalizedLang)

      // 5. Build notes for missing components
      val notesBuilder = Seq.newBuilder[String]
      if detectedServers.isEmpty then
        notesBuilder += s"No LSP found for language '$normalizedLang'; install via Mason or system package manager"
      if treeSitterParsers.isEmpty then
        notesBuilder += s"TreeSitter parser for '$normalizedLang' not available; install via :TSInstall"
      val notes = notesBuilder.result()
      val finalNotes = if notes.nonEmpty then Some(notes) else None

      // 6. Generate Mason package spec
      val masonSpec = lspName.map { name =>
        generateMasonSpec(name, normalizedLang, detectedServers.headOption.flatMap(_.version))
      }

      CumulusResponse(
        success = true,
        data = Some(LspSpec(
          language = normalizedLang,
          lsp_name = lspName,
          lsp_path = lspPath,
          capabilities = capabilities,
          binary_valid = lspPath.isDefined,
          treesitter = treeSitterParsers,
          mason_spec = masonSpec,
          notes = finalNotes
        )),
        error = None,
        error_code = None
      )
    catch
      case e: Exception =>
        CumulusResponse(
          success = false,
          data = None,
          error = Some(s"Error resolving LSP configuration: ${e.getMessage}"),
          error_code = Some("INTERNAL_ERROR")
        )

  /**
   * Detect the path to an installed LSP binary.
   * Searches in standard locations: Mason package directory, Mason bin cache, $PATH, system paths.
   * Validates that the path exists and is a file before returning.
   * Retries on transient failures.
   */
  private def detectLspPath(lspName: String): String =
    retryWithBackoff(() => detectLspPathOnce(lspName), maxRetries = 2)

  /**
   * Single attempt to detect LSP path.
   */
  private def detectLspPathOnce(lspName: String): String =
    try
      val home = System.getProperty("user.home")

      // Standard Mason paths
      val masonPackagePath = s"$home/.local/share/nvim/mason/packages/$lspName/bin/$lspName"
      val masonBinPath = s"$home/.local/share/nvim/mason/bin/$lspName"

      // Check Mason package directory (file must exist)
      val pkgPath = os.Path(masonPackagePath)
      if os.exists(pkgPath) && os.isFile(pkgPath) then
        return masonPackagePath

      // Check Mason bin directory (file must exist)
      val binPath = os.Path(masonBinPath)
      if os.exists(binPath) && os.isFile(binPath) then
        return masonBinPath

      // Check $PATH
      try
        val pathOutput = s"which ${lspName.replaceAll("([^a-zA-Z0-9._-])", "\\\\$1")}".!!.trim
        if pathOutput.nonEmpty then
          return pathOutput
      catch
        case _: Exception => ()

      // Default: return empty string if not found
      ""
    catch
      case _: Exception => ""

  /**
   * Retry a function with exponential backoff on failure.
   * Returns empty string if all retries exhausted.
   */
  private def retryWithBackoff[T](fn: () => T, maxRetries: Int): T =
    var lastError: Option[Exception] = None
    var attempt = 0

    while attempt <= maxRetries do
      try
        return fn()
      catch
        case e: Exception =>
          lastError = Some(e)
          attempt += 1
          if attempt <= maxRetries then
            Thread.sleep(math.pow(2, attempt - 1).toLong * 10)  // exponential backoff

    // All retries exhausted, return fallback
    lastError match
      case Some(e) => throw e
      case None => fn()  // unreachable but satisfies type system

  /**
   * Generate a Mason package spec for the detected LSP.
   */
  private def generateMasonSpec(
    lspName: String,
    language: String,
    version: Option[String]
  ): MasonPackageSpec =
    val minVersion = LspLanguageConfig.getMinVersion(language)
    val tags = Seq("lsp", language)

    MasonPackageSpec(
      name = lspName,
      mason_name = lspName,
      min_version = minVersion,
      installation_method = "mason",
      tags = tags
    )
