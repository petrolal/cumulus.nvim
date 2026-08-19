package cumulus.devops

import munit.FunSuite
import java.nio.file.Files

class HelmParserSpec extends FunSuite:

  test("HelmParser.parseChartYaml extracts metadata, dependencies, and maintainers") {
    val chartYaml =
      """apiVersion: v2
        |name: my-microservice
        |description: A Helm chart for Kubernetes microservice
        |type: application
        |version: 1.2.3
        |appVersion: "2.0.0"
        |keywords:
        |  - backend
        |  - scala
        |maintainers:
        |  - name: Lead Maintainer
        |    email: lead@example.com
        |    url: https://example.com/team
        |dependencies:
        |  - name: redis
        |    version: 17.0.1
        |    repository: https://charts.bitnami.com/bitnami
        |    condition: redis.enabled
        |    tags:
        |      - cache
        |""".stripMargin

    val info = HelmParser.parseChartYaml(chartYaml)

    assertEquals(info.name, "my-microservice")
    assertEquals(info.version, "1.2.3")
    assertEquals(info.apiVersion, "v2")
    assertEquals(info.appVersion, Some("2.0.0"))
    assertEquals(info.description, Some("A Helm chart for Kubernetes microservice"))
    assertEquals(info.chart_type, Some("application"))
    assertEquals(info.keywords, Seq("backend", "scala"))

    assertEquals(info.maintainers.length, 1)
    assertEquals(info.maintainers.head.name, "Lead Maintainer")
    assertEquals(info.maintainers.head.email, Some("lead@example.com"))
    assertEquals(info.maintainers.head.url, Some("https://example.com/team"))

    assertEquals(info.dependencies.length, 1)
    assertEquals(info.dependencies.head.name, "redis")
    assertEquals(info.dependencies.head.version, "17.0.1")
    assertEquals(info.dependencies.head.repository, Some("https://charts.bitnami.com/bitnami"))
    assertEquals(info.dependencies.head.condition, Some("redis.enabled"))
  }

  test("HelmParser.flattenYamlValues flattens multi-level nested structures") {
    val valuesYaml =
      """replicaCount: 3
        |image:
        |  repository: nginx
        |  tag: "1.25.0"
        |  pullPolicy: IfNotPresent
        |service:
        |  type: ClusterIP
        |  port: 80
        |ingress:
        |  enabled: false
        |  hosts:
        |    - host: chart-example.local
        |      paths:
        |        - path: /
        |          pathType: ImplementationSpecific
        |""".stripMargin

    val flattened = HelmParser.flattenYamlValues(valuesYaml)

    assertEquals(flattened.get("replicaCount"), Some("3"))
    assertEquals(flattened.get("image.repository"), Some("nginx"))
    assertEquals(flattened.get("image.tag"), Some("1.25.0"))
    assertEquals(flattened.get("image.pullPolicy"), Some("IfNotPresent"))
    assertEquals(flattened.get("service.type"), Some("ClusterIP"))
    assertEquals(flattened.get("service.port"), Some("80"))
    assertEquals(flattened.get("ingress.enabled"), Some("false"))
  }

  test("HelmParser.extractTemplateVars extracts template variable keys") {
    val tpl =
      """apiVersion: apps/v1
        |kind: Deployment
        |metadata:
        |  name: {{ .Chart.Name }}
        |spec:
        |  replicas: {{ .Values.replicaCount }}
        |  template:
        |    spec:
        |      containers:
        |        - name: {{ .Chart.Name }}
        |          image: "{{ .Values.image.repository }}:{{ .Values.image.tag }}"
        |          imagePullPolicy: {{ .Values.image.pullPolicy }}
        |""".stripMargin

    val vars = HelmParser.extractTemplateVars(tpl)

    assert(vars.contains("replicaCount"))
    assert(vars.contains("image.repository"))
    assert(vars.contains("image.tag"))
    assert(vars.contains("image.pullPolicy"))
    assert(vars.contains("Name"))
  }

  test("HelmParser.inspectChart on complete chart directory") {
    val tempDir = Files.createTempDirectory("test-helm-chart").toFile
    try
      val chartYaml =
        """apiVersion: v2
          |name: demo-chart
          |version: 0.1.0
          |""".stripMargin
      val valuesYaml =
        """replicaCount: 1
          |service:
          |  port: 8080
          |""".stripMargin
      val templateContent =
        """apiVersion: v1
          |kind: Service
          |metadata:
          |  name: {{ .Chart.Name }}
          |spec:
          |  ports:
          |    - port: {{ .Values.service.port }}
          |""".stripMargin

      Files.writeString(new java.io.File(tempDir, "Chart.yaml").toPath, chartYaml)
      Files.writeString(new java.io.File(tempDir, "values.yaml").toPath, valuesYaml)

      val tplDir = new java.io.File(tempDir, "templates")
      tplDir.mkdir()
      Files.writeString(new java.io.File(tplDir, "service.yaml").toPath, templateContent)

      val result = HelmParser.inspectChart(tempDir.getAbsolutePath)
      assert(result.success)
      assert(result.data.isDefined)
      val data = result.data.get

      assertEquals(data.name, "demo-chart")
      assertEquals(data.version, "0.1.0")
      assertEquals(data.values.get("replicaCount"), Some("1"))
      assertEquals(data.values.get("service.port"), Some("8080"))
      assertEquals(data.chart_path, tempDir.getAbsolutePath)
      assert(data.templates.contains("templates/service.yaml"))
      assert(data.template_vars.contains("service.port"))

      // Inspect via direct Chart.yaml path
      val fileResult = HelmParser.inspectChart(new java.io.File(tempDir, "Chart.yaml").getAbsolutePath)
      assert(fileResult.success)
      assertEquals(fileResult.data.get.name, "demo-chart")
    finally
      os.remove.all(os.Path(tempDir))
  }

  test("HelmParser.inspectChartContent merges chart and values contents") {
    val chartYaml = "apiVersion: v2\nname: raw-chart\nversion: 3.2.1\n"
    val valuesYaml = "env: production\nreplicas: 5\n"

    val info = HelmParser.inspectChartContent(chartYaml, Some(valuesYaml))
    assertEquals(info.name, "raw-chart")
    assertEquals(info.version, "3.2.1")
    assertEquals(info.values.get("env"), Some("production"))
    assertEquals(info.values.get("replicas"), Some("5"))

    val noValuesInfo = HelmParser.inspectChartContent(chartYaml, None)
    assertEquals(noValuesInfo.name, "raw-chart")
    assertEquals(noValuesInfo.values.isEmpty, true)
  }

  test("HelmParser.inspectChart error handling on non-existent or invalid directory") {
    val missingRes = HelmParser.inspectChart("/nonexistent/helm/chart")
    assert(!missingRes.success)
    assertEquals(missingRes.error_code, Some("FILE_NOT_FOUND"))

    val emptyDir = Files.createTempDirectory("empty-helm").toFile
    try
      val emptyRes = HelmParser.inspectChart(emptyDir.getAbsolutePath)
      assert(!emptyRes.success)
      assertEquals(emptyRes.error_code, Some("FILE_NOT_FOUND"))
    finally
      emptyDir.delete()
  }


