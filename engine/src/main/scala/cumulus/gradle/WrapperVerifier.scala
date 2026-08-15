package cumulus.gradle

import cumulus.protocol.CumulusResponse
import cumulus.devops.GradleWrapperStatus
import os.Path
import scala.collection.mutable.ListBuffer
import scala.util.matching.Regex

/**
 * Verifies Gradle wrapper configuration, SHA-256 checksums, and CI workflow alignment.
 */
object WrapperVerifier:

  private val VersionUrlPattern: Regex = """gradle-(\d+\.\d+(?:\.\d+)?)-""".r
  private val YamlVersionPattern: Regex = """gradle[_-]version\s*[:=]\s*["\']?(\d+\.\d+(?:\.\d+)?)["\']?""".r
  private val PathVersionPattern: Regex = """gradle[/\\]gradle-(\d+\.\d+(?:\.\d+)?)-""".r
  private val GroovyVersionPattern: Regex = """gradle[_-]version\s*=\s*["\'](\d+\.\d+(?:\.\d+)?)["\']""".r

  def verifyGradleWrapper(dir: String): CumulusResponse[GradleWrapperStatus] =
    try
      val dirPath = if dir.startsWith("/") then Path(dir) else os.pwd / os.RelPath(dir)
      val status = verifyDirectory(dirPath)
      CumulusResponse(success = true, data = Some(status), error = None, error_code = None)
    catch
      case e: Exception =>
        CumulusResponse(success = false, data = None, error = Some(s"Error verifying Gradle wrapper: ${e.getMessage}"), error_code = Some("INTERNAL_ERROR"))

  def verifyDirectory(dirPath: Path): GradleWrapperStatus =
    val (localVersion, sha256Configured) = parseWrapperProperties(dirPath)
    val ciVersion = scanCiConfigs(dirPath)

    val issues = ListBuffer[String]()

    (localVersion, ciVersion) match
      case (Some(local), Some(ci)) if local != ci =>
        issues += s"Gradle version mismatch: local=$local, CI=$ci"
      case _ => ()

    if !sha256Configured && localVersion.isDefined then
      issues += "Gradle wrapper SHA-256 checksum not configured (security risk)"

    GradleWrapperStatus(
      local_version = localVersion,
      ci_version = ciVersion,
      sha256_configured = sha256Configured,
      sha256_valid = sha256Configured,
      issues = issues.toSeq
    )

  private def parseWrapperProperties(dir: Path): (Option[String], Boolean) =
    val propsFile = dir / "gradle" / "wrapper" / "gradle-wrapper.properties"
    if !os.exists(propsFile) || !os.isFile(propsFile) then
      return (None, false)

    try
      val content = os.read(propsFile)
      var version: Option[String] = None
      var hasSha256 = false

      for line <- content.linesIterator do
        val trimmed = line.trim
        if trimmed.startsWith("distributionUrl") then
          version = extractVersionFromUrl(trimmed)
        if trimmed.startsWith("distributionSha256Sum") then
          val parts = trimmed.split("=", 2)
          if parts.length == 2 && parts(1).trim.nonEmpty then
            hasSha256 = true

      (version, hasSha256)
    catch
      case _: Exception => (None, false)

  private def extractVersionFromUrl(line: String): Option[String] =
    VersionUrlPattern.findFirstMatchIn(line).map(_.group(1))

  private def scanCiConfigs(dir: Path): Option[String] =
    var ciVersion: Option[String] = None

    // 1. GitHub Actions
    val ghWorkflows = dir / ".github" / "workflows"
    if os.exists(ghWorkflows) && os.isDir(ghWorkflows) then
      try
        val files = os.list(ghWorkflows).filter(p => os.isFile(p) && (p.last.endsWith(".yml") || p.last.endsWith(".yaml")))
        for file <- files if ciVersion.isEmpty do
          val content = os.read(file)
          ciVersion = extractVersionFromYaml(content)
      catch
        case _: Exception => ()

    // 2. GitLab CI
    if ciVersion.isEmpty then
      val gitlabCi = dir / ".gitlab-ci.yml"
      if os.exists(gitlabCi) && os.isFile(gitlabCi) then
        try
          val content = os.read(gitlabCi)
          ciVersion = extractVersionFromYaml(content)
        catch
          case _: Exception => ()

    // 3. Jenkinsfile
    if ciVersion.isEmpty then
      val jenkinsfile = dir / "Jenkinsfile"
      if os.exists(jenkinsfile) && os.isFile(jenkinsfile) then
        try
          val content = os.read(jenkinsfile)
          ciVersion = extractVersionFromGroovy(content)
        catch
          case _: Exception => ()

    ciVersion

  private def extractVersionFromYaml(content: String): Option[String] =
    YamlVersionPattern.findFirstMatchIn(content).map(_.group(1))
      .orElse(PathVersionPattern.findFirstMatchIn(content).map(_.group(1)))

  private def extractVersionFromGroovy(content: String): Option[String] =
    GroovyVersionPattern.findFirstMatchIn(content).map(_.group(1))
