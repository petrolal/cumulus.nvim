package cumulus.devops

import cumulus.protocol.{CumulusError, CumulusResponse}
import scala.collection.mutable.{ListBuffer, Set as MSet}
import scala.util.matching.Regex

/**
 * Validator for Docker best practices and anti-patterns.
 * Detects issues like unpinned tags, root user execution, missing health checks,
 * and inefficient layer stacking.
 * Built with zero reflection for GraalVM Native Image compatibility.
 */
object DockerValidator:

  /**
   * Validate a Dockerfile from a file path.
   */
  def validateFile(filePath: String): CumulusResponse[Seq[ContainerValidationIssue]] =
    try
      val path = os.Path(filePath, os.pwd)
      if !os.exists(path) || !os.isFile(path) then
        return CumulusResponse(
          success = false,
          data = None,
          error = Some(s"File not found or not a file: $filePath"),
          error_code = Some("FILE_NOT_FOUND")
        )
      val content = os.read(path)
      val issues = validateContent(content)
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
          error = Some(s"Error validating Dockerfile: ${e.getMessage}"),
          error_code = Some("PARSE_ERROR")
        )

  /**
   * Validate Dockerfile content from a string (e.g., stdin).
   */
  def validateContent(content: String): Seq[ContainerValidationIssue] =
    if content.trim.isEmpty then return Seq.empty

    val lines = content.linesIterator.toList
    val issues = ListBuffer[ContainerValidationIssue]()

    val untaggedBaseImages = MSet[Int]() // Line numbers of untagged base images
    val runAsRoot = MSet[Int]()           // Line numbers where USER root or implicit root
    var hasHealthcheck = false
    var hasCmd = false
    var hasEntrypoint = false
    val runInstructions = MSet[Int]()     // Line numbers of RUN instructions

    var idx = 1
    for line <- lines do
      val trimmed = line.trim

      // Skip comments and empty lines
      if !trimmed.startsWith("#") && trimmed.nonEmpty then
        // Check FROM instruction for unpinned tags
        if trimmed.toUpperCase.startsWith("FROM") then
          val fromMatch = """(?i)FROM\s+([^\s]+)""".r.findFirstMatchIn(trimmed)
          fromMatch match
            case Some(m) =>
              val baseImage = m.group(1)
              if !baseImage.contains(":") && !baseImage.contains("@") then
                // Unpinned image (no tag or digest)
                issues += ContainerValidationIssue(
                  issue_type = "UNPINNED_BASE_IMAGE",
                  severity = "ERROR",
                  line = idx,
                  description = s"Base image '$baseImage' is not pinned to a specific version (no ':tag' or '@sha256:...')",
                  remediation = s"Pin the image to a specific version: 'FROM $baseImage:22.04' or use a digest: 'FROM $baseImage@sha256:...'"
                )
              else if baseImage.endsWith(":latest") || baseImage.endsWith(":master") then
                // Floating tag
                issues += ContainerValidationIssue(
                  issue_type = "UNPINNED_BASE_IMAGE",
                  severity = "ERROR",
                  line = idx,
                  description = s"Base image '$baseImage' uses a floating/unstable tag (':latest' or ':master')",
                  remediation = s"Use a specific stable version instead of ':latest' (e.g., use ':22.04' for ubuntu or '@sha256:...' for digest-pinned)"
                )
            case None => ()

        // Check USER instruction
        if trimmed.toUpperCase.startsWith("USER") then
          val userMatch = """(?i)USER\s+([^\s]+)""".r.findFirstMatchIn(trimmed)
          userMatch match
            case Some(m) =>
              val usr = m.group(1)
              if usr.equalsIgnoreCase("root") then
                runAsRoot += idx
            case None => ()

        // Check for RUN instructions (for layer stacking check)
        if trimmed.toUpperCase.startsWith("RUN") then
          runInstructions += idx

        // Check for HEALTHCHECK
        if trimmed.toUpperCase.startsWith("HEALTHCHECK") then
          hasHealthcheck = true

        // Check for CMD and ENTRYPOINT
        if trimmed.toUpperCase.startsWith("CMD") then
          hasCmd = true

        if trimmed.toUpperCase.startsWith("ENTRYPOINT") then
          hasEntrypoint = true

      idx += 1

    // Issue: RUNS_AS_ROOT if USER root is explicit
    for line <- runAsRoot do
      issues += ContainerValidationIssue(
        issue_type = "RUNS_AS_ROOT",
        severity = "ERROR",
        line = line,
        description = "Container runs as root user, which is a security risk",
        remediation = "Create a non-root user and switch to it: RUN useradd -m appuser && USER appuser"
      )

    // Issue: RUNS_AS_ROOT if no USER directive (implicit root)
    if runAsRoot.isEmpty && !content.toUpperCase.contains("USER ") then
      val firstCodeLine = lines.indexWhere(l => !l.trim.startsWith("#") && l.trim.nonEmpty)
      if firstCodeLine >= 0 then
        issues += ContainerValidationIssue(
          issue_type = "RUNS_AS_ROOT",
          severity = "ERROR",
          line = firstCodeLine + 1,
          description = "No USER directive found; container will run as root by default",
          remediation = "Add a USER directive: RUN useradd -m appuser && USER appuser"
        )

    // Issue: MISSING_HEALTHCHECK if CMD or ENTRYPOINT present without HEALTHCHECK
    if (hasCmd || hasEntrypoint) && !hasHealthcheck then
      val cmdLine = if hasCmd then lines.indexWhere(l => l.toUpperCase.contains("CMD")) + 1 else 0
      val epLine = if hasEntrypoint then lines.indexWhere(l => l.toUpperCase.contains("ENTRYPOINT")) + 1 else 0
      val line = if cmdLine > 0 && epLine > 0 then Math.min(cmdLine, epLine) else if cmdLine > 0 then cmdLine else epLine

      if line > 0 then
        issues += ContainerValidationIssue(
          issue_type = "MISSING_HEALTHCHECK",
          severity = "WARN",
          line = line,
          description = "Long-running container has no HEALTHCHECK directive",
          remediation = "Add a HEALTHCHECK: HEALTHCHECK --interval=30s CMD curl -f http://localhost:8080/health || exit 1"
        )

    // Issue: INEFFICIENT_RUN_LAYERS if multiple RUN instructions in sequence
    if runInstructions.size > 1 then
      val sortedRuns = runInstructions.toSeq.sorted
      var consecutiveCount = 0
      var sequenceStart = 0

      for i <- 0 until sortedRuns.length do
        val curr = sortedRuns(i)
        val nextIdx = if i + 1 < sortedRuns.length then Some(i + 1) else None

        val gap = nextIdx match
          case Some(nextI) => sortedRuns(nextI) - curr
          case None => Int.MaxValue

        if gap <= 2 then // RUN instructions are consecutive or one line apart
          if consecutiveCount == 0 then
            sequenceStart = curr
          consecutiveCount += 1
        else
          // End of sequence
          if consecutiveCount > 0 then
            issues += ContainerValidationIssue(
              issue_type = "INEFFICIENT_RUN_LAYERS",
              severity = "WARN",
              line = sequenceStart,
              description = "Multiple RUN commands in sequence can be combined to reduce final image layers",
              remediation = "Combine RUN commands using &&: RUN cmd1 && cmd2 && cmd3"
            )
          consecutiveCount = 0

    // Issue: MISSING_WORKDIR if no WORKDIR directive
    if !content.toUpperCase.contains("WORKDIR") then
      val firstCodeLine = lines.indexWhere(l => !l.trim.startsWith("#") && l.trim.nonEmpty)
      if firstCodeLine >= 0 then
        issues += ContainerValidationIssue(
          issue_type = "MISSING_WORKDIR",
          severity = "WARN",
          line = firstCodeLine + 1,
          description = "No WORKDIR directive found; all operations will run in the root (/) directory",
          remediation = "Set a WORKDIR: WORKDIR /app"
        )

    issues.toSeq.sortBy(_.line)
