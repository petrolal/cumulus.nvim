package cumulus.workspace

import upickle.default.{ReadWriter, macroRW}
import scala.util.Try
import scala.sys.process.*

case class InstallResult(
  success: Boolean,
  target_dir: String,
  message: String
) derives ReadWriter

object DistroInstaller:
  def runInstall(
    targetDirOpt: Option[String] = None,
    appnameOpt: Option[String] = None,
    repoUrlOpt: Option[String] = None,
    skipSystemPackages: Boolean = false
  ): Either[String, InstallResult] =
    try
      val userHome = System.getProperty("user.home")
      val xdgConfig = sys.env.getOrElse("XDG_CONFIG_HOME", s"$userHome/.config")
      
      val targetPath = targetDirOpt match
        case Some(d) => os.Path(d, os.pwd)
        case None =>
          appnameOpt match
            case Some(name) => os.Path(s"$xdgConfig/$name")
            case None       => os.Path(s"$xdgConfig/nvim")

      val repoUrl = repoUrlOpt.getOrElse("https://github.com/petrolal/cumulus.nvim.git")

      println(s"=== Cumulus Neovim CLI Installer ===")
      println(s"Target Directory : $targetPath")
      println(s"Repository       : $repoUrl")

      // 1. Clone or update repository
      if !os.exists(targetPath) then
        println(s"[1/2] Cloning repository to $targetPath...")
        val cloneExit = Process(Seq("git", "clone", repoUrl, targetPath.toString)).!
        if cloneExit != 0 then
          return Left(s"Failed to clone repository from $repoUrl to $targetPath")
      else if os.exists(targetPath / ".git") then
        println(s"[1/2] Repository exists at $targetPath. Fetching latest changes...")
        val pullExit = Process(Seq("git", "pull", "--ff-only"), new java.io.File(targetPath.toString)).!
        if pullExit != 0 then
          println("  ⚠ 'git pull' returned non-zero exit code. Continuing with existing files.")
      else
        println(s"[1/2] Target directory $targetPath exists. Skipping clone.")

      // 2. Run bootstrap script
      val scriptName = if skipSystemPackages then "scripts/install.sh" else "bootstrap.sh"
      val scriptPath = targetPath / os.RelPath(scriptName)

      if !os.exists(scriptPath) then
        return Left(s"Installer script not found at $scriptPath")

      println(s"[2/2] Executing $scriptName...")
      val scriptExit = Process(Seq("bash", scriptPath.toString), new java.io.File(targetPath.toString)).!
      if scriptExit != 0 then
        return Left(s"Bootstrap script $scriptName failed with exit code $scriptExit")

      Right(InstallResult(
        success = true,
        target_dir = targetPath.toString,
        message = s"Cumulus Neovim successfully installed to $targetPath"
      ))
    catch
      case e: Exception =>
        Left(s"Install failed with exception: ${e.getMessage}")
