package cumulus.devops

import munit.FunSuite

class DockerParserTest extends FunSuite:

  test("DockerParser should parse single-stage Dockerfile") {
    val dockerfile =
      """
        |FROM ubuntu:22.04
        |WORKDIR /app
        |COPY . .
        |RUN apt-get update && apt-get install -y build-essential
        |EXPOSE 8080
        |USER appuser
        |ENTRYPOINT ["/app/start.sh"]
        |CMD []
      """.stripMargin

    val result = DockerParser.parseContent(dockerfile)

    assertEquals(result.stages.length, 1)
    assertEquals(result.stages.head.base_image, "ubuntu:22.04")
    assertEquals(result.stages.head.name, None)
    assertEquals(result.exposed_ports.length, 1)
    assertEquals(result.exposed_ports.head, "8080")
    assertEquals(result.user, Some("appuser"))
    assertEquals(result.workdir, Some("/app"))
    assertEquals(result.entrypoint, Some("""["/app/start.sh"]"""))
    assertEquals(result.cmd, Some("[]"))
  }

  test("DockerParser should parse multi-stage Dockerfile") {
    val dockerfile =
      """
        |FROM node:20-alpine AS builder
        |WORKDIR /app
        |COPY package.json package-lock.json .
        |RUN npm ci && npm run build
        |
        |FROM node:20-alpine AS runtime
        |WORKDIR /app
        |COPY --from=builder /app/dist /app/dist
        |COPY package.json .
        |RUN npm ci --only=production
        |EXPOSE 3000
        |CMD ["node", "dist/index.js"]
      """.stripMargin

    val result = DockerParser.parseContent(dockerfile)

    assertEquals(result.stages.length, 2)
    assertEquals(result.stages(0).base_image, "node:20-alpine")
    assertEquals(result.stages(0).name, Some("builder"))
    assertEquals(result.stages(1).base_image, "node:20-alpine")
    assertEquals(result.stages(1).name, Some("runtime"))
    assertEquals(result.exposed_ports.length, 1)
    assertEquals(result.exposed_ports.head, "3000")
    assertEquals(result.cmd, Some("""["node", "dist/index.js"]"""))
  }

  test("DockerParser should extract EXPOSE ports") {
    val dockerfile =
      """
        |FROM alpine:latest
        |EXPOSE 8080 9090
        |EXPOSE 443
      """.stripMargin

    val result = DockerParser.parseContent(dockerfile)

    assert(result.exposed_ports.contains("8080"))
    assert(result.exposed_ports.contains("9090"))
    assert(result.exposed_ports.contains("443"))
  }

  test("DockerParser should extract VOLUME mount points") {
    val dockerfile =
      """
        |FROM ubuntu:22.04
        |VOLUME ["/var/lib/mysql", "/var/log"]
        |VOLUME /data
      """.stripMargin

    val result = DockerParser.parseContent(dockerfile)

    // VOLUME extracts the full string after VOLUME keyword
    assert(result.volumes.nonEmpty)
  }

  test("DockerParser should parse HEALTHCHECK") {
    val dockerfile =
      """
        |FROM nginx:latest
        |HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 CMD curl -f http://localhost:80/health || exit 1
      """.stripMargin

    val result = DockerParser.parseContent(dockerfile)

    assert(result.healthcheck.isDefined)
    assertEquals(result.healthcheck.get.interval, Some("30s"))
    assertEquals(result.healthcheck.get.timeout, Some("3s"))
    assertEquals(result.healthcheck.get.start_period, Some("5s"))
    assertEquals(result.healthcheck.get.retries, Some(3))
  }

  test("DockerParser should handle empty Dockerfile") {
    val dockerfile = ""

    val result = DockerParser.parseContent(dockerfile)

    assertEquals(result.stages.length, 0)
    assertEquals(result.exposed_ports.length, 0)
    assertEquals(result.user, None)
  }

  test("DockerParser should handle comments and blank lines") {
    val dockerfile =
      """
        |# This is a comment
        |FROM ubuntu:22.04
        |
        |# Another comment
        |EXPOSE 8080
      """.stripMargin

    val result = DockerParser.parseContent(dockerfile)

    assertEquals(result.stages.length, 1)
    assertEquals(result.exposed_ports.length, 1)
  }

  test("DockerParser should extract USER directive") {
    val dockerfile =
      """
        |FROM alpine:latest
        |RUN useradd -m appuser
        |USER appuser
      """.stripMargin

    val result = DockerParser.parseContent(dockerfile)

    assertEquals(result.user, Some("appuser"))
  }

  test("DockerParser should handle case-insensitive instructions") {
    val dockerfile =
      """
        |from ubuntu:22.04
        |expose 8080
        |user root
      """.stripMargin

    val result = DockerParser.parseContent(dockerfile)

    assertEquals(result.stages.length, 1)
    assertEquals(result.exposed_ports.length, 1)
    assertEquals(result.user, Some("root"))
  }

  test("DockerParser should parse COPY --from for multi-stage reference") {
    val dockerfile =
      """
        |FROM builder AS stage1
        |RUN echo "build"
        |
        |FROM alpine
        |COPY --from=stage1 /build /app
      """.stripMargin

    val result = DockerParser.parseContent(dockerfile)

    assertEquals(result.stages.length, 2)
    // Verify that --from reference is preserved in instructions
    assert(result.stages(1).instructions.exists(_.contains("--from=stage1")))
  }

  test("DockerParser should track WORKDIR") {
    val dockerfile =
      """
        |FROM ubuntu:22.04
        |WORKDIR /home/app
      """.stripMargin

    val result = DockerParser.parseContent(dockerfile)

    assertEquals(result.workdir, Some("/home/app"))
  }

  test("DockerParser should handle multiple stages correctly") {
    val dockerfile =
      """
        |FROM alpine:latest AS downloader
        |RUN wget https://example.com/file
        |
        |FROM alpine:latest AS extractor
        |COPY --from=downloader /file /tmp/file
        |RUN tar xzf /tmp/file
        |
        |FROM alpine:latest
        |COPY --from=extractor /app /app
        |CMD ["/app/run.sh"]
      """.stripMargin

    val result = DockerParser.parseContent(dockerfile)

    assertEquals(result.stages.length, 3)
    assertEquals(result.stages(0).name, Some("downloader"))
    assertEquals(result.stages(1).name, Some("extractor"))
    assertEquals(result.stages(2).name, None)
  }

  test("DockerParser should handle multi-line RUN with backslash continuation") {
    val dockerfile =
      """
        |FROM alpine:latest
        |RUN apk add --no-cache \
        |    curl \
        |    git
        |EXPOSE 8080
      """.stripMargin

    val result = DockerParser.parseContent(dockerfile)

    assertEquals(result.stages.length, 1)
    // The assembled multi-line RUN instruction should be preserved with continued packages
    assert(result.stages.head.instructions.exists(_.contains("curl")))
    assert(result.stages.head.instructions.exists(_.contains("git")))
    assertEquals(result.exposed_ports.length, 1)
    assertEquals(result.exposed_ports.head, "8080")
  }
