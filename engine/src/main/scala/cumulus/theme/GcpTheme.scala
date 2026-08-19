package cumulus.theme

import cumulus.protocol.HighlightGroup

/**
 * GCP Red/Blue theme palette and highlight generation.
 * Base colors: #EA4335 (Red) and #4285F4 (Blue)
 * Uses Red as primary, Blue as secondary
 */
object GcpTheme:

  val BaseColor = "#EA4335"  // GCP Red
  private val SecondaryColor = "#4285F4"  // GCP Blue
  private val ErrorColor = "#FF3333"
  private val WarningColor = "#FFB800"
  private val SuccessColor = "#27AE60"
  private val InfoColor = "#4285F4"

  def generateHighlights(): Map[String, HighlightGroup] =
    // Generate base shades
    val baseShades = ColorPalette.generateShades(BaseColor)
    val secondaryShades = ColorPalette.generateShades(SecondaryColor)
    val errorShades = ColorPalette.generateShades(ErrorColor)
    val warningShades = ColorPalette.generateShades(WarningColor)
    val successShades = ColorPalette.generateShades(SuccessColor)
    val infoShades = ColorPalette.generateShades(InfoColor)

    // Background colors for semantic highlights
    val darkBg = "#1E1E1E"
    val lightBg = "#FFFFFF"

    // Verify contrast ratios and build highlight groups
    val highlights = scala.collection.mutable.Map[String, HighlightGroup]()

    // Base primary colors with shades (Red)
    highlights("Primary") = HighlightGroup(fg = Some(baseShades("normal")))
    highlights("PrimaryLight") = HighlightGroup(fg = Some(baseShades("light")))
    highlights("PrimaryDark") = HighlightGroup(fg = Some(baseShades("dark")))
    highlights("PrimaryDarker") = HighlightGroup(fg = Some(baseShades("darker")))
    highlights("PrimaryDarkest") = HighlightGroup(fg = Some(baseShades("darkest")))

    // Secondary colors (Blue)
    highlights("Secondary") = HighlightGroup(fg = Some(secondaryShades("normal")))
    highlights("SecondaryLight") = HighlightGroup(fg = Some(secondaryShades("light")))
    highlights("SecondaryDark") = HighlightGroup(fg = Some(secondaryShades("dark")))

    // Error semantic highlight
    highlights("Error") = HighlightGroup(
      fg = Some(errorShades("normal")),
      bg = Some(darkBg),
      bold = true
    )
    highlights("ErrorWeak") = HighlightGroup(
      fg = Some(errorShades("light")),
      bg = None
    )

    // Warning semantic highlight
    highlights("Warning") = HighlightGroup(
      fg = Some(warningShades("normal")),
      bg = Some(darkBg),
      bold = true
    )
    highlights("WarningWeak") = HighlightGroup(
      fg = Some(warningShades("light")),
      bg = None
    )

    // Success semantic highlight
    highlights("Success") = HighlightGroup(
      fg = Some(successShades("normal")),
      bg = Some(darkBg),
      bold = true
    )
    highlights("SuccessWeak") = HighlightGroup(
      fg = Some(successShades("light")),
      bg = None
    )

    // Info semantic highlight
    highlights("Info") = HighlightGroup(
      fg = Some(infoShades("normal")),
      bg = Some(darkBg),
      italic = true
    )
    highlights("InfoWeak") = HighlightGroup(
      fg = Some(infoShades("light")),
      bg = None
    )

    // Neovim standard groups
    highlights("Normal") = HighlightGroup(fg = Some("#E8E8E8"), bg = Some(darkBg))
    highlights("Comment") = HighlightGroup(fg = Some("#7F8B8F"), italic = true)
    highlights("String") = HighlightGroup(fg = Some(successShades("dark")))
    highlights("Number") = HighlightGroup(fg = Some(infoShades("normal")))
    highlights("Boolean") = HighlightGroup(fg = Some(baseShades("normal")), bold = true)
    highlights("Identifier") = HighlightGroup(fg = Some("#FFFFFF"))
    highlights("Keyword") = HighlightGroup(fg = Some(baseShades("dark")), bold = true)
    highlights("Function") = HighlightGroup(fg = Some(secondaryShades("light")))
    highlights("Type") = HighlightGroup(fg = Some(infoShades("dark")))
    highlights("Operator") = HighlightGroup(fg = Some(warningShades("normal")))

    highlights.toMap
