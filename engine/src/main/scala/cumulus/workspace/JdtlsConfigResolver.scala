package cumulus.workspace

import cumulus.protocol.{CumulusResponse, CumulusError, JdtlsConfig, JdkRuntime}
import os.Path
import scala.collection.mutable

/**
 * Orchestrates JDTLS configuration detection and resolution.
 * Combines JDTLS binary detection, Mason bundle validation, classpath resolution,
 * and JDK discovery into a single comprehensive JDTLS config response.
 */
object JdtlsConfigResolver:

  /**
   * Generate complete JDTLS configuration for a project.
   *
   * @param projectRoot The root directory of the Java/Kotlin project
   * @return CumulusResponse[JdtlsConfig] with all initialization data
   */
  def handle(projectRoot: String): CumulusResponse[JdtlsConfig] =
    try
      val rootPath = if projectRoot.startsWith("/") then
        Path(projectRoot)
      else if projectRoot == "." then
        os.pwd
      else
        os.pwd / projectRoot

      // Validate project root exists
      if !os.exists(rootPath) || !os.isDir(rootPath) then
        return CumulusResponse(
          success = false,
          data = None,
          error = Some(s"Directory not found: $projectRoot"),
          error_code = Some("FILE_NOT_FOUND")
        )

      val notes = mutable.ListBuffer[String]()

      // 1. Detect JDTLS binary
      val jdtlsPath = JdtlsDetector.detectJdtls()
      if jdtlsPath.isEmpty then
        notes += "JDTLS not found on system; please install via Mason (nvim-lspconfig) or manually"

      // 2. Validate Mason bundle (if found)
      val (bundleValid, configDir) = jdtlsPath match
        case Some(path) =>
          val masonDir = JdtlsDetector.getMasonPackageDir()
          masonDir match
            case Some(bundlePath) =>
              val valid = MasonBundleValidator.validateBundle(bundlePath)
              val configDir = if valid then MasonBundleValidator.getConfigDir(bundlePath) else None
              if !valid then
                notes += s"JDTLS Mason bundle at $bundlePath is incomplete or corrupted"
              (valid, configDir)
            case None =>
              // JDTLS found but not via Mason (e.g., in PATH)
              (true, None)
        case None =>
          (false, None)

      // 3. Resolve classpath (Maven or Gradle)
      val classpath = resolveclasspath(projectRoot)

      // 4. Detect JDK runtimes
      val jdkRuntimes = detectJdkRuntimes()

      // 5. Generate JVM arguments (Lombok support, etc.)
      val jvmArgs = generateJvmArgs(classpath)

      // 6. Generate init options for JDTLS
      val initOptions = generateInitOptions(configDir)

      // Finalize notes
      val finalNotes = if notes.nonEmpty then Some(notes.toSeq) else None

      CumulusResponse(
        success = true,
        data = Some(JdtlsConfig(
          jdtls_path = jdtlsPath,
          bundle_valid = bundleValid,
          classpath = classpath,
          jvm_runtimes = jdkRuntimes,
          jvm_args = jvmArgs,
          init_options = initOptions,
          notes = finalNotes
        )),
        error = None,
        error_code = None
      )
    catch
      case e: Exception =>
        CumulusResponse(
          success = false,
          data = None,
          error = Some(s"Error resolving JDTLS config: ${e.getMessage}"),
          error_code = Some("INTERNAL_ERROR")
        )

  /**
   * Resolve classpath for the project (Maven or Gradle).
   */
  private def resolveclasspath(projectRoot: String): Seq[String] =
    try
      val rootPath = Path(projectRoot)

      // Check if Maven project
      if os.exists(rootPath / "pom.xml") then
        MavenClasspathResolver.resolveClasspath(projectRoot)
      // Check if Gradle project
      else if os.exists(rootPath / "build.gradle") || os.exists(rootPath / "build.gradle.kts") then
        GradleClasspathResolver.resolveClasspath(projectRoot)
      else
        Seq.empty
    catch
      case _: Exception => Seq.empty

  /**
   * Detect available JDK runtimes on the system.
   */
  private def detectJdkRuntimes(): Seq[JdkRuntime] =
    try
      val scanResults = cumulus.health.JdkScanner.scanJdkInstallations()
      scanResults.map { jdk =>
        JdkRuntime(
          version = jdk.version,
          path = jdk.path,
          is_default = jdk.active
        )
      }
    catch
      case _: Exception => Seq.empty

  /**
   * Generate JVM arguments based on project configuration.
   * Includes Lombok support if lombok.jar is in classpath.
   */
  private def generateJvmArgs(classpath: Seq[String]): Seq[String] =
    val args = mutable.ListBuffer[String]()

    // Check for Lombok in classpath
    classpath.foreach { jar =>
      if jar.contains("lombok") && jar.endsWith(".jar") then
        args += s"-javaagent:$jar"
    }

    // Add common JVM args for JDTLS
    args += "-Xmx4G"

    args.toSeq

  /**
   * Generate JDTLS initialization options.
   */
  private def generateInitOptions(configDir: Option[String]): Map[String, String] =
    val opts = mutable.Map[String, String]()

    // Set config directory if available
    configDir.foreach { dir =>
      opts("configurationUpdateContentOptions.ignoreFirstIfEmpty") = "false"
      opts("configurationUpdateContentOptions.ignoreWillChangeNotification") = "false"
    }

    // Standard JDTLS options
    opts("extendedClientCapabilities.progressReportProvider") = "true"
    opts("extendedClientCapabilities.classFileContentsSupport") = "true"

    opts.toMap
