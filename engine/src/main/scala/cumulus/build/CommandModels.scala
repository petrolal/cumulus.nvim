package cumulus.build

import upickle.default.{ReadWriter, macroRW}

/**
 * Structured intent specification for assembling a CLI command.
 *
 * @param tool The build or DevOps tool (e.g. "maven", "gradle", "sbt", "terraform", "tofu", "sam", "cfn", "ansible", "docker", "helm")
 * @param action The action or goal/task to perform (e.g. "build", "test", "plan", "apply", "run", "lint", etc.)
 * @param target Optional target specification (e.g. module, subproject, playbook, Dockerfile, template, chart, etc.)
 * @param dir Optional project root or working directory
 * @param flags Optional sequence of CLI flags/options
 * @param env Optional environment variables
 */
case class CommandIntent(
  tool: String,
  action: String,
  target: Option[String] = None,
  dir: Option[String] = None,
  flags: Seq[String] = Seq.empty,
  env: Map[String, String] = Map.empty
) derives ReadWriter

object CommandIntent:
  given [T](using rw: ReadWriter[T]): ReadWriter[Option[T]] =
    upickle.default.readwriter[ujson.Value].bimap[Option[T]](
      {
        case Some(v) => upickle.default.writeJs(v)
        case None => ujson.Null
      },
      {
        case ujson.Null => None
        case ujson.Arr(arr) if arr.isEmpty => None
        case ujson.Arr(arr) if arr.nonEmpty => Some(upickle.default.read[T](arr.head))
        case other => Some(upickle.default.read[T](other))
      }
    )

/**
 * Result of command assembly.
 *
 * @param command The full escaped command string ready for shell execution
 * @param executable The primary executable path or binary name (e.g. "./mvnw", "terraform")
 * @param args The individual argument tokens
 * @param cwd The recommended working directory (absolute path)
 * @param env The environment variables to pass
 */
case class CommandAssemblyResult(
  command: String,
  executable: String,
  args: Seq[String] = Seq.empty,
  cwd: String,
  env: Map[String, String] = Map.empty
) derives ReadWriter
