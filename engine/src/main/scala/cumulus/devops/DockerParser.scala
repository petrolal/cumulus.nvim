package cumulus.devops

import cumulus.protocol.{CumulusError, CumulusResponse}
import os.Path
import scala.collection.mutable.{ListBuffer, Map as MMap, Set as MSet}
import scala.util.matching.Regex

/**
 * Native Scala 3 parser for Dockerfiles with multi-stage support.
 * Extracts build stages (FROM/AS blocks), base images, exposed ports, volumes,
 * entrypoint/CMD, health checks, user directives, and workspace directories.
 * Built with zero reflection for GraalVM Native Image compatibility.
 */
object DockerParser:

  // Regex patterns for Dockerfile instructions
  private val FromRegex = """(?i)^\s*FROM\s+([^\s]+)(?:\s+AS\s+([^\s]+))?\s*""".r
  private val ExposeRegex = """(?i)^\s*EXPOSE\s+(.+)\s*""".r
  private val VolumeRegex = """(?i)^\s*VOLUME\s+(.+)\s*""".r
  private val EntrypointRegex = """(?i)^\s*ENTRYPOINT\s+(.+)\s*""".r
  private val CmdRegex = """(?i)^\s*CMD\s+(.+)\s*""".r
  private val HealthcheckRegex = """(?i)^\s*HEALTHCHECK\s+(.+)\s*""".r
  private val UserRegex = """(?i)^\s*USER\s+([^\s]+)\s*""".r
  private val WorkdirRegex = """(?i)^\s*WORKDIR\s+([^\s]+)\s*""".r
  private val RunRegex = """(?i)^\s*RUN\s+(.+)\s*""".r
  private val CopyFromRegex = """(?i)^\s*COPY\s+--from=([^\s]+)\s+(.+)\s*""".r

  /**
   * Parse a Dockerfile from a file path.
   */
  def parseFile(filePath: String): CumulusResponse[DockerImage] =
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
      CumulusResponse(
        success = true,
        data = Some(parseContent(content)),
        error = None,
        error_code = None
      )
    catch
      case e: Exception =>
        CumulusResponse(
          success = false,
          data = None,
          error = Some(s"Error parsing Dockerfile: ${e.getMessage}"),
          error_code = Some("PARSE_ERROR")
        )

  /**
   * Parse Dockerfile content from a string (e.g., stdin).
   */
  def parseContent(content: String): DockerImage =
    if content.trim.isEmpty then return DockerImage()

    val lines = content.linesIterator.toList
    val stages = ListBuffer[DockerStage]()
    val exposedPorts = MSet[String]()
    val volumeList = MSet[String]()
    var entrypointValue: Option[String] = None
    var cmdValue: Option[String] = None
    var healthcheckValue: Option[HealthCheck] = None
    var userValue: Option[String] = None
    var workdirValue: Option[String] = None

    var currentStageBaseImage = ""
    var currentStageName: Option[String] = None
    var currentStageInstructions = ListBuffer[String]()

    var idx = 0
    while idx < lines.length do
      var processLine = lines(idx).trim
      idx += 1

      // Skip comments and empty lines
      if processLine.startsWith("#") || processLine.isEmpty then
        ()
      else
        // Handle line continuation if needed
        if processLine.endsWith("\\") then
          val baseContent = processLine.substring(0, processLine.length - 1).trim
          val (continuedLine, newIdx) = handleLineContinuation(lines, idx, baseContent)
          idx = newIdx
          processLine = continuedLine

        // Now process the (possibly continued) line
        processLine match
          // FROM instruction: starts a new stage or defines base image
          case FromRegex(baseImage, stageName) =>
            // Save previous stage if this isn't the first stage
            if currentStageBaseImage.nonEmpty then
              stages += DockerStage(
                name = currentStageName,
                base_image = currentStageBaseImage,
                instructions = currentStageInstructions.toSeq
              )
              currentStageInstructions.clear()

            currentStageBaseImage = baseImage
            currentStageName = Option(stageName)

          // EXPOSE instruction
          case ExposeRegex(ports) =>
            val portList = ports.split("""[\s,]+""").map(_.trim).filter(_.nonEmpty)
            exposedPorts ++= portList
            currentStageInstructions += processLine

          // VOLUME instruction
          case VolumeRegex(vols) =>
            val volList = vols.split("""[\s,]+""").map(_.trim).filter(_.nonEmpty)
            volumeList ++= volList
            currentStageInstructions += processLine

          // ENTRYPOINT instruction
          case EntrypointRegex(epCmd) =>
            entrypointValue = Some(epCmd)
            currentStageInstructions += processLine

          // CMD instruction
          case CmdRegex(cmdStr) =>
            cmdValue = Some(cmdStr)
            currentStageInstructions += processLine

          // HEALTHCHECK instruction
          case HealthcheckRegex(opts) =>
            healthcheckValue = Some(parseHealthcheck(opts))
            currentStageInstructions += processLine

          // USER instruction
          case UserRegex(usr) =>
            userValue = Some(usr)
            currentStageInstructions += processLine

          // WORKDIR instruction
          case WorkdirRegex(wd) =>
            workdirValue = Some(wd)
            currentStageInstructions += processLine

          // Other instructions
          case _ =>
            currentStageInstructions += processLine

    // Add the final stage
    if currentStageBaseImage.nonEmpty then
      stages += DockerStage(
        name = currentStageName,
        base_image = currentStageBaseImage,
        instructions = currentStageInstructions.toSeq
      )

    DockerImage(
      stages = stages.toSeq,
      exposed_ports = exposedPorts.toSeq.sorted,
      volumes = volumeList.toSeq.sorted,
      entrypoint = entrypointValue,
      cmd = cmdValue,
      healthcheck = healthcheckValue,
      user = userValue,
      workdir = workdirValue
    )

  /**
   * Handle Dockerfile line continuation (backslash at end of line).
   * Returns a tuple of (assembled_content, new_index).
   */
  private def handleLineContinuation(lines: scala.collection.Seq[String], startIdx: Int, baseContent: String): (String, Int) =
    var content = baseContent
    var idx = startIdx
    while idx < lines.length && lines(idx - 1).trim.endsWith("\\") do
      val nextLine = lines(idx).trim
      if nextLine.endsWith("\\") then
        content += " " + nextLine.substring(0, nextLine.length - 1).trim
      else
        content += " " + nextLine
      idx += 1
    (content, idx)

  /**
   * Parse HEALTHCHECK instruction options and command.
   */
  private def parseHealthcheck(opts: String): HealthCheck =
    val optionRegex = """--([a-z-]+)=([^\s]+)""".r
    var interval: Option[String] = None
    var timeout: Option[String] = None
    var startPeriod: Option[String] = None
    var retries: Option[Int] = None
    var cmd: Option[String] = None

    var remaining = opts
    for m <- optionRegex.findAllMatchIn(opts) do
      m.group(1) match
        case "interval" => interval = Some(m.group(2))
        case "timeout" => timeout = Some(m.group(2))
        case "start-period" => startPeriod = Some(m.group(2))
        case "retries" => retries = m.group(2).toIntOption
        case _ => ()
      remaining = remaining.replace(m.matched, "")

    // Extract CMD from remaining content
    val cmdPart = remaining.trim
    if cmdPart.nonEmpty && cmdPart.toUpperCase.startsWith("CMD") then
      cmd = Some(cmdPart.substring("CMD".length).trim)

    HealthCheck(
      interval = interval,
      timeout = timeout,
      start_period = startPeriod,
      retries = retries,
      cmd = cmd
    )
