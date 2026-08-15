package cumulus.dep

import cumulus.protocol.{CumulusResponse, CumulusError}
import cumulus.devops.DependencyInfo
import os.Path
import scala.collection.mutable
import scala.util.matching.Regex
import scala.xml.{Node, XML}

/**
 * Resolves dependencies from Maven POMs (with property macro interpolation) or Gradle version catalogs (.toml).
 */
object DepResolver:

  private val VarPattern: Regex = """^\s*([a-zA-Z0-9_\-\.]+)\s*=\s*"([^"]+)"""".r
  private val LibStringPattern: Regex = """^\s*([a-zA-Z0-9_\-\.]+)\s*=\s*"([^":]+):([^":]+):([^"]+)"""".r
  private val LibModulePattern: Regex = """module\s*=\s*"([^":]+):([^"]+)"""".r
  private val LibVersionPattern: Regex = """version\s*=\s*"([^"]+)"""".r
  private val LibVersionRefPattern: Regex = """version\.ref\s*=\s*"([^"]+)"""".r
  private val LibGroupPattern: Regex = """group\s*=\s*"([^"]+)"""".r
  private val LibNamePattern: Regex = """name\s*=\s*"([^"]+)"""".r

  def resolveDependencies(filePath: String): CumulusResponse[Seq[DependencyInfo]] =
    try
      val path = if filePath.startsWith("/") then Path(filePath) else os.pwd / os.RelPath(filePath)
      if !os.exists(path) then
        return CumulusResponse(success = false, data = None, error = Some(s"File not found: $filePath"), error_code = Some(CumulusError.FILE_NOT_FOUND.toString))
      if !os.isFile(path) then
        return CumulusResponse(success = false, data = None, error = Some(s"Path is not a regular file: $filePath"), error_code = Some(CumulusError.INVALID_INPUT.toString))

      val content = os.read(path)
      val filename = path.last
      val deps = if filename.endsWith(".toml") || content.contains("[libraries]") || content.contains("[versions]") then
        parseVersionCatalog(content)
      else
        parsePomDependencies(content)

      CumulusResponse(success = true, data = Some(deps), error = None, error_code = None)
    catch
      case e: Exception =>
        CumulusResponse(success = false, data = None, error = Some(s"Error resolving dependencies: ${e.getMessage}"), error_code = Some("INTERNAL_ERROR"))

  def parsePomDependencies(content: String): Seq[DependencyInfo] =
    val properties = mutable.Map[String, String]()
    val dependencies = mutable.ListBuffer[DependencyInfo]()

    try
      val xml = XML.loadString(content)

      // Extract properties
      for propBlock <- xml \ "properties" do
        for child <- propBlock.child if child.isInstanceOf[scala.xml.Elem] do
          properties(child.label) = child.text.trim

      def resolveProp(value: String): String =
        var result = value
        var iterations = 0
        while result.contains("${") && iterations < 5 do
          val prev = result
          for (k, v) <- properties do
            val placeholder = s"$${$k}"
            if result.contains(placeholder) then
              result = result.replace(placeholder, v)
          if result == prev then iterations = 5 else iterations += 1
        result

      // Extract dependencies (direct and dependencyManagement)
      val depNodes = (xml \\ "dependency")
      for dep <- depNodes do
        val group = (dep \ "groupId").text.trim
        val artifact = (dep \ "artifactId").text.trim
        val rawVersion = (dep \ "version").text.trim
        val rawScope = (dep \ "scope").text.trim

        if group.nonEmpty && artifact.nonEmpty then
          val resolvedGroup = resolveProp(group)
          val resolvedArtifact = resolveProp(artifact)
          val resolvedVersion = if rawVersion.nonEmpty then resolveProp(rawVersion) else "inherited"
          val scope = if rawScope.nonEmpty then resolveProp(rawScope) else "compile"

          dependencies += DependencyInfo(
            group = resolvedGroup,
            artifact = resolvedArtifact,
            version = resolvedVersion,
            scope = scope
          )
    catch
      case _: Exception => ()

    dependencies.toSeq

  def parseVersionCatalog(content: String): Seq[DependencyInfo] =
    val versions = mutable.Map[String, String]()
    val dependencies = mutable.ListBuffer[DependencyInfo]()

    var inVersions = false
    var inLibraries = false

    for line <- content.linesIterator do
      val trimmed = line.trim
      if trimmed.nonEmpty && !trimmed.startsWith("#") then
        if trimmed == "[versions]" then
          inVersions = true
          inLibraries = false
        else if trimmed == "[libraries]" then
          inVersions = false
          inLibraries = true
        else if trimmed.startsWith("[") then
          inVersions = false
          inLibraries = false
        else if inVersions then
          trimmed match
            case VarPattern(k, v) => versions(k) = v
            case _ => ()
        else if inLibraries then
          trimmed match
            case LibStringPattern(_, group, artifact, version) =>
              dependencies += DependencyInfo(
                group = group,
                artifact = artifact,
                version = version,
                scope = "implementation"
              )
            case _ if trimmed.contains("{") =>
              val group = LibModulePattern.findFirstMatchIn(trimmed).map(_.group(1))
                .orElse(LibGroupPattern.findFirstMatchIn(trimmed).map(_.group(1)))
                .getOrElse("")

              val artifact = LibModulePattern.findFirstMatchIn(trimmed).map(_.group(2))
                .orElse(LibNamePattern.findFirstMatchIn(trimmed).map(_.group(1)))
                .getOrElse("")

              val version = LibVersionRefPattern.findFirstMatchIn(trimmed).map(_.group(1))
                .flatMap(ref => versions.get(ref).orElse(Some(ref)))
                .orElse(LibVersionPattern.findFirstMatchIn(trimmed).map(_.group(1)))
                .getOrElse("latest")

              if group.nonEmpty && artifact.nonEmpty then
                dependencies += DependencyInfo(
                  group = group,
                  artifact = artifact,
                  version = version,
                  scope = "implementation"
                )
            case _ => ()

    dependencies.toSeq
