package cumulus.workspace

import munit.FunSuite
import java.nio.file.{Files, Paths}
import java.io.File
import os.Path

class DiscoveryTest extends FunSuite:

  private def createTempDir(): String =
    Files.createTempDirectory("discovery-test").toString

  private def createTempFile(dir: String, name: String, content: String = ""): String =
    val filePath = Paths.get(dir, name)
    Files.write(filePath, content.getBytes)
    filePath.toString

  private def createTempSubDir(dir: String, name: String): String =
    val subDir = Paths.get(dir, name)
    Files.createDirectories(subDir)
    subDir.toString

  private def makeExecutable(filePath: String): Unit =
    val file = new File(filePath)
    file.setExecutable(true)

  // ===== JdkDiscoverer Tests =====

  test("JdkDiscoverer: discover non-existent JDK version returns error") {
    val result = JdkDiscoverer.discoverJdk("99")
    assert(result.isLeft)
    assert(result.left.exists(_.contains("not found")))
  }

  // ===== BuildToolDetector Tests =====

  test("BuildToolDetector: detect Maven project with pom.xml") {
    val dir = createTempDir()
    try
      createTempFile(dir, "pom.xml", """<?xml version="1.0"?><project></project>""")
      val result = BuildToolDetector.detectBuildTool(dir)
      assert(result.isRight)
      val toolInfo = result.toOption.get
      assert(toolInfo.build_tool == "maven")
    finally
      os.remove.all(Path(dir))
  }

  test("BuildToolDetector: detect Gradle project with build.gradle") {
    val dir = createTempDir()
    try
      createTempFile(dir, "build.gradle", "apply plugin: 'java'")
      val result = BuildToolDetector.detectBuildTool(dir)
      assert(result.isRight)
      val toolInfo = result.toOption.get
      assert(toolInfo.build_tool == "gradle")
    finally
      os.remove.all(Path(dir))
  }

  test("BuildToolDetector: detect Gradle project with build.gradle.kts") {
    val dir = createTempDir()
    try
      createTempFile(dir, "build.gradle.kts", "plugins { kotlin(\"jvm\") }")
      val result = BuildToolDetector.detectBuildTool(dir)
      assert(result.isRight)
      val toolInfo = result.toOption.get
      assert(toolInfo.build_tool == "gradle")
    finally
      os.remove.all(Path(dir))
  }

  test("BuildToolDetector: detect SBT project with build.sbt") {
    val dir = createTempDir()
    try
      createTempFile(dir, "build.sbt", "name := \"my-project\"")
      val result = BuildToolDetector.detectBuildTool(dir)
      assert(result.isRight)
      val toolInfo = result.toOption.get
      assert(toolInfo.build_tool == "sbt")
    finally
      os.remove.all(Path(dir))
  }

  test("BuildToolDetector: Maven precedence over Gradle") {
    val dir = createTempDir()
    try
      createTempFile(dir, "pom.xml", """<?xml version="1.0"?><project></project>""")
      createTempFile(dir, "build.gradle", "apply plugin: 'java'")
      val result = BuildToolDetector.detectBuildTool(dir)
      assert(result.isRight)
      val toolInfo = result.toOption.get
      assert(toolInfo.build_tool == "maven")
    finally
      os.remove.all(Path(dir))
  }

  test("BuildToolDetector: detect Maven wrapper") {
    val dir = createTempDir()
    try
      val mvnwPath = createTempFile(dir, "mvnw", "#!/bin/bash")
      makeExecutable(mvnwPath)
      val result = BuildToolDetector.detectBuildTool(dir)
      assert(result.isRight)
      val toolInfo = result.toOption.get
      assert(toolInfo.build_tool == "maven")
      assert(toolInfo.wrapper.isDefined)
      assert(toolInfo.executable.contains(true))
    finally
      os.remove.all(Path(dir))
  }

  test("BuildToolDetector: detect non-executable Gradle wrapper") {
    val dir = createTempDir()
    try
      val gradlewPath = createTempFile(dir, "gradlew", "#!/bin/bash")
      // Don't make it executable
      val result = BuildToolDetector.detectBuildTool(dir)
      assert(result.isRight)
      val toolInfo = result.toOption.get
      assert(toolInfo.build_tool == "gradle")
      assert(toolInfo.wrapper.isDefined)
      assert(toolInfo.executable.contains(false))
      assert(toolInfo.recommendation.isDefined)
      assert(toolInfo.recommendation.exists(_.contains("chmod +x")))
    finally
      os.remove.all(Path(dir))
  }

  test("BuildToolDetector: no build tool found returns error") {
    val dir = createTempDir()
    try
      val result = BuildToolDetector.detectBuildTool(dir)
      assert(result.isLeft)
      assert(result.left.exists(_.contains("No build tool detected")))
    finally
      os.remove.all(Path(dir))
  }

  test("BuildToolDetector: non-existent directory returns error") {
    val result = BuildToolDetector.detectBuildTool("/nonexistent/path")
    assert(result.isLeft)
  }

  // ===== WorkspaceScanner Tests =====

  test("WorkspaceScanner: find Maven project root in nested directory") {
    val root = createTempDir()
    try
      createTempFile(root, "pom.xml", """<?xml version="1.0"?><project></project>""")
      val nestedDir = createTempSubDir(root, "src/main/java")

      val result = WorkspaceScanner.discoverWorkspace(nestedDir)
      assert(result.isRight)
      val wsInfo = result.toOption.get
      assert(wsInfo.root == root)
      assert(wsInfo.build_files.nonEmpty)
      assert(wsInfo.build_files.contains("pom.xml"))
    finally
      os.remove.all(Path(root))
  }

  test("WorkspaceScanner: find Gradle project root") {
    val root = createTempDir()
    try
      createTempFile(root, "settings.gradle", "include 'module1'")
      val result = WorkspaceScanner.discoverWorkspace(root)
      assert(result.isRight)
      val wsInfo = result.toOption.get
      assert(wsInfo.root == root)
    finally
      os.remove.all(Path(root))
  }

  test("WorkspaceScanner: detect multi-module Maven project") {
    val root = createTempDir()
    try
      val pomContent = """<?xml version="1.0"?>
        |<project>
        |  <modules>
        |    <module>module1</module>
        |    <module>module2</module>
        |  </modules>
        |</project>""".stripMargin
      createTempFile(root, "pom.xml", pomContent)
      val result = WorkspaceScanner.discoverWorkspace(root)
      assert(result.isRight)
      val wsInfo = result.toOption.get
      assert(wsInfo.is_multi_module == true)
    finally
      os.remove.all(Path(root))
  }

  test("WorkspaceScanner: detect multi-module Gradle project") {
    val root = createTempDir()
    try
      createTempFile(root, "settings.gradle", "include 'module1', 'module2'")
      val result = WorkspaceScanner.discoverWorkspace(root)
      assert(result.isRight)
      val wsInfo = result.toOption.get
      assert(wsInfo.is_multi_module == true)
    finally
      os.remove.all(Path(root))
  }

  test("WorkspaceScanner: single-module project") {
    val root = createTempDir()
    try
      val pomContent = """<?xml version="1.0"?>
        |<project>
        |</project>""".stripMargin
      createTempFile(root, "pom.xml", pomContent)
      val result = WorkspaceScanner.discoverWorkspace(root)
      assert(result.isRight)
      val wsInfo = result.toOption.get
      assert(wsInfo.is_multi_module == false)
    finally
      os.remove.all(Path(root))
  }

  test("WorkspaceScanner: detect .mvn directory as build file") {
    val root = createTempDir()
    try
      createTempSubDir(root, ".mvn")
      val result = WorkspaceScanner.discoverWorkspace(root)
      assert(result.isRight)
      val wsInfo = result.toOption.get
      assert(wsInfo.build_files.nonEmpty)
      assert(wsInfo.build_files.contains(".mvn"))
    finally
      os.remove.all(Path(root))
  }

  test("WorkspaceScanner: non-existent directory returns error") {
    val result = WorkspaceScanner.discoverWorkspace("/nonexistent/path")
    assert(result.isLeft)
  }
