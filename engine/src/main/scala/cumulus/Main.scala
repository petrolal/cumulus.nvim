package cumulus

import cumulus.protocol.{CumulusResponse, CumulusError}
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

  def main(args: Array[String]): Unit =
    val result: CumulusResponse[Unit] = if args.isEmpty then
      errorEnvelope[Unit]("No subcommand provided")
    else
      args(0) match
        case "ping" => successEnvelope[Unit](None)
        case _ => errorEnvelope[Unit]("Unknown subcommand")

    // Write JSON to stdout
    given ReadWriter[Unit] = upickle.default.readwriter[String].bimap[Unit](
      _ => "null",
      _ => ()
    )
    println(ujson.write(CumulusResponse.toJson(result)))
