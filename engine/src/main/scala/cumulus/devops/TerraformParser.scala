package cumulus.devops

import cumulus.protocol.{CumulusError, CumulusResponse}
import os.Path
import scala.collection.mutable.{ListBuffer, Map as MMap, Set as MSet}
import scala.util.matching.Regex

/**
 * Native Scala 3 parser and inspector for Terraform and OpenTofu (`.tf`, `.tofu`, `.tfvars`).
 * Extracts resources, variables, providers, outputs, and builds local resource dependency DAGs.
 * Built with zero reflection for GraalVM Native Image compatibility.
 */
object TerraformParser:

  // Regex patterns for Terraform / OpenTofu HCL blocks
  // Matches: resource "aws_s3_bucket" "b" { or data "aws_ami" "ubuntu" {
  private val ResourceHeaderRegex = """^(?:resource|data)\s+"([^"]+)"\s+"([^"]+)"\s*\{?""".r
  private val VariableHeaderRegex = """^variable\s+"([^"]+)"\s*\{?""".r
  private val OutputHeaderRegex = """^output\s+"([^"]+)"\s*\{?""".r
  private val ProviderHeaderRegex = """^provider\s+"([^"]+)"\s*\{?""".r
  private val TerraformBlockHeaderRegex = """^terraform\s*\{?""".r
  private val LocalsHeaderRegex = """^locals\s*\{?""".r
  private val ModuleHeaderRegex = """^module\s+"([^"]+)"\s*\{?""".r

  // Attribute regex: key = value
  private val KeyValRegex = """^\s*([a-zA-Z0-9_-]+)\s*=\s*(.*)$""".r

  // Reference extraction regexes:
  // e.g. aws_s3_bucket.b.id, aws_iam_role.role.arn, data.aws_vpc.selected.id, module.vpc.vpc_id, var.region
  private val ResourceRefRegex = """\b([a-zA-Z][a-zA-Z0-9_-]*)\.([a-zA-Z0-9_-]+)(?:\.([a-zA-Z0-9_-]+))?\b""".r

  /**
   * Inspect a single Terraform/OpenTofu file or string content.
   */
  def inspectContent(content: String, filename: String = "stdin"): TerraformInfo =
    parseContent(content, Some(filename))

  def parseContent(content: String, filenameOpt: Option[String] = None): TerraformInfo =
    val filename = filenameOpt.getOrElse("stdin")
    val trimmed = content.trim
    if trimmed.isEmpty then return TerraformInfo()

    val lines = content.linesIterator.toList
    val resources = ListBuffer[TerraformResource]()
    val variables = ListBuffer[TerraformVariable]()
    val outputs = ListBuffer[TerraformOutput]()
    val providers = ListBuffer[TerraformProvider]()

    var idx = 0
    while idx < lines.length do
      val rawLine = lines(idx)
      val lineNum = idx + 1
      val line = stripComments(rawLine).trim

      if line.isEmpty then
        idx += 1
      else if line.startsWith("resource ") || line.startsWith("data ") then
        val isData = line.startsWith("data ")
        val parts = extractBlockHeader(line)
        if parts.length >= 2 then
          val rType = parts(0)
          val rName = parts(1)
          val (blockLines, nextIdx) = extractBlockBody(lines, idx)
          val attributes = parseAttributes(blockLines)
          val blockText = blockLines.mkString("\n")
          val deps = extractDependencies(blockText, rType, rName)
          resources += TerraformResource(
            resource_type = rType,
            name = rName,
            file = filename,
            line = lineNum,
            is_data = isData,
            attributes = attributes,
            dependencies = deps
          )
          idx = nextIdx
        else
          idx += 1
      else if line.startsWith("variable ") then
        val parts = extractBlockHeader(line)
        if parts.nonEmpty then
          val varName = parts(0)
          val (blockLines, nextIdx) = extractBlockBody(lines, idx)
          val attrs = parseAttributes(blockLines)
          variables += TerraformVariable(
            name = varName,
            file = filename,
            line = lineNum,
            var_type = attrs.get("type"),
            default_value = attrs.get("default"),
            description = attrs.get("description"),
            sensitive = attrs.get("sensitive").exists(_.equalsIgnoreCase("true"))
          )
          idx = nextIdx
        else
          idx += 1
      else if line.startsWith("output ") then
        val parts = extractBlockHeader(line)
        if parts.nonEmpty then
          val outName = parts(0)
          val (blockLines, nextIdx) = extractBlockBody(lines, idx)
          val attrs = parseAttributes(blockLines)
          outputs += TerraformOutput(
            name = outName,
            file = filename,
            line = lineNum,
            value = attrs.get("value"),
            description = attrs.get("description"),
            sensitive = attrs.get("sensitive").exists(_.equalsIgnoreCase("true"))
          )
          idx = nextIdx
        else
          idx += 1
      else if line.startsWith("provider ") then
        val parts = extractBlockHeader(line)
        if parts.nonEmpty then
          val provName = parts(0)
          val (blockLines, nextIdx) = extractBlockBody(lines, idx)
          val attrs = parseAttributes(blockLines)
          providers += TerraformProvider(
            name = provName,
            file = filename,
            line = lineNum,
            alias = attrs.get("alias"),
            version = attrs.get("version"),
            source = attrs.get("source")
          )
          idx = nextIdx
        else
          idx += 1
      else if line.startsWith("terraform ") || line.startsWith("terraform{") || line.startsWith("terraform {") then
        // Handle terraform { required_providers { ... } }
        val (blockLines, nextIdx) = extractBlockBody(lines, idx)
        val extractedProviders = parseRequiredProviders(blockLines, filename, lineNum)
        providers ++= extractedProviders
        idx = nextIdx
      else
        idx += 1

    val edges = buildDependencyGraph(resources.toSeq)

    TerraformInfo(
      resources = resources.toSeq,
      variables = variables.toSeq,
      outputs = outputs.toSeq,
      providers = providers.toSeq,
      dependency_graph = edges
    )

  /**
   * Inspect a file or directory path. If directory, aggregates all `.tf`, `.tofu`, and `.tfvars` files.
   */
  def inspectPath(targetPath: String): CumulusResponse[TerraformInfo] =
    try
      val path = try { os.Path(targetPath, os.pwd) } catch { case _: Exception => Path(targetPath) }
      if !os.exists(path) then
        return CumulusResponse(
          success = false,
          data = None,
          error = Some(s"File or directory not found: $targetPath"),
          error_code = Some(CumulusError.FILE_NOT_FOUND.toString)
        )

      if os.isFile(path) then
        val content = os.read(path)
        val info = inspectContent(content, path.last)
        CumulusResponse(
          success = true,
          data = Some(info),
          error = None,
          error_code = None
        )
      else
        // Directory
        val tfFiles = os.walk(path).filter { p =>
          os.isFile(p) && (p.ext == "tf" || p.ext == "tofu" || p.ext == "tfvars") && !p.segments.contains(".terraform")
        }.sorted

        if tfFiles.isEmpty then
          return CumulusResponse(
            success = true,
            data = Some(TerraformInfo()),
            error = None,
            error_code = None
          )

        val allResources = ListBuffer[TerraformResource]()
        val allVariables = ListBuffer[TerraformVariable]()
        val allOutputs = ListBuffer[TerraformOutput]()
        val allProviders = ListBuffer[TerraformProvider]()

        for f <- tfFiles do
          val content = os.read(f)
          val relName = try f.relativeTo(path).toString catch case _: Exception => f.last
          val info = inspectContent(content, relName)
          allResources ++= info.resources
          allVariables ++= info.variables
          allOutputs ++= info.outputs
          allProviders ++= info.providers

        val edges = buildDependencyGraph(allResources.toSeq)

        CumulusResponse(
          success = true,
          data = Some(TerraformInfo(
            resources = allResources.toSeq,
            variables = allVariables.toSeq,
            outputs = allOutputs.toSeq,
            providers = allProviders.toSeq,
            dependency_graph = edges
          )),
          error = None,
          error_code = None
        )
    catch
      case e: Exception =>
        CumulusResponse(
          success = false,
          data = None,
          error = Some(s"Error inspecting Terraform path: ${e.getMessage}"),
          error_code = Some(CumulusError.INTERNAL_ERROR.toString)
        )

  /**
   * Build directed dependency edges between resources.
   * e.g. If resource A depends on resource B, edge is from A to B.
   */
  def buildDependencyGraph(resources: Seq[TerraformResource]): Seq[TfDependencyEdge] =
    val resourceKeys = resources.map { r =>
      val prefix = if r.is_data then s"data.${r.resource_type}.${r.name}" else s"${r.resource_type}.${r.name}"
      prefix
    }.toSet

    val edges = ListBuffer[TfDependencyEdge]()
    val seen = MSet[(String, String)]()

    for r <- resources do
      val sourceKey = if r.is_data then s"data.${r.resource_type}.${r.name}" else s"${r.resource_type}.${r.name}"
      for dep <- r.dependencies do
        // Match exact resource key or prefix
        if resourceKeys.contains(dep) then
          if !seen.contains((sourceKey, dep)) then
            seen += ((sourceKey, dep))
            edges += TfDependencyEdge(from = sourceKey, to = dep)
        else
          // Check if dep matches without attribute (e.g. aws_s3_bucket.b)
          val matching = resourceKeys.find(k => dep.startsWith(k) || k == dep)
          matching.foreach { targetKey =>
            if !seen.contains((sourceKey, targetKey)) then
              seen += ((sourceKey, targetKey))
              edges += TfDependencyEdge(from = sourceKey, to = targetKey)
          }

    edges.toSeq

  private def stripComments(line: String): String =
    val l = line.trim
    if l.startsWith("#") || l.startsWith("//") then ""
    else
      // Strip trailing inline comments if not inside string
      var inQuote = false
      var commentStart = -1
      var i = 0
      while i < line.length && commentStart == -1 do
        val c = line.charAt(i)
        if c == '"' && (i == 0 || line.charAt(i - 1) != '\\') then
          inQuote = !inQuote
        else if !inQuote then
          if c == '#' then
            commentStart = i
          else if c == '/' && i + 1 < line.length && line.charAt(i + 1) == '/' then
            commentStart = i
        i += 1
      if commentStart != -1 then line.substring(0, commentStart) else line

  private def extractBlockHeader(line: String): Seq[String] =
    val str = stripComments(line).trim
    val quotes = ListBuffer[String]()
    var inQuote = false
    var current = new StringBuilder
    var i = 0
    while i < str.length do
      val c = str.charAt(i)
      if c == '"' && (i == 0 || str.charAt(i - 1) != '\\') then
        if inQuote then
          quotes += current.toString
          current = new StringBuilder
          inQuote = false
        else
          inQuote = true
      else if inQuote then
        current.append(c)
      i += 1

    if quotes.nonEmpty then quotes.toSeq
    else
      // Fallback for unquoted like variable foo { or provider aws {
      val tokens = str.split("\\s+").filter(_.nonEmpty).map(_.stripSuffix("{").trim).filter(_.nonEmpty)
      if tokens.length > 1 then tokens.drop(1).toSeq else Seq.empty

  private def extractBlockBody(lines: List[String], startIdx: Int): (List[String], Int) =
    val body = ListBuffer[String]()
    var braceCount = 0
    var foundFirstBrace = false
    var i = startIdx

    while i < lines.length do
      val line = lines(i)
      var j = 0
      var inQuote = false
      while j < line.length do
        val c = line.charAt(j)
        if c == '"' && (j == 0 || line.charAt(j - 1) != '\\') then
          inQuote = !inQuote
        else if !inQuote then
          if c == '#' || (c == '/' && j + 1 < line.length && line.charAt(j + 1) == '/') then
            j = line.length // ignore comments
          else if c == '{' then
            braceCount += 1
            foundFirstBrace = true
          else if c == '}' then
            braceCount -= 1
        j += 1

      if i > startIdx then body += line
      else
        // Check if there's content after the opening brace on the first line
        val firstBraceIdx = line.indexOf('{')
        if firstBraceIdx != -1 && firstBraceIdx + 1 < line.length then
          val rest = line.substring(firstBraceIdx + 1).trim
          if rest.nonEmpty && rest != "}" then body += rest

      i += 1
      if foundFirstBrace && braceCount <= 0 then
        return (body.toList, i)

    (body.toList, i)

  private def parseAttributes(lines: List[String]): Map[String, String] =
    val attrs = MMap[String, String]()
    for l <- lines do
      val line = stripComments(l).trim
      line match
        case KeyValRegex(k, v) =>
          val key = k.trim
          var cleanVal = v.trim.stripSuffix(",").trim
          if cleanVal.startsWith("\"") && cleanVal.endsWith("\"") && cleanVal.length >= 2 then
            cleanVal = cleanVal.substring(1, cleanVal.length - 1)
          attrs(key) = cleanVal
        case _ =>
    attrs.toMap

  private def extractDependencies(blockText: String, selfType: String, selfName: String): Seq[String] =
    val deps = ListBuffer[String]()
    val selfKey = s"$selfType.$selfName"

    for m <- ResourceRefRegex.findAllMatchIn(blockText) do
      val prefix = m.group(1)
      val name = m.group(2)
      val suffix = Option(m.group(3))
      // Exclude special HCL keywords
      if prefix == "data" then
        val fullDataRef = suffix match
          case Some(s) => s"data.$name.$s"
          case None => s"data.$name"
        if fullDataRef != selfKey && !deps.contains(fullDataRef) then
          deps += fullDataRef
      else if prefix != "var" && prefix != "local" && prefix != "module" && prefix != "count" && prefix != "each" && prefix != "path" && prefix != "self" then
        val fullRef = s"$prefix.$name"
        if fullRef != selfKey && !deps.contains(fullRef) then
          deps += fullRef
      else if prefix == "module" then
        val modRef = s"module.$name"
        if !deps.contains(modRef) then deps += modRef

    // Also check for explicit depends_on = [ aws_s3_bucket.b, ... ]
    val dependsOnRegex = """depends_on\s*=\s*\[(.*?)\]""".r
    for m <- dependsOnRegex.findAllMatchIn(blockText) do
      val arrayContent = m.group(1)
      val tokens = arrayContent.split("[,\\s]+").map(_.trim).filter(_.nonEmpty)
      for t <- tokens do
        val clean = t.stripPrefix("[").stripSuffix("]").stripPrefix("\"").stripSuffix("\"")
        if clean.nonEmpty && clean != selfKey && !deps.contains(clean) then
          deps += clean

    deps.toSeq

  private def parseRequiredProviders(lines: List[String], file: String, lineNum: Int): Seq[TerraformProvider] =
    val providers = ListBuffer[TerraformProvider]()
    var inReqProviders = false
    var currentProvider = ""
    var currentSource = ""
    var currentVersion = ""

    for l <- lines do
      val line = stripComments(l).trim
      if line.contains("required_providers") && line.contains("{") then
        inReqProviders = true
      else if inReqProviders then
        if line.startsWith("}") then
          if currentProvider.nonEmpty then
            providers += TerraformProvider(
              name = currentProvider,
              file = file,
              line = lineNum,
              source = if currentSource.nonEmpty then Some(currentSource) else None,
              version = if currentVersion.nonEmpty then Some(currentVersion) else None
            )
            currentProvider = ""
            currentSource = ""
            currentVersion = ""
        else if line.contains("=") && line.contains("{") then
          val provName = line.takeWhile(_ != '=').trim
          currentProvider = provName
        else if line match
          case KeyValRegex(k, v) =>
            val key = k.trim
            var cleanVal = v.trim.stripSuffix(",").trim
            if cleanVal.startsWith("\"") && cleanVal.endsWith("\"") && cleanVal.length >= 2 then
              cleanVal = cleanVal.substring(1, cleanVal.length - 1)
            if key == "source" then currentSource = cleanVal
            else if key == "version" then currentVersion = cleanVal
            true
          case _ => false
        then ()

    providers.toSeq
