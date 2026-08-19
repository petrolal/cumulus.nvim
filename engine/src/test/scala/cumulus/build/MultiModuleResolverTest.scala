package cumulus.build

import munit.FunSuite
import java.nio.file.{Files, Paths}
import java.io.File

class MultiModuleResolverTest extends FunSuite:

  private def createTempDir(): String =
    Files.createTempDirectory("test-multimodule").toAbsolutePath.toString

  test("resolve Maven project") {
    val tempDir = createTempDir()
    try
      Files.createDirectories(Paths.get(tempDir, "core"))
      Files.createDirectories(Paths.get(tempDir, "api"))

      val pomContent = """<?xml version="1.0" encoding="UTF-8"?>
        |<project xmlns="http://maven.apache.org/POM/4.0.0">
        |  <modelVersion>4.0.0</modelVersion>
        |  <groupId>com.example</groupId>
        |  <artifactId>maven-multi</artifactId>
        |  <version>1.0.0</version>
        |  <modules>
        |    <module>core</module>
        |    <module>api</module>
        |  </modules>
        |</project>""".stripMargin

      Files.write(Paths.get(tempDir, "pom.xml"), pomContent.getBytes)

      val result = MultiModuleResolver.resolve(tempDir)

      assert(result.success)
      assert(result.error.isEmpty)
      assert(result.data.isDefined)

      val tree = result.data.get
      assert(tree.modules.length == 2)
      val moduleNames = tree.modules.map(_.name).toSet
      assert(moduleNames == Set("core", "api"))
    finally
      Files.walk(Paths.get(tempDir))
        .sorted(java.util.Comparator.reverseOrder())
        .forEach(Files.delete(_))
  }

  test("resolve Gradle project") {
    val tempDir = createTempDir()
    try
      Files.createDirectories(Paths.get(tempDir, "core"))
      Files.createDirectories(Paths.get(tempDir, "api"))

      val settingsContent = """rootProject.name = 'gradle-multi'
        |include 'core'
        |include 'api'
        |""".stripMargin

      Files.write(Paths.get(tempDir, "settings.gradle"), settingsContent.getBytes)

      val result = MultiModuleResolver.resolve(tempDir)

      assert(result.success)
      assert(result.error.isEmpty)
      assert(result.data.isDefined)

      val tree = result.data.get
      assert(tree.modules.length == 2)
      val moduleNames = tree.modules.map(_.name).toSet
      assert(moduleNames == Set("core", "api"))
    finally
      Files.walk(Paths.get(tempDir))
        .sorted(java.util.Comparator.reverseOrder())
        .forEach(Files.delete(_))
  }

  test("prefer Maven over Gradle when both exist") {
    val tempDir = createTempDir()
    try
      // Create subdirectories
      Files.createDirectories(Paths.get(tempDir, "maven-core"))
      Files.createDirectories(Paths.get(tempDir, "gradle-core"))

      val pomContent = """<?xml version="1.0" encoding="UTF-8"?>
        |<project xmlns="http://maven.apache.org/POM/4.0.0">
        |  <modelVersion>4.0.0</modelVersion>
        |  <groupId>com.example</groupId>
        |  <artifactId>mixed</artifactId>
        |  <version>1.0.0</version>
        |  <modules>
        |    <module>maven-core</module>
        |  </modules>
        |</project>""".stripMargin

      val settingsContent = """include 'gradle-core'"""

      Files.write(Paths.get(tempDir, "pom.xml"), pomContent.getBytes)
      Files.write(Paths.get(tempDir, "settings.gradle"), settingsContent.getBytes)

      val result = MultiModuleResolver.resolve(tempDir)

      assert(result.success)
      val tree = result.data.get
      // Should use Maven, not Gradle
      assert(tree.modules.length == 1)
      assert(tree.modules(0).name == "maven-core")
    finally
      Files.walk(Paths.get(tempDir))
        .sorted(java.util.Comparator.reverseOrder())
        .forEach(Files.delete(_))
  }

  test("handle single-module Maven project") {
    val tempDir = createTempDir()
    try
      val pomContent = """<?xml version="1.0" encoding="UTF-8"?>
        |<project xmlns="http://maven.apache.org/POM/4.0.0">
        |  <modelVersion>4.0.0</modelVersion>
        |  <groupId>com.example</groupId>
        |  <artifactId>single</artifactId>
        |  <version>1.0.0</version>
        |</project>""".stripMargin

      Files.write(Paths.get(tempDir, "pom.xml"), pomContent.getBytes)

      val result = MultiModuleResolver.resolve(tempDir)

      assert(result.success)
      val tree = result.data.get
      assert(tree.modules.isEmpty)
    finally
      Files.walk(Paths.get(tempDir))
        .sorted(java.util.Comparator.reverseOrder())
        .forEach(Files.delete(_))
  }

  test("handle single-module Gradle project") {
    val tempDir = createTempDir()
    try
      Files.write(Paths.get(tempDir, "build.gradle"), "plugins { }".getBytes)

      val result = MultiModuleResolver.resolve(tempDir)

      assert(result.success)
      val tree = result.data.get
      assert(tree.modules.isEmpty)
    finally
      Files.walk(Paths.get(tempDir))
        .sorted(java.util.Comparator.reverseOrder())
        .forEach(Files.delete(_))
  }

  test("handle directory with no build files") {
    val tempDir = createTempDir()
    try
      val result = MultiModuleResolver.resolve(tempDir)

      assert(!result.success)
      assert(result.error.isDefined)
      assert(result.error_code.contains("FILE_NOT_FOUND"))
    finally
      Files.walk(Paths.get(tempDir))
        .sorted(java.util.Comparator.reverseOrder())
        .forEach(Files.delete(_))
  }

  test("handle nonexistent directory") {
    val result = MultiModuleResolver.resolve("/nonexistent/path/to/project")

    assert(!result.success)
    assert(result.error.isDefined)
    assert(result.error_code.contains("FILE_NOT_FOUND"))
  }

  test("handle null root directory") {
    val result = MultiModuleResolver.resolve(null)

    assert(!result.success)
    assert(result.error.isDefined)
  }

  test("handle empty root directory path") {
    val result = MultiModuleResolver.resolve("")

    assert(!result.success)
    assert(result.error.isDefined)
  }

  test("return valid root in ModuleTree") {
    val tempDir = createTempDir()
    try
      Files.createDirectories(Paths.get(tempDir, "core"))

      val pomContent = """<?xml version="1.0" encoding="UTF-8"?>
        |<project xmlns="http://maven.apache.org/POM/4.0.0">
        |  <modelVersion>4.0.0</modelVersion>
        |  <groupId>com.example</groupId>
        |  <artifactId>test</artifactId>
        |  <version>1.0.0</version>
        |  <modules>
        |    <module>core</module>
        |  </modules>
        |</project>""".stripMargin

      Files.write(Paths.get(tempDir, "pom.xml"), pomContent.getBytes)

      val result = MultiModuleResolver.resolve(tempDir)

      assert(result.success)
      val tree = result.data.get
      assert(tree.root.nonEmpty)
      assert(tree.root.contains(tempDir.split("/").last))
    finally
      Files.walk(Paths.get(tempDir))
        .sorted(java.util.Comparator.reverseOrder())
        .forEach(Files.delete(_))
  }
