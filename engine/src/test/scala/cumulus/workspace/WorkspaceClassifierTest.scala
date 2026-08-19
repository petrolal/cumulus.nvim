package cumulus.workspace

import munit.FunSuite
import java.nio.file.{Files, Paths}
import java.io.File
import os.Path

class WorkspaceClassifierTest extends FunSuite:

  private def createTempDir(): String =
    Files.createTempDirectory("classifier-test").toString

  private def createTempFile(dir: String, name: String, content: String = ""): String =
    val filePath = Paths.get(dir, name)
    if (filePath.getParent != null) then
      Files.createDirectories(filePath.getParent)
    Files.write(filePath, content.getBytes)
    filePath.toString

  private def createTempSubDir(dir: String, name: String): String =
    val subDir = Paths.get(dir, name)
    Files.createDirectories(subDir)
    subDir.toString

  private def makeExecutable(filePath: String): Unit =
    val file = new File(filePath)
    file.setExecutable(true)

  test("WorkspaceClassifier: empty workspace returns unknown with empty subprojects") {
    val dir = createTempDir()
    try
      val response = WorkspaceClassifier.classifyWorkspace(dir)
      assert(response.success)
      val data = response.data.get
      assertEquals(data.primary_type, "unknown")
      assertEquals(data.project_types, Seq.empty[String])
      assertEquals(data.submodules, Seq.empty[ProjectSubmodule])
      assertEquals(data.has_spring, false)
      assertEquals(data.iac_types, Seq.empty[String])
      assertEquals(data.is_multi_module, false)
    finally
      os.remove.all(Path(dir))
  }

  test("WorkspaceClassifier: non-existent directory returns FILE_NOT_FOUND error") {
    val response = WorkspaceClassifier.classifyWorkspace("/nonexistent/directory/for/testing")
    assert(!response.success)
    assertEquals(response.error_code, Some("FILE_NOT_FOUND"))
  }

  test("WorkspaceClassifier: classify JVM Maven Project with Spring and wrapper") {
    val dir = createTempDir()
    try
      val pomContent =
        """<project>
          |  <modelVersion>4.0.0</modelVersion>
          |  <groupId>com.example</groupId>
          |  <artifactId>demo</artifactId>
          |  <dependencies>
          |    <dependency>
          |      <groupId>org.springframework.boot</groupId>
          |      <artifactId>spring-boot-starter-web</artifactId>
          |    </dependency>
          |  </dependencies>
          |</project>""".stripMargin
      createTempFile(dir, "pom.xml", pomContent)
      val mvnw = createTempFile(dir, "mvnw", "#!/bin/sh")
      makeExecutable(mvnw)
      createTempFile(dir, "src/main/resources/application.yml", "server:\n  port: 8080\n")

      val response = WorkspaceClassifier.classifyWorkspace(dir)
      assert(response.success)
      val data = response.data.get
      assertEquals(data.primary_type, "maven")
      assert(data.project_types.contains("maven"))
      assertEquals(data.has_spring, true)
      assertEquals(data.is_multi_module, false)
    finally
      os.remove.all(Path(dir))
  }

  test("WorkspaceClassifier: classify JVM Gradle Project") {
    val dir = createTempDir()
    try
      createTempFile(dir, "build.gradle", "plugins { id 'java' }")
      val gradlew = createTempFile(dir, "gradlew", "#!/bin/sh")
      makeExecutable(gradlew)

      val response = WorkspaceClassifier.classifyWorkspace(dir)
      assert(response.success)
      val data = response.data.get
      assertEquals(data.primary_type, "gradle")
      assert(data.project_types.contains("gradle"))
      assertEquals(data.has_spring, false)
    finally
      os.remove.all(Path(dir))
  }

  test("WorkspaceClassifier: classify JVM SBT Project") {
    val dir = createTempDir()
    try
      createTempFile(dir, "build.sbt", "scalaVersion := \"3.5.2\"")

      val response = WorkspaceClassifier.classifyWorkspace(dir)
      assert(response.success)
      val data = response.data.get
      assertEquals(data.primary_type, "sbt")
      assert(data.project_types.contains("sbt"))
    finally
      os.remove.all(Path(dir))
  }

  test("WorkspaceClassifier: classify Pure DevOps Workspace") {
    val dir = createTempDir()
    try
      createTempFile(dir, "main.tf", "resource \"aws_s3_bucket\" \"b\" {}")
      createTempFile(dir, "ansible.cfg", "[defaults]\ninventory = ./hosts")

      val response = WorkspaceClassifier.classifyWorkspace(dir)
      assert(response.success)
      val data = response.data.get
      assertEquals(data.primary_type, "devops")
      assert(data.project_types.contains("terraform"))
      assert(data.project_types.contains("ansible"))
      assert(data.iac_types.contains("terraform"))
      assert(data.iac_types.contains("ansible"))
      assertEquals(data.has_spring, false)
    finally
      os.remove.all(Path(dir))
  }

  test("WorkspaceClassifier: classify Polyglot Monorepo (Gradle backend + Terraform + Docker infra)") {
    val dir = createTempDir()
    try
      // Subproject 1: Backend service (Gradle)
      createTempFile(dir, "services/backend/build.gradle", "plugins { id 'org.springframework.boot' }")
      val gradlew = createTempFile(dir, "services/backend/gradlew", "#!/bin/sh")
      makeExecutable(gradlew)
      createTempFile(dir, "services/backend/application.properties", "spring.profiles.active=dev")

      // Subproject 2: Infra (Terraform)
      createTempFile(dir, "infra/terraform/main.tf", "terraform { required_version = \">= 1.0\" }")

      // Subproject 3: Deploy (Docker)
      createTempFile(dir, "deploy/Dockerfile", "FROM eclipse-temurin:21")

      val response = WorkspaceClassifier.classifyWorkspace(dir)
      assert(response.success)
      val data = response.data.get
      assertEquals(data.primary_type, "polyglot")
      assert(data.project_types.contains("gradle"))
      assert(data.project_types.contains("terraform"))
      assert(data.project_types.contains("docker"))
      assert(data.iac_types.contains("terraform"))
      assert(data.iac_types.contains("docker"))
      assertEquals(data.has_spring, true)
      assertEquals(data.is_multi_module, true)

      // Submodules checks
      val paths = data.submodules.map(_.path)
      assert(paths.contains("services/backend"))
      assert(paths.contains("infra/terraform"))
      assert(paths.contains("deploy"))

      val backendSub = data.submodules.find(_.path == "services/backend").get
      assertEquals(backendSub.name, "backend")
      assertEquals(backendSub.project_type, "gradle")
      assertEquals(backendSub.build_tool, Some("gradle"))
      assertEquals(backendSub.has_wrapper, true)
    finally
      os.remove.all(Path(dir))
  }

  test("WorkspaceClassifier: ignores noise directories (.git, node_modules, target, .terraform, build, .gradle)") {
    val dir = createTempDir()
    try
      createTempFile(dir, "build.sbt", "name := \"test\"")
      createTempFile(dir, ".git/pom.xml", "<project></project>")
      createTempFile(dir, "node_modules/package/pom.xml", "<project></project>")
      createTempFile(dir, "target/pom.xml", "<project></project>")
      createTempFile(dir, ".terraform/main.tf", "resource \"aws\" {}")
      createTempFile(dir, "build/pom.xml", "<project></project>")
      createTempFile(dir, ".gradle/pom.xml", "<project></project>")

      val response = WorkspaceClassifier.classifyWorkspace(dir)
      assert(response.success)
      val data = response.data.get
      assertEquals(data.primary_type, "sbt")
      assertEquals(data.project_types, Seq("sbt"))
      assertEquals(data.submodules, Seq.empty[ProjectSubmodule])
    finally
      os.remove.all(Path(dir))
  }

  test("WorkspaceClassifier: deeply nested submodules bounded traversal") {
    val dir = createTempDir()
    try
      createTempFile(dir, "a/b/c/d/main.tf", "provider \"aws\" {}")

      val response = WorkspaceClassifier.classifyWorkspace(dir)
      assert(response.success)
      val data = response.data.get
      assert(data.project_types.contains("terraform"))
      assert(data.submodules.exists(_.path == "a/b/c/d"))
    finally
      os.remove.all(Path(dir))
  }
