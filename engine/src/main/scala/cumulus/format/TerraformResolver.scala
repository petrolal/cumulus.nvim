package cumulus.format

import cumulus.protocol.{FormatterSpec, FormatterArg}
import os.Path

/**
 * Detects and configures Terraform formatter from .terraform.lock.hcl
 */
object TerraformResolver:

  def resolve(rootPath: Path): Option[FormatterSpec] =
    try
      // Check for Terraform lock file or .tf files
      val lockFile = rootPath / ".terraform.lock.hcl"
      val hasTfFiles = try
        os.list(rootPath).exists(p =>
          os.isFile(p) && p.last.endsWith(".tf")
        )
      catch
        case _: Exception => false

      // Check for .terraform directory
      val hasTerraformDir = try
        os.exists(rootPath / ".terraform") && os.isDir(rootPath / ".terraform")
      catch
        case _: Exception => false

      if !os.exists(lockFile) && !hasTfFiles && !hasTerraformDir then
        return None

      val args = scala.collection.mutable.Buffer[FormatterArg]()
      args += FormatterArg("action", "fmt")

      Some(FormatterSpec(
        formatter = "terraform",
        config_file = if os.exists(lockFile) then Some(".terraform.lock.hcl") else None,
        args = args.toSeq,
        stdin_mode = false, // terraform fmt works on files/directories
        notes = None
      ))
    catch
      case _: Exception => None
