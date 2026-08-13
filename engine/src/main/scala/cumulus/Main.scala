package cumulus

import cumulus.protocol.{CumulusResponse, CumulusError}
import cumulus.build.{MavenParser, ParsePomResponse, ParseModulesResponse}
import upickle.default.ReadWriter

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

        case "parse-modules" =>
          val argMap = parseArgs(args.slice(1, args.length))
          given ReadWriter[ParseModulesResponse] = upickle.default.macroRW
          val result = (argMap.get("tool"), argMap.get("file")) match
            case (None, _) =>
              errorEnvelope[ParseModulesResponse]("Missing --tool argument")
            case (_, None) =>
              errorEnvelope[ParseModulesResponse]("Missing --file argument")
            case (Some("maven"), Some(filePath)) =>
              MavenParser.parseModules(filePath)
            case (Some(tool), _) =>
              errorEnvelope[ParseModulesResponse](s"Unsupported tool: $tool")
          serializeResponse(result)

        case _ =>
          given ReadWriter[Unit] = upickle.default.readwriter[String].bimap[Unit](
            _ => "null",
            _ => ()
          )
          serializeResponse(errorEnvelope[Unit]("Unknown subcommand"))
