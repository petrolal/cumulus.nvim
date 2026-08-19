package cumulus.toolchain

import cumulus.protocol.ToolSpec
import os.Path
import scala.util.Try
import scala.sys.process._

/**
 * Detects native compiler/interpreter tools for various languages.
 * Uses version queries and filesystem searches to discover installed tools.
 */
object ToolDetector:

  /**
   * Detect all available tools for a given language.
   *
   * @param language The target language (java, kotlin, scala, go, rust, python, node)
   * @return Seq[ToolSpec] of detected tools with versions
   */
  def detectTools(language: String): Seq[ToolSpec] =
    language.toLowerCase match
      case "java" => detectJavaTools()
      case "kotlin" => detectKotlinTools()
      case "scala" => detectScalaTools()
      case "go" => detectGoTools()
      case "rust" => detectRustTools()
      case "python" => detectPythonTools()
      case "node" | "typescript" | "javascript" => detectNodeTools()
      case _ => Seq()

  /**
   * Detect Java tools (javac, maven, gradle, etc.)
   */
  private def detectJavaTools(): Seq[ToolSpec] =
    val tools = scala.collection.mutable.ListBuffer[ToolSpec]()

    // Detect javac
    getVersionOutput("javac", "-version").foreach { version =>
      tools += ToolSpec("javac", Some(parseJavaVersion(version)), getToolPath("javac"))
    }

    // Detect Maven
    getVersionOutput("mvn", "-v").foreach { version =>
      val mavenVersion = version.split("\n").headOption.getOrElse("")
      tools += ToolSpec("maven", Some(extractVersion(mavenVersion)), getToolPath("mvn"))
    }

    // Detect Gradle
    getVersionOutput("gradle", "--version").foreach { version =>
      val gradleVersion = version.split("\n").headOption.getOrElse("")
      tools += ToolSpec("gradle", Some(extractVersion(gradleVersion)), getToolPath("gradle"))
    }

    tools.toSeq

  /**
   * Detect Kotlin tools (kotlinc, kotlin, etc.)
   */
  private def detectKotlinTools(): Seq[ToolSpec] =
    val tools = scala.collection.mutable.ListBuffer[ToolSpec]()

    // Detect kotlinc
    getVersionOutput("kotlinc", "-version").foreach { version =>
      tools += ToolSpec("kotlinc", Some(parseKotlinVersion(version)), getToolPath("kotlinc"))
    }

    // Detect kotlin
    getVersionOutput("kotlin", "-version").foreach { version =>
      tools += ToolSpec("kotlin", Some(parseKotlinVersion(version)), getToolPath("kotlin"))
    }

    tools.toSeq

  /**
   * Detect Scala tools (scalac, scala, coursier, etc.)
   */
  private def detectScalaTools(): Seq[ToolSpec] =
    val tools = scala.collection.mutable.ListBuffer[ToolSpec]()

    // Detect scalac
    getVersionOutput("scalac", "-version").foreach { version =>
      tools += ToolSpec("scalac", Some(parseScalaVersion(version)), getToolPath("scalac"))
    }

    // Detect scala
    getVersionOutput("scala", "-version").foreach { version =>
      tools += ToolSpec("scala", Some(parseScalaVersion(version)), getToolPath("scala"))
    }

    // Detect coursier
    getVersionOutput("cs", "--version").orElse(getVersionOutput("coursier", "--version")).foreach { version =>
      val path = getToolPath("cs").orElse(getToolPath("coursier"))
      tools += ToolSpec("coursier", Some(extractVersion(version)), path)
    }

    tools.toSeq

  /**
   * Detect Go tools (go, gopls, delve, etc.)
   */
  private def detectGoTools(): Seq[ToolSpec] =
    val tools = scala.collection.mutable.ListBuffer[ToolSpec]()

    // Detect go
    getVersionOutput("go", "version").foreach { version =>
      tools += ToolSpec("go", Some(parseGoVersion(version)), getToolPath("go"))
    }

    // Detect gopls
    getVersionOutput("gopls", "version").foreach { version =>
      tools += ToolSpec("gopls", Some(extractVersion(version)), getToolPath("gopls"))
    }

    // Detect delve (dlv)
    getVersionOutput("dlv", "version").foreach { version =>
      tools += ToolSpec("delve", Some(extractVersion(version)), getToolPath("dlv"))
    }

    tools.toSeq

  /**
   * Detect Rust tools (rustc, cargo, rustfmt, etc.)
   */
  private def detectRustTools(): Seq[ToolSpec] =
    val tools = scala.collection.mutable.ListBuffer[ToolSpec]()

    // Detect rustc
    getVersionOutput("rustc", "--version").foreach { version =>
      tools += ToolSpec("rustc", Some(parseRustVersion(version)), getToolPath("rustc"))
    }

    // Detect cargo
    getVersionOutput("cargo", "--version").foreach { version =>
      tools += ToolSpec("cargo", Some(parseRustVersion(version)), getToolPath("cargo"))
    }

    // Detect rustfmt
    getVersionOutput("rustfmt", "--version").foreach { version =>
      tools += ToolSpec("rustfmt", Some(extractVersion(version)), getToolPath("rustfmt"))
    }

    tools.toSeq

  /**
   * Detect Python tools (python, python3, pip, etc.)
   */
  private def detectPythonTools(): Seq[ToolSpec] =
    val tools = scala.collection.mutable.ListBuffer[ToolSpec]()

    // Prefer python3
    val pythonVersion = getVersionOutput("python3", "--version")
      .orElse(getVersionOutput("python", "--version"))

    pythonVersion.foreach { version =>
      val pythonName = if getVersionOutput("python3", "--version").isDefined then "python3" else "python"
      tools += ToolSpec(pythonName, Some(parsePythonVersion(version)), getToolPath(pythonName))
    }

    // Detect pip
    getVersionOutput("pip3", "--version").orElse(getVersionOutput("pip", "--version")).foreach { version =>
      val pipName = if getVersionOutput("pip3", "--version").isDefined then "pip3" else "pip"
      tools += ToolSpec(pipName, Some(extractVersion(version)), getToolPath(pipName))
    }

    tools.toSeq

  /**
   * Detect Node.js tools (node, npm, yarn, etc.)
   */
  private def detectNodeTools(): Seq[ToolSpec] =
    val tools = scala.collection.mutable.ListBuffer[ToolSpec]()

    // Detect node
    getVersionOutput("node", "--version").foreach { version =>
      tools += ToolSpec("node", Some(version.trim), getToolPath("node"))
    }

    // Detect npm
    getVersionOutput("npm", "--version").foreach { version =>
      tools += ToolSpec("npm", Some(version.trim), getToolPath("npm"))
    }

    // Detect yarn
    getVersionOutput("yarn", "--version").foreach { version =>
      tools += ToolSpec("yarn", Some(version.trim), getToolPath("yarn"))
    }

    tools.toSeq

  /**
   * Get version output from a tool by executing it with version flags.
   * Tries common version flags: --version, -version, -v, version
   */
  private def getVersionOutput(tool: String, flags: String*): Option[String] =
    val allFlags = if flags.isEmpty then Seq("--version", "-version", "-v") else flags

    var result: Option[String] = None
    for flag <- allFlags if result.isEmpty do
      try
        val cmd = s"$tool $flag"
        val output = Try {
          cmd.!!
        }.getOrElse("")
        if output.nonEmpty then
          result = Some(output)
      catch
        case _: Exception => ()

    result

  /**
   * Get the full path to a tool if it exists in PATH or standard locations.
   */
  private def getToolPath(tool: String): Option[String] =
    Try {
      s"which $tool".!!
    }.toOption.map(_.trim).filter(_.nonEmpty)

  /**
   * Parse Java version from javac -version output.
   * Example: "javac 11.0.21" or "javac 21.0.2"
   */
  private def parseJavaVersion(output: String): String =
    val pattern = """(\d+(?:\.\d+)*)""".r
    pattern.findFirstMatchIn(output).map(_.group(1)).getOrElse("unknown")

  /**
   * Parse Kotlin version from kotlinc -version output.
   */
  private def parseKotlinVersion(output: String): String =
    val pattern = """(\d+(?:\.\d+)*)""".r
    pattern.findFirstMatchIn(output).map(_.group(1)).getOrElse("unknown")

  /**
   * Parse Scala version from scalac -version output.
   */
  private def parseScalaVersion(output: String): String =
    val pattern = """(\d+(?:\.\d+)*)""".r
    pattern.findFirstMatchIn(output).map(_.group(1)).getOrElse("unknown")

  /**
   * Parse Go version from go version output.
   * Example: "go version go1.18.3 linux/amd64" -> "1.18.3"
   */
  private def parseGoVersion(output: String): String =
    val pattern = """go(\d+(?:\.\d+)*)""".r
    pattern.findFirstMatchIn(output).map(_.group(1)).getOrElse("unknown")

  /**
   * Parse Rust version from rustc --version output.
   * Example: "rustc 1.70.0 (90c541806 2023-05-31)" -> "1.70.0"
   */
  private def parseRustVersion(output: String): String =
    val pattern = """(\d+(?:\.\d+)*)""".r
    pattern.findFirstMatchIn(output).map(_.group(1)).getOrElse("unknown")

  /**
   * Parse Python version from python --version output.
   * Example: "Python 3.10.12" -> "3.10.12"
   */
  private def parsePythonVersion(output: String): String =
    val pattern = """(\d+(?:\.\d+)*)""".r
    pattern.findFirstMatchIn(output).map(_.group(1)).getOrElse("unknown")

  /**
   * Extract first version number from output string.
   */
  private def extractVersion(output: String): String =
    val pattern = """(\d+(?:\.\d+)*)""".r
    pattern.findFirstMatchIn(output).map(_.group(1)).getOrElse("unknown")
