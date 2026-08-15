package cumulus.git

import cumulus.protocol.CumulusResponse
import os.Path
import scala.collection.mutable.ListBuffer

object ConflictParser:

  private sealed trait ConflictState
  private case object Outside extends ConflictState
  private case class InCurrent(startLine: Int, currentHeader: String) extends ConflictState
  private case class InIncoming(startLine: Int, currentHeader: String, sepLine: Int) extends ConflictState

  /**
   * Parse Git conflict markers from content string into a sequence of ConflictBlock records.
   * Extracts 1-indexed start line, separator line, end line, current header, and incoming header.
   */
  def parseConflicts(content: String): Seq[ConflictBlock] =
    val trimmed = content.trim
    if trimmed.isEmpty then return Seq.empty

    val lines = content.linesIterator.toList
    val blocks = ListBuffer[ConflictBlock]()
    var state: ConflictState = Outside

    for (line, idx) <- lines.zipWithIndex do
      val lineNum = idx + 1
      val lineTrimmed = line.trim

      if line.startsWith("<<<<<<<") then
        val headerRaw = line.stripPrefix("<<<<<<<").trim
        val header = if headerRaw.isEmpty then "HEAD" else headerRaw
        state = InCurrent(startLine = lineNum, currentHeader = header)
      else if line.startsWith("=======") then
        state match
          case InCurrent(startLine, currentHeader) =>
            state = InIncoming(startLine = startLine, currentHeader = currentHeader, sepLine = lineNum)
          case _ => ()
      else if line.startsWith(">>>>>>>") then
        state match
          case InIncoming(startLine, currentHeader, sepLine) =>
            val headerRaw = line.stripPrefix(">>>>>>>").trim
            val header = if headerRaw.isEmpty then "INCOMING" else headerRaw
            blocks += ConflictBlock(
              start_line = startLine,
              sep_line = sepLine,
              end_line = lineNum,
              current_header = currentHeader,
              incoming_header = header
            )
            state = Outside
          case _ => ()

    // Handle unterminated conflict blocks at EOF
    state match
      case InIncoming(startLine, currentHeader, sepLine) =>
        blocks += ConflictBlock(
          start_line = startLine,
          sep_line = sepLine,
          end_line = lines.length,
          current_header = currentHeader,
          incoming_header = "UNTERMINATED"
        )
      case InCurrent(startLine, currentHeader) =>
        blocks += ConflictBlock(
          start_line = startLine,
          sep_line = lines.length,
          end_line = lines.length,
          current_header = currentHeader,
          incoming_header = "UNTERMINATED"
        )
      case Outside => ()

    blocks.toSeq

  /**
   * Parse Git conflict markers from content string, returning a CumulusResponse envelope.
   */
  def parseGitConflicts(content: String): CumulusResponse[Seq[ConflictBlock]] =
    try
      val blocks = parseConflicts(content)
      CumulusResponse(
        success = true,
        data = Some(blocks),
        error = None,
        error_code = None
      )
    catch
      case e: Exception =>
        CumulusResponse(
          success = false,
          data = None,
          error = Some(s"Error parsing Git conflicts: ${e.getMessage}"),
          error_code = Some("INTERNAL_ERROR")
        )

  /**
   * Parse Git conflict markers from a file path.
   */
  def parseGitConflictsFile(filePath: String): CumulusResponse[Seq[ConflictBlock]] =
    try
      val pathOpt = try {
        Some(if filePath.startsWith("/") then Path(filePath) else os.pwd / os.RelPath(filePath))
      } catch {
        case _: Exception => None
      }

      pathOpt match
        case None =>
          CumulusResponse(
            success = false,
            data = None,
            error = Some(s"Invalid file path: $filePath"),
            error_code = Some("INVALID_INPUT")
          )
        case Some(path) =>
          if !os.exists(path) then
            return CumulusResponse(
              success = false,
              data = None,
              error = Some(s"File not found: $filePath"),
              error_code = Some("FILE_NOT_FOUND")
            )

          if !os.isFile(path) then
            return CumulusResponse(
              success = false,
              data = None,
              error = Some(s"Path is not a regular file: $filePath"),
              error_code = Some("INVALID_INPUT")
            )

          val content = os.read(path)
          parseGitConflicts(content)
    catch
      case e: Exception =>
        CumulusResponse(
          success = false,
          data = None,
          error = Some(s"Error reading Git conflict file: ${e.getMessage}"),
          error_code = Some("INTERNAL_ERROR")
        )
