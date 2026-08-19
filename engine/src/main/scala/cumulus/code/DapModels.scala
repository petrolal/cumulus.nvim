package cumulus.code

import upickle.default.{ReadWriter, macroRW}

/**
 * Represents a DAP (Debug Adapter Protocol) debug configuration dictionary.
 *
 * @param `type` Debugger type (defaults to "java")
 * @param name Display name of the debug configuration
 * @param request Request type ("launch" or "attach")
 * @param mainClass Optional fully-qualified main class name
 * @param projectName Optional project name
 * @param cwd Working directory
 * @param console Console type (defaults to "integratedTerminal")
 * @param preLaunchTask Optional task to run before launch (e.g., "maven: clean package", "gradle: clean build")
 * @param vmArgs Optional JVM arguments (e.g., JDWP parameters "-agentlib:jdwp=...")
 * @param args Optional application arguments
 * @param env Environment variables (e.g., SPRING_PROFILES_ACTIVE)
 * @param hostName Hostname for remote attach (e.g., "localhost")
 * @param port Port for remote attach (e.g., 5005)
 */
case class DapConfiguration(
  `type`: String = "java",
  name: String,
  request: String, // "launch" or "attach"
  mainClass: Option[String] = None,
  projectName: Option[String] = None,
  cwd: String,
  console: String = "integratedTerminal",
  preLaunchTask: Option[String] = None,
  vmArgs: Option[String] = None,
  args: Option[String] = None,
  env: Map[String, String] = Map.empty,
  hostName: Option[String] = None,
  port: Option[Int] = None
) derives ReadWriter

/**
 * Aggregated DAP configuration result containing default launch and attach configurations
 * as well as the full sequence of configurations.
 *
 * @param launch Primary launch configuration
 * @param attach Primary attach configuration
 * @param configurations Full sequence of all generated DAP configurations
 */
case class DapConfigResult(
  launch: DapConfiguration,
  attach: DapConfiguration,
  configurations: Seq[DapConfiguration] = Seq.empty
) derives ReadWriter
