package cumulus.lsp

import cumulus.protocol.TreeSitterParserSpec
import os.Path
import scala.util.Try

/**
 * Discovers available TreeSitter parsers for supported languages.
 * Checks nvim tree-sitter parser directories and returns parser specs.
 */
object TreeSitterResolver:

  /**
   * Discover available TreeSitter parsers for a given language.
   *
   * @param language The target language (java, kotlin, scala, groovy)
   * @return Seq[TreeSitterParserSpec] of available parsers and their query files
   */
  def discoverParsers(language: String): Seq[TreeSitterParserSpec] =
    if language == null || language.trim.isEmpty then
      return Seq()

    val normalized = language.toLowerCase
    if !LspLanguageConfig.isSupported(normalized) then
      return Seq()

    val queryType = LspLanguageConfig.getQueryType(normalized)
    discoverParser(normalized, queryType)

  /**
   * Discover a single TreeSitter parser if it exists.
   */
  private def discoverParser(parserName: String, queryType: String): Seq[TreeSitterParserSpec] =
    if checkParserExists(parserName) then
      Seq(TreeSitterParserSpec(parser = parserName, queries = queryType, installed = true))
    else
      Seq()

  /**
   * Cache for discovered TreeSitter parser paths.
   * Avoids repeated filesystem lookups.
   */
  private val parserCache = scala.collection.concurrent.TrieMap[String, Boolean]()

  /**
   * Cached lookup of TreeSitter parser directories.
   */
  private lazy val treeSitterPaths: Seq[Path] = {
    Try {
      val home = System.getProperty("user.home")
      val paths = Seq(
        Path(s"$home/.local/share/nvim/site/pack/packer/start/nvim-treesitter/parser"),
        Path(s"$home/.local/share/nvim/site/pack/lazy/start/nvim-treesitter/parser"),
        Path(s"$home/.local/share/nvim/nvim-treesitter/parser")
      )
      paths.filter(p => os.exists(p) && os.isDir(p))
    }.getOrElse(Seq())
  }

  /**
   * Check if a TreeSitter parser exists in nvim parser directory.
   * Uses caching to avoid repeated filesystem lookups.
   */
  private def checkParserExists(parserName: String): Boolean =
    // Guard against null/empty inputs
    if parserName == null || parserName.trim.isEmpty then
      return false

    val normalized = parserName.toLowerCase

    // Check cache first
    parserCache.get(normalized) match
      case Some(cached) => cached
      case None =>
        // Discover and cache result
        val exists = discoverParser(normalized)
        parserCache(normalized) = exists
        exists

  /**
   * Search for parser in cached TreeSitter paths.
   */
  private def discoverParser(parserName: String): Boolean =
    treeSitterPaths.exists { path =>
      Try {
        os.list(path).exists { f =>
          f.last.startsWith(parserName) &&
          (f.last.contains(".so") || f.last.contains(".dll") || f.last.contains(".dylib"))
        }
      }.getOrElse(false)
    }
