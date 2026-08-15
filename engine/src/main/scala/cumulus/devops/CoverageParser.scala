package cumulus.devops

import cumulus.protocol.CumulusResponse
import scala.xml.XML
import scala.collection.mutable.ListBuffer
import os.Path

object CoverageParser:

  /**
   * Parse JaCoCo XML content string into a sequence of CoverageEntry records.
   * Uses scala-xml XPath node traversal without regex.
   */
  def parseJacocoXml(xmlContent: String): Seq[CoverageEntry] =
    val trimmed = xmlContent.trim
    if trimmed.isEmpty then return Seq.empty

    val xml = XML.loadString(trimmed)
    val entries = ListBuffer[CoverageEntry]()

    for pkg <- xml \ "package" do
      val rawPkgName = (pkg \ "@name").text.trim
      val pkgName = rawPkgName.replace('.', '/')
      for sf <- pkg \ "sourcefile" do
        val sfName = (sf \ "@name").text.trim
        if sfName.nonEmpty then
          val fullPath = if pkgName.isEmpty then sfName else s"$pkgName/$sfName"
          val coveredLines = ListBuffer[Int]()
          val missedLines = ListBuffer[Int]()

          for line <- sf \ "line" do
            val lineNr = (line \ "@nr").text.trim.toIntOption.getOrElse(0)
            val ci = (line \ "@ci").text.trim.toIntOption.getOrElse(0)
            val mi = (line \ "@mi").text.trim.toIntOption.getOrElse(0)

            if lineNr > 0 then
              if ci > 0 then
                coveredLines += lineNr
              else if mi > 0 then
                missedLines += lineNr

          entries += CoverageEntry(
            file = fullPath,
            covered_lines = coveredLines.toSeq,
            missed_lines = missedLines.toSeq
          )

    entries.toSeq

  /**
   * Parse JaCoCo XML report from a file path.
   * Returns a CumulusResponse envelope with standard error codes.
   */
  def parseCoverage(filePath: String): CumulusResponse[Seq[CoverageEntry]] =
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
          val entries = parseJacocoXml(content)
          CumulusResponse(
            success = true,
            data = Some(entries),
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
          error = Some(s"Error parsing coverage report: ${e.getMessage}"),
          error_code = Some("INTERNAL_ERROR")
        )
