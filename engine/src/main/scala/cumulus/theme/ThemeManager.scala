package cumulus.theme

import cumulus.protocol.{CumulusResponse, CumulusError}
import cumulus.devops.ThemeState
import os.Path
import scala.collection.mutable

/**
 * Manages persistent Cloud Theme state between Neovim and desktop/dotfiles theme pickers.
 */
object ThemeManager:

  private val DefaultTheme = "aws"
  private val DefaultVariant = "dark"

  private val StandardKeyOrder = Seq(
    "FLAVOR",
    "MODE",
    "WALLPAPER",
    "WALLPAPER_SOURCE",
    "INTERVAL",
    "NVIM_COLORSCHEME"
  )

  def getTheme(fileOpt: Option[String] = None): CumulusResponse[ThemeState] =
    try
      val state = resolveTheme(fileOpt)
      val stateWithHighlights = enrichWithHighlights(state)
      CumulusResponse(success = true, data = Some(stateWithHighlights), error = None, error_code = None)
    catch
      case e: Exception =>
        CumulusResponse(success = false, data = None, error = Some(s"Error getting theme state: ${e.getMessage}"), error_code = Some("INTERNAL_ERROR"))

  def listThemes(): CumulusResponse[Map[String, Seq[String]]] =
    try
      val themes = Map(
        "aws" -> Seq("light", "dark"),
        "azure" -> Seq("light", "dark"),
        "gcp" -> Seq("light", "dark"),
        "oci" -> Seq("light", "dark")
      )
      CumulusResponse(success = true, data = Some(themes), error = None, error_code = None)
    catch
      case e: Exception =>
        CumulusResponse(success = false, data = None, error = Some(s"Error listing themes: ${e.getMessage}"), error_code = Some("INTERNAL_ERROR"))

  def setTheme(theme: String, variantOpt: Option[String] = None, fileOpt: Option[String] = None): CumulusResponse[ThemeState] =
    if theme.trim.isEmpty then
      return CumulusResponse(
        success = false,
        data = None,
        error = Some("Theme name cannot be empty"),
        error_code = Some(CumulusError.INVALID_INPUT.toString)
      )

    try
      val targetPath = getTargetStatePath(fileOpt)
      if targetPath.segmentCount > 1 then
        os.makeDir.all(targetPath / os.up)

      val values = mutable.LinkedHashMap[String, String]()
      if os.exists(targetPath) && os.isFile(targetPath) then
        for line <- os.read(targetPath).linesIterator do
          val trimmed = line.trim
          if trimmed.nonEmpty && !trimmed.startsWith("#") && !trimmed.startsWith("//") then
            val eqIdx = trimmed.indexOf('=')
            if eqIdx > 0 then
              val k = trimmed.substring(0, eqIdx).trim
              val v = unquote(trimmed.substring(eqIdx + 1).trim)
              values(k) = v

      val themeClean = theme.trim.toLowerCase
      values("NVIM_COLORSCHEME") = if themeClean.endsWith("-theme") then themeClean else s"$themeClean-theme"
      values("FLAVOR") = themeClean.stripSuffix("-theme")

      variantOpt match
        case Some(v) if v.trim.nonEmpty => values("MODE") = v.trim.toLowerCase
        case _ => if !values.contains("MODE") then values("MODE") = DefaultVariant

      val writtenKeys = mutable.Set[String]()
      val outLines = mutable.ListBuffer[String]()

      for k <- StandardKeyOrder do
        if values.contains(k) then
          outLines += s"$k=${values(k)}"
          writtenKeys += k

      for (k, v) <- values do
        if !writtenKeys.contains(k) then
          outLines += s"$k=$v"

      os.write.over(targetPath, outLines.mkString("\n") + "\n")

      val finalVariant = values.get("MODE").getOrElse(DefaultVariant)
      val themeState = ThemeState(
        theme = values("FLAVOR"),
        variant = Some(finalVariant)
      )
      val stateWithHighlights = enrichWithHighlights(themeState)
      CumulusResponse(
        success = true,
        data = Some(stateWithHighlights),
        error = None,
        error_code = None
      )
    catch
      case e: Exception =>
        CumulusResponse(success = false, data = None, error = Some(s"Error setting theme state: ${e.getMessage}"), error_code = Some("INTERNAL_ERROR"))

  private def resolveTheme(fileOpt: Option[String]): ThemeState =
    fileOpt match
      case Some(f) =>
        val p = toPath(f)
        if os.exists(p) && os.isFile(p) then
          parseStateFile(p).getOrElse(ThemeState(theme = DefaultTheme, variant = Some(DefaultVariant)))
        else
          ThemeState(theme = DefaultTheme, variant = Some(DefaultVariant))
      case None =>
        // 1. Dotfiles state file (~/.config/cumulus/theme/state)
        val dotfilesPath = getDotfilesStatePath()
        if os.exists(dotfilesPath) && os.isFile(dotfilesPath) then
          val res = parseStateFile(dotfilesPath)
          if res.isDefined then return res.get

        // 2. Neovim internal state file (~/.local/state/nvim/cumulus_theme)
        val internalPath = getInternalStatePath()
        if os.exists(internalPath) && os.isFile(internalPath) then
          val res = parseStateFile(internalPath)
          if res.isDefined then return res.get

        // 3. Default fallback
        ThemeState(theme = DefaultTheme, variant = Some(DefaultVariant))

  private def parseStateFile(path: Path): Option[ThemeState] =
    try
      val content = os.read(path)
      val values = mutable.Map[String, String]()
      var singleLineTheme: Option[String] = None

      for line <- content.linesIterator do
        val trimmed = line.trim
        if trimmed.nonEmpty && !trimmed.startsWith("#") && !trimmed.startsWith("//") then
          val eqIdx = trimmed.indexOf('=')
          if eqIdx > 0 then
            val k = trimmed.substring(0, eqIdx).trim
            val v = unquote(trimmed.substring(eqIdx + 1).trim)
            values(k) = v
          else if values.isEmpty && singleLineTheme.isEmpty then
            // Single line format e.g. "azure" or "azure-theme"
            val t = unquote(trimmed).stripSuffix("-theme").trim.toLowerCase
            singleLineTheme = Some(t)

      singleLineTheme match
        case Some(t) =>
          Some(ThemeState(theme = t, variant = Some(DefaultVariant)))
        case None =>
          val themeOpt = values.get("FLAVOR")
            .orElse(values.get("NVIM_COLORSCHEME").map(_.stripSuffix("-theme")))
            .map(_.toLowerCase)

          val variant = values.get("MODE").orElse(values.get("VARIANT")).map(_.toLowerCase).getOrElse(DefaultVariant)

          themeOpt.map(t => ThemeState(theme = t, variant = Some(variant)))
    catch
      case _: Exception => None

  private def unquote(s: String): String =
    s.stripPrefix("\"").stripSuffix("\"").stripPrefix("'").stripSuffix("'").trim

  private def toPath(f: String): Path =
    if f.startsWith("~") then
      val home = sys.props.getOrElse("user.home", "/tmp")
      val rel = f.stripPrefix("~/").stripPrefix("~").trim
      if rel.isEmpty then os.Path(home) else os.Path(home) / os.RelPath(rel)
    else if f.startsWith("/") then
      Path(f)
    else
      os.pwd / os.RelPath(f)

  private def getTargetStatePath(fileOpt: Option[String]): Path =
    fileOpt match
      case Some(f) => toPath(f)
      case None => getDotfilesStatePath()

  private def getDotfilesStatePath(): Path =
    val base = sys.env.get("XDG_CONFIG_HOME")
      .map(Path(_))
      .getOrElse(os.Path(sys.props.getOrElse("user.home", "/tmp")) / ".config")
    base / "cumulus" / "theme" / "state"

  private def getInternalStatePath(): Path =
    val base = sys.env.get("XDG_STATE_HOME")
      .map(Path(_))
      .getOrElse(os.Path(sys.props.getOrElse("user.home", "/tmp")) / ".local" / "state")
    base / "nvim" / "cumulus_theme"

  private def enrichWithHighlights(state: ThemeState): ThemeState =
    try
      val response = ThemeGenerator.generateThemeHighlights(state.theme)
      if response.success && response.data.isDefined then
        val highlights = response.data.get.highlights
        if highlights.nonEmpty then
          state.copy(highlights = Some(highlights))
        else
          state
      else
        state
    catch
      case e: Exception =>
        System.err.println(s"[ERROR] enrichWithHighlights failed for theme ${state.theme}: ${e.getMessage}")
        e.printStackTrace()
        state
