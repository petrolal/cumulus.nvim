package cumulus.code

import os.Path

object CodeLensExtractor:

  // Compiled regex patterns for annotation detection (lazy to avoid GraalVM issues)
  private lazy val testPattern = """@Test\b""".r
  private lazy val scheduledPattern = """@Scheduled\b""".r
  private lazy val kafkaListenerPattern = """@KafkaListener\b""".r
  private lazy val eventListenerPattern = """@EventListener\b""".r
  private lazy val mainMethodPattern = """public\s+static\s+void\s+main\s*\(""".r

  /**
   * Extract CodeLens items from a Java or Kotlin source file.
   *
   * @param filePath The absolute path to the source file
   * @return Sequence of CodeLensItem, empty if no annotations found
   * @throws Exception on file read errors
   */
  def extractCodeLens(filePath: String): Seq[CodeLensItem] =
    val p = Path(filePath, os.pwd)
    if !os.exists(p) then
      throw new Exception(s"File not found: $filePath")

    if !filePath.endsWith(".java") && !filePath.endsWith(".kt") then
      throw new Exception(s"Unsupported file type; only .java and .kt files are supported")

    // Read file with UTF-8 encoding using os-lib
    val lines = os.read.lines(p, charSet = java.nio.charset.StandardCharsets.UTF_8).toList

    // Process each line, tracking detected items by line number
    val items = scala.collection.mutable.LinkedHashMap[Int, CodeLensItem]()

    var inBlockComment = false
    lines.zipWithIndex.foreach { case (line, idx) if line != null =>
      val lineNumber = idx + 1 // 1-indexed for Neovim

      val trimmed = line.dropWhile(_.isWhitespace)
      if trimmed.startsWith("/*") then
        inBlockComment = true
      if inBlockComment then
        if line.contains("*/") then
          inBlockComment = false
      else if !trimmed.startsWith("//") && !line.contains("/*") then
        // Check for @Test
        if testPattern.findFirstIn(line).isDefined then
          if !items.contains(lineNumber) then
            items(lineNumber) = CodeLensItem(line = lineNumber, title = "▶ Run Test")

        // Check for @Scheduled
        if scheduledPattern.findFirstIn(line).isDefined then
          if !items.contains(lineNumber) then
            items(lineNumber) = CodeLensItem(line = lineNumber, title = "⏰ Scheduled Task")

        // Check for @KafkaListener
        if kafkaListenerPattern.findFirstIn(line).isDefined then
          if !items.contains(lineNumber) then
            items(lineNumber) = CodeLensItem(line = lineNumber, title = "🎧 Event Listener")

        // Check for @EventListener
        if eventListenerPattern.findFirstIn(line).isDefined then
          if !items.contains(lineNumber) then
            items(lineNumber) = CodeLensItem(line = lineNumber, title = "🎧 Event Listener")

        // Check for main method
        if mainMethodPattern.findFirstIn(line).isDefined then
          if !items.contains(lineNumber) then
            items(lineNumber) = CodeLensItem(line = lineNumber, title = "▶ Run Main")
    }

    items.values.toSeq


