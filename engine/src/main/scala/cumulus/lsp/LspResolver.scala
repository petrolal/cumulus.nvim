package cumulus.lsp

import cumulus.protocol.{CumulusResponse, CumulusError, LspSpec, LspCapability, TreeSitterParserSpec, MasonPackageSpec}
import cumulus.toolchain.LanguageServerDetector
import scala.collection.mutable
import scala.sys.process._
import scala.util.Try

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
      if !Seq("java", "kotlin", "scala", "groovy").contains(normalizedLang) then
        return CumulusResponse(
          success = false,
          data = None,
          error = Some(s"Language '$normalizedLang' not supported in this story; defer to future"),
          error_code = Some("UNSUPPORTED_LANGUAGE")
        )

      val notes = mutable.ListBuffer[String]()

      // 1. Detect installed language servers
      val detectedServers = LanguageServerDetector.detectLanguageServers(normalizedLang)

      if detectedServers.isEmpty then
        notes += s"No LSP found for language '$normalizedLang'; install via Mason or system package manager"

      // 2. Pick the first detected server (or None if not found)
      val (lspName, lspPath) = detectedServers.headOption match
        case Some(server) =>
          (Some(server.name), server.version.flatMap(_ => Some(detectLspPath(server.name))))
        case None =>
          (None, None)

      // 3. Detect LSP capabilities
      val capabilities = lspName match
        case Some(name) =>
          LspCapabilityDetector.detectCapabilities(name, normalizedLang)
        case None =>
          Seq()

      // 4. Discover TreeSitter parsers
      val treeSitterParsers = TreeSitterResolver.discoverParsers(normalizedLang)
      if treeSitterParsers.isEmpty then
        notes += s"TreeSitter parser for '$normalizedLang' not available; install via :TSInstall"

      // 5. Generate Mason package spec
      val masonSpec = lspName.map { name =>
        generateMasonSpec(name, normalizedLang, detectedServers.headOption.flatMap(_.version))
      }

      // Finalize notes
      val finalNotes = if notes.nonEmpty then Some(notes.toSeq) else None

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
   */
  private def detectLspPath(lspName: String): String =
    try
      val home = System.getProperty("user.home")

      // Standard Mason paths
      val masonPackagePath = s"$home/.local/share/nvim/mason/packages/$lspName/bin/$lspName"
      val masonBinPath = s"$home/.local/share/nvim/mason/bin/$lspName"

      // Check Mason package directory
      if os.exists(os.Path(masonPackagePath)) then
        return masonPackagePath

      // Check Mason bin directory
      if os.exists(os.Path(masonBinPath)) then
        return masonBinPath

      // Check $PATH
      try
        val pathOutput = s"which $lspName".!!.trim
        if pathOutput.nonEmpty then
          return pathOutput
      catch
        case _: Exception => ()

      // Default to the first found location or empty string
      masonBinPath
    catch
      case _: Exception => ""

  /**
   * Generate a Mason package spec for the detected LSP.
   */
  private def generateMasonSpec(
    lspName: String,
    language: String,
    version: Option[String]
  ): MasonPackageSpec =
    val minVersion = language match
      case "java" => "1.0"
      case "kotlin" => "1.0"
      case "scala" => "0.11"
      case "groovy" => "1.0"
      case _ => "1.0"

    val tags = Seq("lsp", language)

    MasonPackageSpec(
      name = lspName,
      mason_name = lspName,
      min_version = minVersion,
      installation_method = "mason",
      tags = tags
    )
