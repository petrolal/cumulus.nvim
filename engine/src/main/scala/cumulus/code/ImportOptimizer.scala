package cumulus.code

import scala.io.Source

/**
 * ImportOptimizer: Deduplicates and sorts import statements from source code.
 * Reads from stdin, outputs deduplicated and sorted imports followed by other code lines.
 */
object ImportOptimizer:

  // Pattern to detect import statements (including static imports)
  private lazy val importPattern = """^\s*import\s+(?:static\s+)?[\w.*]+;?\s*$""".r

  /**
   * Optimize imports from input string (typically from stdin).
   * Returns a sequence of deduplicated and sorted import lines.
   */
  def optimizeImports(input: String): Seq[String] =
    val lines = input.split("\n")
    val imports = scala.collection.mutable.Set[String]()
    val otherLines = scala.collection.mutable.ArrayBuffer[String]()

    // First pass: separate imports from other lines
    lines.foreach { line =>
      val trimmedLine = line.trim
      if importPattern.matches(trimmedLine) then
        // Normalize the import statement (remove extra whitespace)
        val normalizedImport = trimmedLine.replaceAll("\\s+", " ")
        imports += normalizedImport
      else
        otherLines += line
    }

    // Sort imports lexically
    val sortedImports = imports.toSeq.sorted

    // Return deduplicated and sorted imports
    sortedImports

  /**
   * Optimize imports from stdin and return as formatted output.
   */
  def optimizeImportsFromStdin(): Seq[String] =
    val input = scala.io.Source.stdin.mkString
    optimizeImports(input)
