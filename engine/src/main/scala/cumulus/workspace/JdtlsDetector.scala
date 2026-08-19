package cumulus.workspace

import os.Path
import scala.sys.process._
import scala.util.Try

/**
 * Detects and locates JDTLS binary on the system.
 * Searches in:
 * - Mason package directory (~/.local/share/nvim/mason/packages/jdtls/)
 * - Mason bin cache (~/.local/share/nvim/mason/bin/jdtls)
 * - $PATH entries
 */
object JdtlsDetector:

  /**
   * Detect JDTLS binary location.
   *
   * @return Some(path) if jdtls executable is found, None otherwise
   */
  def detectJdtls(): Option[String] =
    detectInMasonPackages()
      .orElse(detectInMasonBin())
      .orElse(detectInPath())

  /**
   * Detect JDTLS in Mason package directory.
   * Checks ~/.local/share/nvim/mason/packages/jdtls/
   */
  private def detectInMasonPackages(): Option[String] =
    try
      val home = sys.env.getOrElse("HOME", System.getProperty("user.home"))
      val masonPkgs = Path(home) / ".local" / "share" / "nvim" / "mason" / "packages" / "jdtls"
      if os.exists(masonPkgs) && os.isDir(masonPkgs) then
        val exec = masonPkgs / "jdtls"
        if os.exists(exec) && os.isFile(exec) then
          Some(exec.toString)
        else
          None
      else
        None
    catch
      case _: Exception => None

  /**
   * Detect JDTLS in Mason bin cache.
   * Checks ~/.local/share/nvim/mason/bin/jdtls
   */
  private def detectInMasonBin(): Option[String] =
    try
      val home = sys.env.getOrElse("HOME", System.getProperty("user.home"))
      val masonBin = Path(home) / ".local" / "share" / "nvim" / "mason" / "bin" / "jdtls"
      if os.exists(masonBin) && os.isFile(masonBin) then
        Some(masonBin.toString)
      else
        None
    catch
      case _: Exception => None

  /**
   * Detect JDTLS in $PATH.
   */
  private def detectInPath(): Option[String] =
    try
      val result = Try("which jdtls".!!).toOption
      result.map(_.trim).filter(_.nonEmpty)
    catch
      case _: Exception => None

  /**
   * Get the Mason package directory for JDTLS.
   */
  def getMasonPackageDir(): Option[String] =
    try
      val home = sys.env.getOrElse("HOME", System.getProperty("user.home"))
      val masonPkgs = Path(home) / ".local" / "share" / "nvim" / "mason" / "packages" / "jdtls"
      if os.exists(masonPkgs) && os.isDir(masonPkgs) then
        Some(masonPkgs.toString)
      else
        None
    catch
      case _: Exception => None
