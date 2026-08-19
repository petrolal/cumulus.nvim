package cumulus.toolchain

import cumulus.protocol.{CumulusResponse, CumulusError, ToolchainMatrix}
import scala.collection.mutable

/**
 * Orchestrates complete toolchain matrix generation for a target language.
 * Combines tool detection, formatter discovery, LSP detection, and debugger detection
 * into a single comprehensive response.
 *
 * Follows the same architecture as JdtlsConfigResolver (Story 16.1) and
 * KeymapGateEvaluator (Story 16.2): detect -> validate -> assemble -> respond.
 */
object ToolchainMatrixGenerator:

  // Supported languages
  private val SUPPORTED_LANGUAGES = Set("java", "kotlin", "scala", "go", "rust", "python", "node", "typescript", "javascript")

  /**
   * Generate complete toolchain matrix for a target language.
   *
   * @param language The target language (java, kotlin, scala, go, rust, python, node)
   * @return CumulusResponse[ToolchainMatrix] with complete toolchain info or error
   */
  def handle(language: String): CumulusResponse[ToolchainMatrix] =
    try
      val lang = language.toLowerCase.trim

      // Validate language is supported
      if !SUPPORTED_LANGUAGES.contains(lang) then
        return CumulusResponse(
          success = false,
          data = None,
          error = Some(s"Language '$lang' not supported. Supported languages: ${SUPPORTED_LANGUAGES.toList.sorted.mkString(", ")}"),
          error_code = Some("UNSUPPORTED_LANGUAGE")
        )

      // Detect native tools for language
      val tools = ToolDetector.detectTools(lang)
      if tools.isEmpty then
        return CumulusResponse(
          success = false,
          data = None,
          error = Some(s"No ${lang.capitalize} toolchain detected; please install the ${lang.capitalize} compiler/interpreter"),
          error_code = Some("NO_TOOLS_FOUND")
        )

      val availableToolNames = tools.map(_.name)

      // Extract primary compiler version for compatibility checking
      val compilerVersion = tools.headOption.flatMap(_.version)

      // Detect formatters for language
      val formatters = FormatterDetector.detectFormatters(lang)

      // Detect language servers for language
      val languageServers = LanguageServerDetector.detectLanguageServers(lang, compilerVersion)

      // Detect debuggers for language
      val debuggers = DebuggerDetector.detectDebuggers(lang)

      // Separate build tools from other tools
      val buildTools = tools.filter { t =>
        val name = t.name.toLowerCase
        name == "maven" || name == "gradle" || name == "sbt" || name == "cargo" || name == "go" || name == "rustc"
      }

      // Collect diagnostic warnings for version incompatibilities
      val warnings = mutable.ListBuffer[String]()

      // Check for version incompatibilities
      languageServers.foreach { lsp =>
        if lsp.status == "incompatible" then
          lsp.diagnostic_message.foreach { msg =>
            warnings += msg
          }
      }

      debuggers.foreach { dbg =>
        if dbg.status == "incompatible" then
          dbg.diagnostic_message.foreach { msg =>
            warnings += msg
          }
      }

      // Check for partially detected toolchain
      if formatters.isEmpty && languageServers.isEmpty then
        warnings += s"No formatters or language servers detected for $lang. Consider installing one for better IDE experience."

      // Assemble complete matrix
      val matrix = ToolchainMatrix(
        language = lang,
        available_tools = availableToolNames,
        build_tools = buildTools,
        formatters = formatters,
        language_servers = languageServers,
        debuggers = debuggers,
        diagnostic_warnings = warnings.toSeq
      )

      CumulusResponse(
        success = true,
        data = Some(matrix),
        error = None,
        error_code = None
      )
    catch
      case e: Exception =>
        CumulusResponse(
          success = false,
          data = None,
          error = Some(s"Error generating toolchain matrix: ${e.getMessage}"),
          error_code = Some("INTERNAL_ERROR")
        )
