package cumulus.git

import upickle.default.ReadWriter

/**
 * Represents a Git merge conflict marker block.
 *
 * @param start_line 1-indexed line number of <<<<<<<
 * @param sep_line 1-indexed line number of =======
 * @param end_line 1-indexed line number of >>>>>>>
 * @param current_header Header text from <<<<<<< marker (defaults to "HEAD")
 * @param incoming_header Header text from >>>>>>> marker (defaults to "INCOMING")
 */
case class ConflictBlock(
  start_line: Int,
  sep_line: Int,
  end_line: Int,
  current_header: String,
  incoming_header: String
) derives ReadWriter
