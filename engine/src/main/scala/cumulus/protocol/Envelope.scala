package cumulus.protocol

import upickle.default.{ReadWriter, macroRW, writeJs, read}
import ujson.Value

// Error codes for standardized error responses
enum CumulusError derives ReadWriter:
  case FILE_NOT_FOUND
  case PARSE_ERROR
  case INVALID_INPUT
  case NETWORK_ERROR
  case TIMEOUT
  case INTERNAL_ERROR

// Generic response envelope for all CLI operations
case class CumulusResponse[T](
  success: Boolean,
  data: Option[T],
  error: Option[String],
  error_code: Option[String]
)

object CumulusResponse:
  // Reusable ReadWriter for Unit responses (e.g. ping)
  given unitRW: ReadWriter[Unit] = upickle.default.readwriter[String].bimap[Unit](
    _ => "null",
    _ => ()
  )

  // Custom serialization to handle Option as null instead of arrays (avoiding string roundtrips)
  def toJson[T: ReadWriter](response: CumulusResponse[T]): Value =
    ujson.Obj(
      "success" -> ujson.Bool(response.success),
      "data" -> response.data.fold(ujson.Null: Value)(v => writeJs(v)),
      "error" -> response.error.fold(ujson.Null: Value)(ujson.Str(_)),
      "error_code" -> response.error_code.fold(ujson.Null: Value)(ujson.Str(_))
    )

  def fromJson[T: ReadWriter](value: Value): CumulusResponse[T] =
    val obj: scala.collection.mutable.Map[String, Value] = scala.util.Try(value.obj).getOrElse(scala.collection.mutable.Map.empty)
    CumulusResponse(
      success = obj.get("success").flatMap(v => scala.util.Try(v.bool).toOption).getOrElse(false),
      data = obj.get("data") match
        case Some(ujson.Null) | None => None
        case Some(v) => scala.util.Try(read[T](v)).toOption
      ,
      error = obj.get("error") match
        case Some(ujson.Null) | None => None
        case Some(ujson.Str(s)) => Some(s)
        case _ => None
      ,
      error_code = obj.get("error_code") match
        case Some(ujson.Null) | None => None
        case Some(ujson.Str(s)) => Some(s)
        case _ => None
    )


