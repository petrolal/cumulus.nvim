package cumulus.format

import cumulus.protocol.{FormatterSpec, FormatterArg}
import os.Path

/**
 * Detects and configures Prettier formatter from .prettierrc* files
 */
object PrettierResolver:

  def resolve(rootPath: Path): Option[FormatterSpec] =
    try
      // Check for any Prettier config variant
      val configVariants = Seq(
        ".prettierrc",
        ".prettierrc.json",
        ".prettierrc.yaml",
        ".prettierrc.yml",
        ".prettierrc.js",
        ".prettierrc.cjs",
        "prettier.config.js",
        "prettier.config.cjs"
      )

      var configFile: Option[String] = None
      for variant <- configVariants do
        val path = rootPath / variant
        if os.exists(path) && os.isFile(path) then
          configFile = Some(variant)

      if configFile.isEmpty then
        // Check for prettier config in package.json
        val pkgJsonPath = rootPath / "package.json"
        if os.exists(pkgJsonPath) && os.isFile(pkgJsonPath) then
          val content = os.read(pkgJsonPath)
          if content.contains("\"prettier\"") then
            configFile = Some("package.json")

      if configFile.isEmpty then
        return None

      val args = scala.collection.mutable.Buffer[FormatterArg]()
      args += FormatterArg("write", "true")

      Some(FormatterSpec(
        formatter = "prettier",
        config_file = configFile,
        args = args.toSeq,
        stdin_mode = true,
        notes = None
      ))
    catch
      case _: Exception => None
