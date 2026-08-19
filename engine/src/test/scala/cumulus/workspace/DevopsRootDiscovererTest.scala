package cumulus.workspace

import munit.FunSuite
import java.nio.file.{Files, Paths}
import java.io.File
import os.Path

class DevopsRootDiscovererTest extends FunSuite:

  private def createTempDir(): String =
    Files.createTempDirectory("devops-root-test").toString

  private def createTempFile(dir: String, name: String, content: String = ""): String =
    val filePath = Paths.get(dir, name)
    if filePath.getParent != null then
      Files.createDirectories(filePath.getParent)
    Files.write(filePath, content.getBytes)
    filePath.toString

  test("DevopsRootDiscoverer: empty workspace returns empty roots with success envelope") {
    val dir = createTempDir()
    try
      val response = DevopsRootDiscoverer.discoverDevopsRoots(dir)
      assert(response.success)
      val data = response.data.get
      assertEquals(data.workspace_root, dir)
      assertEquals(data.terraform, Seq.empty[String])
      assertEquals(data.sam, Seq.empty[String])
      assertEquals(data.ansible, Seq.empty[String])
      assertEquals(data.docker, Seq.empty[String])
      assertEquals(data.helm, Seq.empty[String])
    finally
      os.remove.all(Path(dir))
  }

  test("DevopsRootDiscoverer: non-existent path returns FILE_NOT_FOUND error code") {
    val response = DevopsRootDiscoverer.discoverDevopsRoots("/nonexistent/devops/path/to/test")
    assert(!response.success)
    assertEquals(response.error_code, Some("FILE_NOT_FOUND"))
  }

  test("DevopsRootDiscoverer: discover from nested file in polyglot monorepo") {
    val dir = createTempDir()
    try
      // Git root / workspace root marker
      createTempFile(dir, ".git/HEAD", "ref: refs/heads/main")
      createTempFile(dir, "pom.xml", "<project></project>")

      // Java nested file
      val javaFile = createTempFile(dir, "services/api/src/main/java/com/example/App.java", "public class App {}")

      // DevOps tool subdirectories
      createTempFile(dir, "infra/terraform/main.tf", "provider \"aws\" {}")
      createTempFile(dir, "infra/sam/template.yaml", "AWSTemplateFormatVersion: '2010-09-09'")
      createTempFile(dir, "deploy/ansible/playbook.yml", "- hosts: all")
      createTempFile(dir, "services/api/Dockerfile", "FROM eclipse-temurin:21")
      createTempFile(dir, "deploy/helm/mychart/Chart.yaml", "name: mychart\nversion: 1.0.0")

      val response = DevopsRootDiscoverer.discoverDevopsRoots(javaFile)
      assert(response.success)
      val data = response.data.get
      assertEquals(data.workspace_root, dir)

      val expectedTf = (Path(dir) / "infra" / "terraform").toString
      val expectedSam = (Path(dir) / "infra" / "sam").toString
      val expectedAnsible = (Path(dir) / "deploy" / "ansible").toString
      val expectedDocker = (Path(dir) / "services" / "api").toString
      val expectedHelm = (Path(dir) / "deploy" / "helm" / "mychart").toString

      assertEquals(data.terraform, Seq(expectedTf))
      assertEquals(data.sam, Seq(expectedSam))
      assertEquals(data.ansible, Seq(expectedAnsible))
      assertEquals(data.docker, Seq(expectedDocker))
      assertEquals(data.helm, Seq(expectedHelm))
    finally
      os.remove.all(Path(dir))
  }

  test("DevopsRootDiscoverer: direct IaC directory returns matched tool roots") {
    val dir = createTempDir()
    try
      val tfDir = Paths.get(dir, "infra", "terraform").toString
      createTempFile(dir, "infra/terraform/main.tf", "resource \"aws_s3_bucket\" \"b\" {}")
      createTempFile(dir, "infra/terraform/variables.tf", "variable \"env\" {}")

      val response = DevopsRootDiscoverer.discoverDevopsRoots(tfDir)
      assert(response.success)
      val data = response.data.get
      assert(data.terraform.contains((Path(dir) / "infra" / "terraform").toString))
    finally
      os.remove.all(Path(dir))
  }

  test("DevopsRootDiscoverer: repository with Dockerfile at root and deploy/helm chart") {
    val dir = createTempDir()
    try
      createTempFile(dir, "Dockerfile", "FROM alpine:3.18")
      createTempFile(dir, "deploy/helm/Chart.yaml", "apiVersion: v2\nname: app")

      val response = DevopsRootDiscoverer.discoverDevopsRoots(dir)
      assert(response.success)
      val data = response.data.get
      assertEquals(data.workspace_root, dir)
      assertEquals(data.docker, Seq(dir))
      assertEquals(data.helm, Seq((Path(dir) / "deploy" / "helm").toString))
    finally
      os.remove.all(Path(dir))
  }

  test("DevopsRootDiscoverer: OpenTofu (.tofu) and Terragrunt (terragrunt.hcl) support") {
    val dir = createTempDir()
    try
      createTempFile(dir, "infra/tofu/main.tofu", "terraform {}")
      createTempFile(dir, "infra/terragrunt/terragrunt.hcl", "include \"root\" {}")

      val response = DevopsRootDiscoverer.discoverDevopsRoots(dir)
      assert(response.success)
      val data = response.data.get
      val expectedTofu = (Path(dir) / "infra" / "tofu").toString
      val expectedTerragrunt = (Path(dir) / "infra" / "terragrunt").toString
      assertEquals(data.terraform, Seq(expectedTerragrunt, expectedTofu))
    finally
      os.remove.all(Path(dir))
  }

  test("DevopsRootDiscoverer: SAM with samconfig.toml and template.yml") {
    val dir = createTempDir()
    try
      createTempFile(dir, "functions/api/samconfig.toml", "version = 0.1")
      createTempFile(dir, "functions/worker/template.yml", "Resources: {}")

      val response = DevopsRootDiscoverer.discoverDevopsRoots(dir)
      assert(response.success)
      val data = response.data.get
      val expectedApi = (Path(dir) / "functions" / "api").toString
      val expectedWorker = (Path(dir) / "functions" / "worker").toString
      assertEquals(data.sam, Seq(expectedApi, expectedWorker))
    finally
      os.remove.all(Path(dir))
  }

  test("DevopsRootDiscoverer: Ansible with roles directory and inventory.ini") {
    val dir = createTempDir()
    try
      createTempFile(dir, "ansible/inventory.ini", "[web]\nnode1")
      createTempFile(dir, "ansible/roles/common/tasks/main.yml", "- name: ping\n  ping:")

      val response = DevopsRootDiscoverer.discoverDevopsRoots(dir)
      assert(response.success)
      val data = response.data.get
      val expectedAnsible = (Path(dir) / "ansible").toString
      assert(data.ansible.contains(expectedAnsible))
    finally
      os.remove.all(Path(dir))
  }

  test("DevopsRootDiscoverer: Docker with compose.yaml and Dockerfile variants") {
    val dir = createTempDir()
    try
      createTempFile(dir, "frontend/Dockerfile.dev", "FROM node:20")
      createTempFile(dir, "stack/compose.yaml", "services: {}")

      val response = DevopsRootDiscoverer.discoverDevopsRoots(dir)
      assert(response.success)
      val data = response.data.get
      val expectedFrontend = (Path(dir) / "frontend").toString
      val expectedStack = (Path(dir) / "stack").toString
      assertEquals(data.docker, Seq(expectedFrontend, expectedStack))
    finally
      os.remove.all(Path(dir))
  }

  test("DevopsRootDiscoverer: ignores noise directories (.git, node_modules, target, .terraform, build)") {
    val dir = createTempDir()
    try
      createTempFile(dir, "main.tf", "provider \"aws\" {}")
      createTempFile(dir, ".git/Dockerfile", "FROM scratch")
      createTempFile(dir, "node_modules/package/Dockerfile", "FROM scratch")
      createTempFile(dir, "target/template.yaml", "Resources: {}")
      createTempFile(dir, ".terraform/main.tf", "provider \"aws\" {}")
      createTempFile(dir, "build/Chart.yaml", "name: foo")
      createTempFile(dir, ".gradle/ansible.cfg", "[defaults]")

      val response = DevopsRootDiscoverer.discoverDevopsRoots(dir)
      assert(response.success)
      val data = response.data.get
      assertEquals(data.workspace_root, dir)
      assertEquals(data.terraform, Seq(dir))
      assertEquals(data.sam, Seq.empty[String])
      assertEquals(data.ansible, Seq.empty[String])
      assertEquals(data.docker, Seq.empty[String])
      assertEquals(data.helm, Seq.empty[String])
    finally
      os.remove.all(Path(dir))
  }
