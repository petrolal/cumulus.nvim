package cumulus.devops

import munit.FunSuite
import os.Path

class K8sValidatorTest extends FunSuite:

  test("valid Kubernetes manifest returns no issues") {
    val yaml =
      """apiVersion: v1
        |kind: Pod
        |metadata:
        |  name: test-pod
        |spec:
        |  containers:
        |    - name: nginx
        |      image: nginx:latest
        |""".stripMargin

    val issues = K8sValidator.validateManifestContent(yaml)
    assertEquals(issues, Seq.empty)
  }

  test("missing top-level apiVersion field flags ERROR at line 1") {
    val yaml =
      """kind: Deployment
        |metadata:
        |  name: test-deploy
        |""".stripMargin

    val issues = K8sValidator.validateManifestContent(yaml)
    assertEquals(issues.length, 1)
    val issue = issues.head
    assertEquals(issue.line, 1)
    assertEquals(issue.severity, "ERROR")
    assertEquals(issue.message, "Missing top-level 'apiVersion' field in Kubernetes manifest")
  }

  test("missing top-level kind field flags ERROR at line 1") {
    val yaml =
      """apiVersion: v1
        |metadata:
        |  name: test-resource
        |""".stripMargin

    val issues = K8sValidator.validateManifestContent(yaml)
    assertEquals(issues.length, 1)
    val issue = issues.head
    assertEquals(issue.line, 1)
    assertEquals(issue.severity, "ERROR")
    assertEquals(issue.message, "Missing top-level 'kind' field in Kubernetes manifest")
  }

  test("non-k8s YAML lacking both apiVersion and kind returns no issues") {
    val yaml =
      """server:
        |  port: 8080
        |database:
        |  url: jdbc:postgresql://localhost:5432/db
        |""".stripMargin

    val issues = K8sValidator.validateManifestContent(yaml)
    assertEquals(issues, Seq.empty)
  }

  test("empty or comment-only YAML returns no issues") {
    val yaml =
      """# This is a comment
        |# Another comment
        |""".stripMargin

    val issues = K8sValidator.validateManifestContent(yaml)
    assertEquals(issues, Seq.empty)
  }

  test("multi-document manifest flags only invalid documents") {
    val yaml =
      """apiVersion: v1
        |kind: Service
        |metadata:
        |  name: my-service
        |---
        |kind: Deployment
        |metadata:
        |  name: my-deploy
        |""".stripMargin

    val issues = K8sValidator.validateManifestContent(yaml)
    assertEquals(issues.length, 1)
    val issue = issues.head
    assertEquals(issue.line, 6)
    assertEquals(issue.message, "Missing top-level 'apiVersion' field in Kubernetes manifest")
  }

  test("validateK8sManifestFile on valid and non-existent files") {
    val tempFile = os.temp(prefix = "k8s-valid-", suffix = ".yaml")
    try
      os.write.over(tempFile, "apiVersion: v1\nkind: Pod\n")
      val resValid = K8sValidator.validateK8sManifestFile(tempFile.toString)
      assert(resValid.success)
      assertEquals(resValid.data, Some(Seq.empty))

      val resNotFound = K8sValidator.validateK8sManifestFile("/nonexistent/file.yaml")
      assert(!resNotFound.success)
      assertEquals(resNotFound.error_code, Some("FILE_NOT_FOUND"))
    finally
      os.remove(tempFile)
  }
