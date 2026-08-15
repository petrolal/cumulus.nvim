package cumulus.code

import upickle.default.{ReadWriter, macroRW}

/**
 * Represents a single CodeLens item to be rendered by the client.
 *
 * @param line The 1-indexed line number where this code hint applies
 * @param title The title/label for the CodeLens (e.g., "▶ Run Test")
 */
case class CodeLensItem(
  line: Int,
  title: String
) derives ReadWriter

/**
 * Response wrapper for CodeLens extraction results.
 *
 * @param items The list of detected CodeLens items
 */
case class CodeLensResponse(
  items: Seq[CodeLensItem]
) derives ReadWriter
