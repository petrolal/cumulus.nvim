package cumulus.theme

import munit.FunSuite

class ThemeManagerTest extends FunSuite:

  test("getTheme returns default theme when no state file exists") {
    val tempFile = os.temp.dir() / "nonexistent" / "state"
    val res = ThemeManager.getTheme(Some(tempFile.toString))
    assert(res.success)
    assertEquals(res.data.get.theme, "aws")
    assertEquals(res.data.get.variant, Some("dark"))
  }

  test("getTheme parses existing KEY=VALUE state file") {
    val tempFile = os.temp(
      """FLAVOR=azure
        |MODE=light
        |WALLPAPER=/home/user/wall.png
        |NVIM_COLORSCHEME=azure-theme
        |""".stripMargin
    )
    try
      val res = ThemeManager.getTheme(Some(tempFile.toString))
      assert(res.success)
      assertEquals(res.data.get.theme, "azure")
      assertEquals(res.data.get.variant, Some("light"))
    finally
      os.remove(tempFile)
  }

  test("setTheme writes new theme and preserves existing properties") {
    val tempFile = os.temp(
      """FLAVOR=aws
        |MODE=dark
        |WALLPAPER=/wallpapers/cloud.jpg
        |INTERVAL=300
        |NVIM_COLORSCHEME=aws-theme
        |""".stripMargin
    )
    try
      val setRes = ThemeManager.setTheme(theme = "gcp", variantOpt = Some("dark"), fileOpt = Some(tempFile.toString))
      assert(setRes.success)
      assertEquals(setRes.data.get.theme, "gcp")

      val content = os.read(tempFile)
      assert(content.contains("FLAVOR=gcp"))
      assert(content.contains("NVIM_COLORSCHEME=gcp-theme"))
      assert(content.contains("WALLPAPER=/wallpapers/cloud.jpg"))
      assert(content.contains("INTERVAL=300"))
    finally
      os.remove(tempFile)
  }

  test("setTheme creates new file if not present") {
    val tempDir = os.temp.dir()
    val targetFile = tempDir / "state"
    try
      val res = ThemeManager.setTheme(theme = "oci-theme", variantOpt = Some("dark"), fileOpt = Some(targetFile.toString))
      assert(res.success)
      assertEquals(res.data.get.theme, "oci")

      val content = os.read(targetFile)
      assert(content.contains("FLAVOR=oci"))
      assert(content.contains("NVIM_COLORSCHEME=oci-theme"))
    finally
      os.remove.all(tempDir)
  }

  test("setTheme rejects empty theme name") {
    val res = ThemeManager.setTheme("   ")
    assert(!res.success)
    assertEquals(res.error_code, Some("INVALID_INPUT"))
  }

  test("getTheme handles comments, blank lines, and quoted values") {
    val tempFile = os.temp(
      """# This is a comment
        |
        |FLAVOR="azure"
        |MODE='light'
        |""".stripMargin
    )
    try
      val res = ThemeManager.getTheme(Some(tempFile.toString))
      assert(res.success)
      assertEquals(res.data.get.theme, "azure")
      assertEquals(res.data.get.variant, Some("light"))
    finally
      os.remove(tempFile)
  }

  test("getTheme parses single-line theme name") {
    val tempFile = os.temp("gcp-theme\n")
    try
      val res = ThemeManager.getTheme(Some(tempFile.toString))
      assert(res.success)
      assertEquals(res.data.get.theme, "gcp")
    finally
      os.remove(tempFile)
  }

  test("setTheme normalizes uppercase theme to lowercase") {
    val tempFile = os.temp("FLAVOR=aws\nMODE=DARK\n")
    try
      val res = ThemeManager.setTheme("AWS", Some("LIGHT"), Some(tempFile.toString))
      assert(res.success)
      assertEquals(res.data.get.theme, "aws")
      assertEquals(res.data.get.variant, Some("light"))
    finally
      os.remove(tempFile)
  }

  test("getTheme handles bare tilde path without crashing") {
    val res = ThemeManager.getTheme(Some("~"))
    assert(res.success)
  }


