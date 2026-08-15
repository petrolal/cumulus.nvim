package cumulus.workspace

import cumulus.protocol.CumulusResponse
import cumulus.devops.SyncStatus
import os.Path

/**
 * Detects whether build configuration files have been modified since JDTLS start time.
 */
object JdtlsSyncChecker:

  private val BuildFiles = Seq(
    "pom.xml",
    "build.gradle",
    "build.gradle.kts",
    "settings.gradle",
    "settings.gradle.kts",
    "gradle/libs.versions.toml"
  )

  def checkJdtlsSync(dir: String, startTime: Long): CumulusResponse[SyncStatus] =
    try
      val dirPath = if dir.startsWith("/") then Path(dir) else os.pwd / os.RelPath(dir)
      if !os.exists(dirPath) || !os.isDir(dirPath) then
        return CumulusResponse(success = true, data = Some(SyncStatus(sync_needed = false, modified_file = None)), error = None, error_code = None)

      val status = checkSync(dirPath, startTime)
      CumulusResponse(success = true, data = Some(status), error = None, error_code = None)
    catch
      case e: Exception =>
        CumulusResponse(success = false, data = None, error = Some(s"Error checking JDTLS sync: ${e.getMessage}"), error_code = Some("INTERNAL_ERROR"))

  def checkSync(dirPath: Path, startTime: Long): SyncStatus =
    if startTime <= 0 then return SyncStatus(sync_needed = false, modified_file = None)
    
    // Check root build files
    for relPathStr <- BuildFiles do
      val filePath = dirPath / os.RelPath(relPathStr)
      if os.exists(filePath) && os.isFile(filePath) then
        val mtimeMillis = os.mtime(filePath)
        val mtimeSec = mtimeMillis / 1000
        val isStale = if startTime > 1000000000000L then mtimeMillis > startTime else mtimeSec > startTime
        if isStale then
          return SyncStatus(sync_needed = true, modified_file = Some(relPathStr))

    // Check submodule build files up to 2 levels deep
    try
      val subBuildFiles = os.walk(dirPath, maxDepth = 3, skip = (p: Path) => p.last.startsWith(".") || p.last == "target" || p.last == "node_modules" || p.last == "build")
        .filter(p => os.isFile(p) && (p.last == "pom.xml" || p.last == "build.gradle" || p.last == "build.gradle.kts"))
      for filePath <- subBuildFiles do
        val mtimeMillis = os.mtime(filePath)
        val mtimeSec = mtimeMillis / 1000
        val isStale = if startTime > 1000000000000L then mtimeMillis > startTime else mtimeSec > startTime
        if isStale then
          val relPath = filePath.relativeTo(dirPath).toString
          return SyncStatus(sync_needed = true, modified_file = Some(relPath))
    catch
      case _: Exception => ()

    SyncStatus(sync_needed = false, modified_file = None)
