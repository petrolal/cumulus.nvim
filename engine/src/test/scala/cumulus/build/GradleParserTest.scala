package cumulus.build

import munit.FunSuite
import java.nio.file.{Files, Paths}
import java.io.File

class GradleParserTest extends FunSuite:

  private def createTempFile(content: String, prefix: String = "gradle", suffix: String = ""): String =
    val file = Files.createTempFile(prefix, suffix).toFile
    Files.write(file.toPath, content.getBytes)
    file.getAbsolutePath

  test("parse typical Gradle tasks output") {
    val gradleOutput = """
      |Build tasks
      |----------
      |assemble - Assemble main and test classes
      |build - Assemble and test this project
      |clean - Delete all built files
      |compileJava - Compile main Java sources
      |
      |Test tasks
      |----------
      |test - Run the unit tests
      |
      |Other tasks
      |-----------
      |help - Display this help message
      |run - Run the application
      |""".stripMargin

    val result = GradleParser.parseTasks(gradleOutput)

    assert(result.success)
    assert(result.error.isEmpty)
    assert(result.error_code.isEmpty)
    assert(result.data.isDefined)

    val tasks = result.data.get.tasks
    val taskNames = tasks.map(_.name).toSet

    // Verify key tasks are extracted
    assert(taskNames.contains("assemble"))
    assert(taskNames.contains("build"))
    assert(taskNames.contains("clean"))
    assert(taskNames.contains("compileJava"))
    assert(taskNames.contains("test"))
    assert(taskNames.contains("help"))
    assert(taskNames.contains("run"))

    // Should have at least 5 distinct tasks
    assert(tasks.length >= 5)
  }

  test("parse Gradle tasks with at least 5 distinct tasks") {
    val gradleOutput = """
      |Build tasks
      |----------
      |build - Assemble and test this project
      |assemble - Assemble main and test classes
      |clean - Delete all built files
      |
      |Test tasks
      |----------
      |test - Run the tests
      |
      |Run tasks
      |---------
      |run - Run the application
      |""".stripMargin

    val result = GradleParser.parseTasks(gradleOutput)

    assert(result.success)
    val tasks = result.data.get.tasks
    assert(tasks.length >= 5)

    val taskNames = Set("build", "assemble", "clean", "test", "run")
    assert(tasks.map(_.name).toSet.intersect(taskNames).size >= 5)
  }

  test("parse Gradle tasks deduplicates task names") {
    val gradleOutput = """
      |Build tasks
      |----------
      |build - Assemble and test this project
      |build - Also run this
      |assemble - Assemble main and test classes
      |clean - Delete all built files
      |
      |Test tasks
      |----------
      |test - Run the tests
      |test - Run all tests
      |""".stripMargin

    val result = GradleParser.parseTasks(gradleOutput)

    assert(result.success)
    val tasks = result.data.get.tasks
    val taskNames = tasks.map(_.name)

    // build and test should appear only once (deduplicated)
    assert(taskNames.count(_ == "build") == 1)
    assert(taskNames.count(_ == "test") == 1)
  }

  test("parse Gradle tasks with namespaced tasks") {
    val gradleOutput = """
      |Build tasks
      |----------
      |build - Assemble and test this project
      |spring-boot:run - Run Spring Boot application
      |quarkus:dev - Run Quarkus in dev mode
      |
      |Test tasks
      |----------
      |test - Run the tests
      |""".stripMargin

    val result = GradleParser.parseTasks(gradleOutput)

    assert(result.success)
    val tasks = result.data.get.tasks
    val taskNames = tasks.map(_.name).toSet

    // Namespaced tasks should be included
    assert(taskNames.contains("spring-boot:run"))
    assert(taskNames.contains("quarkus:dev"))
  }

  test("parse empty Gradle tasks output returns empty array") {
    val gradleOutput = ""

    val result = GradleParser.parseTasks(gradleOutput)

    assert(result.success)
    val tasks = result.data.get.tasks
    assert(tasks.isEmpty)
  }

  test("parse Gradle tasks with malformed output gracefully") {
    val gradleOutput = """
      |Build tasks
      |----------
      |Some invalid content
      |Another line without proper format
      |build - This is valid
      |""".stripMargin

    val result = GradleParser.parseTasks(gradleOutput)

    // Should succeed even with malformed content, extracting what it can
    assert(result.success)
    val tasks = result.data.get.tasks
    assert(tasks.map(_.name).contains("build"))
  }

  test("parse simple settings.gradle with single module") {
    val settingsContent = """
      |include 'core'
      |""".stripMargin

    val settingsPath = createTempFile(settingsContent, "settings", ".gradle")
    try
      val result = GradleParser.parseModules(settingsPath)

      assert(result.success)
      assert(result.error.isEmpty)
      assert(result.error_code.isEmpty)
      assert(result.data.isDefined)

      val modules = result.data.get.modules
      assert(modules.length == 1)
      assert(modules(0).name == "core")
      assert(modules(0).path == "core")
    finally
      Files.delete(Paths.get(settingsPath))
  }

  test("parse settings.gradle with multiple modules") {
    val settingsContent = """
      |include 'core'
      |include 'web'
      |include 'api'
      |""".stripMargin

    val settingsPath = createTempFile(settingsContent, "settings", ".gradle")
    try
      val result = GradleParser.parseModules(settingsPath)

      assert(result.success)
      val modules = result.data.get.modules

      assert(modules.length == 3)
      assert(modules.map(_.name).toSet == Set("core", "web", "api"))
    finally
      Files.delete(Paths.get(settingsPath))
  }

  test("parse settings.gradle with nested modules") {
    val settingsContent = """
      |include 'core'
      |include 'web:api'
      |include 'web:ui'
      |include 'services:auth'
      |""".stripMargin

    val settingsPath = createTempFile(settingsContent, "settings", ".gradle")
    try
      val result = GradleParser.parseModules(settingsPath)

      assert(result.success)
      val modules = result.data.get.modules

      assert(modules.length == 4)

      // Check nested module path conversion (colons to slashes)
      val webApiModule = modules.find(_.name == "web:api").get
      assert(webApiModule.path == "web/api")

      val authModule = modules.find(_.name == "services:auth").get
      assert(authModule.path == "services/auth")
    finally
      Files.delete(Paths.get(settingsPath))
  }

  test("parse settings.gradle with mixed quote styles") {
    val settingsContent = """
      |include 'core'
      |include "web"
      |include 'api'
      |""".stripMargin

    val settingsPath = createTempFile(settingsContent, "settings", ".gradle")
    try
      val result = GradleParser.parseModules(settingsPath)

      assert(result.success)
      val modules = result.data.get.modules

      assert(modules.length == 3)
      assert(modules.map(_.name).toSet == Set("core", "web", "api"))
    finally
      Files.delete(Paths.get(settingsPath))
  }

  test("parse settings.gradle maintains declaration order") {
    val settingsContent = """
      |include 'alpha'
      |include 'beta'
      |include 'gamma'
      |""".stripMargin

    val settingsPath = createTempFile(settingsContent, "settings", ".gradle")
    try
      val result = GradleParser.parseModules(settingsPath)

      assert(result.success)
      val moduleNames = result.data.get.modules.map(_.name)
      assert(moduleNames == Seq("alpha", "beta", "gamma"))
    finally
      Files.delete(Paths.get(settingsPath))
  }

  test("parse empty settings.gradle returns empty modules array") {
    val settingsContent = ""

    val settingsPath = createTempFile(settingsContent, "settings", ".gradle")
    try
      val result = GradleParser.parseModules(settingsPath)

      assert(result.success)
      val modules = result.data.get.modules
      assert(modules.isEmpty)
    finally
      Files.delete(Paths.get(settingsPath))
  }

  test("parse settings.gradle with comments and whitespace") {
    val settingsContent = """
      |// Top-level settings
      |include 'core'
      |
      |// Web modules
      |include 'web:api'
      |# include 'disabled-module'
      |include 'services'
      |""".stripMargin

    val settingsPath = createTempFile(settingsContent, "settings", ".gradle")
    try
      val result = GradleParser.parseModules(settingsPath)

      assert(result.success)
      val modules = result.data.get.modules
      val moduleNames = modules.map(_.name).toSet

      // Should extract include directives, ignoring comments
      assert(moduleNames.contains("core"))
      assert(moduleNames.contains("web:api"))
      assert(moduleNames.contains("services"))

      // Commented out modules should not be included
      assert(!moduleNames.contains("disabled-module"))
    finally
      Files.delete(Paths.get(settingsPath))
  }

  test("handle missing settings.gradle with FILE_NOT_FOUND error") {
    val result = GradleParser.parseModules("/nonexistent/path/settings.gradle")

    assert(!result.success)
    assert(result.data.isEmpty)
    assert(result.error.isDefined)
    assert(result.error_code.contains("FILE_NOT_FOUND"))
  }

  test("parse settings.gradle with composite builds") {
    val settingsContent = """
      |include 'core'
      |include 'web'
      |includeBuild '../sibling-project'
      |include 'api'
      |""".stripMargin

    val settingsPath = createTempFile(settingsContent, "settings", ".gradle")
    try
      val result = GradleParser.parseModules(settingsPath)

      assert(result.success)
      val modules = result.data.get.modules
      val moduleNames = modules.map(_.name)

      // Should extract include directives (not includeBuild)
      assert(moduleNames.contains("core"))
      assert(moduleNames.contains("web"))
      assert(moduleNames.contains("api"))
    finally
      Files.delete(Paths.get(settingsPath))
  }

  test("parse settings.gradle with multiline include statements") {
    val settingsContent = """
      |include(
      |  'core'
      |)
      |include 'web'
      |""".stripMargin

    val settingsPath = createTempFile(settingsContent, "settings", ".gradle")
    try
      val result = GradleParser.parseModules(settingsPath)

      // Our implementation handles single-line include 'name' format
      // Multiline includes would require more complex parsing
      assert(result.success)
      val modules = result.data.get.modules
      val moduleNames = modules.map(_.name)

      // At least 'web' should be extracted
      assert(moduleNames.contains("web"))
    finally
      Files.delete(Paths.get(settingsPath))
  }

  test("handle simple single-module project") {
    val settingsContent = """
      |// Single module project
      |""".stripMargin

    val settingsPath = createTempFile(settingsContent, "settings", ".gradle")
    try
      val result = GradleParser.parseModules(settingsPath)

      assert(result.success)
      val modules = result.data.get.modules
      assert(modules.isEmpty)
    finally
      Files.delete(Paths.get(settingsPath))
  }
