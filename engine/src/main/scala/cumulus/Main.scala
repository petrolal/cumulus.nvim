package cumulus

import cumulus.protocol.{CumulusResponse, CumulusError}
import cumulus.build.{MavenParser, GradleParser, ParsePomResponse, ParseGradleTasksResponse, ParseModulesResponse, ParseGradleModulesResponse}
import upickle.default.ReadWriter
import scala.io.Source

object Main:
  // Helper functions to construct response envelopes
  def successEnvelope[T](data: Option[T]): CumulusResponse[T] =
    CumulusResponse(
      success = true,
      data = data,
      error = None,
      error_code = None
    )

  def errorEnvelope[T](message: String, code: CumulusError = CumulusError.INVALID_INPUT): CumulusResponse[T] =
    CumulusResponse(
      success = false,
      data = None,
      error = Some(message),
      error_code = Some(code.toString)
    )

  /**
   * Parse command-line arguments as key-value pairs.
   * Example: ["--file", "pom.xml", "--tool", "maven"] becomes Map("file" -> "pom.xml", "tool" -> "maven")
   */
  def parseArgs(args: Seq[String]): Map[String, String] =
    args.sliding(2, 1).collect {
      case Seq(key, value) if key.startsWith("--") => (key.substring(2), value)
    }.toMap

  def serializeResponse[T: ReadWriter](response: CumulusResponse[T]): Unit =
    try
      println(ujson.write(CumulusResponse.toJson(response)))
    catch
      case e: Exception =>
        // On serialization error, return an error envelope to stdout (not a stack trace)
        val errorResponse = CumulusResponse[Unit](
          success = false,
          data = None,
          error = Some(s"Serialization error: ${e.getMessage}"),
          error_code = Some("INTERNAL_ERROR")
        )
        given ReadWriter[Unit] = upickle.default.readwriter[String].bimap[Unit](
          _ => "null",
          _ => ()
        )
        println(ujson.write(CumulusResponse.toJson(errorResponse)))

  def main(args: Array[String]): Unit =
    if args.isEmpty then
      given ReadWriter[Unit] = upickle.default.readwriter[String].bimap[Unit](
        _ => "null",
        _ => ()
      )
      serializeResponse(errorEnvelope[Unit]("No subcommand provided"))
    else
      args(0) match
        case "ping" =>
          given ReadWriter[Unit] = upickle.default.readwriter[String].bimap[Unit](
            _ => "null",
            _ => ()
          )
          serializeResponse(successEnvelope[Unit](None))

        case "parse-pom" =>
          val argMap = parseArgs(args.slice(1, args.length))
          given ReadWriter[ParsePomResponse] = upickle.default.macroRW
          val result = argMap.get("file") match
            case None =>
              errorEnvelope[ParsePomResponse]("Missing --file argument")
            case Some(filePath) =>
              MavenParser.parseGoals(filePath)
          serializeResponse(result)

        case "parse-gradle-tasks" =>
          given ReadWriter[ParseGradleTasksResponse] = upickle.default.macroRW
          try
            val stdinInput = Source.fromInputStream(System.in).mkString
            val result = GradleParser.parseTasks(stdinInput)
            serializeResponse(result)
          catch
            case e: java.io.IOException =>
              serializeResponse(CumulusResponse(
                success = false,
                data = None,
                error = Some(s"IO error reading stdin: ${e.getMessage}"),
                error_code = Some("IO_ERROR")
              ))

        case "parse-modules" =>
          val argMap = parseArgs(args.slice(1, args.length))
          (argMap.get("tool"), argMap.get("file")) match
            case (None, _) =>
              given ReadWriter[ParseModulesResponse] = upickle.default.macroRW
              serializeResponse(errorEnvelope[ParseModulesResponse]("Missing --tool argument"))
            case (_, None) =>
              given ReadWriter[ParseModulesResponse] = upickle.default.macroRW
              serializeResponse(errorEnvelope[ParseModulesResponse]("Missing --file argument"))
            case (Some("maven"), Some(filePath)) =>
              given ReadWriter[ParseModulesResponse] = upickle.default.macroRW
              val result = MavenParser.parseModules(filePath)
              serializeResponse(result)
            case (Some("gradle"), Some(filePath)) =>
              given ReadWriter[ParseGradleModulesResponse] = upickle.default.macroRW
              val result = GradleParser.parseModules(filePath)
              serializeResponse(result)
            case (Some(tool), _) =>
              given ReadWriter[ParseModulesResponse] = upickle.default.macroRW
              serializeResponse(errorEnvelope[ParseModulesResponse](s"Unsupported tool: $tool"))

        case _ =>
          given ReadWriter[Unit] = upickle.default.readwriter[String].bimap[Unit](
            _ => "null",
            _ => ()
          )
          serializeResponse(errorEnvelope[Unit]("Unknown subcommand"))
