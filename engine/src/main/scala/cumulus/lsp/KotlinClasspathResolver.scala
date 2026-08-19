package cumulus.lsp

import cumulus.workspace.{GradleClasspathResolver, MavenClasspathResolver}
import os.Path
import scala.collection.mutable

/**
 * Discovers Kotlin source roots and dependency JARs for Gradle and Maven projects.
 */
object KotlinClasspathResolver:

  /**
   * Discover Kotlin source roots in a project directory.
   * Checks standard conventions, Kotlin Multiplatform, and Android roots:
   * - src/main/kotlin
   * - src/test/kotlin
   * - src/main/java
   * - src/test/java
   * - src/commonMain/kotlin
   * - src/jvmMain/kotlin
   * - src/commonTest/kotlin
   * - src/jvmTest/kotlin
   * - src/androidMain/kotlin
   *
   * @param projectRoot The root directory of the project
   * @return Seq[String] of discovered existing source root absolute paths
   */
  def discoverSourceRoots(projectRoot: Path): Seq[String] =
    try
      val candidateSubdirs = Seq(
        "src/main/kotlin",
        "src/test/kotlin",
        "src/main/java",
        "src/test/java",
        "src/commonMain/kotlin",
        "src/jvmMain/kotlin",
        "src/commonTest/kotlin",
        "src/jvmTest/kotlin",
        "src/androidMain/kotlin"
      )

      val sourceRoots = mutable.ListBuffer[String]()
      for subdir <- candidateSubdirs do
        val candidatePath = projectRoot / os.RelPath(subdir)
        if os.exists(candidatePath) && os.isDir(candidatePath) then
          sourceRoots += candidatePath.toString

      sourceRoots.toSeq
    catch
      case _: Exception => Seq.empty

  /**
   * Resolve dependency classpath for Gradle or Maven projects.
   *
   * @param projectRoot The root directory of the project
   * @return Seq[String] of dependency JAR paths
   */
  def resolveClasspath(projectRoot: Path): Seq[String] =
    try
      val pomFile = projectRoot / "pom.xml"
      val buildGradle = projectRoot / "build.gradle"
      val buildGradleKts = projectRoot / "build.gradle.kts"

      if os.exists(pomFile) && os.isFile(pomFile) then
        MavenClasspathResolver.resolveClasspath(projectRoot.toString)
      else if (os.exists(buildGradle) && os.isFile(buildGradle)) || (os.exists(buildGradleKts) && os.isFile(buildGradleKts)) then
        GradleClasspathResolver.resolveClasspath(projectRoot.toString)
      else
        Seq.empty
    catch
      case _: Exception => Seq.empty

  /**
   * Locate the primary build definition file and maximum last modified timestamp across companion build files.
   * Checks: build.gradle.kts, build.gradle, pom.xml, settings.gradle.kts, settings.gradle, gradle.properties.
   *
   * @param projectRoot The root directory of the project
   * @return Option[(Path, Long)] with primary build file path and newest modified time in milliseconds
   */
  def findBuildFile(projectRoot: Path): Option[(Path, Long)] =
    try
      val primaryCandidates = Seq(
        projectRoot / "build.gradle.kts",
        projectRoot / "build.gradle",
        projectRoot / "pom.xml"
      )

      val companionCandidates = Seq(
        projectRoot / "settings.gradle.kts",
        projectRoot / "settings.gradle",
        projectRoot / "gradle.properties"
      )

      primaryCandidates.find(p => os.exists(p) && os.isFile(p)).map { primaryBuildFile =>
        val allExisting = (primaryBuildFile +: companionCandidates).filter(p => os.exists(p) && os.isFile(p))
        val maxMtime = allExisting.map(os.mtime).maxOption.getOrElse(os.mtime(primaryBuildFile))
        (primaryBuildFile, maxMtime)
      }
    catch
      case _: Exception => None
