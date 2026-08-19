package cumulus.health

import scala.util.Try
import scala.sys.process._
import scala.collection.mutable.Map as MutableMap

/**
 * Extracts compiler versions by parsing --version output.
 */
object CompilerVersionExtractor:

  private val versionCache = MutableMap[String, Option[String]]()

  /**
   * Extract version from a binary by running --version or -version flag.
   * @param binary Binary name (e.g., "javac", "scalac", "rustc")
   * @return Extracted version string, or None if extraction failed
   */
  def extractVersion(binary: String): Option[String] =
    if versionCache.contains(binary) then
      return versionCache(binary)

    val result = try
      val cmd = s"$binary --version"
      val output = Try {
        cmd.!!
      }.getOrElse(
        Try {
          s"$binary -version".!!
        }.getOrElse("")
      )

      parseVersion(binary, output)
    catch
      case _: Exception => None

    versionCache(binary) = result
    result

  /**
   * Parse version from --version output based on binary type.
   * Handles javac, scalac, kotlinc, rustc, sbt, and generic formats.
   */
  private def parseVersion(binary: String, output: String): Option[String] =
    if output.isEmpty then return None

    val firstLine = output.linesIterator.take(1).mkString

    binary match
      case "javac" =>
        // Format: "javac 21.0.1"
        val pattern = """javac\s+([\d.]+)""".r
        pattern.findFirstMatchIn(firstLine).map(_.group(1))
          .orElse {
            // Try alternative: "21.0.1\n..."
            val altPattern = """([\d.]+(?:\-\w+)?)""".r
            altPattern.findFirstMatchIn(firstLine).map(_.group(1))
          }

      case "scalac" =>
        // Format: "Scala compiler version 3.3.1" or similar
        val pattern = """(?i)version\s+([\d.]+)""".r
        pattern.findFirstMatchIn(firstLine).map(_.group(1))
          .orElse {
            val altPattern = """([\d.]+)""".r
            altPattern.findFirstMatchIn(firstLine).map(_.group(1))
          }

      case "kotlinc" =>
        // Format: "Kotlin version 1.9.10"
        val pattern = """(?i)version\s+([\d.]+)""".r
        pattern.findFirstMatchIn(firstLine).map(_.group(1))

      case "rustc" =>
        // Format: "rustc 1.75.0 (2023-09-26)" or just "rustc 1.75.0"
        val pattern = """rustc\s+([\d.]+)""".r
        pattern.findFirstMatchIn(firstLine).map(_.group(1))

      case "sbt" =>
        // Format: "sbt version 1.9.7" or just "1.9.7"
        val pattern = """(?i)sbt\s+version\s+([\d.]+)""".r
        pattern.findFirstMatchIn(firstLine).map(_.group(1))
          .orElse {
            val altPattern = """([\d.]+)""".r
            altPattern.findFirstMatchIn(firstLine).map(_.group(1))
          }

      case "node" =>
        // Format: "v18.16.0"
        val pattern = """v?([\d.]+)""".r
        pattern.findFirstMatchIn(firstLine).map(_.group(1))

      case "python3" | "python" =>
        // Format: "Python 3.11.4" or similar
        val pattern = """(?i)python\s+([0-9.]+)""".r
        pattern.findFirstMatchIn(firstLine).map(_.group(1))
          .orElse {
            val altPattern = """([\d.]+)""".r
            altPattern.findFirstMatchIn(firstLine).map(_.group(1))
          }

      case "git" =>
        // Format: "git version 2.41.0" or similar
        val pattern = """git\s+version\s+([\d.]+)""".r
        pattern.findFirstMatchIn(firstLine).map(_.group(1))

      case "rg" | "ripgrep" =>
        // Format: "ripgrep 13.0.0 (rev ...)"
        val pattern = """([\d.]+)""".r
        pattern.findFirstMatchIn(firstLine).map(_.group(1))

      case "fd" =>
        // Format: "fd 9.0.0"
        val pattern = """([\d.]+)""".r
        pattern.findFirstMatchIn(firstLine).map(_.group(1))

      case _ =>
        // Generic pattern: try to extract first version-like string
        val pattern = """([\d.]+(?:\-\w+)?)""".r
        pattern.findFirstMatchIn(firstLine).map(_.group(1))
