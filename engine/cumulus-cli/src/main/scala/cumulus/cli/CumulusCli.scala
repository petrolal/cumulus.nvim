package cumulus.cli

import scala.sys.process.*
import scala.util.{Try, Success, Failure}
import java.nio.file.{Files, Paths}
import java.lang.ProcessBuilder as JavaProcessBuilder
import os.Path

object CumulusCli:
  private val VERSION = "0.1.0"

  def main(args: Array[String]): Unit = {
    val result = args.headOption match
      case Some("install") => installCumulus()
      case Some("update") => updateCumulus()
      case Some("--version") | Some("-v") => printVersion()
      case Some("--help") | Some("-h") => printHelp()
      case Some(cmd) if cmd.startsWith("-") =>
        // Pass through to nvim
        launchNvim(args)
      case Some(_) =>
        // Any other single command - pass to nvim
        launchNvim(args)
      case None =>
        // No args - just launch nvim
        launchNvim(Array.empty)

    System.exit(result)
  }

  private def installCumulus(): Int = {
    println("================================================")
    println("  Cumulus Neovim: Full Installation          ")
    println("================================================")
    println()

    val repoDir = findCumulusRepo()
    if (repoDir.isEmpty) {
      println("✖ Cumulus repository not found at ~/.config/nvim")
      println("  Clone and try again:")
      println("  git clone https://github.com/petrolal/cumulus.nvim ~/.config/nvim")
      return 1
    }

    val repo = repoDir.get
    println(s"✓ Found Cumulus at: $repo")
    println()

    // Run the installation script
    val scriptPath = s"$repo/scripts/install-cn.sh"
    if (!os.exists(Path(scriptPath))) {
      println(s"✖ Installation script not found: $scriptPath")
      return 1
    }

    try {
      val exitCode = os.proc(Seq("bash", scriptPath)).call().exitCode
      exitCode
    } catch {
      case e: Exception =>
        println(s"✖ Installation failed: ${e.getMessage}")
        1
    }
  }

  private def updateCumulus(): Int = {
    println("Updating Cumulus Neovim...")
    installCumulus() // update is same as install
  }

  private def launchNvim(args: Array[String]): Int = {
    try {
      val allArgs = ("nvim" +: args).toSeq
      val exitCode = os.proc(allArgs).call().exitCode
      exitCode
    } catch {
      case e: Exception =>
        println(s"✖ Failed to launch nvim: ${e.getMessage}")
        1
    }
  }

  private def printVersion(): Int = {
    println(s"cn version $VERSION")
    0
  }

  private def printHelp(): Int = {
    println("""Cumulus Neovim CLI
      |
      |USAGE:
      |    cn [OPTIONS] [ARGS]...
      |
      |OPTIONS:
      |    install                 Full installation & setup
      |    update                  Update Cumulus & all components
      |    -v, --version           Show version
      |    -h, --help              Show this help
      |
      |ARGS:
      |    Any arguments are passed to nvim
      |
      |EXAMPLES:
      |    cn install              # Full setup
      |    cn                      # Launch nvim
      |    cn -u init.lua          # Launch with custom init
      |    cn --noplugin           # Launch without plugins
      |    cn -c "set number"      # Launch with command
      |""".stripMargin)
    0
  }

  private def findCumulusRepo(): Option[String] = {
    val configHome = sys.env.getOrElse("XDG_CONFIG_HOME", s"${sys.props("user.home")}/.config")
    val nvimPath = Paths.get(configHome, "nvim")

    if (Files.exists(nvimPath)) {
      // Check if it's a symlink to cumulus repo
      if (Files.isSymbolicLink(nvimPath)) {
        Try {
          Files.readSymbolicLink(nvimPath).toString
        }.toOption
      } else {
        // Check if it contains cumulus structure
        if (Files.exists(nvimPath.resolve("init.lua")) &&
            Files.exists(nvimPath.resolve("engine"))) {
          Some(nvimPath.toString)
        } else {
          None
        }
      }
    } else {
      None
    }
  }
