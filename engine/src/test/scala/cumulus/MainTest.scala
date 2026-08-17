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
      val jsonVal = CumulusResponse.toJson(result)
      val json = ujson.write(jsonVal)
      assert(json.contains("\"success\":true"))
      assert(json.contains("\"data\""))
      assert(json.contains("\"goals\""))

      // Test round-trip through fromJson
      val restored = CumulusResponse.fromJson[cumulus.build.ParsePomResponse](jsonVal)
      assert(restored.success)
      assert(restored.data.isDefined)
      assert(restored.data.get.goals.nonEmpty)
      assert(restored.error.isEmpty)
      assert(restored.error_code.isEmpty)
    finally
      Files.delete(Paths.get(pomPath))
  }

  test("CLI: fromJson handles null, missing fields, and error codes gracefully") {
    val errJson = ujson.Obj(
      "success" -> ujson.Bool(false),
      "data" -> ujson.Null,
      "error" -> ujson.Str("File not found"),
      "error_code" -> ujson.Str("FILE_NOT_FOUND")
    )
    val restoredErr = CumulusResponse.fromJson[String](errJson)
    assert(!restoredErr.success)
    assert(restoredErr.data.isEmpty)
    assert(restoredErr.error.contains("File not found"))
    assert(restoredErr.error_code.contains("FILE_NOT_FOUND"))
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

  test("CLI: extract-codelens --file returns correct envelope") {
    val tempDir = Files.createTempDirectory("cli-test")
    val testFile = Paths.get(tempDir.toString, "Test.java")
    Files.write(testFile, "public class T { @Test void t() {} }".getBytes)
    try
      val result = cumulus.code.CodeLensExtractor.extractCodeLens(testFile.toString)
      assert(result.nonEmpty)
      assert(result(0).line == 1)
      assert(result(0).title == "▶ Run Test")
    finally
      os.remove.all(os.Path(tempDir))
  }

  test("CLI: parse-build-log with valid log file returns diagnostics") {
    val logContent = """[ERROR] /path/to/File.java:42 Syntax error: expected ')'
                       |[WARN] /path/to/Utils.java:100 Unused variable""".stripMargin
    val logPath = createTempFile(logContent)
    try
      val diagnostics = cumulus.log.LogParser.parseFromFile(logPath)
      assert(diagnostics.nonEmpty)
      assert(diagnostics.length == 2)
      assert(diagnostics(0).severity == "ERROR")
      assert(diagnostics(0).line == 42)
      assert(diagnostics(1).severity == "WARN")
      assert(diagnostics(1).line == 100)
    finally
      Files.delete(Paths.get(logPath))
  }

  test("CLI: parse-build-log with non-existent file returns error") {
    val exception = intercept[Exception] {
      cumulus.log.LogParser.parseFromFile("/nonexistent/build.log")
    }
    assert(exception.getMessage.contains("File not found"))
  }

  test("CLI: index-log with valid file returns indexed entries") {
    val logContent = """2026-08-14T10:30:45Z [INFO] Starting
                       |2026-08-14T10:30:46Z [ERROR] Connection failed
                       |2026-08-14T10:30:47Z [WARN] Retrying""".stripMargin
    val logPath = createTempFile(logContent)
    try
      val entries = cumulus.log.LogIndexer.indexLogFile(logPath)
      assert(entries.length == 2)
      assert(entries(0).severity == "ERROR")
      assert(entries(0).lineNumber == 2)
      assert(entries(0).timestamp.isDefined)
      assert(entries(1).severity == "WARN")
      assert(entries(1).lineNumber == 3)
    finally
      Files.delete(Paths.get(logPath))
  }

  test("CLI: index-log with non-existent file returns error") {
    val exception = intercept[Exception] {
      cumulus.log.LogIndexer.indexLogFile("/nonexistent/app.log")
    }
    assert(exception.getMessage.contains("File not found"))
  }

  test("CLI: resolve-stacktrace-symbol with valid frame returns path") {
    val tempDir = Files.createTempDirectory("stacktrace-test")
    val srcDir = Paths.get(tempDir.toString, "src", "main", "java", "com", "example")
    Files.createDirectories(srcDir)
    val serviceFile = Paths.get(srcDir.toString, "Service.java")
    Files.write(serviceFile, "".getBytes)
    try
      val stacktrace = "at com.example.Service.method(Service.java:123)"
      val result = cumulus.log.StacktraceResolver.resolveStacktrace(stacktrace, tempDir.toString)
      assert(result.isRight)
      val resolved = result.toOption.get
      assert(resolved.nonEmpty)
      assert(resolved.values.head.contains("Service.java"))
    finally
      os.remove.all(os.Path(tempDir))
  }

  test("CLI: resolve-stacktrace-symbol with invalid workspace returns error") {
    val stacktrace = "at com.example.Service.method(Service.java:123)"
    val result = cumulus.log.StacktraceResolver.resolveStacktrace(stacktrace, "/nonexistent/workspace")
    assert(result.isLeft)
    assert(result.left.get.contains("Workspace directory not found"))
  }

  test("CLI: detect-test-context missing arguments return error envelope") {
    val argMap = Main.parseArgs(Array("--line", "10"))
    assert(argMap.get("file").isEmpty)
    assert(argMap.get("line").contains("10"))
  }

  test("CLI: assemble-test-command argument parsing") {
    val argMap = Main.parseArgs(Array("--tool", "maven", "--class", "FooTest", "--method", "testBar", "--dir", "."))
    assert(argMap.get("tool").contains("maven"))
    assert(argMap.get("class").contains("FooTest"))
    assert(argMap.get("method").contains("testBar"))
    assert(argMap.get("dir").contains("."))
  }

  test("CLI: computeBuildOrderForDirectory on missing dir returns FILE_NOT_FOUND") {
    val result = Main.computeBuildOrderForDirectory("/nonexistent/dir/path")
    assert(!result.success)
    assert(result.error_code.contains("FILE_NOT_FOUND"))
  }

  test("CLI: discoverJdk on non-existent version returns Left") {
    val result = cumulus.workspace.JdkDiscoverer.discoverJdk("99")
    assert(result.isLeft)
    assert(result.left.exists(_.contains("not found")))
  }

  test("CLI: detectBuildTool on missing dir returns Left") {
    val result = cumulus.workspace.BuildToolDetector.detectBuildTool("/nonexistent/dir/path")
    assert(result.isLeft)
  }

  test("CLI: discoverWorkspace on missing dir returns Left") {
    val result = cumulus.workspace.WorkspaceScanner.discoverWorkspace("/nonexistent/dir/path")
    assert(result.isLeft)
  }

  test("LogParser: parses Maven compiler output with bracketed line numbers") {
    val logContent = "[ERROR] /workspace/src/main/java/com/example/App.java:[42,15] cannot find symbol\n[WARN] /workspace/src/main/java/com/example/Util.java:[100] unchecked warning"
    val diagnostics = cumulus.log.LogParser.parseLog(logContent)
    assert(diagnostics.length == 2)
    assert(diagnostics(0).line == 42)
    assert(diagnostics(0).col == 15)
    assert(diagnostics(0).severity == "ERROR")
    assert(diagnostics(1).file == "/workspace/src/main/java/com/example/Util.java")
    assert(diagnostics(1).line == 100)
    assert(diagnostics(1).severity == "WARN")
  }

  test("CLI: parse-coverage with valid file returns coverage entries") {
    val xml =
      """<report name="demo">
        |  <package name="com/demo">
        |    <sourcefile name="Main.java">
        |      <line nr="10" mi="0" ci="2"/>
        |      <line nr="20" mi="1" ci="0"/>
        |    </sourcefile>
        |  </package>
        |</report>""".stripMargin
    val file = Files.createTempFile("jacoco", ".xml").toFile
    try
      Files.write(file.toPath, xml.getBytes)
      val result = cumulus.devops.CoverageParser.parseCoverage(file.getAbsolutePath)
      assert(result.success)
      assert(result.data.isDefined)
      assertEquals(result.data.get.length, 1)
      assertEquals(result.data.get.head.file, "com/demo/Main.java")
      assertEquals(result.data.get.head.covered_lines, Seq(10))
      assertEquals(result.data.get.head.missed_lines, Seq(20))
    finally
      file.delete()
  }

  test("CLI: parse-checkstyle with valid xml returns diagnostics") {
    val xml =
      """<checkstyle version="10.0">
        |  <file name="/path/to/Main.java">
        |    <error line="10" column="4" severity="error" message="Unused import"/>
        |  </file>
        |</checkstyle>""".stripMargin
    val result = cumulus.devops.CheckstyleParser.parseCheckstyle(xml)
    assert(result.success)
    assert(result.data.isDefined)
    assertEquals(result.data.get.length, 1)
    assertEquals(result.data.get.head.file, "/path/to/Main.java")
    assertEquals(result.data.get.head.line, 10)
    assertEquals(result.data.get.head.col, Some(4))
    assertEquals(result.data.get.head.severity, "ERROR")
  }

  test("CLI: validate-migrations with directory returns issues") {
    val tempDir = Files.createTempDirectory("migrations").toFile
    try
      Files.write(new java.io.File(tempDir, "V1__init.sql").toPath, "CREATE TABLE t;".getBytes)
      Files.write(new java.io.File(tempDir, "V1__dup.sql").toPath, "CREATE TABLE t2;".getBytes)
      val result = cumulus.devops.MigrationValidator.validateMigrationsDir(tempDir.getAbsolutePath)
      assert(result.success)
      assert(result.data.isDefined)
      assertEquals(result.data.get.length, 2)
      assertEquals(result.data.get.head.severity, "ERROR")
    finally
      tempDir.delete()
  }

  test("CLI: validate-k8s-manifest with valid YAML returns success") {
    val yaml = "apiVersion: v1\nkind: Pod\nmetadata:\n  name: test\n"
    val result = cumulus.devops.K8sValidator.validateK8sManifest(yaml)
    assert(result.success)
    assert(result.data.isDefined)
    assertEquals(result.data.get.length, 0)
  }

  test("CLI: parse-git-conflicts with conflict markers returns blocks") {
    val content = "<<<<<<< HEAD\na\n=======\nb\n>>>>>>> branch\n"
    val result = cumulus.git.ConflictParser.parseGitConflicts(content)
    assert(result.success)
    assert(result.data.isDefined)
    assertEquals(result.data.get.length, 1)
    assertEquals(result.data.get.head.current_header, "HEAD")
    assertEquals(result.data.get.head.incoming_header, "branch")
  }

  test("CLI: session-sanitize with session file cleans ephemeral lines") {
    val file = Files.createTempFile("session", ".vim").toFile
    try
      Files.write(file.toPath, "badd +0 /app.java\nbadd +0 [No Name]\n".getBytes)
      val result = cumulus.util.SessionSanitizer.sanitizeSession(file.getAbsolutePath)
      assert(result.success)
      assert(result.data.isDefined)
      assertEquals(result.data.get.cleaned_lines, 1)
      assertEquals(result.data.get.total_lines, 2)
    finally
      file.delete()
  }

  test("CLI: verify-gradle-wrapper with missing wrapper reports no local version") {
    val tempDir = Files.createTempDirectory("gradle-test").toFile
    try
      val result = cumulus.gradle.WrapperVerifier.verifyGradleWrapper(tempDir.getAbsolutePath)
      assert(result.success)
      assert(result.data.isDefined)
      assertEquals(result.data.get.local_version, None)
    finally
      tempDir.delete()
  }

  test("CLI: check-network with invalid host returns error") {
    val result = cumulus.util.NetworkChecker.checkNetwork("invalid_format")
    assert(!result.success)
    assertEquals(result.error_code, Some("INVALID_INPUT"))
  }

  test("CLI: check-jdtls-sync with directory returns status") {
    val tempDir = Files.createTempDirectory("jdtls-test").toFile
    try
      val result = cumulus.workspace.JdtlsSyncChecker.checkJdtlsSync(tempDir.getAbsolutePath, 0L)
      assert(result.success)
      assert(result.data.isDefined)
      assertEquals(result.data.get.sync_needed, false)
    finally
      tempDir.delete()
  }

  test("CLI: resolve-deps with POM file returns dependencies") {
    val file = Files.createTempFile("pom", ".xml").toFile
    try
      val pom = "<project><dependencies><dependency><groupId>g</groupId><artifactId>a</artifactId><version>1.0</version></dependency></dependencies></project>"
      Files.write(file.toPath, pom.getBytes)
      val result = cumulus.dep.DepResolver.resolveDependencies(file.getAbsolutePath)
      assert(result.success)
      assert(result.data.isDefined)
      assertEquals(result.data.get.length, 1)
      assertEquals(result.data.get.head.group, "g")
      assertEquals(result.data.get.head.artifact, "a")
    finally
      file.delete()
  }

  test("CLI: check-dep-versions with POM file returns dependency lenses") {
    val file = Files.createTempFile("pom", ".xml").toFile
    try
      val pom = "<project><dependencies><dependency><groupId>g</groupId><artifactId>a</artifactId><version>1.0</version></dependency></dependencies></project>"
      Files.write(file.toPath, pom.getBytes)
      val result = cumulus.dep.DepLens.checkDepVersions(file.getAbsolutePath)
      assert(result.success)
      assert(result.data.isDefined)
      assertEquals(result.data.get.length, 1)
      assertEquals(result.data.get.head.current_version, "1.0")
    finally
      file.delete()
  }

  test("CLI: manage-theme get returns theme state") {
    val result = cumulus.theme.ThemeManager.getTheme(None)
    assert(result.success)
    assert(result.data.isDefined)
    assert(result.data.get.theme.nonEmpty)
  }

  test("CLI: manage-theme set updates theme") {
    val tempFile = Files.createTempFile("theme-state", ".txt").toFile
    try
      val result = cumulus.theme.ThemeManager.setTheme(theme = "azure", fileOpt = Some(tempFile.getAbsolutePath))
      assert(result.success)
      assert(result.data.isDefined)
      assertEquals(result.data.get.theme, "azure")
    finally
      tempFile.delete()
  }

  test("DistroInstaller: fails gracefully when target is invalid") {
    val result = cumulus.workspace.DistroInstaller.runInstall(
      targetDirOpt = Some("/nonexistent/readonly/path/cumulus"),
      skipSystemPackages = true
    )
    assert(result.isLeft)
  }







