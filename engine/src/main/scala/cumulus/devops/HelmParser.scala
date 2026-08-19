package cumulus.devops

import cumulus.protocol.{CumulusError, CumulusResponse}
import os.Path
import scala.collection.mutable.ListBuffer
import scala.util.matching.Regex

/**
 * Native Helm chart metadata parser, nested values hierarchy engine, and template variable extractor.
 * Provides fast, zero-reflection YAML parsing for Helm charts in GraalVM native binaries.
 */
object HelmParser:

  /**
   * Parse `Chart.yaml` content into a HelmChartInfo model.
   */
  def parseChartYaml(content: String): HelmChartInfo =
    var apiVersion = "v2"
    var name = ""
    var version = "0.1.0"
    var appVersion: Option[String] = None
    var description: Option[String] = None
    var chartType: Option[String] = None
    val keywords = ListBuffer[String]()
    val maintainers = ListBuffer[HelmMaintainer]()
    val dependencies = ListBuffer[HelmDependency]()

    val lines = content.linesIterator.toList

    var currentSection = ""
    var currentMaintainerName = ""
    var currentMaintainerEmail: Option[String] = None
    var currentMaintainerUrl: Option[String] = None

    var currentDepName = ""
    var currentDepVersion = ""
    var currentDepRepo: Option[String] = None
    var currentDepCondition: Option[String] = None
    val currentDepTags = ListBuffer[String]()

    def flushMaintainer(): Unit =
      if currentMaintainerName.nonEmpty then
        maintainers += HelmMaintainer(
          name = currentMaintainerName,
          email = currentMaintainerEmail,
          url = currentMaintainerUrl
        )
      currentMaintainerName = ""
      currentMaintainerEmail = None
      currentMaintainerUrl = None

    def flushDependency(): Unit =
      if currentDepName.nonEmpty then
        dependencies += HelmDependency(
          name = currentDepName,
          version = if currentDepVersion.nonEmpty then currentDepVersion else "*",
          repository = currentDepRepo,
          condition = currentDepCondition,
          tags = currentDepTags.toSeq
        )
      currentDepName = ""
      currentDepVersion = ""
      currentDepRepo = None
      currentDepCondition = None
      currentDepTags.clear()

    for line <- lines do
      val lineClean = if line.contains("#") && !line.contains("\"#") && !line.contains("'#") then
        line.substring(0, line.indexOf('#'))
      else line

      val trimmed = lineClean.trim
      if trimmed.nonEmpty && trimmed != "---" && trimmed != "..." then
        val indent = lineClean.takeWhile(_ == ' ').length

        if indent == 0 && trimmed.contains(":") then
          val colonIdx = trimmed.indexOf(':')
          val key = trimmed.substring(0, colonIdx).trim
          val value = trimmed.substring(colonIdx + 1).trim.stripPrefix("\"").stripSuffix("\"").stripPrefix("'").stripSuffix("'")

          if currentSection == "maintainers" then flushMaintainer()
          if currentSection == "dependencies" then flushDependency()

          key match
            case "apiVersion" => apiVersion = value
            case "name" => name = value
            case "version" => version = value
            case "appVersion" => appVersion = if value.nonEmpty then Some(value) else None
            case "description" => description = if value.nonEmpty then Some(value) else None
            case "type" => chartType = if value.nonEmpty then Some(value) else None
            case "keywords" => currentSection = "keywords"
            case "maintainers" => currentSection = "maintainers"
            case "dependencies" => currentSection = "dependencies"
            case _ => currentSection = ""
        else if currentSection == "keywords" && trimmed.startsWith("-") then
          val kw = trimmed.stripPrefix("-").trim.stripPrefix("\"").stripSuffix("\"").stripPrefix("'").stripSuffix("'")
          if kw.nonEmpty then keywords += kw
        else if currentSection == "maintainers" then
          if trimmed.startsWith("-") then
            flushMaintainer()
            val itemContent = trimmed.stripPrefix("-").trim
            if itemContent.startsWith("name:") then
              currentMaintainerName = itemContent.stripPrefix("name:").trim.stripPrefix("\"").stripSuffix("\"").stripPrefix("'").stripSuffix("'")
          else if trimmed.startsWith("name:") then
            currentMaintainerName = trimmed.stripPrefix("name:").trim.stripPrefix("\"").stripSuffix("\"").stripPrefix("'").stripSuffix("'")
          else if trimmed.startsWith("email:") then
            currentMaintainerEmail = Some(trimmed.stripPrefix("email:").trim.stripPrefix("\"").stripSuffix("\"").stripPrefix("'").stripSuffix("'"))
          else if trimmed.startsWith("url:") then
            currentMaintainerUrl = Some(trimmed.stripPrefix("url:").trim.stripPrefix("\"").stripSuffix("\"").stripPrefix("'").stripSuffix("'"))
        else if currentSection == "dependencies" then
          if trimmed.startsWith("-") then
            flushDependency()
            val itemContent = trimmed.stripPrefix("-").trim
            if itemContent.startsWith("name:") then
              currentDepName = itemContent.stripPrefix("name:").trim.stripPrefix("\"").stripSuffix("\"").stripPrefix("'").stripSuffix("'")
          else if trimmed.startsWith("name:") then
            currentDepName = trimmed.stripPrefix("name:").trim.stripPrefix("\"").stripSuffix("\"").stripPrefix("'").stripSuffix("'")
          else if trimmed.startsWith("version:") then
            currentDepVersion = trimmed.stripPrefix("version:").trim.stripPrefix("\"").stripSuffix("\"").stripPrefix("'").stripSuffix("'")
          else if trimmed.startsWith("repository:") then
            currentDepRepo = Some(trimmed.stripPrefix("repository:").trim.stripPrefix("\"").stripSuffix("\"").stripPrefix("'").stripSuffix("'"))
          else if trimmed.startsWith("condition:") then
            currentDepCondition = Some(trimmed.stripPrefix("condition:").trim.stripPrefix("\"").stripSuffix("\"").stripPrefix("'").stripSuffix("'"))
          else if trimmed.startsWith("tags:") then
            ()
          else if trimmed.startsWith("-") && currentDepName.nonEmpty then
            val t = trimmed.stripPrefix("-").trim.stripPrefix("\"").stripSuffix("\"").stripPrefix("'").stripSuffix("'")
            if t.nonEmpty then currentDepTags += t

    if currentSection == "maintainers" then flushMaintainer()
    if currentSection == "dependencies" then flushDependency()

    HelmChartInfo(
      name = name,
      version = version,
      apiVersion = apiVersion,
      appVersion = appVersion,
      description = description,
      chart_type = chartType,
      keywords = keywords.toSeq,
      maintainers = maintainers.toSeq,
      dependencies = dependencies.toSeq,
      values = Map.empty,
      templates = Seq.empty,
      template_vars = Seq.empty
    )

  /**
   * Flatten nested YAML values content into a dot-notation key-value map.
   * e.g.
   *   image:
   *     repository: nginx
   *     tag: "1.25.0"
   * becomes
   *   Map("image.repository" -> "nginx", "image.tag" -> "1.25.0")
   */
  def flattenYamlValues(content: String): Map[String, String] =
    val result = collection.mutable.LinkedHashMap[String, String]()
    val lines = content.linesIterator.toList

    // Stack of (indentationLevel, key)
    val stack = ListBuffer[(Int, String)]()

    for line <- lines do
      val lineClean = if line.contains("#") && !line.contains("\"#") && !line.contains("'#") then
        line.substring(0, line.indexOf('#'))
      else line

      val trimmed = lineClean.trim
      if trimmed.nonEmpty && trimmed != "---" && trimmed != "..." then
        val indent = lineClean.takeWhile(_ == ' ').length

        // Pop keys from stack that have >= indent
        while stack.nonEmpty && stack.last._1 >= indent do
          stack.remove(stack.length - 1)

        if trimmed.contains(":") then
          val colonIdx = trimmed.indexOf(':')
          val key = trimmed.substring(0, colonIdx).trim.stripPrefix("-").trim
          val rawVal = trimmed.substring(colonIdx + 1).trim
          val value = rawVal.stripPrefix("\"").stripSuffix("\"").stripPrefix("'").stripSuffix("'")

          val fullPath = if stack.nonEmpty then
            s"${stack.map(_._2).mkString(".")}.$key"
          else
            key

          if rawVal.isEmpty || rawVal == "{}" || rawVal == "[]" then
            // Nested object block
            stack += ((indent, key))
            if rawVal == "{}" || rawVal == "[]" then
              result(fullPath) = rawVal
          else
            result(fullPath) = value
            stack += ((indent, key))
        else if trimmed.startsWith("-") then
          // Array element
          val itemVal = trimmed.stripPrefix("-").trim.stripPrefix("\"").stripSuffix("\"").stripPrefix("'").stripSuffix("'")
          if stack.nonEmpty then
            val parentPath = stack.map(_._2).mkString(".")
            val existing = result.get(parentPath).map(v => s"$v, $itemVal").getOrElse(itemVal)
            result(parentPath) = existing

    result.toMap

  /**
   * Scan template files content and extract referenced `.Values.<key>` and `.Chart.<key>` expressions.
   */
  def extractTemplateVars(templateContent: String): Seq[String] =
    val varRegex = """\{\{\s*(?:-?\s*)?(?:\$\.)?(?:\.Values|\.Chart)\.([a-zA-Z0-9_.-]+)""".r
    varRegex.findAllMatchIn(templateContent).map(_.group(1)).toList.distinct

  /**
   * Inspect a Helm chart directory or Chart.yaml file path.
   */
  def inspectChart(pathStr: String): CumulusResponse[HelmChartInfo] =
    try
      val pathOpt = try {
        Some(if pathStr.startsWith("/") then Path(pathStr) else os.pwd / os.RelPath(pathStr))
      } catch {
        case _: Exception => None
      }

      pathOpt match
        case None =>
          CumulusResponse(
            success = false,
            data = None,
            error = Some(s"Invalid path: $pathStr"),
            error_code = Some("INVALID_INPUT")
          )
        case Some(path) =>
          if !os.exists(path) then
            return CumulusResponse(
              success = false,
              data = None,
              error = Some(s"Path not found: $pathStr"),
              error_code = Some("FILE_NOT_FOUND")
            )

          val chartDir = if os.isFile(path) then
            if path.last.toLowerCase.startsWith("chart.y") then path / os.up else path / os.up
          else
            path

          val chartYaml = if os.exists(chartDir / "Chart.yaml") then
            Some(chartDir / "Chart.yaml")
          else if os.exists(chartDir / "Chart.yml") then
            Some(chartDir / "Chart.yml")
          else
            None

          chartYaml match
            case None =>
              CumulusResponse(
                success = false,
                data = None,
                error = Some(s"Chart.yaml not found in directory: $chartDir"),
                error_code = Some("FILE_NOT_FOUND")
              )
            case Some(chartFile) =>
              val chartContent = os.read(chartFile)
              val baseInfo = parseChartYaml(chartContent)

              // Parse values.yaml if present
              val valuesYaml = if os.exists(chartDir / "values.yaml") then
                Some(chartDir / "values.yaml")
              else if os.exists(chartDir / "values.yml") then
                Some(chartDir / "values.yml")
              else
                None

              val valuesMap = valuesYaml match
                case Some(vf) => flattenYamlValues(os.read(vf))
                case None => Map.empty[String, String]

              // Discover templates
              val templatesDir = chartDir / "templates"
              val templatesList = ListBuffer[String]()
              val templateVars = ListBuffer[String]()

              if os.exists(templatesDir) && os.isDir(templatesDir) then
                val templateFiles = os.walk(templatesDir).filter(p => os.isFile(p) && (p.ext == "yaml" || p.ext == "yml" || p.ext == "tpl" || p.ext == "txt"))
                for tf <- templateFiles do
                  val rel = tf.relativeTo(chartDir).toString
                  templatesList += rel
                  val content = os.read(tf)
                  templateVars ++= extractTemplateVars(content)

              val combinedInfo = baseInfo.copy(
                values = valuesMap,
                templates = templatesList.toSeq,
                template_vars = templateVars.distinct.toSeq,
                chart_path = chartDir.toString
              )

              CumulusResponse(
                success = true,
                data = Some(combinedInfo),
                error = None,
                error_code = None
              )
    catch
      case e: Exception =>
        CumulusResponse(
          success = false,
          data = None,
          error = Some(s"Error inspecting Helm chart: ${e.getMessage}"),
          error_code = Some("INTERNAL_ERROR")
        )

  /**
   * Inspect Helm chart from raw Chart.yaml and optional values.yaml contents.
   */
  def inspectChartContent(chartYamlContent: String, valuesYamlContent: Option[String] = None): HelmChartInfo =
    val base = parseChartYaml(chartYamlContent)
    val valuesMap = valuesYamlContent.map(flattenYamlValues).getOrElse(Map.empty)
    base.copy(values = valuesMap)
