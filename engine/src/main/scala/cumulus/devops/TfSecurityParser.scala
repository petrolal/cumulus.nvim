package cumulus.devops

import cumulus.protocol.{CumulusError, CumulusResponse}
import os.Path
import scala.collection.mutable.ListBuffer
import ujson.Value

/**
 * Security scan report parser for Trivy and tfsec JSON outputs.
 * Parses and normalizes findings into standard `Seq[SecurityFinding]` models with zero runtime reflection.
 */
object TfSecurityParser:

  /**
   * Parse JSON string containing either Trivy or tfsec security audit results.
   */
  def parseJson(jsonStr: String): CumulusResponse[Seq[SecurityFinding]] =
    parseSecurityJson(jsonStr) match
      case Right(findings) =>
        CumulusResponse(
          success = true,
          data = Some(findings),
          error = None,
          error_code = None
        )
      case Left(err) =>
        CumulusResponse(
          success = false,
          data = None,
          error = Some(err),
          error_code = Some(CumulusError.PARSE_ERROR.toString)
        )

  def parseFile(filePath: String): CumulusResponse[Seq[SecurityFinding]] =
    parseSecurityFile(filePath)

  def parseSecurityJson(jsonStr: String): Either[String, Seq[SecurityFinding]] =
    val trimmed = jsonStr.trim
    if trimmed.isEmpty then return Right(Seq.empty)

    try
      val parsed = ujson.read(trimmed)
      if !parsed.isInstanceOf[ujson.Obj] then
        return Left("Security report JSON must be a root JSON object")

      val obj = parsed.obj
      if obj.contains("Results") then
        // Trivy JSON report format
        Right(parseTrivyResults(parsed))
      else if obj.contains("results") then
        // tfsec JSON report format
        Right(parseTfsecResults(parsed))
      else
        // If neither, check if it has "misconfigurations" directly or empty valid report
        Right(Seq.empty)
    catch
      case e: Exception =>
        Left(s"Failed to parse security report JSON: ${e.getMessage}")

  /**
   * Parse Trivy report JSON AST:
   * Look for `Results[].Misconfigurations[]` or `Results[].Vulnerabilities[]`.
   */
  private def parseTrivyResults(root: Value): Seq[SecurityFinding] =
    val findings = ListBuffer[SecurityFinding]()
    val resultsArr = root.obj.get("Results").flatMap(_.arrOpt).getOrElse(Seq.empty)

    for res <- resultsArr do
      val targetFile = res.obj.get("Target").flatMap(_.strOpt).getOrElse("unknown")
      val misconfigs = res.obj.get("Misconfigurations").flatMap(_.arrOpt).getOrElse(Seq.empty)
      val secrets = res.obj.get("Secrets").flatMap(_.arrOpt).getOrElse(Seq.empty)
      val vulns = res.obj.get("Vulnerabilities").flatMap(_.arrOpt).getOrElse(Seq.empty)

      for m <- misconfigs ++ secrets ++ vulns do
        val mObj = m.obj
        val ruleId = mObj.get("ID").orElse(mObj.get("RuleID")).orElse(mObj.get("VulnerabilityID")).flatMap(_.strOpt).getOrElse("UNKNOWN")
        val title = mObj.get("Title").orElse(mObj.get("PkgName")).flatMap(_.strOpt).getOrElse(ruleId)
        val severity = mObj.get("Severity").flatMap(_.strOpt).getOrElse("UNKNOWN").toUpperCase
        val message = mObj.get("Message").orElse(mObj.get("Description")).orElse(mObj.get("InstalledVersion")).flatMap(_.strOpt).getOrElse("")
        val resolution = mObj.get("Resolution").orElse(mObj.get("PrimaryURL")).orElse(mObj.get("FixedVersion")).flatMap(_.strOpt)

        var startLine = 1
        var endLine: Option[Int] = None

        mObj.get("CauseMetadata").foreach { cm =>
          cm.obj.get("StartLine").flatMap(_.numOpt).foreach(n => startLine = n.toInt)
          cm.obj.get("EndLine").flatMap(_.numOpt).foreach(n => endLine = Some(n.toInt))
        }

        if startLine == 1 then
          mObj.get("StartLine").flatMap(_.numOpt).foreach(n => startLine = n.toInt)
          mObj.get("EndLine").flatMap(_.numOpt).foreach(n => endLine = Some(n.toInt))

        findings += SecurityFinding(
          rule_id = ruleId,
          title = title,
          severity = severity,
          file = targetFile,
          line = startLine,
          end_line = endLine,
          message = message,
          resolution = resolution
        )

    findings.toSeq

  /**
   * Parse tfsec report JSON AST:
   * Look for `results[]` with `rule_id`, `description`, `severity`, `location.filename`, `location.start_line`, `resolution`.
   */
  private def parseTfsecResults(root: Value): Seq[SecurityFinding] =
    val findings = ListBuffer[SecurityFinding]()
    val resultsArr = root.obj.get("results").flatMap(_.arrOpt).getOrElse(Seq.empty)

    for r <- resultsArr do
      val rObj = r.obj
      val ruleId = rObj.get("rule_id").orElse(rObj.get("long_id")).orElse(rObj.get("id")).flatMap(_.strOpt).getOrElse("UNKNOWN")
      val title = rObj.get("rule_description").orElse(rObj.get("description")).flatMap(_.strOpt).getOrElse(ruleId)
      val severity = rObj.get("severity").flatMap(_.strOpt).getOrElse("UNKNOWN").toUpperCase
      val message = rObj.get("description").flatMap(_.strOpt).getOrElse("")
      val resolution = rObj.get("resolution").flatMap(_.strOpt)

      var filename = "unknown"
      var startLine = 1
      var endLine: Option[Int] = None

      rObj.get("location").foreach { loc =>
        loc.obj.get("filename").flatMap(_.strOpt).foreach(f => filename = f)
        loc.obj.get("start_line").flatMap(_.numOpt).foreach(n => startLine = n.toInt)
        loc.obj.get("end_line").flatMap(_.numOpt).foreach(n => endLine = Some(n.toInt))
      }

      findings += SecurityFinding(
        rule_id = ruleId,
        title = title,
        severity = severity,
        file = filename,
        line = startLine,
        end_line = endLine,
        message = message,
        resolution = resolution
      )

    findings.toSeq

  /**
   * Parse security scan file by path.
   */
  def parseSecurityFile(filePath: String): CumulusResponse[Seq[SecurityFinding]] =
    try
      val path = try { os.Path(filePath, os.pwd) } catch { case _: Exception => Path(filePath) }
      if !os.exists(path) || !os.isFile(path) then
        return CumulusResponse(
          success = false,
          data = None,
          error = Some(s"Security report file not found: $filePath"),
          error_code = Some(CumulusError.FILE_NOT_FOUND.toString)
        )

      val content = os.read(path)
      parseSecurityJson(content) match
        case Right(findings) =>
          CumulusResponse(
            success = true,
            data = Some(findings),
            error = None,
            error_code = None
          )
        case Left(err) =>
          CumulusResponse(
            success = false,
            data = None,
            error = Some(err),
            error_code = Some(CumulusError.PARSE_ERROR.toString)
          )
    catch
      case e: Exception =>
        CumulusResponse(
          success = false,
          data = None,
          error = Some(s"Error reading security report file: ${e.getMessage}"),
          error_code = Some(CumulusError.INTERNAL_ERROR.toString)
        )
