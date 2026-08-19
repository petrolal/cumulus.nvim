package cumulus.build

import cumulus.protocol.{CumulusResponse, Module, ModuleTree}
import java.io.File

/**
 * MultiModuleResolver orchestrates module tree resolution for both Maven and Gradle projects.
 * Detects the build tool by checking for presence of build configuration files and delegates
 * to the appropriate parser's resolveModuleTree() method.
 */
object MultiModuleResolver:

  /**
   * Resolve modules from a project root directory by auto-detecting Maven vs Gradle.
   *
   * @param rootDir Path to the project root directory
   * @return CumulusResponse containing the ModuleTree with resolved modules
   */
  def resolve(rootDir: String): CumulusResponse[ModuleTree] =
    try
      if rootDir == null || rootDir.isEmpty then
        return CumulusResponse(
          success = false,
          data = None,
          error = Some("Root directory path cannot be null or empty"),
          error_code = Some("FILE_NOT_FOUND")
        )

      val root = new File(rootDir)
      if !root.exists() || !root.isDirectory then
        return CumulusResponse(
          success = false,
          data = None,
          error = Some(s"Directory not found or not a directory: $rootDir"),
          error_code = Some("FILE_NOT_FOUND")
        )

      val pomFile = new File(root, "pom.xml")
      val settingsFile = new File(root, "settings.gradle")
      val settingsKtsFile = new File(root, "settings.gradle.kts")
      val buildFile = new File(root, "build.gradle")
      val buildKtsFile = new File(root, "build.gradle.kts")

      // Detect project type and resolve modules accordingly
      if pomFile.exists() && pomFile.isFile then
        // Maven project detected
        MavenParser.resolveModuleTree(pomFile.getAbsolutePath)
      else if settingsFile.exists() || settingsKtsFile.exists() || buildFile.exists() || buildKtsFile.exists() then
        // Gradle project detected
        GradleParser.resolveModuleTree(rootDir)
      else
        // No Maven or Gradle project found
        CumulusResponse(
          success = false,
          data = None,
          error = Some(s"No Maven or Gradle build files found in $rootDir"),
          error_code = Some("FILE_NOT_FOUND")
        )
    catch
      case e: Exception =>
        CumulusResponse(
          success = false,
          data = None,
          error = Some(s"Error resolving modules: ${e.getMessage}"),
          error_code = Some("INTERNAL_ERROR")
        )
