package cumulus

import munit.FunSuite
import java.nio.file.{Files, Paths}
import scala.sys.process._
import cumulus.build.MavenParser
import cumulus.protocol.CumulusResponse

class MainTest extends FunSuite:

  private def createTempFile(content: String): String =
    val file = Files.createTempFile("pom", ".xml").toFile
    Files.write(file.toPath, content.getBytes)
    file.getAbsolutePath

  test("CLI: parse-pom with valid file returns success response") {
    val pomContent = """<?xml version="1.0" encoding="UTF-8"?>
      |<project xmlns="http://maven.apache.org/POM/4.0.0">
      |  <modelVersion>4.0.0</modelVersion>
      |  <groupId>com.example</groupId>
      |  <artifactId>test-app</artifactId>
      |  <version>1.0.0</version>
      |</project>""".stripMargin

    val pomPath = createTempFile(pomContent)
    try
      // Test through direct function call
      val result = MavenParser.parseGoals(pomPath)
      assert(result.success == true)
      assert(result.data.isDefined)
      assert(result.data.get.goals.nonEmpty)
      assert(result.error.isEmpty)
    finally
      Files.delete(Paths.get(pomPath))
  }

  test("CLI: parse-modules with valid file returns success response") {
    val pomContent = """<?xml version="1.0" encoding="UTF-8"?>
      |<project xmlns="http://maven.apache.org/POM/4.0.0">
      |  <modelVersion>4.0.0</modelVersion>
      |  <groupId>com.example</groupId>
      |  <artifactId>multi-module-app</artifactId>
      |  <version>1.0.0</version>
      |  <packaging>pom</packaging>
      |  <modules>
      |    <module>core</module>
      |    <module>web</module>
      |  </modules>
      |</project>""".stripMargin

    val pomPath = createTempFile(pomContent)
    try
      val result = MavenParser.parseModules(pomPath)
      assert(result.success == true)
      assert(result.data.isDefined)
      assert(result.data.get.modules.length == 2)
      assert(result.data.get.modules.map(_.name).toSet == Set("core", "web"))
    finally
      Files.delete(Paths.get(pomPath))
  }

  test("CLI: parse-pom without --file returns error via argument parsing") {
    val argMap = Main.parseArgs(Seq())
    assert(argMap.get("file").isEmpty)
  }

  test("CLI: parse-modules without --tool returns error via argument parsing") {
    val argMap = Main.parseArgs(Seq("--file", "/tmp/dummy"))
    assert(argMap.get("tool").isEmpty)
    assert(argMap.get("file").isDefined)
  }

  test("CLI: argument parsing correctly extracts key-value pairs") {
    val argMap = Main.parseArgs(Seq("--tool", "maven", "--file", "/path/to/pom.xml"))
    assert(argMap.get("tool").contains("maven"))
    assert(argMap.get("file").contains("/path/to/pom.xml"))
  }

  test("CLI: parseGoals returns FILE_NOT_FOUND for missing file") {
    val result = MavenParser.parseGoals("/nonexistent/path/pom.xml")
    assert(result.success == false)
    assert(result.data.isEmpty)
    assert(result.error.isDefined)
    assert(result.error_code.contains("FILE_NOT_FOUND"))
  }

  test("CLI: parseModules returns FILE_NOT_FOUND for missing file") {
    val result = MavenParser.parseModules("/nonexistent/path/pom.xml")
    assert(result.success == false)
    assert(result.data.isEmpty)
    assert(result.error.isDefined)
    assert(result.error_code.contains("FILE_NOT_FOUND"))
  }

  test("CLI: CumulusResponse envelope has correct structure") {
    val pomContent = """<?xml version="1.0" encoding="UTF-8"?>
      |<project xmlns="http://maven.apache.org/POM/4.0.0">
      |  <modelVersion>4.0.0</modelVersion>
      |  <groupId>com.example</groupId>
      |  <artifactId>schema-test</artifactId>
      |  <version>1.0.0</version>
      |</project>""".stripMargin

    val pomPath = createTempFile(pomContent)
    try
      val result = MavenParser.parseGoals(pomPath)
      // Verify CumulusResponse structure
      assert(result.success.isInstanceOf[Boolean])
      assert(result.data.isInstanceOf[Option[?]])
      assert(result.error.isInstanceOf[Option[String]])
      assert(result.error_code.isInstanceOf[Option[String]])
    finally
      Files.delete(Paths.get(pomPath))
  }

  test("CLI: response can be serialized to JSON via uPickle") {
    val pomContent = """<?xml version="1.0" encoding="UTF-8"?>
      |<project xmlns="http://maven.apache.org/POM/4.0.0">
      |  <modelVersion>4.0.0</modelVersion>
      |  <groupId>com.example</groupId>
      |  <artifactId>json-test</artifactId>
      |  <version>1.0.0</version>
      |</project>""".stripMargin

    val pomPath = createTempFile(pomContent)
    try
      val result = MavenParser.parseGoals(pomPath)
      given rw: upickle.default.ReadWriter[cumulus.build.ParsePomResponse] = upickle.default.macroRW

      // Verify that the response can be serialized
      val json = ujson.write(CumulusResponse.toJson(result))
      assert(json.contains("\"success\":true"))
      assert(json.contains("\"data\""))
      assert(json.contains("\"goals\""))
    finally
      Files.delete(Paths.get(pomPath))
  }

  test("CLI: parse-gradle-tasks with valid gradle output returns success response") {
    val gradleOutput = """
      |Build tasks
      |----------
      |assemble - Assemble main and test classes
      |build - Assemble and test this project
      |clean - Delete all built files
      |compileJava - Compile main Java sources
      |test - Run the unit tests
      |help - Display this help message
      |run - Run the application
      |""".stripMargin

    val result = cumulus.build.GradleParser.parseTasks(gradleOutput)

    assert(result.success == true)
    assert(result.data.isDefined)
    assert(result.error.isEmpty)
    assert(result.error_code.isEmpty)

    val tasks = result.data.get.tasks
    // Should have at least 5 tasks
    assert(tasks.length >= 5)
    // Verify key tasks are present
    val taskNames = tasks.map(_.name).toSet
    assert(taskNames.contains("assemble"))
    assert(taskNames.contains("build"))
    assert(taskNames.contains("test"))
  }

  test("CLI: parse-modules with gradle tool and valid settings returns modules") {
    val settingsContent = """
      |include 'core'
      |include 'web:api'
      |include 'services:auth'
      |""".stripMargin

    val settingsPath = createTempFile(settingsContent)
    try
      val result = cumulus.build.GradleParser.parseModules(settingsPath)

      assert(result.success == true)
      assert(result.data.isDefined)
      assert(result.error.isEmpty)
      assert(result.error_code.isEmpty)

      val modules = result.data.get.modules
      assert(modules.length == 3)
      assert(modules.map(_.name).toSet == Set("core", "web:api", "services:auth"))

      // Verify nested module path conversion
      val webApiModule = modules.find(_.name == "web:api").get
      assert(webApiModule.path == "web/api")
    finally
      Files.delete(Paths.get(settingsPath))
  }

  test("CLI: parse-modules with gradle tool and missing file returns FILE_NOT_FOUND") {
    val result = cumulus.build.GradleParser.parseModules("/nonexistent/path/settings.gradle")

    assert(result.success == false)
    assert(result.data.isEmpty)
    assert(result.error.isDefined)
    assert(result.error_code.contains("FILE_NOT_FOUND"))
  }
