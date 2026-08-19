package cumulus.workspace

import os.Path

/**
 * Validates JDTLS Mason bundle structure.
 * Checks for:
 * - plugins/ directory
 * - config_linux/ or config_mac/ (OS-specific)
 * - jdtls executable
 */
object MasonBundleValidator:

  /**
   * Validate a JDTLS Mason bundle at the given path.
   *
   * @param bundlePath The path to the JDTLS bundle root
   * @return true if all required directories and files exist, false otherwise
   */
  def validateBundle(bundlePath: String): Boolean =
    try
      val bundleDir = Path(bundlePath)
      if !os.exists(bundleDir) || !os.isDir(bundleDir) then return false

      // Check for plugins directory
      val pluginsDir = bundleDir / "plugins"
      if !os.exists(pluginsDir) || !os.isDir(pluginsDir) then return false

      // Check for OS-specific config directory (linux or mac)
      val configLinux = bundleDir / "config_linux"
      val configMac = bundleDir / "config_mac"
      val configWindows = bundleDir / "config_win"
      val hasConfigDir = (os.exists(configLinux) && os.isDir(configLinux)) ||
        (os.exists(configMac) && os.isDir(configMac)) ||
        (os.exists(configWindows) && os.isDir(configWindows))

      if !hasConfigDir then return false

      // Check for jdtls executable (or jdtls.cmd on Windows)
      val jdtlsExec = bundleDir / "jdtls"
      val jdtlsCmd = bundleDir / "jdtls.cmd"
      val hasExecutable = (os.exists(jdtlsExec) && os.isFile(jdtlsExec)) ||
        (os.exists(jdtlsCmd) && os.isFile(jdtlsCmd))

      hasExecutable
    catch
      case _: Exception => false

  /**
   * Get the OS-specific config directory for a JDTLS bundle.
   *
   * @param bundlePath The path to the JDTLS bundle root
   * @return Some(path) if config directory exists, None otherwise
   */
  def getConfigDir(bundlePath: String): Option[String] =
    try
      val bundleDir = Path(bundlePath)

      // Prefer config_linux on Linux/Unix, config_mac on macOS, config_win on Windows
      val osName = System.getProperty("os.name", "").toLowerCase
      if osName.contains("windows") then
        val configWin = bundleDir / "config_win"
        if os.exists(configWin) && os.isDir(configWin) then
          Some(configWin.toString)
        else
          None
      else if osName.contains("mac") then
        val configMac = bundleDir / "config_mac"
        if os.exists(configMac) && os.isDir(configMac) then
          Some(configMac.toString)
        else
          None
      else
        // Default to Linux config
        val configLinux = bundleDir / "config_linux"
        if os.exists(configLinux) && os.isDir(configLinux) then
          Some(configLinux.toString)
        else
          None
    catch
      case _: Exception => None
