package cumulus.gradle

import munit.FunSuite

class WrapperVerifierTest extends FunSuite:

  test("verifies valid wrapper properties and sha256 checksum") {
    val tempDir = os.temp.dir()
    try
      val wrapperDir = tempDir / "gradle" / "wrapper"
      os.makeDir.all(wrapperDir)
      os.write(
        wrapperDir / "gradle-wrapper.properties",
        """distributionUrl=https\://services.gradle.org/distributions/gradle-8.5-bin.zip
          |distributionSha256Sum=e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855
          |""".stripMargin
      )

      val status = WrapperVerifier.verifyDirectory(tempDir)
      assertEquals(status.local_version, Some("8.5"))
      assertEquals(status.sha256_configured, true)
      assertEquals(status.sha256_valid, true)
      assertEquals(status.issues, Seq.empty)
    finally
      os.remove.all(tempDir)
  }

  test("flags missing sha256 checksum as issue") {
    val tempDir = os.temp.dir()
    try
      val wrapperDir = tempDir / "gradle" / "wrapper"
      os.makeDir.all(wrapperDir)
      os.write(
        wrapperDir / "gradle-wrapper.properties",
        "distributionUrl=https\\://services.gradle.org/distributions/gradle-7.6.1-bin.zip\n"
      )

      val status = WrapperVerifier.verifyDirectory(tempDir)
      assertEquals(status.local_version, Some("7.6.1"))
      assertEquals(status.sha256_configured, false)
      assert(status.issues.exists(_.contains("SHA-256 checksum not configured")))
    finally
      os.remove.all(tempDir)
  }

  test("detects version mismatch against GitHub Actions workflow") {
    val tempDir = os.temp.dir()
    try
      val wrapperDir = tempDir / "gradle" / "wrapper"
      os.makeDir.all(wrapperDir)
      os.write(
        wrapperDir / "gradle-wrapper.properties",
        """distributionUrl=https\://services.gradle.org/distributions/gradle-8.5-bin.zip
          |distributionSha256Sum=abc123
          |""".stripMargin
      )

      val ghDir = tempDir / ".github" / "workflows"
      os.makeDir.all(ghDir)
      os.write(
        ghDir / "ci.yml",
        """name: CI
          |jobs:
          |  build:
          |    steps:
          |      - uses: gradle/gradle-build-action@v2
          |        with:
          |          gradle-version: "8.0"
          |""".stripMargin
      )

      val status = WrapperVerifier.verifyDirectory(tempDir)
      assertEquals(status.local_version, Some("8.5"))
      assertEquals(status.ci_version, Some("8.0"))
      assert(status.issues.exists(_.contains("Gradle version mismatch: local=8.5, CI=8.0")))
    finally
      os.remove.all(tempDir)
  }

  test("detects CI version from .gitlab-ci.yml") {
    val tempDir = os.temp.dir()
    try
      val wrapperDir = tempDir / "gradle" / "wrapper"
      os.makeDir.all(wrapperDir)
      os.write(
        wrapperDir / "gradle-wrapper.properties",
        """distributionUrl=https\://services.gradle.org/distributions/gradle-8.4-bin.zip
          |distributionSha256Sum=abc123
          |""".stripMargin
      )
      os.write(tempDir / ".gitlab-ci.yml", "image: gradle:8.4-jdk17\ngradle-version: 8.4\n")

      val status = WrapperVerifier.verifyDirectory(tempDir)
      assertEquals(status.local_version, Some("8.4"))
      assertEquals(status.ci_version, Some("8.4"))
      assertEquals(status.issues, Seq.empty)
    finally
      os.remove.all(tempDir)
  }

  test("detects CI version from Jenkinsfile") {
    val tempDir = os.temp.dir()
    try
      val wrapperDir = tempDir / "gradle" / "wrapper"
      os.makeDir.all(wrapperDir)
      os.write(
        wrapperDir / "gradle-wrapper.properties",
        """distributionUrl=https\://services.gradle.org/distributions/gradle-8.5-bin.zip
          |distributionSha256Sum=abc123
          |""".stripMargin
      )
      os.write(tempDir / "Jenkinsfile", "pipeline { environment { GRADLE_VERSION = '8.5' } }\n")

      val status = WrapperVerifier.verifyDirectory(tempDir)
      assertEquals(status.local_version, Some("8.5"))
      assertEquals(status.ci_version, Some("8.5"))
      assertEquals(status.issues, Seq.empty)
    finally
      os.remove.all(tempDir)
  }

  test("flags missing wrapper properties file in a Gradle project") {
    val tempDir = os.temp.dir()
    try
      os.write(tempDir / "build.gradle", "plugins { id 'java' }\n")
      val status = WrapperVerifier.verifyDirectory(tempDir)
      assertEquals(status.local_version, None)
      assert(status.issues.exists(_.contains("Gradle wrapper properties file")))
    finally
      os.remove.all(tempDir)
  }

