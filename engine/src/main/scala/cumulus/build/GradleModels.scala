package cumulus.build

import upickle.default.{ReadWriter, macroRW}

/**
 * Represents a Gradle task extracted from `gradle tasks` output.
 *
 * @param name The name of the Gradle task (e.g., "build", "test", "spring-boot:run")
 */
case class GradleTask(
  name: String
) derives ReadWriter

/**
 * Represents a Gradle submodule extracted from settings.gradle.
 *
 * @param name The module name as declared in include directive (e.g., "web:api")
 * @param path The filesystem path (colons converted to slashes, e.g., "web/api")
 */
case class GradleModule(
  name: String,
  path: String
) derives ReadWriter

/**
 * Response wrapper for parsed Gradle tasks.
 *
 * @param tasks Sequence of parsed GradleTask objects
 */
case class ParseGradleTasksResponse(
  tasks: Seq[GradleTask]
) derives ReadWriter

/**
 * Response wrapper for parsed Gradle modules.
 *
 * @param modules Sequence of parsed GradleModule objects
 */
case class ParseGradleModulesResponse(
  modules: Seq[GradleModule]
) derives ReadWriter
