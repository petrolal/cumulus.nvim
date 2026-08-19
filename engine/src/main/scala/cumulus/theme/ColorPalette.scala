package cumulus.theme

import scala.math.{pow, round}

/**
 * Utility object for color manipulation, RGB/HSL conversion, contrast ratio calculation, and shade generation.
 * All operations use native Scala integer bit manipulation without external libraries.
 */
object ColorPalette:

  // Parse hex color string to RGB tuple
  def hexToRgb(hex: String): (Int, Int, Int) =
    val cleanHex = hex.stripPrefix("#")
    if cleanHex.length != 6 then
      throw new IllegalArgumentException(s"Invalid hex color: $hex (expected #RRGGBB)")

    val rgb = Integer.parseInt(cleanHex, 16)
    val r = (rgb >> 16) & 0xFF
    val g = (rgb >> 8) & 0xFF
    val b = rgb & 0xFF
    (r, g, b)

  // Convert RGB to hex color string
  def rgbToHex(r: Int, g: Int, b: Int): String =
    val rgb = (r << 16) | (g << 8) | b
    f"#${rgb}%06X"

  // RGB to HSL conversion
  def rgbToHsl(r: Int, g: Int, b: Int): (Double, Double, Double) =
    val rn = r / 255.0
    val gn = g / 255.0
    val bn = b / 255.0

    val max = scala.math.max(rn, scala.math.max(gn, bn))
    val min = scala.math.min(rn, scala.math.min(gn, bn))
    val delta = max - min

    // Lightness
    val l = (max + min) / 2.0

    if delta == 0.0 then
      (0.0, 0.0, l)
    else
      // Saturation
      val s = if l < 0.5 then
        delta / (max + min)
      else
        delta / (2.0 - max - min)

      // Hue
      val h = if max == rn then
        ((gn - bn) / delta + (if gn < bn then 6 else 0)) / 6.0
      else if max == gn then
        ((bn - rn) / delta + 2.0) / 6.0
      else
        ((rn - gn) / delta + 4.0) / 6.0

      (h, s, l)

  // HSL to RGB conversion
  def hslToRgb(h: Double, s: Double, l: Double): (Int, Int, Int) =
    def hueToRgb(p: Double, q: Double, t: Double): Double =
      val tt = if t < 0 then t + 1 else if t > 1 then t - 1 else t
      if tt < 1.0/6.0 then p + (q - p) * 6.0 * tt
      else if tt < 1.0/2.0 then q
      else if tt < 2.0/3.0 then p + (q - p) * (2.0/3.0 - tt) * 6.0
      else p

    if s == 0.0 then
      val gray = (l * 255).toInt
      (gray, gray, gray)
    else
      val q = if l < 0.5 then l * (1.0 + s) else l + s - l * s
      val p = 2.0 * l - q

      val r = (hueToRgb(p, q, h + 1.0/3.0) * 255).toInt
      val g = (hueToRgb(p, q, h) * 255).toInt
      val b = (hueToRgb(p, q, h - 1.0/3.0) * 255).toInt

      (r, g, b)

  // Calculate relative luminance for WCAG contrast ratio
  def relativeLuminance(r: Int, g: Int, b: Int): Double =
    val rn = r / 255.0
    val gn = g / 255.0
    val bn = b / 255.0

    val rsRGB = if rn <= 0.03928 then rn / 12.92 else pow((rn + 0.055) / 1.055, 2.4)
    val gsRGB = if gn <= 0.03928 then gn / 12.92 else pow((gn + 0.055) / 1.055, 2.4)
    val bsRGB = if bn <= 0.03928 then bn / 12.92 else pow((bn + 0.055) / 1.055, 2.4)

    0.2126 * rsRGB + 0.7152 * gsRGB + 0.0722 * bsRGB

  // WCAG AA contrast ratio calculation: (L1 + 0.05) / (L2 + 0.05) where L1 >= L2
  def contrastRatio(fgHex: String, bgHex: String): Double =
    val (fgR, fgG, fgB) = hexToRgb(fgHex)
    val (bgR, bgG, bgB) = hexToRgb(bgHex)

    val fgL = relativeLuminance(fgR, fgG, fgB)
    val bgL = relativeLuminance(bgR, bgG, bgB)

    val (l1, l2) = if fgL >= bgL then (fgL, bgL) else (bgL, fgL)
    (l1 + 0.05) / (l2 + 0.05)

  // Generate shade variants by adjusting lightness in HSL
  // light: +20%, normal: 0%, dark: -20%, darker: -40%, darkest: -60%
  def generateShades(baseHex: String): Map[String, String] =
    val (r, g, b) = hexToRgb(baseHex)
    val (h, s, baseL) = rgbToHsl(r, g, b)

    Map(
      "light" -> hslToHex(h, s, scala.math.min(1.0, baseL + 0.20)),
      "normal" -> baseHex,
      "dark" -> hslToHex(h, s, scala.math.max(0.0, baseL - 0.20)),
      "darker" -> hslToHex(h, s, scala.math.max(0.0, baseL - 0.40)),
      "darkest" -> hslToHex(h, s, scala.math.max(0.0, baseL - 0.60))
    )

  // Generate shade with specific lightness adjustment
  def generateShadeWithLightness(baseHex: String, lightnessOffset: Double): String =
    val (r, g, b) = hexToRgb(baseHex)
    val (h, s, l) = rgbToHsl(r, g, b)
    val newL = scala.math.max(0.0, scala.math.min(1.0, l + lightnessOffset))
    hslToHex(h, s, newL)

  // Helper: HSL to hex
  def hslToHex(h: Double, s: Double, l: Double): String =
    val (r, g, b) = hslToRgb(h, s, l)
    rgbToHex(r, g, b)

  // Verify contrast ratio meets WCAG AA (4.5:1 for normal text, 3:1 for large text)
  def meetsWcagAA(fgHex: String, bgHex: String, largeText: Boolean = false): Boolean =
    val ratio = contrastRatio(fgHex, bgHex)
    if largeText then ratio >= 3.0 else ratio >= 4.5

  // Round double to 2 decimal places for display
  def roundTo(value: Double, decimals: Int): Double =
    val factor = pow(10, decimals)
    round(value * factor) / factor
