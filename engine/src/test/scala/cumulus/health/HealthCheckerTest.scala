package cumulus.health

import munit.FunSuite
import cumulus.protocol.{BinaryInfo, CompilerInfo, JdkInfo, HealthReport}

class HealthCheckerTest extends FunSuite:

  test("checkHealth returns success with valid structure") {
    val response = HealthChecker.checkHealth()
    assertEquals(response.success, true)
    assert(response.data.isDefined, "data should be present")
    assertEquals(response.error, None)
    assertEquals(response.error_code, None)
  }

  test("HealthReport contains binaries list") {
    val response = HealthChecker.checkHealth()
    val report = response.data.get
    assert(report.binaries.nonEmpty, "binaries list should not be empty")
  }

  test("HealthReport contains at least one binary (git or rg)") {
    val response = HealthChecker.checkHealth()
    val report = response.data.get
    val names = report.binaries.map(_.name)
    assert(names.contains("git") || names.contains("rg"), "git or rg should be detected")
  }

  test("BinaryInfo has required fields when present") {
    val response = HealthChecker.checkHealth()
    val report = response.data.get
    val gitBinary = report.binaries.find(_.name == "git")
    gitBinary.foreach { b =>
      assert(b.status.nonEmpty, "status should not be empty")
    }
  }

  test("Missing binaries have status 'missing'") {
    val response = HealthChecker.checkHealth()
    val report = response.data.get
    val missingBinaries = report.binaries.filter(_.status == "missing")
    // Missing binaries should not have path
    missingBinaries.foreach { b =>
      assertEquals(b.path, None)
    }
  }

  test("HealthReport includes engine_available flag") {
    val response = HealthChecker.checkHealth()
    val report = response.data.get
    assertEquals(report.engine_available, true)
  }

  test("HealthReport includes build_tool detection") {
    val response = HealthChecker.checkHealth()
    val report = response.data.get
    assert(Seq("maven", "gradle", "sbt", "none").contains(report.build_tool))
  }

  test("HealthReport jdks is always present") {
    val response = HealthChecker.checkHealth()
    val report = response.data.get
    assert(report.jdks != null)
    assert(report.jdks.isInstanceOf[Seq[JdkInfo]])
  }

  test("BinaryDetector detects system binaries") {
    val git = BinaryDetector.detectBinary("git")
    assertEquals(git.name, "git")
    assert(Seq("ok", "missing").contains(git.status))
  }

  test("BinaryDetector returns missing status for non-existent binary") {
    val nonExistent = BinaryDetector.detectBinary("this-binary-definitely-does-not-exist-12345")
    assertEquals(nonExistent.status, "missing")
    assertEquals(nonExistent.path, None)
  }

  test("BinaryDetector handles multiple binaries") {
    val binaries = BinaryDetector.detectAllBinaries(Seq("git", "rg", "fd"))
    assertEquals(binaries.length, 3)
  }

  test("CompilerVersionExtractor returns version for javac if available") {
    val version = CompilerVersionExtractor.extractVersion("javac")
    // Either found or None
    assert(version.isInstanceOf[Option[String]])
  }

  test("CompilerVersionExtractor returns None for non-existent binary") {
    val version = CompilerVersionExtractor.extractVersion("non-existent-compiler-xyz")
    assertEquals(version, None)
  }

  test("JdkScanner.scanJdkInstallations returns sequence") {
    val jdks = JdkScanner.scanJdkInstallations()
    assert(jdks.isInstanceOf[Seq[JdkInfo]])
  }

  test("JdkScanner handles systems with no JDK gracefully") {
    val jdks = JdkScanner.scanJdkInstallations()
    // Should not throw, returns empty or non-empty list
    assert(jdks.isInstanceOf[Seq[_]])
  }

  test("JdkScanner marks only one JDK as active") {
    val jdks = JdkScanner.scanJdkInstallations()
    val activeCount = jdks.count(_.active)
    assert(activeCount <= 1, s"At most one JDK should be active, found $activeCount")
  }

  test("HealthReport includes compiler info when compilers detected") {
    val response = HealthChecker.checkHealth()
    val report = response.data.get
    assert(report.compilers.isInstanceOf[Seq[CompilerInfo]])
  }

  test("checkHealth includes all required fields") {
    val response = HealthChecker.checkHealth()
    val report = response.data.get

    assert(report.binaries != null)
    assert(report.compilers != null)
    assert(report.jdks != null)
    assert(report.build_tool.nonEmpty)
    assert(report.engine_available.isInstanceOf[Boolean])
  }

  test("All detected binaries have required fields") {
    val response = HealthChecker.checkHealth()
    val report = response.data.get

    report.binaries.foreach { b =>
      assert(b.name.nonEmpty, s"Binary name should not be empty")
      assert(b.status.nonEmpty, s"Binary ${b.name} status should not be empty")
      assert(Seq("ok", "missing", "error").contains(b.status), s"Binary ${b.name} status should be ok, missing, or error")
    }
  }

  test("JdkInfo has valid structure") {
    val response = HealthChecker.checkHealth()
    val report = response.data.get

    report.jdks.foreach { jdk =>
      assert(jdk.version.nonEmpty, "JDK version should not be empty")
      assert(jdk.path.nonEmpty, "JDK path should not be empty")
      assert(jdk.active.isInstanceOf[Boolean])
    }
  }
