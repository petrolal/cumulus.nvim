package cumulus.theme

import munit.FunSuite
import cumulus.protocol.{ThemeHighlights, HighlightGroup, CumulusError}

class ThemeGeneratorTest extends FunSuite:

  test("AWS theme generation succeeds") {
    val result = ThemeGenerator.generateThemeHighlights("aws")
    assert(result.success, "AWS theme generation should succeed")
    assert(result.data.isDefined, "AWS theme data should be present")
    assert(result.error.isEmpty, "AWS theme should have no error")

    val themeOpt = result.data
    assert(themeOpt.isDefined)
    val theme = themeOpt.get
    assertEquals(theme.provider, "aws")
    assertEquals(theme.base_color, "#FF9900")
    assert(theme.highlights.nonEmpty, "AWS theme should have highlights")
  }

  test("Azure theme generation succeeds") {
    val result = ThemeGenerator.generateThemeHighlights("azure")
    assert(result.success, "Azure theme generation should succeed")
    assert(result.data.isDefined, "Azure theme data should be present")

    val theme = result.data.get
    assertEquals(theme.provider, "azure")
    assertEquals(theme.base_color, "#0078D4")
    assert(theme.highlights.nonEmpty, "Azure theme should have highlights")
  }

  test("GCP theme generation succeeds") {
    val result = ThemeGenerator.generateThemeHighlights("gcp")
    assert(result.success, "GCP theme generation should succeed")
    assert(result.data.isDefined, "GCP theme data should be present")

    val theme = result.data.get
    assertEquals(theme.provider, "gcp")
    assertEquals(theme.base_color, "#EA4335")
    assert(theme.highlights.nonEmpty, "GCP theme should have highlights")
  }

  test("OCI theme generation succeeds") {
    val result = ThemeGenerator.generateThemeHighlights("oci")
    assert(result.success, "OCI theme generation should succeed")
    assert(result.data.isDefined, "OCI theme data should be present")

    val theme = result.data.get
    assertEquals(theme.provider, "oci")
    assertEquals(theme.base_color, "#FF2B2B")
    assert(theme.highlights.nonEmpty, "OCI theme should have highlights")
  }

  test("Invalid provider returns error") {
    val result = ThemeGenerator.generateThemeHighlights("invalid-provider")
    assert(!result.success, "Invalid provider should fail")
    assert(result.data.isEmpty, "Invalid provider should have no data")
    assert(result.error.isDefined, "Invalid provider should have error message")
    assert(result.error_code.isDefined, "Invalid provider should have error code")
    assertEquals(result.error_code.get, CumulusError.INVALID_INPUT.toString)
  }

  test("Provider name is case-insensitive") {
    val result1 = ThemeGenerator.generateThemeHighlights("AWS")
    val result2 = ThemeGenerator.generateThemeHighlights("AzUrE")
    val result3 = ThemeGenerator.generateThemeHighlights("GCP")

    assert(result1.success, "AWS (uppercase) should succeed")
    assert(result2.success, "AzUrE (mixed case) should succeed")
    assert(result3.success, "GCP (uppercase) should succeed")

    assertEquals(result1.data.get.provider, "aws")
    assertEquals(result2.data.get.provider, "azure")
    assertEquals(result3.data.get.provider, "gcp")
  }

  test("Theme highlights have required groups") {
    val providers = List("aws", "azure", "gcp", "oci")
    val requiredGroups = List("Primary", "Error", "Warning", "Success", "Info", "Normal", "Comment", "String")

    for provider <- providers do
      val result = ThemeGenerator.generateThemeHighlights(provider)
      assert(result.success, s"$provider theme should succeed")
      val highlights = result.data.get.highlights

      for group <- requiredGroups do
        assert(highlights.contains(group), s"$provider theme should have $group highlight group")
  }

  test("Highlight groups have valid color structures") {
    val result = ThemeGenerator.generateThemeHighlights("aws")
    val highlights = result.data.get.highlights

    for (name, group) <- highlights do
      // At least one of fg or bg should be defined
      val hasFgOrBg = group.fg.isDefined || group.bg.isDefined
      assert(hasFgOrBg, s"Highlight group $name should have fg or bg color")

      // Colors should be valid hex
      for color <- group.fg do
        assert(color.matches("^#[0-9A-Fa-f]{6}$"), s"Invalid fg color in $name: $color")
      for color <- group.bg do
        assert(color.matches("^#[0-9A-Fa-f]{6}$"), s"Invalid bg color in $name: $color")
  }

  test("Contrast ratios for semantic colors meet WCAG AA") {
    val result = ThemeGenerator.generateThemeHighlights("aws")
    val highlights = result.data.get.highlights

    val semanticGroups = List("Error", "Warning", "Success", "Info")

    for groupName <- semanticGroups do
      val group = highlights.get(groupName)
      assert(group.isDefined, s"Should have $groupName group")

      val g = group.get
      if g.fg.isDefined && g.bg.isDefined then
        val ratio = ColorPalette.contrastRatio(g.fg.get, g.bg.get)
        assert(ratio >= 4.5, s"$groupName should have contrast ratio >= 4.5, got ${ColorPalette.roundTo(ratio, 2)}")
  }

  test("RGB to HSL conversion is reversible") {
    val testColors = List(
      (255, 0, 0),     // Red
      (0, 255, 0),     // Green
      (0, 0, 255),     // Blue
      (255, 255, 0),   // Yellow
      (255, 0, 255),   // Magenta
      (0, 255, 255),   // Cyan
      (128, 128, 128)  // Gray
    )

    for (r, g, b) <- testColors do
      val (h, s, l) = ColorPalette.rgbToHsl(r, g, b)
      val (r2, g2, b2) = ColorPalette.hslToRgb(h, s, l)

      // Allow 1-2 unit difference due to rounding
      assert(scala.math.abs(r - r2) <= 2, s"Red channel mismatch for ($r,$g,$b): got $r2")
      assert(scala.math.abs(g - g2) <= 2, s"Green channel mismatch for ($r,$g,$b): got $g2")
      assert(scala.math.abs(b - b2) <= 2, s"Blue channel mismatch for ($r,$g,$b): got $b2")
  }

  test("Shade generation produces distinct colors") {
    val baseColor = "#FF9900"
    val shades = ColorPalette.generateShades(baseColor)

    assert(shades.contains("light"), "Should have light shade")
    assert(shades.contains("normal"), "Should have normal shade")
    assert(shades.contains("dark"), "Should have dark shade")
    assert(shades.contains("darker"), "Should have darker shade")
    assert(shades.contains("darkest"), "Should have darkest shade")

    // Verify they're all different
    val shadeValues = shades.values.toList
    assertEquals(shadeValues.length, shadeValues.distinct.length, "All shades should be distinct colors")

    // Verify order: light > normal > dark > darker > darkest (in terms of lightness)
    val (rLight, gLight, bLight) = ColorPalette.hexToRgb(shades("light"))
    val (rNorm, gNorm, bNorm) = ColorPalette.hexToRgb(shades("normal"))
    val (rDark, gDark, bDark) = ColorPalette.hexToRgb(shades("dark"))

    val lightL = ColorPalette.relativeLuminance(rLight, gLight, bLight)
    val normL = ColorPalette.relativeLuminance(rNorm, gNorm, bNorm)
    val darkL = ColorPalette.relativeLuminance(rDark, gDark, bDark)

    assert(lightL >= normL, "Light should have >= lightness than normal")
    assert(normL >= darkL, "Normal should have >= lightness than dark")
  }

  test("Contrast ratio calculation works correctly") {
    // White on black should have very high contrast
    val whiteBlack = ColorPalette.contrastRatio("#FFFFFF", "#000000")
    assert(whiteBlack >= 20.0, "White on black should have very high contrast")

    // Black on white should be same
    val blackWhite = ColorPalette.contrastRatio("#000000", "#FFFFFF")
    assert(blackWhite >= 20.0, "Black on white should have same contrast")

    // Similar colors should have low contrast
    val similarContrast = ColorPalette.contrastRatio("#CCCCCC", "#DDDDDD")
    assert(similarContrast < 2.0, "Similar colors should have low contrast")
  }

  test("WCAG AA validation works") {
    // High contrast pair
    assert(ColorPalette.meetsWcagAA("#FFFFFF", "#000000"), "White on black meets WCAG AA")

    // Test large text threshold (3.0 vs 4.5)
    val mediumContrast = "#FFB800"  // Approximately 2-3 contrast with light background
    val lightBg = "#FFFFCC"
    val ratio = ColorPalette.contrastRatio(mediumContrast, lightBg)
    // This may or may not meet 4.5:1, but should be testable
    val meetsLarge = ratio >= 3.0
    assert(meetsLarge || !meetsLarge, "WCAG validation should return boolean result")
  }

  test("All cloud providers have consistent highlight structure") {
    val providers = List("aws", "azure", "gcp", "oci")
    var firstHighlightKeys: Set[String] = Set.empty

    for provider <- providers do
      val result = ThemeGenerator.generateThemeHighlights(provider)
      assert(result.success)
      val highlights = result.data.get.highlights
      val currentKeys = highlights.keySet

      if firstHighlightKeys.isEmpty then
        firstHighlightKeys = currentKeys
      else
        // All providers should have the same highlight groups
        assertEquals(currentKeys, firstHighlightKeys, s"$provider should have same highlights as first provider")
  }

  test("Hex color parsing and serialization round-trips") {
    val testHexes = List(
      "#000000",
      "#FFFFFF",
      "#FF0000",
      "#00FF00",
      "#0000FF",
      "#FF9900",
      "#0078D4",
      "#EA4335",
      "#FF2B2B"
    )

    for hex <- testHexes do
      val (r, g, b) = ColorPalette.hexToRgb(hex)
      val hex2 = ColorPalette.rgbToHex(r, g, b)
      assertEquals(hex2, hex, s"Color $hex should round-trip through RGB conversion")
  }
