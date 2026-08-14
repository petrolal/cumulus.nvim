package cumulus.workspace

import upickle.default.{ReadWriter, macroRW}

/**
 * Represents JDK installation information.
 *
 * @param java_home The absolute path to the JDK installation
 * @param version The version string of the JDK (e.g., "21.0.3")
 */
case class JdkInfo(
  java_home: String,
  version: String
) derives ReadWriter

/**
 * Represents build tool detection result.
 *
 * @param build_tool The detected build tool (maven, gradle, sbt)
 * @param wrapper Optional path to the wrapper executable (e.g., ./mvnw, ./gradlew)
 * @param executable Whether the wrapper is executable (if wrapper is present)
 * @param recommendation Optional recommendation (e.g., "chmod +x ./gradlew")
 */
case class BuildToolInfo(
  build_tool: String,
  wrapper: Option[String] = None,
  executable: Option[Boolean] = None,
  recommendation: Option[String] = None
) derives ReadWriter

/**
 * Represents workspace root detection result.
 *
 * @param root The absolute path to the workspace root (project root)
 * @param build_files List of detected build files in the workspace
 * @param is_multi_module Whether the workspace contains multiple modules
 */
case class WorkspaceInfo(
  root: String,
  build_files: List[String] = List(),
  is_multi_module: Boolean = false
) derives ReadWriter
