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
    // Guard against null/empty inputs
    if language == null || language.trim.isEmpty then
      return Seq()

    language.toLowerCase match
      case "java" => discoverJavaParsers()
      case "kotlin" => discoverKotlinParsers()
      case "scala" => discoverScalaParsers()
      case "groovy" => discoverGroovyParsers()
      case _ => Seq()

  /**
   * Discover Java TreeSitter parsers.
   */
  private def discoverJavaParsers(): Seq[TreeSitterParserSpec] =
    val parsers = scala.collection.mutable.ListBuffer[TreeSitterParserSpec]()

    val javaParser = checkParserExists("java")
    if javaParser then
      parsers += TreeSitterParserSpec(
        parser = "java",
        queries = "standard",
        installed = true
      )

    parsers.toSeq

  /**
   * Discover Kotlin TreeSitter parsers.
   */
  private def discoverKotlinParsers(): Seq[TreeSitterParserSpec] =
    val parsers = scala.collection.mutable.ListBuffer[TreeSitterParserSpec]()

    val kotlinParser = checkParserExists("kotlin")
    if kotlinParser then
      parsers += TreeSitterParserSpec(
        parser = "kotlin",
        queries = "standard",
        installed = true
      )

    parsers.toSeq

  /**
   * Discover Scala TreeSitter parsers.
   */
  private def discoverScalaParsers(): Seq[TreeSitterParserSpec] =
    val parsers = scala.collection.mutable.ListBuffer[TreeSitterParserSpec]()

    val scalaParser = checkParserExists("scala")
    if scalaParser then
      parsers += TreeSitterParserSpec(
        parser = "scala",
        queries = "extended", // Scala has extended query support
        installed = true
      )

    parsers.toSeq

  /**
   * Discover Groovy TreeSitter parsers.
   */
  private def discoverGroovyParsers(): Seq[TreeSitterParserSpec] =
    val parsers = scala.collection.mutable.ListBuffer[TreeSitterParserSpec]()

    val groovyParser = checkParserExists("groovy")
    if groovyParser then
      parsers += TreeSitterParserSpec(
        parser = "groovy",
        queries = "standard",
        installed = true
      )

    parsers.toSeq

  /**
   * Check if a TreeSitter parser exists in nvim parser directory.
   * Checks both the standard nvim tree-sitter location and fallback paths.
   */
  private def checkParserExists(parserName: String): Boolean =
    // Guard against null/empty inputs
    if parserName == null || parserName.trim.isEmpty then
      return false

    Try {
      val home = System.getProperty("user.home")

      // Standard nvim tree-sitter parser location
      val standardPath = Path(s"$home/.local/share/nvim/site/pack/packer/start/nvim-treesitter/parser")

      // Alternative nvim tree-sitter locations
      val lazyPath = Path(s"$home/.local/share/nvim/site/pack/lazy/start/nvim-treesitter/parser")
      val nvimDataPath = Path(s"$home/.local/share/nvim/nvim-treesitter/parser")

      // Check for parser.so or parser.dll
      val paths = Seq(standardPath, lazyPath, nvimDataPath)
      paths.exists { path =>
        if os.exists(path) && os.isDir(path) then
          val files = os.list(path)
          files.exists { f =>
            f.last.startsWith(parserName) &&
            (f.last.contains(".so") || f.last.contains(".dll") || f.last.contains(".dylib"))
          }
        else
          false
      }
    }.getOrElse(false)
