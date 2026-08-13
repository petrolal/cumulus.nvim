package cumulus.build

import upickle.default.{ReadWriter, macroRW}

// Represents a Maven goal (lifecycle or plugin)
case class PomGoal(
  name: String,
  source: String // "lifecycle" or "plugin"
) derives ReadWriter

// Represents a Maven submodule
case class PomModule(
  name: String,
  path: String
) derives ReadWriter

// Response types
case class ParsePomResponse(
  goals: Seq[PomGoal]
) derives ReadWriter

case class ParseModulesResponse(
  modules: Seq[PomModule]
) derives ReadWriter
