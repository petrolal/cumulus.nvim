package cumulus.lsp

import cumulus.protocol.LspCapability

/**
 * Detects LSP capabilities for supported language servers.
 * Single source of truth for canonical capabilities per language,
 * with optional LSP-specific enhancements.
 */
object LspCapabilityDetector:

  /**
   * Get capabilities for a language (with optional LSP-specific enhancements).
   * Serves as the canonical source—used by both detectCapabilities and getDefaultCapabilities.
   *
   * @param language The target language
   * @param lspName Optional LSP name for additional enhancements
   * @return Seq[LspCapability] of all supported capabilities
   */
  def getCapabilities(language: String, lspName: Option[String] = None): Seq[LspCapability] =
    if language == null || language.trim.isEmpty then
      return Seq()

    val normalized = language.toLowerCase
    val canonical = normalized match
      case "java" => javaCapabilities()
      case "kotlin" => kotlinCapabilities()
      case "scala" => scalaCapabilities()
      case "groovy" => groovyCapabilities()
      case _ => Seq()

    // Add LSP-specific enhancements if available
    lspName match
      case Some(name) if name != null && name.trim.nonEmpty =>
        canonical ++ getLspEnhancements(name, language)
      case _ => canonical

  /**
   * Get all capabilities for a given LSP server.
   * Only returns capabilities if the LSP is recognized for that language.
   *
   * @param lspName The name of the LSP server (jdtls, metals, kotlin-language-server, etc.)
   * @param language The language being configured
   * @return Seq[LspCapability] of all supported capabilities
   */
  def detectCapabilities(lspName: String, language: String): Seq[LspCapability] =
    if lspName == null || lspName.trim.isEmpty then
      return Seq()
    if language == null || language.trim.isEmpty then
      return Seq()

    // Only return capabilities if this LSP is recognized for this language
    if isKnownLsp(lspName, language) then
      getCapabilities(language, Some(lspName))
    else
      Seq()

  /**
   * Get default capabilities for a language when no specific LSP is detected.
   * Canonical source—same for all LSPs of a language.
   *
   * @param language The target language
   * @return Seq[LspCapability] of baseline capabilities for the language
   */
  def getDefaultCapabilities(language: String): Seq[LspCapability] =
    getCapabilities(language, None)

  /**
   * Canonical Java capabilities.
   */
  private def javaCapabilities(): Seq[LspCapability] =
    Seq(
      LspCapability("textDocument/completion", true),
      LspCapability("textDocument/hover", true),
      LspCapability("textDocument/definition", true),
      LspCapability("textDocument/references", true),
      LspCapability("textDocument/rename", true),
      LspCapability("textDocument/codeAction", true),
      LspCapability("textDocument/codeLens", true),
      LspCapability("textDocument/formatting", true),
      LspCapability("textDocument/rangeFormatting", true),
      LspCapability("textDocument/onTypeFormatting", true),
      LspCapability("textDocument/signatureHelp", true),
      LspCapability("textDocument/implementation", true),
      LspCapability("textDocument/documentSymbol", true),
      LspCapability("workspace/symbol", true),
      LspCapability("workspace/executeCommand", true)
    )

  /**
   * Canonical Kotlin capabilities.
   */
  private def kotlinCapabilities(): Seq[LspCapability] =
    Seq(
      LspCapability("textDocument/completion", true),
      LspCapability("textDocument/hover", true),
      LspCapability("textDocument/definition", true),
      LspCapability("textDocument/references", true),
      LspCapability("textDocument/rename", true),
      LspCapability("textDocument/formatting", true),
      LspCapability("textDocument/signatureHelp", true),
      LspCapability("textDocument/implementation", true),
      LspCapability("textDocument/documentSymbol", true),
      LspCapability("workspace/symbol", true),
      LspCapability("workspace/executeCommand", true)
    )

  /**
   * Canonical Scala capabilities.
   */
  private def scalaCapabilities(): Seq[LspCapability] =
    Seq(
      LspCapability("textDocument/completion", true),
      LspCapability("textDocument/hover", true),
      LspCapability("textDocument/definition", true),
      LspCapability("textDocument/references", true),
      LspCapability("textDocument/rename", true),
      LspCapability("textDocument/codeAction", true),
      LspCapability("textDocument/formatting", true),
      LspCapability("textDocument/signatureHelp", true),
      LspCapability("textDocument/implementation", true),
      LspCapability("textDocument/documentSymbol", true),
      LspCapability("workspace/symbol", true),
      LspCapability("workspace/executeCommand", true)
    )

  /**
   * Canonical Groovy capabilities.
   */
  private def groovyCapabilities(): Seq[LspCapability] =
    Seq(
      LspCapability("textDocument/completion", true),
      LspCapability("textDocument/hover", true),
      LspCapability("textDocument/definition", true),
      LspCapability("textDocument/references", true),
      LspCapability("textDocument/formatting", true),
      LspCapability("textDocument/signatureHelp", true),
      LspCapability("textDocument/documentSymbol", true)
    )

  /**
   * Check if an LSP is recognized/supported for a given language.
   */
  private def isKnownLsp(lspName: String, language: String): Boolean =
    LspLanguageConfig.isRecognizedLsp(lspName, language)

  /**
   * LSP-specific capability enhancements beyond canonical language capabilities.
   * Only called when a specific LSP is detected.
   */
  private def getLspEnhancements(lspName: String, language: String): Seq[LspCapability] =
    val normalizedLsp = lspName.toLowerCase
    val normalizedLang = language.toLowerCase

    (normalizedLang, normalizedLsp) match
      case ("scala", "metals") =>
        // Metals adds Scala-specific capabilities
        Seq(
          LspCapability("scala/superMethodHierarchy", true),
          LspCapability("scala/worksheetPublishOutput", true),
          LspCapability("scala/executeClientCommand", true)
        )
      case _ =>
        // All other LSP/language combinations use canonical set only
        Seq()
