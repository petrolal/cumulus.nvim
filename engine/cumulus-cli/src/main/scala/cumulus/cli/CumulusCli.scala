package cumulus.cli

import scala.sys.process.*
import scala.util.{Try, Success, Failure}
import java.nio.file.{Files, Paths}
import java.lang.ProcessBuilder as JavaProcessBuilder
import os.Path

object CumulusCli:
  private val VERSION = "0.1.0"

  def main(args: Array[String]): Unit = {
    val result = args match
      // cumulus nvim setup/sync/status
      case Array("nvim", "setup", _*) => setupCumulus()
      case Array("nvim", "sync", _*) => syncCumulus()
      case Array("nvim", "status", _*) => checkStatus()
      case Array("nvim", "--help", _*) | Array("nvim", "-h", _*) => printNvimHelp()
      // cumulus nvim [args] - launch nvim
      case Array("nvim", rest @ _*) => launchNvim(rest.toArray)
      // cumulus --help/--version
      case Array("--help") | Array("-h") => printMainHelp()
      case Array("--version") | Array("-v") => printVersion()
      // cumulus (no args)
      case Array() => printMainHelp()
      case _ => printMainHelp()

    System.exit(result)
  }

  private def setupCumulus(): Int = {
    println("================================================")
    println("  Cumulus Neovim: Setup & Installation        ")
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
        println(s"✖ Setup failed: ${e.getMessage}")
        1
    }
  }

  private def syncCumulus(): Int = {
    println("Syncing Cumulus Neovim...")
    setupCumulus()
  }

  private def checkStatus(): Int = {
    val repoDir = findCumulusRepo()
    println("================================================")
    println("  Cumulus Neovim: Status Report               ")
    println("================================================")
    println()

    repoDir match
      case Some(repo) =>
        println(s"✓ Installation found: $repo")
        val enginePath = s"$repo/engine/target/release/cumulus-engine"
        if (os.exists(Path(enginePath))) {
          println("✓ Engine binary: present")
        } else {
          println("⚠ Engine binary: not found (will be built on first use)")
        }
        0
      case None =>
        println("✖ Cumulus not installed at ~/.config/nvim")
        println("  Run: git clone https://github.com/petrolal/cumulus.nvim ~/.config/nvim")
        println("  Then: cumulus nvim setup")
        1
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
    println(s"cumulus-nvim version $VERSION")
    0
  }

  private def printMainHelp(): Int = {
    println("""Cumulus Package Manager

USAGE:
    cumulus [COMMAND] [SUBCOMMAND] [ARGS]...

COMMANDS:
    nvim                    Manage Cumulus Neovim IDE
    help                    Show this help

EXAMPLES:
    cumulus nvim setup      # Initialize or update Cumulus
    cumulus nvim sync       # Sync plugins & components
    cumulus nvim status     # Show installation status
    cumulus nvim            # Launch Neovim
    cumulus --help          # Show this help
    cumulus --version       # Show version

Use 'cumulus nvim --help' for Neovim-specific commands.
""".stripMargin)
    0
  }

  private def printNvimHelp(): Int = {
    println("""Cumulus Neovim Management

USAGE:
    cumulus nvim [SUBCOMMAND] [ARGS]...

SUBCOMMANDS:
    setup                   Initialize or update Cumulus
    sync                    Sync plugins & components (same as setup)
    status                  Show installation status

    (no subcommand)         Launch Neovim

EXAMPLES:
    cumulus nvim setup      # Initialize or update everything
    cumulus nvim status     # Check what's installed
    cumulus nvim            # Launch Neovim
    cumulus nvim -u init.lua    # Launch with custom init
    cumulus nvim --noplugin     # Launch without plugins
    cumulus nvim -c "set number" # Launch with command

FIRST TIME:
    1. git clone https://github.com/petrolal/cumulus.nvim ~/.config/nvim
    2. cumulus nvim setup   # Full setup (~5-15 min)
    3. cumulus nvim         # Launch Cumulus IDE
""".stripMargin)
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
