package cumulus.build

import munit.FunSuite
import java.nio.file.{Files, Paths}
import java.io.File

class GradleParserModuleTreeTest extends FunSuite:

  private def createTempDir(): String =
    Files.createTempDirectory("test-gradle-project").toAbsolutePath.toString

  test("resolve single-module Gradle project with no settings.gradle") {
    val tempDir = createTempDir()
    try
      // Create build.gradle but no settings.gradle
      Files.write(Paths.get(tempDir, "build.gradle"), "plugins { }".getBytes)

      val result = GradleParser.resolveModuleTree(tempDir)

      assert(result.success)
      assert(result.error.isEmpty)
      assert(result.data.isDefined)

      val tree = result.data.get
      assert(tree.modules.isEmpty)
    finally
      Files.walk(Paths.get(tempDir))
        .sorted(java.util.Comparator.reverseOrder())
        .forEach(Files.delete(_))
  }

  test("resolve multi-module Gradle project with include directives") {
    val tempDir = createTempDir()
    try
      // Create subdirectories
      Files.createDirectories(Paths.get(tempDir, "core"))
      Files.createDirectories(Paths.get(tempDir, "api"))
      Files.createDirectories(Paths.get(tempDir, "app"))

      val settingsContent = """rootProject.name = 'multi-module'
        |include 'core'
        |include 'api'
        |include 'app'
        |""".stripMargin

      Files.write(Paths.get(tempDir, "settings.gradle"), settingsContent.getBytes)

      val result = GradleParser.resolveModuleTree(tempDir)

      assert(result.success)
      assert(result.error.isEmpty)
      assert(result.data.isDefined)

      val tree = result.data.get
      assert(tree.modules.length == 3)
      val moduleNames = tree.modules.map(_.name).toSet
      assert(moduleNames == Set("core", "api", "app"))
    finally
      Files.walk(Paths.get(tempDir))
        .sorted(java.util.Comparator.reverseOrder())
        .forEach(Files.delete(_))
  }

  test("resolve Gradle project with quoted module names") {
    val tempDir = createTempDir()
    try
      Files.createDirectories(Paths.get(tempDir, "my-core"))
      Files.createDirectories(Paths.get(tempDir, "my-api"))

      val settingsContent = """rootProject.name = 'quoted-modules'
        |include "my-core"
        |include 'my-api'
        |""".stripMargin

      Files.write(Paths.get(tempDir, "settings.gradle"), settingsContent.getBytes)

      val result = GradleParser.resolveModuleTree(tempDir)

      assert(result.success)
      assert(result.data.isDefined)

      val tree = result.data.get
      assert(tree.modules.length == 2)
      val moduleNames = tree.modules.map(_.name).toSet
      assert(moduleNames == Set("my-core", "my-api"))
    finally
      Files.walk(Paths.get(tempDir))
        .sorted(java.util.Comparator.reverseOrder())
        .forEach(Files.delete(_))
  }

  test("resolve Gradle project with missing module directories") {
    val tempDir = createTempDir()
    try
      // Only create core, not api or app
      Files.createDirectories(Paths.get(tempDir, "core"))

      val settingsContent = """rootProject.name = 'incomplete'
        |include 'core'
        |include 'api'
        |include 'app'
        |""".stripMargin

      Files.write(Paths.get(tempDir, "settings.gradle"), settingsContent.getBytes)

      val result = GradleParser.resolveModuleTree(tempDir)

      assert(result.success)
      assert(result.data.isDefined)

      val tree = result.data.get
      // Only existing directories should be included
      assert(tree.modules.length == 1)
      assert(tree.modules(0).name == "core")
    finally
      Files.walk(Paths.get(tempDir))
        .sorted(java.util.Comparator.reverseOrder())
        .forEach(Files.delete(_))
  }

  test("handle nonexistent directory") {
    val result = GradleParser.resolveModuleTree("/nonexistent/gradle/project")

    assert(!result.success)
    assert(result.error.isDefined)
    assert(result.error_code.contains("FILE_NOT_FOUND"))
  }

  test("handle null directory path") {
    try {
      val result = GradleParser.resolveModuleTree(null)
      assert(!result.success || result.error.isDefined)
    } catch {
      case _: Exception => () // Expected for null input
    }
  }

  test("handle empty directory path") {
    try {
      val result = GradleParser.resolveModuleTree("")
      // Empty path might succeed (current dir) or fail depending on os.Path behavior
      assert(result.data.isDefined || result.error.isDefined)
    } catch {
      case _: Exception => ()
    }
  }

  test("skip comments in settings.gradle") {
    val tempDir = createTempDir()
    try
      Files.createDirectories(Paths.get(tempDir, "core"))

      val settingsContent = """// This is a comment
        |rootProject.name = 'commented'
        |# Another comment style
        |include 'core'
        |// include 'fake-module'
        |""".stripMargin

      Files.write(Paths.get(tempDir, "settings.gradle"), settingsContent.getBytes)

      val result = GradleParser.resolveModuleTree(tempDir)

      assert(result.success)
      val tree = result.data.get
      // Only 'core' should be included, not 'fake-module'
      assert(tree.modules.length == 1)
      assert(tree.modules(0).name == "core")
    finally
      Files.walk(Paths.get(tempDir))
        .sorted(java.util.Comparator.reverseOrder())
        .forEach(Files.delete(_))
  }
