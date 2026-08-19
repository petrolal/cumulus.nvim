package cumulus.format

import cumulus.protocol.{FormatterSpec, FormatterArg}
import os.Path
import scala.util.Try
import scala.xml.XML

/**
 * Detects and configures Scalafmt formatter from .scalafmt.conf
 */
object ScalafmtResolver:

  def resolve(rootPath: Path): Option[FormatterSpec] =
    try
      val configFile = rootPath / ".scalafmt.conf"
      if !os.exists(configFile) || !os.isFile(configFile) then
        return None

      // Parse configuration file for indent size and other options
      val configContent = os.read(configFile)
      val indentSize = parseIndentSize(configContent)

      val args = scala.collection.mutable.Buffer[FormatterArg]()
      args += FormatterArg("check", "true")

      if indentSize.isDefined then
        args += FormatterArg("indent", indentSize.get.toString)

      Some(FormatterSpec(
        formatter = "scalafmt",
        config_file = Some(".scalafmt.conf"),
        args = args.toSeq,
        stdin_mode = true,
        notes = None
      ))
    catch
      case _: Exception => None

  /**
   * Parses indent_size from .scalafmt.conf file
   * Example: `indent {
   *            main = 2
   *          }`
   */
  private def parseIndentSize(content: String): Option[Int] =
    try
      // Look for patterns like "indent { main = 2 }" or just "indent = 2"
      val indentPattern = """indent\s*(?:\{\s*)?main\s*=\s*(\d+)""".r
      indentPattern.findFirstMatchIn(content).map(_.group(1).toInt)
    catch
      case _: Exception => None
