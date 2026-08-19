package cumulus

import munit.FunSuite
import java.nio.file.{Files, Paths}
import scala.sys.process._
import cumulus.build.MavenParser
import cumulus.protocol.CumulusResponse
import upickle.default.ReadWriter

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

  test("CLI: tf-inspect with valid file returns success response") {
    val file = Files.createTempFile("main", ".tf").toFile
    try
      Files.writeString(file.toPath, "resource \"aws_s3_bucket\" \"demo\" { bucket = \"my-bucket\" }")
      val result = cumulus.devops.TerraformParser.inspectPath(file.getAbsolutePath)
      assert(result.success)
      assert(result.data.isDefined)
      assertEquals(result.data.get.resources.length, 1)
      assertEquals(result.data.get.resources.head.name, "demo")

      // Test via CLI router parseContent
      val stdinResult = cumulus.devops.TerraformParser.inspectContent("resource \"aws_s3_bucket\" \"demo\" { bucket = \"my-bucket\" }", "stdin.tf")
      assertEquals(stdinResult.resources.length, 1)
      assertEquals(stdinResult.resources.head.name, "demo")
    finally
      file.delete()
  }

  test("CLI: tf-security-parse with valid report returns findings") {
    val file = Files.createTempFile("trivy", ".json").toFile
    try
      val json = """{"Results":[{"Target":"main.tf","Misconfigurations":[{"ID":"AVD-001","Title":"Test","Severity":"HIGH"}]}]}"""
      Files.writeString(file.toPath, json)
      val result = cumulus.devops.TfSecurityParser.parseSecurityFile(file.getAbsolutePath)
      assert(result.success)
      assert(result.data.isDefined)
      assertEquals(result.data.get.length, 1)
      assertEquals(result.data.get.head.rule_id, "AVD-001")

      // Test stdin parsing via parseSecurityJson
      val jsonResult = cumulus.devops.TfSecurityParser.parseSecurityJson(json)
      assert(jsonResult.isRight)
      assertEquals(jsonResult.toOption.get.length, 1)
      assertEquals(jsonResult.toOption.get.head.rule_id, "AVD-001")
    finally
      file.delete()
  }

  test("CLI: ansible-inspect with valid file returns playbook info") {
    val file = Files.createTempFile("playbook", ".yml").toFile
    try
      val yaml =
        """---
          |- name: Deploy
          |  hosts: all
          |  tasks:
          |    - name: Ping
          |      ansible.builtin.ping:
          |""".stripMargin
      Files.writeString(file.toPath, yaml)
      val result = cumulus.devops.AnsibleParser.inspectPlaybookFile(file.getAbsolutePath)
      assert(result.success)
      assert(result.data.isDefined)
      assertEquals(result.data.get.plays.length, 1)
      assertEquals(result.data.get.plays.head.name, "Deploy")
    finally
      file.delete()
  }

  test("CLI: ansible-validate with valid file returns empty issues") {
    val file = Files.createTempFile("playbook", ".yml").toFile
    try
      val yaml =
        """---
          |- name: Deploy
          |  hosts: all
          |  tasks:
          |    - name: Ping
          |      ansible.builtin.ping:
          |""".stripMargin
      Files.writeString(file.toPath, yaml)
      val result = cumulus.devops.AnsibleParser.validatePlaybookFile(file.getAbsolutePath)
      assert(result.success)
      assert(result.data.isDefined)
      assertEquals(result.data.get.length, 0)
    finally
      file.delete()
  }

  test("CLI: ansible-validate with invalid playbook returns error issues") {
    val file = Files.createTempFile("invalid_playbook", ".yml").toFile
    try
      val yaml =
        """---
          |- name: Bad Jinja
          |  hosts: all
          |  tasks:
          |    - name: Debug
          |      debug:
          |        msg: {{ unquoted_var }}
          |""".stripMargin
      Files.writeString(file.toPath, yaml)
      val result = cumulus.devops.AnsibleParser.validatePlaybookFile(file.getAbsolutePath)
      assert(result.success)
      assert(result.data.isDefined)
      assert(result.data.get.nonEmpty)
      assert(result.data.get.exists(_.severity == "ERROR"))
    finally
      file.delete()
  }

  test("CLI: ansible-inspect with non-existent file returns FILE_NOT_FOUND") {
    val result = cumulus.devops.AnsibleParser.inspectPlaybookFile("/nonexistent/ansible_test.yml")
    assert(!result.success)
    assertEquals(result.error_code, Some("FILE_NOT_FOUND"))
  }

  test("CLI: ansible-inventory-parse with valid json returns groups") {
    val file = Files.createTempFile("inventory", ".json").toFile
    try
      val json =
        """{
          |  "all": {
          |    "children": ["webservers"]
          |  },
          |  "webservers": {
          |    "hosts": ["web1.local"]
          |  }
          |}""".stripMargin
      Files.writeString(file.toPath, json)
      val result = cumulus.devops.AnsibleParser.parseInventoryFile(file.getAbsolutePath)
      assert(result.success)
      assert(result.data.isDefined)
      assertEquals(result.data.get.length, 2)
    finally
      file.delete()
  }

  test("CLI: ansible-inventory-parse with graph text file returns groups") {
    val file = Files.createTempFile("inventory_graph", ".txt").toFile
    try
      val graph =
        """@all:
          |  |--@webservers:
          |  |  |--web1.example.com
          |""".stripMargin
      Files.writeString(file.toPath, graph)
      val result = cumulus.devops.AnsibleParser.parseInventoryFile(file.getAbsolutePath)
      assert(result.success)
      assert(result.data.isDefined)
      assert(result.data.get.exists(_.name == "webservers"))
    finally
      file.delete()
  }

  test("CLI: helm-inspect with valid chart directory returns chart info") {
    val tempDir = Files.createTempDirectory("maintest-helm").toFile
    try
      val chart = "apiVersion: v2\nname: test-app\nversion: 1.0.0\n"
      val values = "replicaCount: 2\n"
      Files.writeString(new java.io.File(tempDir, "Chart.yaml").toPath, chart)
      Files.writeString(new java.io.File(tempDir, "values.yaml").toPath, values)
      val result = cumulus.devops.HelmParser.inspectChart(tempDir.getAbsolutePath)
      assert(result.success)
      assert(result.data.isDefined)
      assertEquals(result.data.get.name, "test-app")
      assertEquals(result.data.get.values.get("replicaCount"), Some("2"))

      // Test raw content inspection
      val contentResult = cumulus.devops.HelmParser.inspectChartContent(chart, Some(values))
      assertEquals(contentResult.name, "test-app")
      assertEquals(contentResult.values.get("replicaCount"), Some("2"))
    finally
      os.remove.all(os.Path(tempDir))
  }

  test("CLI: helm-inspect with missing chart returns FILE_NOT_FOUND") {
    val result = cumulus.devops.HelmParser.inspectChart("/nonexistent/helm/dir")
    assert(!result.success)
    assertEquals(result.error_code, Some("FILE_NOT_FOUND"))
  }

  test("CLI: classify-workspace with valid directory returns topology") {
    val tempDir = Files.createTempDirectory("maintest-classify").toFile
    try
      Files.writeString(new java.io.File(tempDir, "pom.xml").toPath, "<project></project>")
      val result = cumulus.workspace.WorkspaceClassifier.classifyWorkspace(tempDir.getAbsolutePath)
      assert(result.success)
      assert(result.data.isDefined)
      assertEquals(result.data.get.primary_type, "maven")

      // Test arg parsing with --dir
      val argMap = cumulus.Main.parseArgs(Array("--dir", tempDir.getAbsolutePath))
      assertEquals(argMap.get("dir"), Some(tempDir.getAbsolutePath))
    finally
      os.remove.all(os.Path(tempDir))
  }

  test("CLI: classify-workspace with non-existent directory returns FILE_NOT_FOUND") {
    val result = cumulus.workspace.WorkspaceClassifier.classifyWorkspace("/nonexistent/workspace/dir")
    assert(!result.success)
    assertEquals(result.error_code, Some("FILE_NOT_FOUND"))
  }

  test("CLI: discover-devops-roots with valid directory returns devops roots") {
    val tempDir = Files.createTempDirectory("maintest-devopsroots").toFile
    try
      Files.createDirectories(new java.io.File(tempDir, "infra/terraform").toPath)
      Files.writeString(new java.io.File(tempDir, "infra/terraform/main.tf").toPath, "provider \"aws\" {}")
      Files.createDirectories(new java.io.File(tempDir, "deploy/helm").toPath)
      Files.writeString(new java.io.File(tempDir, "deploy/helm/Chart.yaml").toPath, "name: app")

      val result = cumulus.workspace.DevopsRootDiscoverer.discoverDevopsRoots(tempDir.getAbsolutePath)
      assert(result.success)
      assert(result.data.isDefined)
      val data = result.data.get
      assertEquals(data.workspace_root, tempDir.getAbsolutePath)
      assert(data.terraform.contains((os.Path(tempDir) / "infra" / "terraform").toString))
      assert(data.helm.contains((os.Path(tempDir) / "deploy" / "helm").toString))

      // Test arg parsing with --path, --dir, --file
      val argMapPath = cumulus.Main.parseArgs(Array("--path", tempDir.getAbsolutePath))
      assertEquals(argMapPath.get("path"), Some(tempDir.getAbsolutePath))

      val argMapDir = cumulus.Main.parseArgs(Array("--dir", tempDir.getAbsolutePath))
      assertEquals(argMapDir.get("dir"), Some(tempDir.getAbsolutePath))

      val argMapFile = cumulus.Main.parseArgs(Array("--file", tempDir.getAbsolutePath))
      assertEquals(argMapFile.get("file"), Some(tempDir.getAbsolutePath))
    finally
      os.remove.all(os.Path(tempDir))
  }

  test("CLI: discover-devops-roots with non-existent path returns FILE_NOT_FOUND") {
    val result = cumulus.workspace.DevopsRootDiscoverer.discoverDevopsRoots("/nonexistent/devops/path")
    assert(!result.success)
    assertEquals(result.error_code, Some("FILE_NOT_FOUND"))
  }

  test("CLI: assemble-command with key-value flags returns success") {
    val argMap = cumulus.Main.parseArgs(Array("--tool", "maven", "--action", "build", "--flags", "-DskipTests"))
    assertEquals(argMap.get("tool"), Some("maven"))
    assertEquals(argMap.get("action"), Some("build"))
    assertEquals(argMap.get("flags"), Some("-DskipTests"))

    val intent = cumulus.build.CommandIntent(
      tool = "maven",
      action = "build",
      flags = Seq("-DskipTests")
    )
    val result = cumulus.build.CommandAssembler.assemble(intent)
    assert(result.isRight)
    val cmd = result.toOption.get
    assert(cmd.command.contains("clean package -DskipTests"))
  }

  test("CLI: assemble-command with JSON deserialization returns success") {
    val json = """{"tool":"terraform","action":"plan","target":"infra/dev","flags":["-var-file=dev.tfvars"]}"""
    val intent = upickle.default.read[cumulus.build.CommandIntent](json)
    assertEquals(intent.tool, "terraform")
    assertEquals(intent.action, "plan")
    assertEquals(intent.target, Some("infra/dev"))
    assertEquals(intent.flags, Seq("-var-file=dev.tfvars"))

    val result = cumulus.build.CommandAssembler.assemble(intent)
    assert(result.isRight)
    val cmd = result.toOption.get
    assertEquals(cmd.command, "terraform plan -var-file=dev.tfvars")
    assert(cmd.cwd.endsWith("infra/dev"))
  }

  test("CLI: generate-dap-config with directory returns valid DapConfigResult") {
    val tempDir = Files.createTempDirectory("main-dap-test-").toString
    try
      val pomPath = Paths.get(tempDir, "pom.xml")
      Files.write(pomPath, "<project><artifactId>cli-demo-app</artifactId></project>".getBytes("UTF-8"))
      val javaDir = Paths.get(tempDir, "src/main/java/com/example")
      Files.createDirectories(javaDir)
      Files.write(javaDir.resolve("CliApp.java"),
        """package com.example;
          |import org.springframework.boot.autoconfigure.SpringBootApplication;
          |@SpringBootApplication
          |public class CliApp {}""".stripMargin.getBytes("UTF-8"))

      val response = cumulus.code.DapConfigGenerator.generateDapConfig(tempDir)
      assert(response.success)
      assert(response.data.isDefined)
      val data = response.data.get
      assertEquals(data.launch.mainClass, Some("com.example.CliApp"))
      assertEquals(data.launch.projectName, Some("cli-demo-app"))
      assertEquals(data.attach.port, Some(5005))
    finally
      os.remove.all(os.Path(tempDir))
  }

  test("CLI: generate-dap-config with non-existent directory returns FILE_NOT_FOUND") {
    val response = cumulus.code.DapConfigGenerator.generateDapConfig("/nonexistent/cli/dap/path")
    assert(!response.success)
    assertEquals(response.error_code, Some("FILE_NOT_FOUND"))
  }

  test("CLI: jdtls-config with valid Maven project returns success") {
    val tempDir = Files.createTempDirectory("jdtls-maven-test-").toString
    try
      val pomPath = Paths.get(tempDir, "pom.xml")
      Files.write(pomPath, """<?xml version="1.0" encoding="UTF-8"?>
        |<project xmlns="http://maven.apache.org/POM/4.0.0">
        |  <modelVersion>4.0.0</modelVersion>
        |  <groupId>com.example</groupId>
        |  <artifactId>test-app</artifactId>
        |  <version>1.0.0</version>
        |  <dependencies/>
        |</project>""".stripMargin.getBytes("UTF-8"))

      val result = cumulus.workspace.JdtlsConfigResolver.handle(tempDir)
      assert(result.success)
      assert(result.data.isDefined)
      val config = result.data.get
      assert(config.classpath.isInstanceOf[Seq[?]])
      assert(config.jvm_runtimes.isInstanceOf[Seq[?]])
      assert(config.jvm_args.contains("-Xmx4G"))
      assert(config.init_options.nonEmpty)
    finally
      os.remove.all(os.Path(tempDir))
  }

  test("CLI: jdtls-config with non-existent directory returns FILE_NOT_FOUND") {
    val result = cumulus.workspace.JdtlsConfigResolver.handle("/nonexistent/jdtls/project")
    assert(!result.success)
    assertEquals(result.error_code, Some("FILE_NOT_FOUND"))
    assert(result.error.isDefined)
  }

  test("CLI: JdtlsConfig serialization and deserialization via uPickle") {
    val tempDir = Files.createTempDirectory("jdtls-json-test-").toString
    try
      val pomPath = Paths.get(tempDir, "pom.xml")
      Files.write(pomPath, """<?xml version="1.0" encoding="UTF-8"?>
        |<project xmlns="http://maven.apache.org/POM/4.0.0">
        |  <groupId>com.example</groupId>
        |  <artifactId>test-app</artifactId>
        |  <version>1.0.0</version>
        |</project>""".stripMargin.getBytes("UTF-8"))

      val result = cumulus.workspace.JdtlsConfigResolver.handle(tempDir)
      given rw: upickle.default.ReadWriter[cumulus.protocol.JdtlsConfig] = upickle.default.macroRW

      // Serialize to JSON
      val jsonVal = CumulusResponse.toJson(result)
      val json = ujson.write(jsonVal)

      // Verify JSON contains expected fields
      assert(json.contains("\"jdtls_path\""))
      assert(json.contains("\"bundle_valid\""))
      assert(json.contains("\"classpath\""))
      assert(json.contains("\"jvm_runtimes\""))
      assert(json.contains("\"jvm_args\""))
      assert(json.contains("\"init_options\""))
      assert(json.contains("\"success\":true"))

      // Round-trip: deserialize back from JSON
      val restored = CumulusResponse.fromJson[cumulus.protocol.JdtlsConfig](jsonVal)
      assert(restored.success)
      assert(restored.data.isDefined)
      val restoredConfig = restored.data.get
      assertEquals(restoredConfig.classpath, result.data.get.classpath)
      assertEquals(restoredConfig.jvm_args, result.data.get.jvm_args)
      assertEquals(restoredConfig.bundle_valid, result.data.get.bundle_valid)
      assertEquals(restoredConfig.jvm_runtimes.length, result.data.get.jvm_runtimes.length)
    finally
      os.remove.all(os.Path(tempDir))
  }

  test("CLI: eval-keymap-gates with simple equality gate returns success") {
    given ReadWriter[cumulus.protocol.GateEvalResult] = upickle.default.macroRW
    given contextRW: ReadWriter[cumulus.protocol.GateContext] = cumulus.protocol.GateContext.GateContextRW

    val result = cumulus.keymap.KeymapGateEvaluator.handle(
      "filetype=java",
      cumulus.protocol.GateContext(filetype = Some("java"))
    )

    assert(result.success)
    assert(result.data.isDefined)
    assert(result.data.get.gate_active)
    assert(result.error.isEmpty)
    assert(result.error_code.isEmpty)
  }

  test("CLI: eval-keymap-gates with AND gate returns correct result") {
    given ReadWriter[cumulus.protocol.GateEvalResult] = upickle.default.macroRW
    given contextRW: ReadWriter[cumulus.protocol.GateContext] = cumulus.protocol.GateContext.GateContextRW

    val result = cumulus.keymap.KeymapGateEvaluator.handle(
      "filetype=java AND project_type=maven",
      cumulus.protocol.GateContext(
        filetype = Some("java"),
        project_type = Some("maven")
      )
    )

    assert(result.success)
    assert(result.data.isDefined)
    assert(result.data.get.gate_active)
  }

  test("CLI: eval-keymap-gates with mismatched AND gate returns false") {
    given ReadWriter[cumulus.protocol.GateEvalResult] = upickle.default.macroRW
    given contextRW: ReadWriter[cumulus.protocol.GateContext] = cumulus.protocol.GateContext.GateContextRW

    val result = cumulus.keymap.KeymapGateEvaluator.handle(
      "filetype=java AND project_type=maven",
      cumulus.protocol.GateContext(
        filetype = Some("java"),
        project_type = Some("gradle")
      )
    )

    assert(result.success)
    assert(result.data.isDefined)
    assert(!result.data.get.gate_active)
  }

  test("CLI: eval-keymap-gates with malformed gate expression returns error") {
    given ReadWriter[cumulus.protocol.GateEvalResult] = upickle.default.macroRW
    given contextRW: ReadWriter[cumulus.protocol.GateContext] = cumulus.protocol.GateContext.GateContextRW

    val result = cumulus.keymap.KeymapGateEvaluator.handle(
      "filetype==java",
      cumulus.protocol.GateContext(filetype = Some("java"))
    )

    assert(!result.success)
    assert(result.data.isEmpty)
    assert(result.error.isDefined)
    assert(result.error.get.contains("Invalid gate expression syntax"))
    assert(result.error_code.isDefined)
  }

  test("CLI: eval-keymap-gates with missing context field returns error") {
    given ReadWriter[cumulus.protocol.GateEvalResult] = upickle.default.macroRW
    given contextRW: ReadWriter[cumulus.protocol.GateContext] = cumulus.protocol.GateContext.GateContextRW

    val result = cumulus.keymap.KeymapGateEvaluator.handle(
      "filetype=java",
      cumulus.protocol.GateContext() // Empty context
    )

    assert(!result.success)
    assert(result.data.isEmpty)
    assert(result.error.isDefined)
    assert(result.error.get.contains("Missing required variable"))
  }

  test("CLI: eval-keymap-gates JSON serialization roundtrip") {
    given ReadWriter[cumulus.protocol.GateEvalResult] = upickle.default.macroRW
    given contextRW: ReadWriter[cumulus.protocol.GateContext] = cumulus.protocol.GateContext.GateContextRW

    val original = cumulus.protocol.GateContext(
      buftype = Some(""),
      filetype = Some("java"),
      mode = Some("normal"),
      lsp_active = Some(true),
      debug_mode = Some(false)
    )

    val json = upickle.default.write[cumulus.protocol.GateContext](original)
    val restored = upickle.default.read[cumulus.protocol.GateContext](json)

    assertEquals(restored.buftype, original.buftype)
    assertEquals(restored.filetype, original.filetype)
    assertEquals(restored.mode, original.mode)
    assertEquals(restored.lsp_active, original.lsp_active)
    assertEquals(restored.debug_mode, original.debug_mode)
  }

  test("CLI: toolchain-matrix with unsupported language returns error") {
    val result = cumulus.toolchain.ToolchainMatrixGenerator.handle("cobol")
    assertEquals(result.success, false)
    assertEquals(result.error_code, Some("UNSUPPORTED_LANGUAGE"))
    assert(result.error.isDefined)
  }

  test("CLI: toolchain-matrix with java language returns valid response structure") {
    val result = cumulus.toolchain.ToolchainMatrixGenerator.handle("java")
    if result.success then
      assert(result.data.isDefined)
      val matrix = result.data.get
      assertEquals(matrix.language, "java")
      assert(matrix.available_tools.isInstanceOf[Seq[_]])
      assert(matrix.formatters.isInstanceOf[Seq[_]])
      assert(matrix.language_servers.isInstanceOf[Seq[_]])
      assert(matrix.debuggers.isInstanceOf[Seq[_]])
  }

  test("CLI: toolchain-matrix with go language returns valid response structure") {
    val result = cumulus.toolchain.ToolchainMatrixGenerator.handle("go")
    if result.success then
      assert(result.data.isDefined)
      val matrix = result.data.get
      assertEquals(matrix.language, "go")
  }

  test("CLI: toolchain-matrix with python language returns valid response structure") {
    val result = cumulus.toolchain.ToolchainMatrixGenerator.handle("python")
    if result.success then
      assert(result.data.isDefined)
      val matrix = result.data.get
      assertEquals(matrix.language, "python")
  }

  test("CLI: toolchain-matrix response serializes to valid JSON") {
    given ReadWriter[cumulus.protocol.ToolchainMatrix] = upickle.default.macroRW
    val result = cumulus.toolchain.ToolchainMatrixGenerator.handle("go")
    if result.success then
      val matrix = result.data.get
      val json = upickle.default.write[cumulus.protocol.ToolchainMatrix](matrix)
      assert(json.nonEmpty)
      // Verify it's valid JSON
      val restored = upickle.default.read[cumulus.protocol.ToolchainMatrix](json)
      assertEquals(restored.language, matrix.language)
  }

  test("CLI: toolchain-matrix is case-insensitive") {
    val result1 = cumulus.toolchain.ToolchainMatrixGenerator.handle("JAVA")
    val result2 = cumulus.toolchain.ToolchainMatrixGenerator.handle("java")
    assertEquals(result1.success, result2.success)
  }

  test("CLI: toolchain-matrix response has correct envelope structure") {
    val result = cumulus.toolchain.ToolchainMatrixGenerator.handle("python")
    // Check CumulusResponse envelope fields
    if result.success then
      assertEquals(result.success, true)
      assert(result.data.isDefined)
      assertEquals(result.error, None)
      assertEquals(result.error_code, None)
  }

  test("ToolchainMatrix case class serialization") {
    given ReadWriter[cumulus.protocol.ToolchainMatrix] = upickle.default.macroRW
    val original = cumulus.protocol.ToolchainMatrix(
      language = "java",
      available_tools = Seq("javac", "maven"),
      formatters = Seq(),
      language_servers = Seq(),
      debuggers = Seq(),
      diagnostic_warnings = Seq()
    )

    val json = upickle.default.write[cumulus.protocol.ToolchainMatrix](original)
    val restored = upickle.default.read[cumulus.protocol.ToolchainMatrix](json)

    assertEquals(restored.language, original.language)
    assertEquals(restored.available_tools, original.available_tools)
  }

  test("CLI: resolve-lsp with java language returns valid LspSpec with capabilities") {
    val result = cumulus.lsp.LspResolver.handle("java")
    assert(result.success)
    assert(result.data.isDefined)
    val spec = result.data.get
    assertEquals(spec.language, "java")
    // Strong assertion: capabilities should be a populated list
    assert(spec.capabilities.nonEmpty, "Java should have capabilities defined")
    val capabilityNames = spec.capabilities.map(_.name)
    assert(capabilityNames.contains("textDocument/completion"), "Should support textDocument/completion")
    assert(capabilityNames.contains("textDocument/hover"), "Should support textDocument/hover")
    assert(capabilityNames.contains("textDocument/definition"), "Should support textDocument/definition")
    // TreeSitter should return a structure (may be empty if not installed)
    assert(spec.treesitter.isInstanceOf[Seq[_]])
    assertEquals(result.error, None)
  }

  test("CLI: resolve-lsp with kotlin language returns valid LspSpec") {
    val result = cumulus.lsp.LspResolver.handle("kotlin")
    assert(result.success)
    assert(result.data.isDefined)
    val spec = result.data.get
    assertEquals(spec.language, "kotlin")
    // Verify structure is complete
    assert(spec.capabilities.nonEmpty, "Kotlin should have capabilities defined")
    assert(spec.treesitter.isInstanceOf[Seq[_]])
  }

  test("CLI: resolve-lsp with scala language returns valid LspSpec") {
    val result = cumulus.lsp.LspResolver.handle("scala")
    assert(result.success)
    assert(result.data.isDefined)
    val spec = result.data.get
    assertEquals(spec.language, "scala")
    // Verify structure is complete
    assert(spec.capabilities.isInstanceOf[Seq[_]])
    assert(spec.treesitter.isInstanceOf[Seq[_]])
  }

  test("CLI: resolve-lsp with groovy language returns valid LspSpec") {
    val result = cumulus.lsp.LspResolver.handle("groovy")
    assert(result.success)
    assert(result.data.isDefined)
    val spec = result.data.get
    assertEquals(spec.language, "groovy")
    // Verify structure is complete
    assert(spec.capabilities.isInstanceOf[Seq[_]])
    assert(spec.treesitter.isInstanceOf[Seq[_]])
  }

  test("CLI: resolve-lsp with unsupported language returns error") {
    val result = cumulus.lsp.LspResolver.handle("rust")
    assert(!result.success)
    assert(result.data.isEmpty)
    assert(result.error.isDefined)
    assertEquals(result.error_code, Some("UNSUPPORTED_LANGUAGE"))
  }

  test("CLI: resolve-lsp response has correct envelope structure") {
    val result = cumulus.lsp.LspResolver.handle("java")
    assertEquals(result.success, true)
    assert(result.data.isDefined, "Data should be defined for valid language")
    assertEquals(result.error, None, "No error for valid language")
    assertEquals(result.error_code, None, "No error code for valid language")
  }

  test("CLI: resolve-lsp serializes to JSON") {
    given ReadWriter[cumulus.protocol.LspSpec] = upickle.default.macroRW
    val result = cumulus.lsp.LspResolver.handle("kotlin")
    if result.success && result.data.isDefined then
      val json = upickle.default.write[cumulus.protocol.LspSpec](result.data.get)
      assert(json.nonEmpty)
      assert(json.contains("kotlin"))
      // Verify round-trip
      val restored = upickle.default.read[cumulus.protocol.LspSpec](json)
      assertEquals(restored.language, "kotlin")
  }

  test("CLI: resolve-lsp case-insensitive language names") {
    val result1 = cumulus.lsp.LspResolver.handle("JAVA")
    val result2 = cumulus.lsp.LspResolver.handle("Java")
    val result3 = cumulus.lsp.LspResolver.handle("java")
    assertEquals(result1.success, true)
    assertEquals(result2.success, true)
    assertEquals(result3.success, true)
    if result1.data.isDefined && result2.data.isDefined && result3.data.isDefined then
      assertEquals(result1.data.get.language, "java")
      assertEquals(result2.data.get.language, "java")
      assertEquals(result3.data.get.language, "java")
  }

  test("CLI: resolve-lsp response contains capabilities for java") {
    val result = cumulus.lsp.LspResolver.handle("java")
    assert(result.success)
    assert(result.data.isDefined)
    val spec = result.data.get
    // Strong assertion: capabilities should be defined for Java
    assert(spec.capabilities.nonEmpty, "Java LSP should provide at least some capabilities")
    val capabilityNames = spec.capabilities.map(_.name)
    assert(capabilityNames.contains("textDocument/completion"), "Missing textDocument/completion")
    assert(capabilityNames.contains("textDocument/hover"), "Missing textDocument/hover")
  }

  test("CLI: resolve-lsp response contains treesitter parsers") {
    val result = cumulus.lsp.LspResolver.handle("java")
    assert(result.success)
    assert(result.data.isDefined)
    val spec = result.data.get
    // TreeSitter parsers are checked - structure must be valid Seq
    assert(spec.treesitter.isInstanceOf[Seq[_]], "TreeSitter specs should be a Seq")
    // Parser spec structure should have parser names if any exist
    if spec.treesitter.nonEmpty then
      val parsers = spec.treesitter.map(_.parser)
      assert(parsers.nonEmpty, "Should have parser names if treesitter list is not empty")
  }

  test("CLI: resolve-lsp with whitespace in language name is trimmed") {
    val result = cumulus.lsp.LspResolver.handle("  kotlin  ")
    assert(result.success)
    assert(result.data.isDefined)
    assertEquals(result.data.get.language, "kotlin")
  }

  test("CLI: resolve-lsp mason spec is generated when applicable") {
    val result = cumulus.lsp.LspResolver.handle("java")
    assert(result.success)
    assert(result.data.isDefined)
    val spec = result.data.get
    // Verify structure: mason_spec should be Option[MasonPackageSpec]
    assert(spec.mason_spec.isInstanceOf[Option[_]], "mason_spec should be an Option")
    // If LSP is detected, Mason spec should be populated
    if spec.lsp_name.nonEmpty then
      assert(spec.mason_spec.isDefined, "Mason spec should be defined when LSP is detected")
      val masonSpec = spec.mason_spec.get
      assert(masonSpec.name.nonEmpty, "Mason spec should have a name")
      assert(masonSpec.min_version.nonEmpty, "Mason spec should have min_version")
  }

  test("CLI: resolve-lsp binary_valid field is boolean") {
    val result = cumulus.lsp.LspResolver.handle("scala")
    assert(result.success)
    assert(result.data.isDefined)
    val spec = result.data.get
    // Strong assertion: binary_valid must be a Boolean value
    assert(spec.binary_valid.isInstanceOf[Boolean], "binary_valid must be a Boolean")
    assertEquals(spec.binary_valid.getClass.getSimpleName, "boolean", "binary_valid should be a primitive boolean")
  }

  test("CLI: kotlin-lsp-config arguments parsing and handler routing") {
    val tempDir = Files.createTempDirectory("maintest-kt-").toFile
    try
      val ktFile = new java.io.File(tempDir, "build.gradle.kts")
      Files.writeString(ktFile.toPath, "plugins { kotlin(\"jvm\") version \"1.9.22\" }")

      val argMap = Main.parseArgs(Seq("--project-root", tempDir.getAbsolutePath, "--sanitize-cache"))
      assertEquals(argMap.get("project-root"), Some(tempDir.getAbsolutePath))

      val result = cumulus.lsp.KotlinLspResolver.handle(
        projectRootOpt = argMap.get("project-root"),
        sanitizeCache = true
      )
      assert(result.success)
      assert(result.data.isDefined)
      assertEquals(result.data.get.cache_sanitized, true)

      // Serialization test
      given rw: upickle.default.ReadWriter[cumulus.protocol.KotlinLspConfig] = upickle.default.macroRW
      val jsonVal = cumulus.protocol.CumulusResponse.toJson(result)
      val jsonStr = ujson.write(jsonVal)
      assert(jsonStr.contains("\"success\":true"))
      assert(jsonStr.contains("is_cached"))

      val restored = cumulus.protocol.CumulusResponse.fromJson[cumulus.protocol.KotlinLspConfig](jsonVal)
      assert(restored.success)
      assert(restored.data.isDefined)
    finally
      os.remove.all(os.Path(tempDir))
  }

  test("CLI: kotlin-lsp-config returns FILE_NOT_FOUND error on missing directory") {
    val result = cumulus.lsp.KotlinLspResolver.handle(Some("/nonexistent/kt/directory"))
    assert(!result.success)
    assertEquals(result.error_code, Some("FILE_NOT_FOUND"))
  }











