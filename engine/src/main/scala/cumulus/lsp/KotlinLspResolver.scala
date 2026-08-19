package cumulus.lsp

import cumulus.protocol.{CumulusResponse, KotlinLspConfig, CumulusError}
import cumulus.health.CompilerVersionExtractor
import os.Path
import scala.collection.mutable
import scala.util.Try

/**
 * Coordinates Kotlin LSP binary detection, classpath resolution, cache lookups,
 * compiler version extraction, and cache sanitization.
 */
object KotlinLspResolver:

  /**
   * Normalize input project root path supporting tilde expansion, relative paths, and current working directory.
   */
  def resolveProjectRoot(projectRootStr: String): Option[Path] =
    Try {
      val trimmed = projectRootStr.trim
      val home = sys.env.getOrElse("HOME", System.getProperty("user.home"))
      val expanded = if trimmed.startsWith("~") then
        trimmed.replaceFirst("^~", home)
      else
        trimmed

      if expanded.isEmpty || expanded == "." then
        Some(os.pwd)
      else if expanded.startsWith("/") then
        Some(Path(expanded))
      else
        Some(os.pwd / os.RelPath(expanded))
    }.getOrElse(None)

  /**
   * Handle kotlin-lsp-config request.
   *
   * @param projectRootOpt Optional project root path (defaults to current working directory)
   * @param sanitizeCache Whether to perform cache sanitization
   * @param customCacheFile Optional custom cache file path (for testing)
   * @return CumulusResponse[KotlinLspConfig]
   */
  def handle(
    projectRootOpt: Option[String] = None,
    sanitizeCache: Boolean = false,
    customCacheFile: Option[Path] = None
  ): CumulusResponse[KotlinLspConfig] =
    try
      val cachePath = customCacheFile.getOrElse(KotlinLspCache.defaultCachePath)
      var wasSanitized = false

      if sanitizeCache then
        KotlinLspCache.sanitize(cachePath)
        wasSanitized = true

      val projectRootStr = projectRootOpt.getOrElse(".")
      val rootPathOpt = resolveProjectRoot(projectRootStr)

      val rootPath = rootPathOpt match
        case Some(p) if os.exists(p) && os.isDir(p) => p
        case _ =>
          return CumulusResponse(
            success = false,
            data = None,
            error = Some("Directory not found"),
            error_code = Some("FILE_NOT_FOUND")
          )

      val diagnostics = mutable.ListBuffer[String]()

      // 1. Detect Kotlin language server binary
      val lspPath = detectKotlinLspBinary()
      if lspPath.isEmpty then
        diagnostics += "Kotlin language server not found on system; please install via Mason (kotlin-language-server) or package manager"

      // 2. Extract Kotlin compiler version
      val compilerVersion = CompilerVersionExtractor.extractVersion("kotlinc")

      // 3. Classpath and Source Root resolution with Cache
      val buildFileOpt = KotlinClasspathResolver.findBuildFile(rootPath)
      val (classpath, sourceRoots, isCached) = buildFileOpt match
        case Some((buildFile, mtime)) =>
          val cachedOpt = KotlinLspCache.get(rootPath.toString, buildFile.toString, mtime, cachePath)
          cachedOpt match
            case Some((cachedCp, cachedSr)) =>
              (cachedCp, cachedSr, true)
            case None =>
              val freshCp = KotlinClasspathResolver.resolveClasspath(rootPath)
              val freshSr = KotlinClasspathResolver.discoverSourceRoots(rootPath)
              KotlinLspCache.put(rootPath.toString, buildFile.toString, mtime, freshCp, freshSr, cachePath)
              (freshCp, freshSr, false)
        case None =>
          // Non-Gradle / Non-Maven project
          val freshSr = KotlinClasspathResolver.discoverSourceRoots(rootPath)
          (Seq.empty[String], freshSr, false)

      val finalDiagnostics = if diagnostics.nonEmpty then Some(diagnostics.toSeq) else None

      CumulusResponse(
        success = true,
        data = Some(KotlinLspConfig(
          server_path = lspPath,
          server_valid = lspPath.isDefined,
          classpath = classpath,
          source_roots = sourceRoots,
          compiler_version = compilerVersion,
          is_cached = isCached,
          cache_sanitized = wasSanitized,
          diagnostics = finalDiagnostics
        )),
        error = None,
        error_code = None
      )
    catch
      case e: Exception =>
        CumulusResponse(
          success = false,
          data = None,
          error = Some(s"Error resolving Kotlin LSP config: ${e.getMessage}"),
          error_code = Some("INTERNAL_ERROR")
        )

  /**
   * Detect Kotlin language server binary location.
   * Checks XDG_DATA_HOME/nvim/mason, ~/.local/share/nvim/mason, $PATH, and system paths.
   */
  def detectKotlinLspBinary(): Option[String] =
    try
      val home = sys.env.getOrElse("HOME", System.getProperty("user.home"))
      val xdgDataHome = sys.env.get("XDG_DATA_HOME").map(Path(_)).getOrElse(Path(home) / ".local" / "share")

      // 1. XDG / Mason package directory
      val masonPackagePath = xdgDataHome / "nvim" / "mason" / "packages" / "kotlin-language-server" / "bin" / "kotlin-language-server"
      if os.exists(masonPackagePath) && os.isFile(masonPackagePath) then
        return Some(masonPackagePath.toString)

      // 2. Fallback ~/.local/share Mason package directory if XDG differs
      val defaultMasonPackagePath = Path(home) / ".local" / "share" / "nvim" / "mason" / "packages" / "kotlin-language-server" / "bin" / "kotlin-language-server"
      if os.exists(defaultMasonPackagePath) && os.isFile(defaultMasonPackagePath) then
        return Some(defaultMasonPackagePath.toString)

      // 3. XDG / Mason bin directory
      val masonBinPath = xdgDataHome / "nvim" / "mason" / "bin" / "kotlin-language-server"
      if os.exists(masonBinPath) && os.isFile(masonBinPath) then
        return Some(masonBinPath.toString)

      val defaultMasonBinPath = Path(home) / ".local" / "share" / "nvim" / "mason" / "bin" / "kotlin-language-server"
      if os.exists(defaultMasonBinPath) && os.isFile(defaultMasonBinPath) then
        return Some(defaultMasonBinPath.toString)

      // 4. $PATH lookup using os.proc
      Try {
        val res = os.proc("which", "kotlin-language-server").call(check = false)
        if res.exitCode == 0 then
          val pathStr = res.out.text().trim
          if pathStr.nonEmpty && os.exists(Path(pathStr)) then
            return Some(pathStr)
      }

      // 5. Common system paths
      val systemPaths = Seq(
        Path("/usr/local/bin/kotlin-language-server"),
        Path("/usr/bin/kotlin-language-server"),
        Path("/opt/kotlin-language-server/bin/kotlin-language-server")
      )
      systemPaths.find(p => os.exists(p) && os.isFile(p)).map(_.toString)
    catch
      case _: Exception => None
