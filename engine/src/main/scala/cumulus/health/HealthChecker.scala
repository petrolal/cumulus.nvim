package cumulus.health

import cumulus.protocol.{CumulusResponse, BinaryInfo, HealthReport, GradleWrapperInfo}
import cumulus.gradle.WrapperVerifier
import cumulus.devops.GradleWrapperStatus
import os.Path
import scala.util.Try

/**
 * Orchestrates system health auditing: detects binaries, versions, JDKs, and Gradle wrapper status.
 * Returns a single structured response with all findings.
 */
object HealthChecker:

  /**
   * Perform a complete health check of the system.
   * Scans for binaries, detects compiler versions, finds JDKs, and verifies Gradle wrapper.
   */
  def checkHealth(): CumulusResponse[HealthReport] =
    try
      val cwd = os.pwd.toString

      // 1. Detect system binaries (required and optional)
      val requiredBinaries = Seq("git", "rg")
      val optionalBinaries = Seq("fd", "npm", "node", "python3", "sbt")
      val allBinaries = (requiredBinaries ++ optionalBinaries)
      val binaries = BinaryDetector.detectAllBinaries(allBinaries)

      // 2. Detect compilers (JVM and non-JVM)
      val compilerBinaries = Seq("javac", "scalac", "kotlinc", "rustc")
      val compilers = compilerBinaries.flatMap { binary =>
        BinaryDetector.detectBinary(binary) match
          case b if b.status == "ok" =>
            val version = CompilerVersionExtractor.extractVersion(binary)
            Some(cumulus.protocol.CompilerInfo(name = binary, version = version))
          case _ => None
      }

      // 3. Detect JDK installations
      val jdks = JdkScanner.scanJdkInstallations()

      // 4. Check Gradle wrapper (in current directory)
      val gradleWrapper = WrapperVerifier.verifyGradleWrapper(cwd) match
        case CumulusResponse(true, Some(status), _, _) =>
          Some(convertGradleWrapperStatus(status))
        case _ => None

      // 5. Detect build tool
      val buildTool = detectBuildTool(cwd)

      // 6. Check engine availability (simple check: assume engine is available if we're running)
      val engineAvailable = true

      // 7. Collect notes/warnings
      val notes = scala.collection.mutable.ListBuffer[String]()

      // Check for missing required binaries
      val missingRequired = binaries.filter(b =>
        requiredBinaries.contains(b.name) && b.status == "missing"
      )
      if missingRequired.nonEmpty then
        notes += s"Missing required binaries: ${missingRequired.map(_.name).mkString(", ")}"

      // Check for no JDK found
      if jdks.isEmpty then
        notes += "No JDK detected on the system"

      val notesSeq = if notes.nonEmpty then Some(notes.toSeq) else None

      val report = HealthReport(
        binaries = binaries,
        compilers = compilers,
        jdks = jdks,
        gradle_wrapper = gradleWrapper,
        build_tool = buildTool,
        engine_available = engineAvailable,
        notes = notesSeq
      )

      CumulusResponse(
        success = true,
        data = Some(report),
        error = None,
        error_code = None
      )
    catch
      case e: Exception =>
        CumulusResponse(
          success = false,
          data = None,
          error = Some(s"Health check failed: ${e.getMessage}"),
          error_code = Some("INTERNAL_ERROR")
        )

  /**
   * Detect the primary build tool in the given directory.
   */
  private def detectBuildTool(dir: String): String =
    val dirPath = os.Path(dir)
    if os.exists(dirPath / "pom.xml") && os.isFile(dirPath / "pom.xml") then
      "maven"
    else if os.exists(dirPath / "build.gradle") && os.isFile(dirPath / "build.gradle") then
      "gradle"
    else if os.exists(dirPath / "build.gradle.kts") && os.isFile(dirPath / "build.gradle.kts") then
      "gradle"
    else if os.exists(dirPath / "build.sbt") && os.isFile(dirPath / "build.sbt") then
      "sbt"
    else
      "none"

  /**
   * Convert cumulus.devops.GradleWrapperStatus to cumulus.protocol.GradleWrapperInfo
   */
  private def convertGradleWrapperStatus(status: GradleWrapperStatus): GradleWrapperInfo =
    GradleWrapperInfo(
      local_version = status.local_version,
      ci_version = status.ci_version,
      sha256_configured = status.sha256_configured,
      sha256_valid = status.sha256_valid,
      issues = status.issues
    )
