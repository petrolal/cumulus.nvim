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

/**
 * Represents a dependency injected via @Autowired or @Inject.
 *
 * @param field_name The name of the field
 * @param field_type The type of the field (e.g., "MyService", "List<Bean>")
 * @param qualifier Optional qualifier annotation value (e.g., from @Qualifier)
 */
case class Dependency(
  field_name: String,
  field_type: String,
  qualifier: Option[String] = None
) derives ReadWriter

/**
 * Represents a Spring Bean definition.
 *
 * @param name The bean name (class simple name by default)
 * @param class_name The fully-qualified class name
 * @param file_path The absolute path to the source file
 * @param line_number The 1-indexed line number where the class is defined
 * @param stereotype The Spring stereotype type (@Component, @Service, etc.)
 * @param injected_dependencies Sequence of injected dependencies
 */
case class SpringBean(
  name: String,
  class_name: String,
  file_path: String,
  line_number: Int,
  stereotype: String,
  injected_dependencies: Seq[Dependency]
) derives ReadWriter

/**
 * Represents a Spring Boot application entry point and metadata.
 *
 * @param main_class The fully-qualified main class name with @SpringBootApplication
 * @param project_name The project/application name
 * @param build_tool The build tool ("maven" or "gradle", or null if not detected)
 * @param jvm_debug_args JVM debug arguments (null if not configured)
 * @param active_profiles List of active profiles
 */
case class SpringBootApp(
  main_class: String,
  project_name: String,
  build_tool: Option[String],
  jvm_debug_args: Option[String],
  active_profiles: Seq[String]
) derives ReadWriter

/**
 * Response wrapper for Spring Bean parsing results.
 *
 * @param beans The list of detected beans
 */
case class SpringBeansResponse(
  beans: Seq[SpringBean]
) derives ReadWriter
