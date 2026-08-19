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

// Theme highlight group models
case class HighlightGroup(
  fg: Option[String] = None,
  bg: Option[String] = None,
  bold: Boolean = false,
  italic: Boolean = false,
  underline: Boolean = false
)

object HighlightGroup:
  given HighlightGroupRW: ReadWriter[HighlightGroup] = upickle.default.readwriter[ujson.Value].bimap[HighlightGroup](
    group => ujson.Obj(
      "fg" -> group.fg.fold(ujson.Null: ujson.Value)(ujson.Str(_)),
      "bg" -> group.bg.fold(ujson.Null: ujson.Value)(ujson.Str(_)),
      "bold" -> ujson.Bool(group.bold),
      "italic" -> ujson.Bool(group.italic),
      "underline" -> ujson.Bool(group.underline)
    ),
    json => {
      val obj = json.obj
      HighlightGroup(
        fg = obj.get("fg").flatMap {
          case ujson.Str(s) => Some(s)
          case ujson.Null => None
          case _ => None
        },
        bg = obj.get("bg").flatMap {
          case ujson.Str(s) => Some(s)
          case ujson.Null => None
          case _ => None
        },
        bold = obj.get("bold").exists { case ujson.Bool(b) => b; case _ => false },
        italic = obj.get("italic").exists { case ujson.Bool(b) => b; case _ => false },
        underline = obj.get("underline").exists { case ujson.Bool(b) => b; case _ => false }
      )
    }
  )

case class ThemeHighlights(
  provider: String,
  base_color: String,
  highlights: Map[String, HighlightGroup]
)

object ThemeHighlights:
  given ThemeHighlightsRW: ReadWriter[ThemeHighlights] = upickle.default.readwriter[ujson.Value].bimap[ThemeHighlights](
    theme => {
      val highlightObjs = upickle.core.LinkedHashMap[String, ujson.Value]()
      for (name, group) <- theme.highlights do
        highlightObjs(name) = ujson.Obj(
          "fg" -> group.fg.fold(ujson.Null: ujson.Value)(ujson.Str(_)),
          "bg" -> group.bg.fold(ujson.Null: ujson.Value)(ujson.Str(_)),
          "bold" -> ujson.Bool(group.bold),
          "italic" -> ujson.Bool(group.italic),
          "underline" -> ujson.Bool(group.underline)
        )
      ujson.Obj(
        "provider" -> ujson.Str(theme.provider),
        "base_color" -> ujson.Str(theme.base_color),
        "highlights" -> ujson.Obj(highlightObjs)
      )
    },
    json => {
      val obj = json.obj
      val provider = obj.get("provider") match {
        case Some(ujson.Str(s)) => s
        case _ => ""
      }
      val baseColor = obj.get("base_color") match {
        case Some(ujson.Str(s)) => s
        case _ => "#000000"
      }
      val highlights = obj.get("highlights") match {
        case Some(ujson.Obj(highlightsMap)) =>
          highlightsMap.map { case (groupName, groupJson) =>
            val groupObj = groupJson.obj
            val group = HighlightGroup(
              fg = groupObj.get("fg").flatMap {
                case ujson.Str(s) => Some(s)
                case ujson.Null => None
                case _ => None
              },
              bg = groupObj.get("bg").flatMap {
                case ujson.Str(s) => Some(s)
                case ujson.Null => None
                case _ => None
              },
              bold = groupObj.get("bold").exists { case ujson.Bool(b) => b; case _ => false },
              italic = groupObj.get("italic").exists { case ujson.Bool(b) => b; case _ => false },
              underline = groupObj.get("underline").exists { case ujson.Bool(b) => b; case _ => false }
            )
            (groupName, group)
          }.toMap
        case _ => Map.empty
      }
      ThemeHighlights(provider, baseColor, highlights)
    }
  )

// Keymap gate evaluation models
case class GateContext(
  buftype: Option[String] = None,
  filetype: Option[String] = None,
  mode: Option[String] = None,
  project_type: Option[String] = None,
  lsp_active: Option[Boolean] = None,
  debug_mode: Option[Boolean] = None,
  test_context: Option[Boolean] = None
)

object GateContext:
  // Custom ReadWriter needed for proper Option field handling in partial JSON
  given GateContextRW: ReadWriter[GateContext] = upickle.default.readwriter[ujson.Value].bimap[GateContext](
    ctx => ujson.Obj(
      "buftype" -> ctx.buftype.fold(ujson.Null: ujson.Value)(ujson.Str(_)),
      "filetype" -> ctx.filetype.fold(ujson.Null: ujson.Value)(ujson.Str(_)),
      "mode" -> ctx.mode.fold(ujson.Null: ujson.Value)(ujson.Str(_)),
      "project_type" -> ctx.project_type.fold(ujson.Null: ujson.Value)(ujson.Str(_)),
      "lsp_active" -> ctx.lsp_active.fold(ujson.Null: ujson.Value)(ujson.Bool(_)),
      "debug_mode" -> ctx.debug_mode.fold(ujson.Null: ujson.Value)(ujson.Bool(_)),
      "test_context" -> ctx.test_context.fold(ujson.Null: ujson.Value)(ujson.Bool(_))
    ),
    json => {
      val obj = json.obj
      GateContext(
        buftype = obj.get("buftype").flatMap { case ujson.Str(s) => Some(s); case ujson.Null => None; case _ => None },
        filetype = obj.get("filetype").flatMap { case ujson.Str(s) => Some(s); case ujson.Null => None; case _ => None },
        mode = obj.get("mode").flatMap { case ujson.Str(s) => Some(s); case ujson.Null => None; case _ => None },
        project_type = obj.get("project_type").flatMap { case ujson.Str(s) => Some(s); case ujson.Null => None; case _ => None },
        lsp_active = obj.get("lsp_active").flatMap { case ujson.Bool(b) => Some(b); case ujson.Null => None; case _ => None },
        debug_mode = obj.get("debug_mode").flatMap { case ujson.Bool(b) => Some(b); case ujson.Null => None; case _ => None },
        test_context = obj.get("test_context").flatMap { case ujson.Bool(b) => Some(b); case ujson.Null => None; case _ => None }
      )
    }
  )

case class GateEvalResult(
  gate_active: Boolean
) derives ReadWriter

// JDTLS configuration models
case class JdkRuntime(
  version: String,
  path: String,
  is_default: Boolean
) derives ReadWriter

case class JdtlsBundle(
  path: String,
  valid: Boolean,
  config_dir: Option[String]
) derives ReadWriter

case class JdtlsConfig(
  jdtls_path: Option[String],
  bundle_valid: Boolean,
  classpath: Seq[String],
  jvm_runtimes: Seq[JdkRuntime],
  jvm_args: Seq[String],
  init_options: Map[String, String],
  notes: Option[Seq[String]]
) derives ReadWriter

// Toolchain matrix models
case class ToolSpec(
  name: String,
  version: Option[String] = None,
  path: Option[String] = None,
  status: String = "available" // "available", "missing", "unknown"
) derives ReadWriter

case class MasonPackageSpec(
  name: String,
  mason_name: String,
  min_version: String,
  installation_method: String = "mason",
  tags: Seq[String] = Seq()
) derives ReadWriter

case class FormatterInfo(
  name: String,
  mason_name: String,
  version: Option[String] = None,
  cli_args: Seq[String] = Seq(),
  status: String = "available"
) derives ReadWriter

case class LanguageServerInfo(
  name: String,
  mason_name: String,
  min_version: String,
  version: Option[String] = None,
  status: String = "available", // "compatible", "incompatible", "unknown"
  diagnostic_message: Option[String] = None
) derives ReadWriter

case class DebuggerInfo(
  name: String,
  mason_name: String,
  version: Option[String] = None,
  status: String = "available",
  diagnostic_message: Option[String] = None
) derives ReadWriter

case class ToolchainMatrix(
  language: String,
  available_tools: Seq[String],
  build_tools: Seq[ToolSpec] = Seq(),
  formatters: Seq[FormatterInfo] = Seq(),
  language_servers: Seq[LanguageServerInfo] = Seq(),
  debuggers: Seq[DebuggerInfo] = Seq(),
  diagnostic_warnings: Seq[String] = Seq()
) derives ReadWriter

// LSP Specification models
case class LspCapability(
  name: String,
  supported: Boolean = true
) derives ReadWriter

case class TreeSitterParserSpec(
  parser: String,
  queries: String = "standard", // "standard", "extended", "minimal"
  installed: Boolean = false
) derives ReadWriter

case class LspSpec(
  language: String,
  lsp_name: Option[String],
  lsp_path: Option[String],
  capabilities: Seq[LspCapability] = Seq(),
  binary_valid: Boolean = true,
  treesitter: Seq[TreeSitterParserSpec] = Seq(),
  mason_spec: Option[MasonPackageSpec] = None,
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


