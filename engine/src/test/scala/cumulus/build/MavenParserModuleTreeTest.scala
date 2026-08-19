package cumulus.build

import munit.FunSuite
import java.nio.file.{Files, Paths}
import java.io.File

class MavenParserModuleTreeTest extends FunSuite:

  private def createTempFile(content: String): String =
    val file = Files.createTempFile("pom", ".xml").toFile
    Files.write(file.toPath, content.getBytes)
    file.getAbsolutePath

  private def createTempDir(): String =
    Files.createTempDirectory("test-project").toAbsolutePath.toString

  test("resolve single-module Maven project with no modules element") {
    val pomContent = """<?xml version="1.0" encoding="UTF-8"?>
      |<project xmlns="http://maven.apache.org/POM/4.0.0">
      |  <modelVersion>4.0.0</modelVersion>
      |  <groupId>com.example</groupId>
      |  <artifactId>single-module</artifactId>
      |  <version>1.0.0</version>
      |</project>""".stripMargin

    val pomPath = createTempFile(pomContent)
    try
      val result = MavenParser.resolveModuleTree(pomPath)

      assert(result.success)
      assert(result.error.isEmpty)
      assert(result.error_code.isEmpty)
      assert(result.data.isDefined)

      val tree = result.data.get
      assert(tree.modules.isEmpty)
      assert(tree.root.nonEmpty)
    finally
      Files.delete(Paths.get(pomPath))
  }

  test("resolve multi-module Maven project") {
    val tempDir = createTempDir()
    try
      // Create subdirectories
      Files.createDirectories(Paths.get(tempDir, "core"))
      Files.createDirectories(Paths.get(tempDir, "api"))
      Files.createDirectories(Paths.get(tempDir, "app"))

      val pomContent = """<?xml version="1.0" encoding="UTF-8"?>
        |<project xmlns="http://maven.apache.org/POM/4.0.0">
        |  <modelVersion>4.0.0</modelVersion>
        |  <groupId>com.example</groupId>
        |  <artifactId>multi-module</artifactId>
        |  <version>1.0.0</version>
        |  <modules>
        |    <module>core</module>
        |    <module>api</module>
        |    <module>app</module>
        |  </modules>
        |</project>""".stripMargin

      val pomPath = s"$tempDir/pom.xml"
      Files.write(Paths.get(pomPath), pomContent.getBytes)

      val result = MavenParser.resolveModuleTree(pomPath)

      assert(result.success)
      assert(result.error.isEmpty)
      assert(result.data.isDefined)

      val tree = result.data.get
      assert(tree.modules.length == 3)
      val moduleNames = tree.modules.map(_.name).toSet
      assert(moduleNames == Set("core", "api", "app"))
    finally
      // Cleanup
      Files.walk(Paths.get(tempDir))
        .sorted(java.util.Comparator.reverseOrder())
        .forEach(Files.delete(_))
  }

  test("resolve Maven project with missing module directories") {
    val tempDir = createTempDir()
    try
      // Only create core directory, not api or app
      Files.createDirectories(Paths.get(tempDir, "core"))

      val pomContent = """<?xml version="1.0" encoding="UTF-8"?>
        |<project xmlns="http://maven.apache.org/POM/4.0.0">
        |  <modelVersion>4.0.0</modelVersion>
        |  <groupId>com.example</groupId>
        |  <artifactId>incomplete-modules</artifactId>
        |  <version>1.0.0</version>
        |  <modules>
        |    <module>core</module>
        |    <module>api</module>
        |    <module>app</module>
        |  </modules>
        |</project>""".stripMargin

      val pomPath = s"$tempDir/pom.xml"
      Files.write(Paths.get(pomPath), pomContent.getBytes)

      val result = MavenParser.resolveModuleTree(pomPath)

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

  test("handle missing POM file") {
    val result = MavenParser.resolveModuleTree("/nonexistent/path/pom.xml")

    assert(!result.success)
    assert(result.error.isDefined)
    assert(result.error_code.contains("FILE_NOT_FOUND"))
  }

  test("handle null POM path") {
    val result = MavenParser.resolveModuleTree(null)

    assert(!result.success)
    assert(result.error.isDefined)
  }

  test("handle empty POM path") {
    val result = MavenParser.resolveModuleTree("")

    assert(!result.success)
    assert(result.error.isDefined)
  }

  test("reject directory traversal attempts") {
    val tempDir = createTempDir()
    try
      Files.createDirectories(Paths.get(tempDir, "parent"))

      val pomContent = """<?xml version="1.0" encoding="UTF-8"?>
        |<project xmlns="http://maven.apache.org/POM/4.0.0">
        |  <modelVersion>4.0.0</modelVersion>
        |  <groupId>com.example</groupId>
        |  <artifactId>traversal-test</artifactId>
        |  <version>1.0.0</version>
        |  <modules>
        |    <module>../../../etc/passwd</module>
        |  </modules>
        |</project>""".stripMargin

      val pomPath = s"$tempDir/pom.xml"
      Files.write(Paths.get(pomPath), pomContent.getBytes)

      val result = MavenParser.resolveModuleTree(pomPath)

      assert(result.success)
      val tree = result.data.get
      // Directory traversal attempts should be filtered out
      assert(tree.modules.isEmpty)
    finally
      Files.walk(Paths.get(tempDir))
        .sorted(java.util.Comparator.reverseOrder())
        .forEach(Files.delete(_))
  }
