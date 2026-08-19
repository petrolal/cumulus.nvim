package cumulus.lsp

import cumulus.protocol.LspCapability
import scala.util.Try

/**
 * Detects LSP capabilities for supported language servers.
 * Returns standard LSP capabilities based on language server type.
 */
object LspCapabilityDetector:

  /**
   * Get all capabilities for a given LSP server.
   *
   * @param lspName The name of the LSP server (jdtls, metals, kotlin-language-server, etc.)
   * @param language The language being configured
   * @return Seq[LspCapability] of all supported capabilities
   */
  def detectCapabilities(lspName: String, language: String): Seq[LspCapability] =
    // Guard against null/empty inputs
    if lspName == null || lspName.trim.isEmpty then
      return Seq()
    if language == null || language.trim.isEmpty then
      return Seq()

    language.toLowerCase match
      case "java" => detectJavaCapabilities(lspName)
      case "kotlin" => detectKotlinCapabilities(lspName)
      case "scala" => detectScalaCapabilities(lspName)
      case "groovy" => detectGroovyCapabilities(lspName)
      case _ => Seq()

  /**
   * Detect Java LSP capabilities (JDTLS).
   */
  private def detectJavaCapabilities(lspName: String): Seq[LspCapability] =
    lspName.toLowerCase match
      case "jdtls" =>
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
      case _ => Seq()

  /**
   * Detect Kotlin LSP capabilities (kotlin-language-server).
   */
  private def detectKotlinCapabilities(lspName: String): Seq[LspCapability] =
    lspName.toLowerCase match
      case "kotlin-language-server" =>
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
      case _ => Seq()

  /**
   * Detect Scala LSP capabilities (Metals).
   */
  private def detectScalaCapabilities(lspName: String): Seq[LspCapability] =
    lspName.toLowerCase match
      case "metals" =>
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
          LspCapability("workspace/executeCommand", true),
          // Scala-specific capabilities
          LspCapability("scala/superMethodHierarchy", true),
          LspCapability("scala/worksheetPublishOutput", true),
          LspCapability("scala/executeClientCommand", true)
        )
      case _ => Seq()

  /**
   * Detect Groovy LSP capabilities (groovy-lsp).
   */
  private def detectGroovyCapabilities(lspName: String): Seq[LspCapability] =
    lspName.toLowerCase match
      case "groovy-lsp" =>
        Seq(
          LspCapability("textDocument/completion", true),
          LspCapability("textDocument/hover", true),
          LspCapability("textDocument/definition", true),
          LspCapability("textDocument/references", true),
          LspCapability("textDocument/formatting", true),
          LspCapability("textDocument/signatureHelp", true),
          LspCapability("textDocument/documentSymbol", true)
        )
      case _ => Seq()
