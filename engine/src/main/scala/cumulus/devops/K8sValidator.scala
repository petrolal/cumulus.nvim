package cumulus.devops

import cumulus.protocol.CumulusResponse
import os.Path
import scala.collection.mutable.ListBuffer

object K8sValidator:

  private val ApiVersionPattern = """^apiVersion:\s*(\S.*)?""".r
  private val KindPattern = """^kind:\s*(\S.*)?""".r
  private val DocSeparatorPattern = """^---\s*$""".r

  /**
   * Validates a Kubernetes YAML manifest content string.
   * Checks each YAML document for presence of both top-level 'apiVersion' and 'kind'.
   * If neither is present, the document is considered non-k8s YAML and ignored.
   */
  def validateManifestContent(content: String): Seq[K8sValidationIssue] =
    val trimmed = content.trim
    if trimmed.isEmpty then return Seq.empty

    val lines = content.linesIterator.toList
    val issues = ListBuffer[K8sValidationIssue]()

    case class DocState(
      startLine: Int,
      var hasApiVersion: Boolean = false,
      var hasKind: Boolean = false,
      var hasContent: Boolean = false
    )

    var currentDoc = DocState(startLine = 1)

    def evaluateDoc(doc: DocState): Unit =
      if doc.hasContent then
        if doc.hasKind && !doc.hasApiVersion then
          issues += K8sValidationIssue(
            line = doc.startLine,
            col = None,
            severity = "ERROR",
            message = "Missing top-level 'apiVersion' field in Kubernetes manifest"
          )
        else if doc.hasApiVersion && !doc.hasKind then
          issues += K8sValidationIssue(
            line = doc.startLine,
            col = None,
            severity = "ERROR",
            message = "Missing top-level 'kind' field in Kubernetes manifest"
          )

    for (line, idx) <- lines.zipWithIndex do
      val lineNum = idx + 1
      val lineTrimmed = line.trim

      if DocSeparatorPattern.matches(lineTrimmed) then
        evaluateDoc(currentDoc)
        currentDoc = DocState(startLine = lineNum + 1)
      else if lineTrimmed.nonEmpty && !lineTrimmed.startsWith("#") then
        currentDoc.hasContent = true
        // Top-level keys must not have leading indentation
        if !line.startsWith(" ") && !line.startsWith("\t") then
          line match
            case ApiVersionPattern(_) =>
              currentDoc.hasApiVersion = true
            case KindPattern(_) =>
              currentDoc.hasKind = true
            case _ => ()

    evaluateDoc(currentDoc)
    issues.toSeq

  /**
   * Validates Kubernetes manifest from raw content string.
   */
  def validateK8sManifest(content: String): CumulusResponse[Seq[K8sValidationIssue]] =
    try
      val issues = validateManifestContent(content)
      CumulusResponse(
        success = true,
        data = Some(issues),
        error = None,
        error_code = None
      )
    catch
      case e: Exception =>
        CumulusResponse(
          success = false,
          data = None,
          error = Some(s"Error validating Kubernetes manifest: ${e.getMessage}"),
          error_code = Some("INTERNAL_ERROR")
        )

  /**
   * Validates Kubernetes manifest from a file path.
   */
  def validateK8sManifestFile(filePath: String): CumulusResponse[Seq[K8sValidationIssue]] =
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
          validateK8sManifest(content)
    catch
      case e: Exception =>
        CumulusResponse(
          success = false,
          data = None,
          error = Some(s"Error reading Kubernetes manifest file: ${e.getMessage}"),
          error_code = Some("INTERNAL_ERROR")
        )
