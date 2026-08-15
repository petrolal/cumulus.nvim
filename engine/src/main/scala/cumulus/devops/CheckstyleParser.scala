package cumulus.devops

import cumulus.protocol.CumulusResponse
import scala.xml.XML
import scala.collection.mutable.ListBuffer
import os.Path

object CheckstyleParser:

  /**
   * Parse Checkstyle XML content string into a sequence of CheckstyleDiagnostic records.
   * Uses scala-xml XPath node traversal without regex.
   */
  def parseCheckstyleXml(xmlContent: String): Seq[CheckstyleDiagnostic] =
    val trimmed = xmlContent.trim
    if trimmed.isEmpty then return Seq.empty

    val xml = XML.loadString(trimmed)
    val diagnostics = ListBuffer[CheckstyleDiagnostic]()

    for fileNode <- xml \ "file" do
      val fileName = (fileNode \ "@name").text.trim
      if fileName.nonEmpty then
        for errorNode <- fileNode \ "error" do
          val lineStr = (errorNode \ "@line").text.trim
          val line = lineStr.toIntOption.getOrElse(1)
          val colStr = (errorNode \ "@column").text.trim
          val col = colStr.toIntOption
          val rawSeverity = (errorNode \ "@severity").text.trim.toUpperCase
          val severity = if rawSeverity.isEmpty then "WARN" else rawSeverity
          val rawMessage = (errorNode \ "@message").text.trim
          val source = (errorNode \ "@source").text.trim
          val message = if rawMessage.nonEmpty then rawMessage else if source.nonEmpty then source else "Checkstyle violation"

          diagnostics += CheckstyleDiagnostic(
            file = fileName,
            line = line,
            col = col,
            severity = severity,
            message = message
          )

    diagnostics.toSeq

  /**
   * Parse Checkstyle XML report from a raw string (e.g. from stdin).
   * Returns a CumulusResponse envelope.
   */
  def parseCheckstyle(content: String): CumulusResponse[Seq[CheckstyleDiagnostic]] =
    try
      val trimmed = content.trim
      if trimmed.isEmpty then
        return CumulusResponse(
          success = true,
          data = Some(Seq.empty),
          error = None,
          error_code = None
        )

      val diags = parseCheckstyleXml(trimmed)
      CumulusResponse(
        success = true,
        data = Some(diags),
        error = None,
        error_code = None
      )
    catch
      case e: org.xml.sax.SAXException =>
        CumulusResponse(
          success = false,
          data = None,
          error = Some(s"XML parse error: ${e.getMessage}"),
          error_code = Some("PARSE_ERROR")
        )
      case e: Exception =>
        CumulusResponse(
          success = false,
          data = None,
          error = Some(s"Error parsing Checkstyle report: ${e.getMessage}"),
          error_code = Some("INTERNAL_ERROR")
        )

  /**
   * Parse Checkstyle XML report from a file path.
   * Returns a CumulusResponse envelope with standard error codes.
   */
  def parseCheckstyleFile(filePath: String): CumulusResponse[Seq[CheckstyleDiagnostic]] =
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
          parseCheckstyle(content)
    catch
      case e: Exception =>
        CumulusResponse(
          success = false,
          data = None,
          error = Some(s"Error reading Checkstyle file: ${e.getMessage}"),
          error_code = Some("INTERNAL_ERROR")
        )
