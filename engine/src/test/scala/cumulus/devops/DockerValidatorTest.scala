package cumulus.devops

import munit.FunSuite

class DockerValidatorTest extends FunSuite:

  test("DockerValidator should detect unpinned base image (no tag)") {
    val dockerfile =
      """
        |FROM ubuntu
        |RUN echo "test"
      """.stripMargin

    val issues = DockerValidator.validateContent(dockerfile)

    val untagged = issues.filter(_.issue_type == "UNPINNED_BASE_IMAGE")
    assert(untagged.nonEmpty)
    assertEquals(untagged.head.severity, "ERROR")
    assert(untagged.head.line > 0)
  }

  test("DockerValidator should detect floating :latest tag") {
    val dockerfile =
      """
        |FROM ubuntu:latest
        |RUN echo "test"
      """.stripMargin

    val issues = DockerValidator.validateContent(dockerfile)

    val floating = issues.filter(_.issue_type == "UNPINNED_BASE_IMAGE")
    assert(floating.nonEmpty)
    assertEquals(floating.head.severity, "ERROR")
    assert(floating.head.description.contains("floating"))
  }

  test("DockerValidator should not flag pinned tags") {
    val dockerfile =
      """
        |FROM ubuntu:22.04
        |FROM node:20-alpine
      """.stripMargin

    val issues = DockerValidator.validateContent(dockerfile)

    val untagged = issues.filter(_.issue_type == "UNPINNED_BASE_IMAGE")
    assertEquals(untagged.length, 0)
  }

  test("DockerValidator should not flag digest-pinned images") {
    val dockerfile =
      """
        |FROM ubuntu@sha256:abcdef1234567890
      """.stripMargin

    val issues = DockerValidator.validateContent(dockerfile)

    val untagged = issues.filter(_.issue_type == "UNPINNED_BASE_IMAGE")
    assertEquals(untagged.length, 0)
  }

  test("DockerValidator should detect explicit root user") {
    val dockerfile =
      """
        |FROM ubuntu:22.04
        |USER root
      """.stripMargin

    val issues = DockerValidator.validateContent(dockerfile)

    val rootUser = issues.filter(_.issue_type == "RUNS_AS_ROOT")
    assert(rootUser.nonEmpty)
    assertEquals(rootUser.head.severity, "ERROR")
  }

  test("DockerValidator should detect implicit root (no USER directive)") {
    val dockerfile = "FROM ubuntu:22.04\nRUN apt-get update"

    val issues = DockerValidator.validateContent(dockerfile)

    val rootUser = issues.filter(_.issue_type == "RUNS_AS_ROOT")
    // Should detect root execution either explicitly or implicitly
    assert(rootUser.nonEmpty)
  }

  test("DockerValidator should not flag non-root user") {
    val dockerfile =
      """
        |FROM ubuntu:22.04
        |RUN useradd -m appuser
        |USER appuser
      """.stripMargin

    val issues = DockerValidator.validateContent(dockerfile)

    val rootUser = issues.filter(_.issue_type == "RUNS_AS_ROOT")
    assertEquals(rootUser.length, 0)
  }

  test("DockerValidator should warn about missing HEALTHCHECK with CMD") {
    val dockerfile =
      """
        |FROM ubuntu:22.04
        |CMD ["sleep", "infinity"]
      """.stripMargin

    val issues = DockerValidator.validateContent(dockerfile)

    val healthWarn = issues.filter(_.issue_type == "MISSING_HEALTHCHECK")
    assert(healthWarn.nonEmpty)
    assertEquals(healthWarn.head.severity, "WARN")
  }

  test("DockerValidator should warn about missing HEALTHCHECK with ENTRYPOINT") {
    val dockerfile =
      """
        |FROM ubuntu:22.04
        |ENTRYPOINT ["/app/start.sh"]
      """.stripMargin

    val issues = DockerValidator.validateContent(dockerfile)

    val healthWarn = issues.filter(_.issue_type == "MISSING_HEALTHCHECK")
    assert(healthWarn.nonEmpty)
  }

  test("DockerValidator should not warn when HEALTHCHECK is present") {
    val dockerfile =
      """
        |FROM ubuntu:22.04
        |CMD ["sleep", "infinity"]
        |HEALTHCHECK --interval=30s CMD curl -f http://localhost/health || exit 1
      """.stripMargin

    val issues = DockerValidator.validateContent(dockerfile)

    val healthWarn = issues.filter(_.issue_type == "MISSING_HEALTHCHECK")
    assertEquals(healthWarn.length, 0)
  }

  test("DockerValidator should warn about missing WORKDIR") {
    val dockerfile =
      """
        |FROM ubuntu:22.04
        |RUN echo "test"
      """.stripMargin

    val issues = DockerValidator.validateContent(dockerfile)

    val noWorkdir = issues.filter(_.issue_type == "MISSING_WORKDIR")
    assert(noWorkdir.nonEmpty)
    assertEquals(noWorkdir.head.severity, "WARN")
  }

  test("DockerValidator should not warn when WORKDIR is present") {
    val dockerfile =
      """
        |FROM ubuntu:22.04
        |WORKDIR /app
      """.stripMargin

    val issues = DockerValidator.validateContent(dockerfile)

    val noWorkdir = issues.filter(_.issue_type == "MISSING_WORKDIR")
    assertEquals(noWorkdir.length, 0)
  }

  test("DockerValidator should return empty list for valid Dockerfile") {
    val dockerfile =
      """
        |FROM ubuntu:22.04
        |WORKDIR /app
        |RUN useradd -m appuser
        |USER appuser
        |HEALTHCHECK --interval=30s CMD curl -f http://localhost:8080/health || exit 1
        |EXPOSE 8080
      """.stripMargin

    val issues = DockerValidator.validateContent(dockerfile)

    assertEquals(issues.length, 0)
  }

  test("DockerValidator should warn about inefficient RUN layers") {
    val dockerfile =
      """
        |FROM ubuntu:22.04
        |RUN apt-get update
        |RUN apt-get install -y build-essential
        |RUN apt-get install -y git
      """.stripMargin

    val issues = DockerValidator.validateContent(dockerfile)

    val inefficient = issues.filter(_.issue_type == "INEFFICIENT_RUN_LAYERS")
    assert(inefficient.nonEmpty)
    assertEquals(inefficient.head.severity, "WARN")
  }

  test("DockerValidator should handle empty Dockerfile") {
    val dockerfile = ""

    val issues = DockerValidator.validateContent(dockerfile)

    assertEquals(issues.length, 0)
  }

  test("DockerValidator should provide remediation suggestions") {
    val dockerfile =
      """
        |FROM ubuntu
        |USER root
      """.stripMargin

    val issues = DockerValidator.validateContent(dockerfile)

    assert(issues.nonEmpty)
    for issue <- issues do
      assert(issue.remediation.nonEmpty)
      assert(issue.remediation.length > 0)
  }

  test("DockerValidator should set correct line numbers") {
    val dockerfile = "FROM ubuntu:latest\nRUN echo \"test\"\nUSER root"

    val issues = DockerValidator.validateContent(dockerfile)

    assert(issues.nonEmpty)
    assert(issues.forall(_.line > 0))
    // First issue should be on line 1 (FROM)
    val untagged = issues.filter(_.issue_type == "UNPINNED_BASE_IMAGE")
    if untagged.nonEmpty then
      assertEquals(untagged.head.line, 1)
  }

  test("DockerValidator should handle floating :master tag") {
    val dockerfile =
      """
        |FROM ubuntu:master
      """.stripMargin

    val issues = DockerValidator.validateContent(dockerfile)

    val floating = issues.filter(_.issue_type == "UNPINNED_BASE_IMAGE")
    assert(floating.nonEmpty)
    assert(floating.head.description.contains("floating"))
  }

  test("DockerValidator should detect multiple issues in same Dockerfile") {
    val dockerfile =
      """
        |FROM ubuntu
        |RUN apt-get update
        |USER root
      """.stripMargin

    val issues = DockerValidator.validateContent(dockerfile)

    // Should have unpinned, root user, and missing workdir issues
    assert(issues.length >= 2)
    val issueTypes = issues.map(_.issue_type).toSet
    assert(issueTypes.contains("UNPINNED_BASE_IMAGE"))
    assert(issueTypes.contains("RUNS_AS_ROOT"))
  }

  test("DockerValidator should handle comments and blank lines") {
    val dockerfile =
      """
        |# Base image without tag - bad practice
        |FROM ubuntu
        |
        |# Install dependencies
        |RUN echo "test"
      """.stripMargin

    val issues = DockerValidator.validateContent(dockerfile)

    val untagged = issues.filter(_.issue_type == "UNPINNED_BASE_IMAGE")
    assert(untagged.nonEmpty)
  }
