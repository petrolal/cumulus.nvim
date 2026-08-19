package cumulus.format

import cumulus.protocol.{FormatterSpec, FormatterArg}
import os.Path

/**
 * Detects and configures Ktlint formatter from ktlint.json
 */
object KtlintResolver:

  def resolve(rootPath: Path): Option[FormatterSpec] =
    try
      val configFile = rootPath / "ktlint.json"
      if !os.exists(configFile) || !os.isFile(configFile) then
        return None

      // Parse the ktlint.json for configuration
      val configContent = os.read(configFile)

      val args = scala.collection.mutable.Buffer[FormatterArg]()
      // -F flag means format-in-place (apply formatting directly to file)
      args += FormatterArg("format", "true")

      Some(FormatterSpec(
        formatter = "ktlint",
        config_file = Some("ktlint.json"),
        args = args.toSeq,
        stdin_mode = false, // ktlint typically works on files, not stdin
        notes = None
      ))
    catch
      case _: Exception => None
