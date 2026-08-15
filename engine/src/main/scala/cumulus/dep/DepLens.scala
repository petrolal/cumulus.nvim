package cumulus.dep

import cumulus.protocol.{CumulusResponse, CumulusError}
import cumulus.devops.DependencyLens
import os.Path
import scala.collection.mutable
import scala.util.matching.Regex
import ujson.Value

/**
 * Evaluates semantic version age of project dependencies and reports 1-indexed line locations.
 */
object DepLens:

  private val VarPattern: Regex = """^\s*([a-zA-Z0-9_\-\.]+)\s*=\s*"([^"]+)"""".r
  private val LibStringPattern: Regex = """^\s*([a-zA-Z0-9_\-\.]+)\s*=\s*"([^":]+):([^":]+):([^"]+)"""".r
  private val LibModulePattern: Regex = """module\s*=\s*"([^":]+):([^"]+)"""".r
  private val LibVersionPattern: Regex = """version\s*=\s*"([^"]+)"""".r
  private val LibVersionRefPattern: Regex = """version\.ref\s*=\s*"([^"]+)"""".r
  private val LibGroupPattern: Regex = """group\s*=\s*"([^"]+)"""".r
  private val LibNamePattern: Regex = """name\s*=\s*"([^"]+)"""".r

  def checkDepVersions(filePath: String): CumulusResponse[Seq[DependencyLens]] =
    try
      val path = if filePath.startsWith("/") then Path(filePath) else os.pwd / os.RelPath(filePath)
      if !os.exists(path) then
        return CumulusResponse(success = false, data = None, error = Some(s"File not found: $filePath"), error_code = Some(CumulusError.FILE_NOT_FOUND.toString))
      if !os.isFile(path) then
        return CumulusResponse(success = false, data = None, error = Some(s"Path is not a regular file: $filePath"), error_code = Some(CumulusError.INVALID_INPUT.toString))

      val content = os.read(path)
      val filename = path.last

      val rawLenses = if filename.endsWith(".toml") || content.contains("[libraries]") || content.contains("[versions]") then
        parseGradleVersionsWithLines(content)
      else
        parsePomWithLines(content)

      val versionCache = loadVersionCache()
      val lenses = rawLenses.map { lens =>
        val key = s"${lens.group}:${lens.artifact}"
        versionCache.get(key) match
          case Some(latest) =>
            val age = classifyVersionAge(lens.current_version, latest)
            lens.copy(latest_version = latest, age_status = age)
          case None =>
            lens.copy(age_status = "UNKNOWN")
      }

      CumulusResponse(success = true, data = Some(lenses), error = None, error_code = None)
    catch
      case e: Exception =>
        CumulusResponse(success = false, data = None, error = Some(s"Error checking dependency versions: ${e.getMessage}"), error_code = Some("INTERNAL_ERROR"))

  def classifyVersionAge(current: String, latest: String): String =
    def parseVersion(v: String): Seq[Int] =
      val clean = v.trim.stripPrefix("v").stripPrefix("V")
      clean.split("""[.-]""").take(3).flatMap(_.toIntOption).toSeq

    val currentParts = parseVersion(current)
    val latestParts = parseVersion(latest)

    if currentParts.isEmpty || latestParts.isEmpty then
      "UNKNOWN"
    else if currentParts == latestParts then
      "CURRENT"
    else
      // Pad to 3 parts for reliable comparison
      val cMajor = currentParts.lift(0).getOrElse(0)
      val cMinor = currentParts.lift(1).getOrElse(0)
      val cPatch = currentParts.lift(2).getOrElse(0)

      val lMajor = latestParts.lift(0).getOrElse(0)
      val lMinor = latestParts.lift(1).getOrElse(0)
      val lPatch = latestParts.lift(2).getOrElse(0)

      if cMajor > lMajor || (cMajor == lMajor && cMinor > lMinor) || (cMajor == lMajor && cMinor == lMinor && cPatch >= lPatch) then
        "CURRENT"
      else if cMajor < lMajor then
        "MAJOR_OUTDATED"
      else if cMinor < lMinor then
        "MINOR_OUTDATED"
      else
        "PATCH_OUTDATED"

  def loadVersionCache(): Map[String, String] =
    val cachePath = getVersionCachePath()
    if os.exists(cachePath) && os.isFile(cachePath) then
      try
        val raw = os.read(cachePath)
        val json = ujson.read(raw)
        val map = mutable.Map[String, String]()
        if json.obj.contains("dependencies") && json("dependencies").arr.nonEmpty then
          for dep <- json("dependencies").arr if dep.obj.contains("group") && dep.obj.contains("artifact") && dep.obj.contains("latest_version") do
            val group = dep("group").str
            val artifact = dep("artifact").str
            val latest = dep("latest_version").str
            map(s"$group:$artifact") = latest
        map.toMap
      catch
        case _: Exception => Map.empty
    else
      Map.empty

  private def getVersionCachePath(): Path =
    val home = sys.props.get("user.home").getOrElse("/tmp")
    os.Path(home) / ".cache" / "nvim" / "dependency-versions.json"

  def parsePomWithLines(content: String): Seq[DependencyLens] =
    val lines = content.linesIterator.toList
    val results = mutable.ListBuffer[DependencyLens]()
    val properties = mutable.Map[String, String]()

    // First scan properties
    var inProps = false
    val propLineRegex = """<([a-zA-Z0-9_\-\.]+)>([^<]+)</([a-zA-Z0-9_\-\.]+)>""".r
    for line <- lines do
      val trimmed = line.trim
      if trimmed.contains("<properties>") then inProps = true
      if trimmed.contains("</properties>") then inProps = false
      if inProps then
        propLineRegex.findFirstMatchIn(trimmed).foreach { m =>
          if m.group(1) == m.group(3) then
            properties(m.group(1)) = m.group(2).trim
        }

    def resolveProp(value: String): String =
      var res = value
      for (k, v) <- properties do
        res = res.replace(s"$${$k}", v)
      res

    var inDep = false
    var depStartLine = 0
    var currentGroup: Option[String] = None
    var currentArtifact: Option[String] = None
    var currentVersion: Option[String] = None

    val groupRegex = """<groupId>\s*([^<]+?)\s*</groupId>""".r
    val artifactRegex = """<artifactId>\s*([^<]+?)\s*</artifactId>""".r
    val versionRegex = """<version>\s*([^<]+?)\s*</version>""".r

    for (line, idx) <- lines.zipWithIndex do
      val lineNum = idx + 1
      val trimmed = line.trim

      if trimmed.contains("<dependency>") then
        inDep = true
        depStartLine = lineNum
        currentGroup = None
        currentArtifact = None
        currentVersion = None

      if inDep then
        groupRegex.findFirstMatchIn(trimmed).foreach(m => currentGroup = Some(resolveProp(m.group(1).trim)))
        artifactRegex.findFirstMatchIn(trimmed).foreach(m => currentArtifact = Some(resolveProp(m.group(1).trim)))
        versionRegex.findFirstMatchIn(trimmed).foreach(m => currentVersion = Some(resolveProp(m.group(1).trim)))

      if trimmed.contains("</dependency>") && inDep then
        for
          g <- currentGroup
          a <- currentArtifact
        do
          results += DependencyLens(
            group = g,
            artifact = a,
            current_version = currentVersion.getOrElse("inherited"),
            latest_version = "latest",
            line = depStartLine,
            age_status = "UNKNOWN"
          )
        inDep = false

    results.toSeq

  def parseGradleVersionsWithLines(content: String): Seq[DependencyLens] =
    val lines = content.linesIterator.toList
    val versions = mutable.Map[String, String]()
    val results = mutable.ListBuffer[DependencyLens]()

    var inVersions = false
    var inLibraries = false

    for (line, idx) <- lines.zipWithIndex do
      val lineNum = idx + 1
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
              results += DependencyLens(
                group = group,
                artifact = artifact,
                current_version = version,
                latest_version = "latest",
                line = lineNum,
                age_status = "UNKNOWN"
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
                results += DependencyLens(
                  group = group,
                  artifact = artifact,
                  current_version = version,
                  latest_version = "latest",
                  line = lineNum,
                  age_status = "UNKNOWN"
                )
            case _ => ()

    results.toSeq
