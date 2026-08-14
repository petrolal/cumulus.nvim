package cumulus.build

import upickle.default.{ReadWriter, macroRW}

/**
 * Represents a single step in the build order.
 *
 * @param step The execution order (1-indexed)
 * @param name The module name
 * @param path The module path (relative to project root)
 * @param buildCommand The build command to execute (e.g., "mvn" or "gradle")
 */
case class ModuleBuildStep(
  step: Int,
  name: String,
  path: String,
  buildCommand: String
) derives ReadWriter

/**
 * Represents the complete build order for a multi-module project.
 *
 * @param modules The ordered sequence of build steps
 * @param warnings Optional list of warnings (e.g., detected cycles)
 */
case class BuildOrder(
  modules: Seq[ModuleBuildStep],
  warnings: Option[Seq[String]] = None
) derives ReadWriter

/**
 * Response wrapper for compute-build-order subcommand.
 */
case class ComputeBuildOrderResponse(
  modules: Seq[ModuleBuildStep],
  warnings: Option[List[String]] = None
) derives ReadWriter
