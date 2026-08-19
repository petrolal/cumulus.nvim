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

/**
 * Represents a submodule or subproject discovered in a workspace.
 *
 * @param name The name of the submodule
 * @param path The relative path from workspace root to submodule
 * @param project_type The type of project (maven, gradle, sbt, terraform, etc.)
 * @param build_tool Optional build tool name
 * @param has_wrapper Whether the submodule contains a build tool wrapper (mvnw, gradlew)
 */
case class ProjectSubmodule(
  name: String,
  path: String,
  project_type: String,
  build_tool: Option[String] = None,
  has_wrapper: Boolean = false
) derives ReadWriter

/**
 * Represents the complete topology of a scanned workspace.
 *
 * @param root Absolute path of the workspace root
 * @param primary_type Primary project type (maven, gradle, sbt, devops, polyglot, unknown)
 * @param project_types All detected project/tool types in the workspace
 * @param submodules Discovered submodules with relative paths
 * @param has_spring Whether Spring / Spring Boot was detected
 * @param iac_types Detected IaC configuration types (terraform, sam, ansible, docker, helm)
 * @param is_multi_module Whether multi-module hierarchy is present
 */
case class WorkspaceTopology(
  root: String,
  primary_type: String,
  project_types: Seq[String] = Seq.empty,
  submodules: Seq[ProjectSubmodule] = Seq.empty,
  has_spring: Boolean = false,
  iac_types: Seq[String] = Seq.empty,
  is_multi_module: Boolean = false
) derives ReadWriter

/**
 * Represents discovered DevOps & IaC tool roots in a workspace.
 *
 * @param workspace_root Absolute path of the workspace root
 * @param terraform List of discovered Terraform/OpenTofu root directory paths
 * @param sam List of discovered AWS SAM/CloudFormation root directory paths
 * @param ansible List of discovered Ansible root directory paths
 * @param docker List of discovered Docker root directory paths
 * @param helm List of discovered Helm chart directory paths
 */
case class DevopsRoots(
  workspace_root: String,
  terraform: Seq[String] = Seq.empty,
  sam: Seq[String] = Seq.empty,
  ansible: Seq[String] = Seq.empty,
  docker: Seq[String] = Seq.empty,
  helm: Seq[String] = Seq.empty
) derives ReadWriter


