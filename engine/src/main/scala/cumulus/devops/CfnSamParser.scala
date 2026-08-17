package cumulus.devops

import cumulus.protocol.{CumulusError, CumulusResponse}
import os.Path
import scala.collection.mutable.ListBuffer
import scala.util.matching.Regex

/**
 * CloudFormation and SAM template parser and offline validator.
 * Handles both JSON and YAML templates without external heavy reflection-based YAML parsers.
 */
object CfnSamParser:

  /**
   * Inspect a CloudFormation / SAM template content and extract metadata,
   * parameters, resources, lambda/serverless functions, and outputs.
   */
  def inspectTemplate(content: String): CfnTemplateInfo =
    val trimmed = content.trim
    if trimmed.isEmpty then return CfnTemplateInfo()

    if trimmed.startsWith("{") then
      inspectJsonTemplate(trimmed)
    else
      inspectYamlTemplate(content)

  /**
   * Validate a CloudFormation / SAM template content offline.
   * Checks for AWSTemplateFormatVersion, Resources section, unknown/missing resource types,
   * unresolvable !Ref / !GetAtt references within the template, and empty resource bodies.
   */
  def validateTemplate(content: String): Seq[CfnValidationIssue] =
    val trimmed = content.trim
    if trimmed.isEmpty then
      return Seq(CfnValidationIssue(line = 1, severity = "ERROR", message = "Template is empty"))

    if trimmed.startsWith("{") then
      validateJsonTemplate(trimmed)
    else
      validateYamlTemplate(content)

  /**
   * Inspect a template file by path.
   */
  def inspectTemplateFile(filePath: String): CumulusResponse[CfnTemplateInfo] =
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
          val info = inspectTemplate(content)
          CumulusResponse(
            success = true,
            data = Some(info),
            error = None,
            error_code = None
          )
    catch
      case e: Exception =>
        CumulusResponse(
          success = false,
          data = None,
          error = Some(s"Error inspecting CloudFormation/SAM template: ${e.getMessage}"),
          error_code = Some("INTERNAL_ERROR")
        )

  /**
   * Validate a template file by path.
   */
  def validateTemplateFile(filePath: String): CumulusResponse[Seq[CfnValidationIssue]] =
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
          val issues = validateTemplate(content)
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
          error = Some(s"Error validating CloudFormation/SAM template: ${e.getMessage}"),
          error_code = Some("INTERNAL_ERROR")
        )

  // =========================================================================
  // JSON CloudFormation parsing & validation (ujson)
  // =========================================================================

  private def inspectJsonTemplate(jsonStr: String): CfnTemplateInfo =
    try
      val parsed = ujson.read(jsonStr)
      parsed.objOpt match
        case None => CfnTemplateInfo()
        case Some(obj) =>
          val formatVersion = obj.get("AWSTemplateFormatVersion").flatMap(_.strOpt)
          val transform = obj.get("Transform").map {
            case v: ujson.Str => v.str
            case v: ujson.Arr => v.arr.map(_.strOpt.getOrElse("")).mkString(",")
            case other => other.toString
          }
          val description = obj.get("Description").flatMap(_.strOpt)
          val isSam = transform.exists(_.contains("AWS::Serverless"))

          val params = ListBuffer[CfnParameter]()
          obj.get("Parameters").flatMap(_.objOpt).foreach { pObj =>
            pObj.foreach { case (name, pVal) =>
              val pType = pVal.objOpt.flatMap(_.get("Type")).flatMap(_.strOpt).getOrElse("String")
              val defVal = pVal.objOpt.flatMap(_.get("Default")).map(_.value.toString)
              val desc = pVal.objOpt.flatMap(_.get("Description")).flatMap(_.strOpt)
              params += CfnParameter(name, pType, defVal, desc)
            }
          }

          val resources = ListBuffer[CfnResource]()
          val functions = ListBuffer[SamFunctionInfo]()
          obj.get("Resources").flatMap(_.objOpt).foreach { rObj =>
            rObj.foreach { case (logicalId, rVal) =>
              val rType = rVal.objOpt.flatMap(_.get("Type")).flatMap(_.strOpt).getOrElse("Unknown")
              val props = rVal.objOpt.flatMap(_.get("Properties")).flatMap(_.objOpt).map { pMap =>
                pMap.view.mapValues(_.value.toString).toMap
              }.getOrElse(Map.empty[String, String])

              resources += CfnResource(logicalId, rType, None, props)

              if rType == "AWS::Serverless::Function" || rType == "AWS::Lambda::Function" then
                val handler = props.get("Handler").map(_.stripPrefix("\"").stripSuffix("\""))
                val runtime = props.get("Runtime").map(_.stripPrefix("\"").stripSuffix("\""))
                val codeUri = props.get("CodeUri").orElse(props.get("Code")).map(_.stripPrefix("\"").stripSuffix("\""))
                functions += SamFunctionInfo(logicalId, handler, runtime, codeUri, None)
            }
          }

          val outputs = ListBuffer[String]()
          obj.get("Outputs").flatMap(_.objOpt).foreach { oObj =>
            outputs ++= oObj.keys
          }

          CfnTemplateInfo(
            format_version = formatVersion,
            transform = transform,
            description = description,
            is_sam = isSam,
            parameters = params.toSeq,
            resources = resources.toSeq,
            functions = functions.toSeq,
            outputs = outputs.toSeq
          )
    catch
      case _: Exception =>
        CfnTemplateInfo()


  private def validateJsonTemplate(jsonStr: String): Seq[CfnValidationIssue] =
    val issues = ListBuffer[CfnValidationIssue]()
    try
      val parsed = ujson.read(jsonStr)
      val objOpt = parsed.objOpt
      if objOpt.isEmpty then
        return Seq(CfnValidationIssue(line = 1, severity = "ERROR", message = "Root of CloudFormation JSON must be an object"))

      val obj = objOpt.get
      if !obj.contains("Resources") then
        issues += CfnValidationIssue(line = 1, severity = "ERROR", message = "Missing required top-level 'Resources' section")
      else
        val resObjOpt = obj("Resources").objOpt
        if resObjOpt.isEmpty || resObjOpt.get.isEmpty then
          issues += CfnValidationIssue(line = 1, severity = "ERROR", message = "'Resources' section cannot be empty")
        else
          val declaredResources = resObjOpt.get
          declaredResources.foreach { case (logicalId, rVal) =>
            val rObjOpt = rVal.objOpt
            if rObjOpt.isEmpty then
              issues += CfnValidationIssue(line = 1, severity = "ERROR", message = s"Resource '$logicalId' must be an object", logical_id = Some(logicalId))
            else
              val rObj = rObjOpt.get
              if !rObj.contains("Type") then
                issues += CfnValidationIssue(line = 1, severity = "ERROR", message = s"Resource '$logicalId' is missing required 'Type' field", logical_id = Some(logicalId))
              else
                val rType = rObj("Type").strOpt.getOrElse("")
                if !rType.startsWith("AWS::") && !rType.startsWith("Custom::") && !rType.contains("::") && !rType.startsWith("Alexa::") then
                  issues += CfnValidationIssue(line = 1, severity = "WARN", message = s"Resource '$logicalId' has non-standard type '$rType'", logical_id = Some(logicalId))
          }

      issues.toSeq
    catch
      case e: Exception =>
        Seq(CfnValidationIssue(line = 1, severity = "ERROR", message = s"Malformed JSON template: ${e.getMessage}"))

  // =========================================================================
  // YAML CloudFormation line-by-line parsing & validation
  // =========================================================================

  private val TopKeyRegex = """^([A-Za-z0-9_]+)\s*:\s*(.*)$""".r
  private val SubKeyRegex = """^\s{2}([A-Za-z0-9_]+)\s*:\s*(.*)$""".r
  private val RefRegex = """(!Ref\s+([A-Za-z0-9_:]+)|Ref:\s*['"]?([A-Za-z0-9_:]+)['"]?)""".r
  private val GetAttRegex = """(!GetAtt\s+([A-Za-z0-9_]+)\.([A-Za-z0-9_]+)|Fn::GetAtt:\s*\[\s*['"]?([A-Za-z0-9_]+)['"]?\s*,\s*['"]?([A-Za-z0-9_]+)['"]?\s*\])""".r

  private def inspectYamlTemplate(content: String): CfnTemplateInfo =
    val lines = content.linesIterator.toList
    var currentTopSection = ""
    var currentSubItem = ""
    var currentSubType = "Unknown"
    var currentSubLine = 0
    val currentProps = collection.mutable.Map[String, String]()

    var formatVersion: Option[String] = None
    var transform: Option[String] = None
    var description: Option[String] = None

    val params = ListBuffer[CfnParameter]()
    val resources = ListBuffer[CfnResource]()
    val functions = ListBuffer[SamFunctionInfo]()
    val outputs = ListBuffer[String]()

    var paramName = ""
    var paramType = "String"
    var paramDef: Option[String] = None
    var paramDesc: Option[String] = None

    def flushCurrentSubItem(): Unit =
      if currentSubItem.nonEmpty then
        currentTopSection match
          case "Resources" =>
            resources += CfnResource(currentSubItem, currentSubType, Some(currentSubLine), currentProps.toMap)
            if currentSubType == "AWS::Serverless::Function" || currentSubType == "AWS::Lambda::Function" then
              val handler = currentProps.get("Handler").map(_.stripPrefix("\"").stripSuffix("\""))
              val runtime = currentProps.get("Runtime").map(_.stripPrefix("\"").stripSuffix("\""))
              val codeUri = currentProps.get("CodeUri").orElse(currentProps.get("Code")).map(_.stripPrefix("\"").stripSuffix("\""))
              functions += SamFunctionInfo(currentSubItem, handler, runtime, codeUri, Some(currentSubLine))
          case "Parameters" =>
            params += CfnParameter(paramName, paramType, paramDef, paramDesc)
          case _ => ()
      currentSubItem = ""
      currentSubType = "Unknown"
      currentSubLine = 0
      currentProps.clear()
      paramName = ""
      paramType = "String"
      paramDef = None
      paramDesc = None

    for (line, idx) <- lines.zipWithIndex do
      val lineNum = idx + 1
      val lineWithoutComment = if line.contains("#") && !line.contains("\"#") && !line.contains("'#") then
        line.substring(0, line.indexOf('#'))
      else line

      if lineWithoutComment.trim.nonEmpty then
        if !lineWithoutComment.startsWith(" ") && !lineWithoutComment.startsWith("\t") then
          flushCurrentSubItem()
          lineWithoutComment match
            case TopKeyRegex(key, rest) =>
              currentTopSection = key
              val v = rest.trim.stripPrefix("\"").stripSuffix("\"").stripPrefix("'").stripSuffix("'")
              key match
                case "AWSTemplateFormatVersion" => formatVersion = Some(v)
                case "Transform" => transform = Some(v)
                case "Description" => description = Some(v)
                case _ => ()
            case _ => ()
        else if lineWithoutComment.startsWith("  ") && !lineWithoutComment.startsWith("    ") then
          lineWithoutComment match
            case SubKeyRegex(key, rest) =>
              flushCurrentSubItem()
              currentSubItem = key
              currentSubLine = lineNum
              currentTopSection match
                case "Parameters" =>
                  paramName = key
                case "Outputs" =>
                  outputs += key
                case _ => ()
            case _ => ()
        else if lineWithoutComment.startsWith("    ") then
          val trimmedIndented = lineWithoutComment.trim
          currentTopSection match
            case "Resources" =>
              if trimmedIndented.startsWith("Type:") then
                currentSubType = trimmedIndented.stripPrefix("Type:").trim.stripPrefix("\"").stripSuffix("\"")
              else if trimmedIndented.contains(":") then
                val colonIdx = trimmedIndented.indexOf(':')
                val pKey = trimmedIndented.substring(0, colonIdx).trim
                val pVal = trimmedIndented.substring(colonIdx + 1).trim
                currentProps(pKey) = pVal
            case "Parameters" =>
              if trimmedIndented.startsWith("Type:") then
                paramType = trimmedIndented.stripPrefix("Type:").trim
              else if trimmedIndented.startsWith("Default:") then
                paramDef = Some(trimmedIndented.stripPrefix("Default:").trim)
              else if trimmedIndented.startsWith("Description:") then
                paramDesc = Some(trimmedIndented.stripPrefix("Description:").trim)
            case _ => ()

    flushCurrentSubItem()

    val isSam = transform.exists(_.contains("AWS::Serverless")) || resources.exists(_.resource_type.startsWith("AWS::Serverless::"))

    CfnTemplateInfo(
      format_version = formatVersion,
      transform = transform,
      description = description,
      is_sam = isSam,
      parameters = params.toSeq,
      resources = resources.toSeq,
      functions = functions.toSeq,
      outputs = outputs.toSeq
    )

  private def validateYamlTemplate(content: String): Seq[CfnValidationIssue] =
    val issues = ListBuffer[CfnValidationIssue]()
    val info = inspectYamlTemplate(content)

    val lines = content.linesIterator.toList
    var hasResources = false
    var resourcesLine = 1

    for (line, idx) <- lines.zipWithIndex do
      val lineNum = idx + 1
      val trimmed = line.trim
      if !line.startsWith(" ") && !line.startsWith("\t") && trimmed.startsWith("Resources:") then
        hasResources = true
        resourcesLine = lineNum

    if !hasResources then
      issues += CfnValidationIssue(
        line = 1,
        col = None,
        severity = "ERROR",
        message = "Missing required top-level 'Resources' section in CloudFormation template"
      )
    else if info.resources.isEmpty then
      issues += CfnValidationIssue(
        line = resourcesLine,
        col = None,
        severity = "ERROR",
        message = "'Resources' section is empty or contains no valid resource definitions"
      )

    // Check resource types and validate references (!Ref and !GetAtt)
    val knownDeclaredIds = (info.resources.map(_.logical_id) ++ info.parameters.map(_.name) ++ Seq(
      "AWS::Region", "AWS::AccountId", "AWS::StackName", "AWS::StackId", "AWS::Partition", "AWS::URLSuffix", "AWS::NoValue", "AWS::NotificationARNs"
    )).toSet

    for r <- info.resources do
      if r.resource_type == "Unknown" || r.resource_type.isEmpty then
        issues += CfnValidationIssue(
          line = r.line.getOrElse(1),
          col = None,
          severity = "ERROR",
          message = s"Resource '${r.logical_id}' is missing a 'Type' specification",
          logical_id = Some(r.logical_id)
        )
      else if !r.resource_type.startsWith("AWS::") && !r.resource_type.startsWith("Custom::") && !r.resource_type.contains("::") && !r.resource_type.startsWith("Alexa::") then
        issues += CfnValidationIssue(
          line = r.line.getOrElse(1),
          col = None,
          severity = "WARN",
          message = s"Resource '${r.logical_id}' has non-standard type '${r.resource_type}'",
          logical_id = Some(r.logical_id)
        )

    // Check intrinsic references in YAML lines (!Ref and !GetAtt)
    for (line, idx) <- lines.zipWithIndex do
      val lineNum = idx + 1
      val lineClean = if line.contains("#") then line.substring(0, line.indexOf('#')) else line

      // Check !Ref
      for m <- RefRegex.findAllMatchIn(lineClean) do
        val refTarget = Option(m.group(2)).orElse(Option(m.group(3))).map(_.trim).getOrElse("")
        if refTarget.nonEmpty && !refTarget.startsWith("${") && !knownDeclaredIds.contains(refTarget) then
          issues += CfnValidationIssue(
            line = lineNum,
            col = Some(m.start + 1),
            severity = "WARN",
            message = s"Reference '!Ref $refTarget' targets undeclared resource or parameter '$refTarget'"
          )

      // Check !GetAtt
      for m <- GetAttRegex.findAllMatchIn(lineClean) do
        val targetRes = Option(m.group(2)).orElse(Option(m.group(4))).map(_.trim).getOrElse("")
        if targetRes.nonEmpty && !info.resources.exists(_.logical_id == targetRes) then
          issues += CfnValidationIssue(
            line = lineNum,
            col = Some(m.start + 1),
            severity = "WARN",
            message = s"GetAtt targets undeclared resource '$targetRes'"
          )

    issues.toSeq
