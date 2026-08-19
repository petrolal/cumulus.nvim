package cumulus.theme

import cumulus.protocol.{CumulusResponse, ThemeHighlights, CumulusError}

/**
 * Main orchestration object for theme highlight generation.
 * Routes cloud provider requests to appropriate theme providers and validates contrast ratios.
 */
object ThemeGenerator:

  /**
   * Generate theme highlights for a specified cloud provider.
   *
   * @param provider Cloud provider name: "aws", "azure", "gcp", or "oci"
   * @return CumulusResponse containing ThemeHighlights with base color and semantic highlight groups
   */
  def generateThemeHighlights(provider: String): CumulusResponse[ThemeHighlights] =
    try
      val providerLower = provider.toLowerCase.trim
      val result = providerLower match
        case "aws" =>
          val highlights = AwsTheme.generateHighlights()
          Some((AwsTheme.BaseColor, highlights))
        case "azure" =>
          val highlights = AzureTheme.generateHighlights()
          Some((AzureTheme.BaseColor, highlights))
        case "gcp" =>
          val highlights = GcpTheme.generateHighlights()
          Some((GcpTheme.BaseColor, highlights))
        case "oci" =>
          val highlights = OciTheme.generateHighlights()
          Some((OciTheme.BaseColor, highlights))
        case _ =>
          None

      result match
        case None =>
          CumulusResponse(
            success = false,
            data = None,
            error = Some(s"Unknown cloud provider: $providerLower"),
            error_code = Some(CumulusError.INVALID_INPUT.toString)
          )
        case Some((baseColor, highlights)) =>
          // Convert highlights to protocol model
          val themeHighlights = ThemeHighlights(
            provider = providerLower,
            base_color = baseColor,
            highlights = highlights
          )

          CumulusResponse(
            success = true,
            data = Some(themeHighlights),
            error = None,
            error_code = None
          )
    catch
      case e: Exception =>
        CumulusResponse(
          success = false,
          data = None,
          error = Some(s"Error generating theme highlights: ${e.getMessage}"),
          error_code = Some(CumulusError.INTERNAL_ERROR.toString)
        )

  // Private access to base colors for testing
  private[theme] object BaseColors:
    val AWS = AwsTheme.BaseColor
    val AZURE = AzureTheme.BaseColor
    val GCP = GcpTheme.BaseColor
    val OCI = OciTheme.BaseColor
