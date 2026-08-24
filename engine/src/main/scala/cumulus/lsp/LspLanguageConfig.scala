package cumulus.lsp

/**
 * Centralized configuration for supported JVM languages.
 * Single source of truth for language-specific settings.
 */
object LspLanguageConfig:

  /**
   * Canonical supported languages.
   */
  val SupportedLanguages: Set[String] = Set("java", "kotlin", "scala", "groovy")

  /**
   * Minimum Mason package versions per language.
   */
  val MinVersions: Map[String, String] = Map(
    "java" -> "1.0",
    "kotlin" -> "1.0",
    "scala" -> "0.11",
    "groovy" -> "1.0"
  )

  /**
   * TreeSitter query type per language.
   */
  val TreeSitterQueryTypes: Map[String, String] = Map(
    "java" -> "standard",
    "kotlin" -> "standard",
    "scala" -> "extended",    // Scala has extended query support
    "groovy" -> "standard"
  )

  /**
   * Recognized LSP names per language.
   */
  val RecognizedLsps: Map[String, Set[String]] = Map(
    "java" -> Set("jdtls"),
    "kotlin" -> Set("kotlin-language-server"),
    "scala" -> Set("metals"),
    "groovy" -> Set("groovy-lsp")
  )

  /**
   * Get minimum version for a language.
   */
  def getMinVersion(language: String): String =
    MinVersions.getOrElse(language.toLowerCase, "1.0")

  /**
   * Get TreeSitter query type for a language.
   */
  def getQueryType(language: String): String =
    TreeSitterQueryTypes.getOrElse(language.toLowerCase, "standard")

  /**
   * Check if a language is supported.
   */
  def isSupported(language: String): Boolean =
    SupportedLanguages.contains(language.toLowerCase)

  /**
   * Check if an LSP is recognized for a language.
   */
  def isRecognizedLsp(lspName: String, language: String): Boolean =
    RecognizedLsps
      .get(language.toLowerCase)
      .exists(_.contains(lspName.toLowerCase))

  /**
   * Get all recognized LSP names for a language.
   */
  def getRecognizedLsps(language: String): Set[String] =
    RecognizedLsps.getOrElse(language.toLowerCase, Set())
