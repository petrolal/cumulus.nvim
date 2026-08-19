package cumulus.format

import cumulus.protocol.{CumulusResponse, CumulusError, FormatterSpec, FormatterArg}
import os.Path
import scala.util.Try

/**
 * Orchestrates formatter detection by scanning project root for config files
 * and delegating to appropriate formatter resolver.
 */
object FormatterResolver:

  /**
   * Finds project root by traversing upward from a file path until finding
   * a marker (.scalafmt.conf, pom.xml, build.gradle, .git) or filesystem root.
   */
  private def findProjectRoot(filePath: String): String =
    try
      // Convert to absolute path using java.nio
      val javaPath = java.nio.file.Paths.get(filePath).toAbsolutePath.normalize
      val pathObj = os.Path(javaPath.toString)

      val startDir = if os.isFile(pathObj) then pathObj / os.up else pathObj
      var current = startDir

      val maxDepth = 100
      var depth = 0
      var prevPath = ""

      while depth < maxDepth do
        val currentStr = current.toString

        // Stop if we've reached the same path twice (can't go up further)
        if currentStr == prevPath then
          // Return the original starting directory if no markers found
          return startDir.toString

        prevPath = currentStr

        val hasTfFiles = try
          os.list(current).exists(p => os.isFile(p) && p.last.endsWith(".tf"))
        catch
          case _: Exception => false

        if os.exists(current / ".git") ||
           os.exists(current / ".scalafmt.conf") ||
           os.exists(current / "pom.xml") ||
           os.exists(current / "build.gradle") ||
           os.exists(current / "build.gradle.kts") ||
           os.exists(current / "ktlint.json") ||
           os.exists(current / ".prettierrc") ||
           os.exists(current / ".prettierrc.json") ||
           os.exists(current / ".prettierrc.yaml") ||
           os.exists(current / ".terraform.lock.hcl") ||
           hasTfFiles then
          return currentStr

        try
          current = current / os.up
          depth += 1
        catch
          case _: Exception =>
            // Can't go up further, return the original starting directory
            return startDir.toString

      // Fallback to starting directory
      startDir.toString
    catch
      case _: Exception => os.pwd.toString

  /**
   * Main entry point: resolves formatter for a given file.
   * Returns highest-priority formatter found in project root.
   */
  def resolveFormatter(filePath: String): CumulusResponse[FormatterSpec] =
    try
      val rootDir = findProjectRoot(filePath)
      val rootPath = os.Path(rootDir)

      // Try each resolver in priority order (language-specific first)
      // Priority: Scalafmt > Kotlin > Spotless (Maven) > Spotless (Gradle) > Prettier > Terraform

      val scalafmtResult = ScalafmtResolver.resolve(rootPath)
      if scalafmtResult.isDefined then
        return CumulusResponse(
          success = true,
          data = scalafmtResult,
          error = None,
          error_code = None
        )

      val ktlintResult = KtlintResolver.resolve(rootPath)
      if ktlintResult.isDefined then
        return CumulusResponse(
          success = true,
          data = ktlintResult,
          error = None,
          error_code = None
        )

      val spotlessMavenResult = SpotlessResolver.resolveMaven(rootPath)
      if spotlessMavenResult.isDefined then
        return CumulusResponse(
          success = true,
          data = spotlessMavenResult,
          error = None,
          error_code = None
        )

      val spotlessGradleResult = SpotlessResolver.resolveGradle(rootPath)
      if spotlessGradleResult.isDefined then
        return CumulusResponse(
          success = true,
          data = spotlessGradleResult,
          error = None,
          error_code = None
        )

      val prettierResult = PrettierResolver.resolve(rootPath)
      if prettierResult.isDefined then
        return CumulusResponse(
          success = true,
          data = prettierResult,
          error = None,
          error_code = None
        )

      val terraformResult = TerraformResolver.resolve(rootPath)
      if terraformResult.isDefined then
        return CumulusResponse(
          success = true,
          data = terraformResult,
          error = None,
          error_code = None
        )

      // No formatter found
      CumulusResponse(
        success = false,
        data = None,
        error = Some("No formatter configuration found"),
        error_code = Some("NOT_FOUND")
      )
    catch
      case e: Exception =>
        CumulusResponse(
          success = false,
          data = None,
          error = Some(s"Error resolving formatter: ${e.getMessage}"),
          error_code = Some("INTERNAL_ERROR")
        )
