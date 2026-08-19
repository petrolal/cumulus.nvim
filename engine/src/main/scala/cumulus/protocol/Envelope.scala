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

// Represents a single module in a multi-module project
case class Module(
  name: String,
  path: String,
  relativePath: Option[String] = None
) derives ReadWriter

// Represents the complete module tree for a multi-module project
case class ModuleTree(
  root: String,
  modules: Seq[Module],
  dependencies: Option[Map[String, Seq[String]]] = None
) derives ReadWriter

// Health check related models
case class BinaryInfo(
  name: String,
  version: Option[String],
  status: String, // "ok", "missing", "error"
  path: Option[String]
) derives ReadWriter

case class CompilerInfo(
  name: String,
  version: Option[String]
) derives ReadWriter

case class JdkInfo(
  version: String,
  path: String,
  active: Boolean = false
) derives ReadWriter

case class GradleWrapperInfo(
  local_version: Option[String],
  ci_version: Option[String],
  sha256_configured: Boolean,
  sha256_valid: Boolean,
  issues: Seq[String]
) derives ReadWriter

case class HealthReport(
  binaries: Seq[BinaryInfo],
  compilers: Seq[CompilerInfo],
  jdks: Seq[JdkInfo],
  gradle_wrapper: Option[GradleWrapperInfo],
  build_tool: String, // "maven", "gradle", "sbt", "none"
  engine_available: Boolean,
  notes: Option[Seq[String]] = None
) derives ReadWriter

// Formatter specification models
case class FormatterArg(
  name: String,
  value: String
) derives ReadWriter

case class FormatterSpec(
  formatter: String,
  config_file: Option[String],
  args: Seq[FormatterArg],
  stdin_mode: Boolean,
  notes: Option[Seq[String]] = None
) derives ReadWriter

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


