package cumulus.lsp

import os.Path
import upickle.default.{ReadWriter, macroRW}
import scala.util.Try

case class CacheEntry(
  build_file_mtime: Long,
  build_file_path: String,
  classpath: Seq[String],
  source_roots: Seq[String],
  last_accessed: Long
) derives ReadWriter

case class CacheStore(
  entries: Map[String, CacheEntry] = Map.empty
) derives ReadWriter

/**
 * Manages persistent timestamp-validated cache store and cache sanitization in ~/.cache/cumulus/kotlin-classpath-cache.json
 */
object KotlinLspCache:

  def defaultCachePath: Path =
    val home = sys.env.getOrElse("HOME", System.getProperty("user.home"))
    Path(home) / ".cache" / "cumulus" / "kotlin-classpath-cache.json"

  /**
   * Load cache store from JSON file.
   */
  def loadCache(cacheFile: Path = defaultCachePath): CacheStore =
    try
      if os.exists(cacheFile) && os.isFile(cacheFile) then
        val content = os.read(cacheFile)
        if content.trim.isEmpty then
          CacheStore()
        else
          Try(upickle.default.read[CacheStore](content)).getOrElse(CacheStore())
      else
        CacheStore()
    catch
      case _: Exception => CacheStore()

  /**
   * Save cache store to JSON file atomically / safely via temp file rename or safe overwrite.
   */
  def saveCache(store: CacheStore, cacheFile: Path = defaultCachePath): Unit =
    try
      val dir = cacheFile / os.up
      if !os.exists(dir) then
        os.makeDir.all(dir)
      val json = upickle.default.write(store, indent = 2)
      val tmpFile = dir / s".${cacheFile.last}.tmp.${System.currentTimeMillis()}"
      os.write.over(tmpFile, json)
      os.move.over(tmpFile, cacheFile)
    catch
      case _: Exception => ()

  /**
   * Get cached classpath and source roots if valid against build file mtime.
   * Does not rewrite cache file on get to avoid unnecessary I/O.
   *
   * @param projectRoot Absolute path of the project
   * @param currentBuildFile Current build file path (e.g. build.gradle.kts)
   * @param currentMtime Current modification timestamp of the build file
   * @param cacheFile Optional custom cache file path
   * @return Option[(Seq[String], Seq[String])] if cache hit and still valid
   */
  def get(
    projectRoot: String,
    currentBuildFile: String,
    currentMtime: Long,
    cacheFile: Path = defaultCachePath
  ): Option[(Seq[String], Seq[String])] =
    try
      val store = loadCache(cacheFile)
      store.entries.get(projectRoot).flatMap { entry =>
        if entry.build_file_path == currentBuildFile && entry.build_file_mtime == currentMtime then
          Some((entry.classpath, entry.source_roots))
        else
          None
      }
    catch
      case _: Exception => None

  /**
   * Put or update cache entry for a project.
   */
  def put(
    projectRoot: String,
    buildFilePath: String,
    buildFileMtime: Long,
    classpath: Seq[String],
    sourceRoots: Seq[String],
    cacheFile: Path = defaultCachePath
  ): Unit =
    try
      val store = loadCache(cacheFile)
      val newEntry = CacheEntry(
        build_file_mtime = buildFileMtime,
        build_file_path = buildFilePath,
        classpath = classpath,
        source_roots = sourceRoots,
        last_accessed = System.currentTimeMillis()
      )
      val updatedStore = CacheStore(store.entries + (projectRoot -> newEntry))
      saveCache(updatedStore, cacheFile)
    catch
      case _: Exception => ()

  /**
   * Sanitize cache store by pruning entries for non-existent project roots or stale build files.
   *
   * @param cacheFile Optional custom cache file path
   * @return Number of pruned entries
   */
  def sanitize(cacheFile: Path = defaultCachePath): Int =
    try
      val store = loadCache(cacheFile)
      val validEntries = store.entries.filter { case (projPathStr, entry) =>
        Try {
          val projPath = Path(projPathStr)
          val buildPath = Path(entry.build_file_path)
          os.exists(projPath) && os.isDir(projPath) && os.exists(buildPath) && os.isFile(buildPath)
        }.getOrElse(false)
      }
      val prunedCount = store.entries.size - validEntries.size
      if prunedCount > 0 || !os.exists(cacheFile) then
        saveCache(CacheStore(validEntries), cacheFile)
      prunedCount
    catch
      case _: Exception => 0
