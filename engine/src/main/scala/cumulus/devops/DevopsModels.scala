package cumulus.devops

import upickle.default.ReadWriter
import cumulus.protocol.HighlightGroup

/**
 * Represents code coverage data for a single source file parsed from JaCoCo XML.
 *
 * @param file The package-qualified relative path to the source file (e.g. "com/example/App.java")
 * @param covered_lines List of 1-indexed line numbers with executed instructions (ci > 0)
 * @param missed_lines List of 1-indexed line numbers with missed instructions (ci == 0 && mi > 0)
 */
case class CoverageEntry(
  file: String,
  covered_lines: Seq[Int],
  missed_lines: Seq[Int]
) derives ReadWriter

/**
 * Represents a diagnostic issue parsed from a Checkstyle XML audit report.
 *
 * @param file The path to the file containing the violation
 * @param line The 1-indexed line number (defaults to 1 if not specified)
 * @param col The 1-indexed column number, or None if omitted
 * @param severity The severity level in uppercase (e.g. "ERROR", "WARNING", "INFO")
 * @param message The Checkstyle rule violation description
 */
case class CheckstyleDiagnostic(
  file: String,
  line: Int,
  col: Option[Int],
  severity: String,
  message: String
) derives ReadWriter

/**
 * Represents an issue found when validating Flyway database migrations.
 *
 * @param file The filename of the migration script
 * @param line Optional 1-indexed line number where the issue occurs
 * @param severity Severity of the issue ("ERROR" | "WARN")
 * @param message Description of the validation issue
 */
case class MigrationIssue(
  file: String,
  line: Option[Int] = None,
  severity: String,
  message: String
) derives ReadWriter

/**
 * Represents a validation issue in a Kubernetes YAML manifest.
 *
 * @param line 1-indexed line number of the issue
 * @param col Optional 1-indexed column number
 * @param severity Severity level (defaults to "ERROR")
 * @param message Description of the validation issue
 */
case class K8sValidationIssue(
  line: Int,
  col: Option[Int] = None,
  severity: String = "ERROR",
  message: String
) derives ReadWriter

/**
 * Result of sanitizing a .vim session file.
 */
case class SessionSanitizeResult(
  success: Boolean,
  cleaned_lines: Int,
  total_lines: Int
) derives ReadWriter

/**
 * Status of Gradle wrapper and CI configuration verification.
 */
case class GradleWrapperStatus(
  local_version: Option[String],
  ci_version: Option[String],
  sha256_configured: Boolean,
  sha256_valid: Boolean,
  issues: Seq[String]
) derives ReadWriter

/**
 * Network socket connectivity status.
 */
case class NetworkStatus(
  connected: Boolean,
  host: String,
  port: Int,
  elapsed_ms: Long
) derives ReadWriter

/**
 * JDTLS classpath synchronization status.
 */
case class SyncStatus(
  sync_needed: Boolean,
  modified_file: Option[String]
) derives ReadWriter

/**
 * Project dependency information.
 */
case class DependencyInfo(
  group: String,
  artifact: String,
  version: String,
  scope: String
) derives ReadWriter

/**
 * Dependency version lens information for inlay/virtual text hints.
 */
case class DependencyLens(
  group: String,
  artifact: String,
  current_version: String,
  latest_version: String,
  line: Int,
  age_status: String
) derives ReadWriter

/**
 * Cloud theme state information including highlight definitions.
 */
case class ThemeState(
  theme: String,
  variant: Option[String] = Some("dark"),
  highlights: Option[Map[String, HighlightGroup]] = None
)

object ThemeState:
  given ThemeStateRW: ReadWriter[ThemeState] = upickle.default.readwriter[ujson.Value].bimap[ThemeState](
    state => {
      val highlightsValue = state.highlights match {
        case Some(map) =>
          val highlightObjs = upickle.core.LinkedHashMap[String, ujson.Value]()
          for ((name, group) <- map) do
            highlightObjs(name) = ujson.Obj(
              "fg" -> group.fg.fold(ujson.Null: ujson.Value)(ujson.Str(_)),
              "bg" -> group.bg.fold(ujson.Null: ujson.Value)(ujson.Str(_)),
              "bold" -> ujson.Bool(group.bold),
              "italic" -> ujson.Bool(group.italic),
              "underline" -> ujson.Bool(group.underline)
            )
          ujson.Obj(highlightObjs)
        case None => ujson.Null
      }
      ujson.Obj(
        "theme" -> ujson.Str(state.theme),
        "variant" -> state.variant.fold(ujson.Null: ujson.Value)(ujson.Str(_)),
        "highlights" -> highlightsValue
      )
    },
    json => {
      val obj = json.obj
      val theme = obj.get("theme") match {
        case Some(ujson.Str(s)) => s
        case _ => "aws"
      }
      val variant = obj.get("variant") match {
        case Some(ujson.Str(s)) => Some(s)
        case Some(ujson.Null) => None
        case _ => Some("dark")
      }
      val highlights = obj.get("highlights") match {
        case Some(ujson.Null) | None => None
        case Some(ujson.Obj(map)) =>
          val result = scala.collection.mutable.Map[String, HighlightGroup]()
          for ((name, value) <- map) do
            value match {
              case ujson.Obj(props) =>
                val fg = props.get("fg") match {
                  case Some(ujson.Str(s)) => Some(s)
                  case _ => None
                }
                val bg = props.get("bg") match {
                  case Some(ujson.Str(s)) => Some(s)
                  case _ => None
                }
                val bold = props.get("bold") match {
                  case Some(ujson.Bool(b)) => b
                  case _ => false
                }
                val italic = props.get("italic") match {
                  case Some(ujson.Bool(b)) => b
                  case _ => false
                }
                val underline = props.get("underline") match {
                  case Some(ujson.Bool(b)) => b
                  case _ => false
                }
                result(name) = HighlightGroup(fg = fg, bg = bg, bold = bold, italic = italic, underline = underline)
              case _ =>
            }
          Some(result.toMap)
        case _ => None
      }
      ThemeState(theme = theme, variant = variant, highlights = highlights)
    }
  )

/**
 * Information about a CloudFormation parameter.
 */
case class CfnParameter(
  name: String,
  param_type: String = "String",
  default_value: Option[String] = None,
  description: Option[String] = None
) derives ReadWriter

/**
 * Information about a CloudFormation resource.
 */
case class CfnResource(
  logical_id: String,
  resource_type: String,
  line: Option[Int] = None,
  properties: Map[String, String] = Map.empty
) derives ReadWriter

/**
 * Information about a Serverless/Lambda function in a template.
 */
case class SamFunctionInfo(
  logical_id: String,
  handler: Option[String] = None,
  runtime: Option[String] = None,
  code_uri: Option[String] = None,
  line: Option[Int] = None
) derives ReadWriter

/**
 * Inspected CloudFormation/SAM template metadata.
 */
case class CfnTemplateInfo(
  format_version: Option[String] = None,
  transform: Option[String] = None,
  description: Option[String] = None,
  is_sam: Boolean = false,
  parameters: Seq[CfnParameter] = Seq.empty,
  resources: Seq[CfnResource] = Seq.empty,
  functions: Seq[SamFunctionInfo] = Seq.empty,
  outputs: Seq[String] = Seq.empty
) derives ReadWriter

/**
 * Issue detected during offline CloudFormation/SAM template validation.
 */
case class CfnValidationIssue(
  line: Int,
  col: Option[Int] = None,
  severity: String = "ERROR",
  message: String,
  logical_id: Option[String] = None
) derives ReadWriter

/**
 * Information about a single task in an Ansible play or role.
 */
case class AnsibleTaskInfo(
  name: String,
  module: String,
  line: Option[Int] = None
) derives ReadWriter

/**
 * Information about a single play in an Ansible playbook.
 */
case class AnsiblePlayInfo(
  name: String,
  hosts: String,
  gather_facts: Option[Boolean] = None,
  roles: Seq[String] = Seq.empty,
  tasks: Seq[AnsibleTaskInfo] = Seq.empty,
  line: Option[Int] = None
) derives ReadWriter

/**
 * Inspected Ansible playbook metadata.
 */
case class AnsiblePlaybookInfo(
  plays: Seq[AnsiblePlayInfo] = Seq.empty,
  total_tasks: Int = 0,
  roles: Seq[String] = Seq.empty,
  hosts: Seq[String] = Seq.empty
) derives ReadWriter

/**
 * Issue detected during offline Ansible playbook validation.
 */
case class AnsibleValidationIssue(
  line: Int,
  col: Option[Int] = None,
  severity: String = "ERROR",
  message: String
) derives ReadWriter

/**
 * Parsed group from an Ansible inventory hierarchy/graph.
 */
case class AnsibleInventoryGroup(
  name: String,
  hosts: Seq[String] = Seq.empty,
  children: Seq[String] = Seq.empty,
  vars: Map[String, String] = Map.empty
) derives ReadWriter

/**
 * Represents a Terraform / OpenTofu resource or data source block.
 */
case class TerraformResource(
  resource_type: String,
  name: String,
  file: String,
  line: Int,
  is_data: Boolean = false,
  attributes: Map[String, String] = Map.empty,
  dependencies: Seq[String] = Seq.empty
) derives ReadWriter

/**
 * Represents an input variable in Terraform / OpenTofu.
 */
case class TerraformVariable(
  name: String,
  file: String,
  line: Int,
  var_type: Option[String] = None,
  default_value: Option[String] = None,
  description: Option[String] = None,
  sensitive: Boolean = false
) derives ReadWriter

/**
 * Represents an output value in Terraform / OpenTofu.
 */
case class TerraformOutput(
  name: String,
  file: String,
  line: Int,
  value: Option[String] = None,
  description: Option[String] = None,
  sensitive: Boolean = false
) derives ReadWriter

/**
 * Represents a provider block or requirement in Terraform / OpenTofu.
 */
case class TerraformProvider(
  name: String,
  file: String,
  line: Int,
  alias: Option[String] = None,
  version: Option[String] = None,
  source: Option[String] = None
) derives ReadWriter

/**
 * Directed edge in Terraform local resource dependency graph.
 */
case class TfDependencyEdge(
  from: String,
  to: String
) derives ReadWriter

/**
 * Aggregated Terraform / OpenTofu inspection information for file or directory.
 */
case class TerraformInfo(
  resources: Seq[TerraformResource] = Seq.empty,
  variables: Seq[TerraformVariable] = Seq.empty,
  outputs: Seq[TerraformOutput] = Seq.empty,
  providers: Seq[TerraformProvider] = Seq.empty,
  dependency_graph: Seq[TfDependencyEdge] = Seq.empty
) derives ReadWriter

/**
 * Standardized security finding from Trivy or tfsec scans.
 */
case class SecurityFinding(
  rule_id: String,
  title: String,
  severity: String,
  file: String,
  line: Int,
  end_line: Option[Int] = None,
  message: String,
  resolution: Option[String] = None
) derives ReadWriter

/**
 * Represents a single build stage in a multi-stage Dockerfile.
 *
 * @param name Optional stage name from `FROM ... AS <name>`
 * @param base_image The base image reference (e.g. "ubuntu:22.04", "node:20-alpine")
 * @param instructions List of instructions in this stage (RUN, COPY, ADD, etc.)
 */
case class DockerStage(
  name: Option[String] = None,
  base_image: String,
  instructions: Seq[String] = Seq.empty
) derives ReadWriter

/**
 * Represents a Dockerfile health check configuration.
 *
 * @param interval Check interval (e.g. "30s")
 * @param timeout Check timeout (e.g. "3s")
 * @param start_period Initial startup grace period (e.g. "5s")
 * @param retries Number of retries before marking as unhealthy
 * @param cmd The health check command
 */
case class HealthCheck(
  interval: Option[String] = None,
  timeout: Option[String] = None,
  start_period: Option[String] = None,
  retries: Option[Int] = None,
  cmd: Option[String] = None
) derives ReadWriter

/**
 * Parsed Dockerfile content with all multi-stage information.
 *
 * @param stages List of build stages (single or multi-stage)
 * @param exposed_ports List of exposed ports (from EXPOSE directives)
 * @param volumes List of volume mount points (from VOLUME directives)
 * @param entrypoint ENTRYPOINT directive, if present
 * @param cmd CMD directive (default command), if present
 * @param healthcheck HEALTHCHECK configuration, if present
 * @param user USER directive (if present, e.g. "appuser" or "root")
 * @param workdir WORKDIR directive (working directory)
 */
case class DockerImage(
  stages: Seq[DockerStage] = Seq.empty,
  exposed_ports: Seq[String] = Seq.empty,
  volumes: Seq[String] = Seq.empty,
  entrypoint: Option[String] = None,
  cmd: Option[String] = None,
  healthcheck: Option[HealthCheck] = None,
  user: Option[String] = None,
  workdir: Option[String] = None
) derives ReadWriter

/**
 * Represents a validation issue detected in a Dockerfile.
 *
 * @param issue_type Type of issue (e.g. "UNPINNED_BASE_IMAGE", "RUNS_AS_ROOT", "MISSING_HEALTHCHECK")
 * @param severity Severity level ("ERROR", "WARN", "INFO")
 * @param line 1-indexed line number where the issue is detected
 * @param description Human-readable description of the issue
 * @param remediation Suggested fix for the issue
 */
case class ContainerValidationIssue(
  issue_type: String,
  severity: String,
  line: Int,
  description: String,
  remediation: String
) derives ReadWriter

/**
 * Represents a Helm chart dependency in Chart.yaml.
 */
case class HelmDependency(
  name: String,
  version: String,
  repository: Option[String] = None,
  condition: Option[String] = None,
  tags: Seq[String] = Seq.empty
) derives ReadWriter

/**
 * Represents a maintainer in Chart.yaml.
 */
case class HelmMaintainer(
  name: String,
  email: Option[String] = None,
  url: Option[String] = None
) derives ReadWriter

/**
 * Inspected Helm chart metadata, dependencies, values, and templates.
 */
case class HelmChartInfo(
  name: String,
  version: String,
  apiVersion: String = "v2",
  appVersion: Option[String] = None,
  description: Option[String] = None,
  chart_type: Option[String] = None,
  keywords: Seq[String] = Seq.empty,
  maintainers: Seq[HelmMaintainer] = Seq.empty,
  dependencies: Seq[HelmDependency] = Seq.empty,
  values: Map[String, String] = Map.empty,
  templates: Seq[String] = Seq.empty,
  template_vars: Seq[String] = Seq.empty,
  chart_path: String = ""
) derives ReadWriter


