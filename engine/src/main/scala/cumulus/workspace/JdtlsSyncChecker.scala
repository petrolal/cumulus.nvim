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
    if startTime <= 0 then
      SyncStatus(sync_needed = false, modified_file = None)
    else
      // Check root build files
      val rootStale = BuildFiles.iterator.collectFirst {
        case relPathStr if {
          val filePath = dirPath / os.RelPath(relPathStr)
          os.exists(filePath) && os.isFile(filePath) && {
            val mtimeMillis = os.mtime(filePath)
            val mtimeSec = mtimeMillis / 1000
            if startTime > 1000000000000L then mtimeMillis > startTime else mtimeSec > startTime
          }
        } =>
          SyncStatus(sync_needed = true, modified_file = Some(relPathStr))
      }

      rootStale.getOrElse {
        // Check submodule build files up to 2 levels deep
        val subStale = try
          val subBuildFiles = os.walk(dirPath, maxDepth = 3, skip = (p: Path) => p.last.startsWith(".") || p.last == "target" || p.last == "node_modules" || p.last == "build")
            .filter(p => os.isFile(p) && (p.last == "pom.xml" || p.last == "build.gradle" || p.last == "build.gradle.kts"))
          subBuildFiles.iterator.collectFirst {
            case filePath if {
              val mtimeMillis = os.mtime(filePath)
              val mtimeSec = mtimeMillis / 1000
              if startTime > 1000000000000L then mtimeMillis > startTime else mtimeSec > startTime
            } =>
              val relPath = filePath.relativeTo(dirPath).toString
              SyncStatus(sync_needed = true, modified_file = Some(relPath))
          }
        catch
          case _: Exception => None

        subStale.getOrElse(SyncStatus(sync_needed = false, modified_file = None))
      }
