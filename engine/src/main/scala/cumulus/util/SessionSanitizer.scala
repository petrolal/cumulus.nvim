package cumulus.util

import cumulus.protocol.{CumulusResponse, CumulusError}
import cumulus.devops.SessionSanitizeResult
import os.Path
import scala.collection.mutable

/**
 * Sanitizes Neovim .vim session files by removing ephemeral buffers (e.g. [No Name], term://, snacks_*)
 * and floating window layouts.
 */
object SessionSanitizer:

  def sanitizeSession(filePath: String): CumulusResponse[SessionSanitizeResult] =
    try
      val path = if filePath.startsWith("/") then Path(filePath) else os.pwd / os.RelPath(filePath)
      if !os.exists(path) then
        return CumulusResponse(
          success = false,
          data = Some(SessionSanitizeResult(success = false, cleaned_lines = 0, total_lines = 0)),
          error = Some(s"File not found: $filePath"),
          error_code = Some(CumulusError.FILE_NOT_FOUND.toString)
        )
      if !os.isFile(path) then
        return CumulusResponse(
          success = false,
          data = Some(SessionSanitizeResult(success = false, cleaned_lines = 0, total_lines = 0)),
          error = Some(s"Path is not a regular file: $filePath"),
          error_code = Some(CumulusError.INVALID_INPUT.toString)
        )

      val content = os.read(path)
      val lines = content.linesIterator.toList
      val totalLines = lines.length

      val (filteredLines, removedCount) = filterSessionLines(lines)

      val cleanedContent = if filteredLines.isEmpty then "" else filteredLines.mkString("\n") + "\n"
      val rawWithEnding = if content.endsWith("\n") then content else content + "\n"
      if cleanedContent != rawWithEnding then
        os.write.over(path, cleanedContent)

      CumulusResponse(
        success = true,
        data = Some(SessionSanitizeResult(
          success = true,
          cleaned_lines = removedCount,
          total_lines = totalLines
        )),
        error = None,
        error_code = None
      )
    catch
      case e: Exception =>
        CumulusResponse(
          success = false,
          data = None,
          error = Some(s"Error sanitizing session: ${e.getMessage}"),
          error_code = Some("INTERNAL_ERROR")
        )

  def filterSessionLines(lines: Seq[String]): (Seq[String], Int) =
    val filtered = mutable.ListBuffer[String]()

    // First pass: identify and remove ephemeral buffer declarations
    for line <- lines do
      if line.startsWith("badd +") && isEphemeralBuffer(line) then
        () // skip
      else
        filtered += line

    val removedFirstPass = lines.length - filtered.length

    // Second pass: remove window/layout commands referencing ephemeral buffers or floating windows
    val finalFiltered = filtered.filterNot(line => isFloatingWindowCommand(line) || shouldSkipBufferReference(line))
    val removedSecondPass = filtered.length - finalFiltered.length
    val totalRemoved = removedFirstPass + removedSecondPass

    (finalFiltered.toSeq, totalRemoved)

  private def isEphemeralBuffer(line: String): Boolean =
    line.contains("[No Name]") ||
      line.contains("term://") ||
      line.contains("snacks_dashboard") ||
      line.contains("snacks_picker") ||
      line.contains("snacks_explorer")

  private def isFloatingWindowCommand(line: String): Boolean =
    val trimmed = line.trim
    (trimmed.startsWith("setlocal") && (trimmed.contains("buftype=nofile") || trimmed.contains("buftype=floating") || trimmed.contains("floating"))) ||
      trimmed.startsWith("let w:snacks_") ||
      (trimmed.startsWith("wincmd") && trimmed.contains("snacks_"))

  private def shouldSkipBufferReference(line: String): Boolean =
    val trimmed = line.trim
    !trimmed.startsWith("badd +") && (
      trimmed.contains("[No Name]") ||
      trimmed.contains("term://") ||
      trimmed.contains("snacks_picker") ||
      trimmed.contains("snacks_dashboard") ||
      trimmed.contains("snacks_explorer")
    )
