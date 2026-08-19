package cumulus.health

import cumulus.protocol.BinaryInfo
import os.Path
import scala.util.Try

/**
 * Detects system binaries by scanning $PATH and checking file existence.
 */
object BinaryDetector:

  /**
   * Detect a single binary on $PATH.
   * @param name Binary name (e.g., "rg", "fd", "git")
   * @return BinaryInfo with status "ok" if found, "missing" if not, "error" if check failed
   */
  def detectBinary(name: String): BinaryInfo =
    try
      val pathEnv = sys.env.getOrElse("PATH", "/usr/local/bin:/usr/bin:/bin:/usr/sbin:/sbin")
      val pathEntries = pathEnv.split(":").toList

      // Try to find the binary in PATH entries
      val found = pathEntries.collectFirst { entry =>
        val binaryPath = os.Path(entry) / name
        if os.exists(binaryPath) && os.isFile(binaryPath) then
          Some(binaryPath.toString)
        else
          None
      }.flatten

      found match
        case Some(path) =>
          // Extract version if possible
          val version = CompilerVersionExtractor.extractVersion(name)
          BinaryInfo(name = name, version = version, status = "ok", path = Some(path))
        case None =>
          BinaryInfo(name = name, version = None, status = "missing", path = None)
    catch
      case e: Exception =>
        BinaryInfo(name = name, version = None, status = "error", path = None)

  /**
   * Detect multiple binaries in one pass.
   * @param names List of binary names to check
   * @return Sequence of BinaryInfo for each name
   */
  def detectAllBinaries(names: Seq[String]): Seq[BinaryInfo] =
    names.map(detectBinary)
