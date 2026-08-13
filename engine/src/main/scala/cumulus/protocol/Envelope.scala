package cumulus.protocol

import upickle.default.{ReadWriter, macroRW, write, read}
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
  // Custom serialization to handle Option as null instead of arrays
  def toJson[T: ReadWriter](response: CumulusResponse[T]): Value =
    ujson.Obj(
      "success" -> ujson.Bool(response.success),
      "data" -> response.data.fold(ujson.Null: Value)(v => read[Value](write(v))),
      "error" -> response.error.fold(ujson.Null: Value)(ujson.Str(_)),
      "error_code" -> response.error_code.fold(ujson.Null: Value)(ujson.Str(_))
    )

  def fromJson[T: ReadWriter](value: Value): CumulusResponse[T] =
    val obj = value.obj
    CumulusResponse(
      success = obj.get("success").map(_.bool).getOrElse(false),
      data = obj.get("data") match
        case Some(ujson.Null) | None => None
        case Some(v) => Some(read[T](ujson.write(v)))
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
