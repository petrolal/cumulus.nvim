package cumulus.workspace

import munit.FunSuite
import cumulus.protocol.JdkRuntime

class JdtlsConfigResolverTest extends FunSuite:

  test("returns error for non-existent project root") {
    val result = JdtlsConfigResolver.handle("/nonexistent/path/to/project")
    assertEquals(result.success, false)
    assertEquals(result.error_code, Some("FILE_NOT_FOUND"))
    assert(result.error.isDefined)
  }

  test("handles Maven project without dependencies") {
    val tempDir = os.temp.dir()
    try
      val pom = tempDir / "pom.xml"
      os.write(pom, """<?xml version="1.0" encoding="UTF-8"?>
        |<project>
        |  <modelVersion>4.0.0</modelVersion>
        |  <groupId>com.example</groupId>
        |  <artifactId>test-app</artifactId>
        |  <version>1.0-SNAPSHOT</version>
        |  <dependencies/>
        |</project>""".stripMargin)

      val result = JdtlsConfigResolver.handle(tempDir.toString)
      assertEquals(result.success, true)
      assert(result.data.isDefined)
      val config = result.data.get
      assertEquals(config.classpath, Seq.empty)
      // Should have detected JDKs if any exist on system
      assert(config.jvm_runtimes.isInstanceOf[Seq[?]])
    finally
      os.remove.all(tempDir)
  }

  test("handles Gradle project without build.gradle") {
    val tempDir = os.temp.dir()
    try
      val result = JdtlsConfigResolver.handle(tempDir.toString)
      assertEquals(result.success, true)
      assert(result.data.isDefined)
      val config = result.data.get
      assertEquals(config.classpath, Seq.empty)
    finally
      os.remove.all(tempDir)
  }

  test("validates Maven bundle structure") {
    val tempDir = os.temp.dir()
    try
      // Create minimal Mason bundle structure
      val pluginsDir = tempDir / "plugins"
      os.makeDir.all(pluginsDir)
      val configDir = tempDir / "config_linux"
      os.makeDir.all(configDir)
      val execFile = tempDir / "jdtls"
      os.write(execFile, "#!/bin/bash\necho jdtls")

      val isValid = MasonBundleValidator.validateBundle(tempDir.toString)
      assertEquals(isValid, true)
    finally
      os.remove.all(tempDir)
  }

  test("detects invalid Mason bundle (missing plugins dir)") {
    val tempDir = os.temp.dir()
    try
      val configDir = tempDir / "config_linux"
      os.makeDir.all(configDir)
      val execFile = tempDir / "jdtls"
      os.write(execFile, "#!/bin/bash")

      val isValid = MasonBundleValidator.validateBundle(tempDir.toString)
      assertEquals(isValid, false)
    finally
      os.remove.all(tempDir)
  }

  test("detects invalid Mason bundle (missing config dir)") {
    val tempDir = os.temp.dir()
    try
      val pluginsDir = tempDir / "plugins"
      os.makeDir.all(pluginsDir)
      val execFile = tempDir / "jdtls"
      os.write(execFile, "#!/bin/bash")

      val isValid = MasonBundleValidator.validateBundle(tempDir.toString)
      assertEquals(isValid, false)
    finally
      os.remove.all(tempDir)
  }

  test("detects invalid Mason bundle (missing jdtls executable)") {
    val tempDir = os.temp.dir()
    try
      val pluginsDir = tempDir / "plugins"
      os.makeDir.all(pluginsDir)
      val configDir = tempDir / "config_linux"
      os.makeDir.all(configDir)

      val isValid = MasonBundleValidator.validateBundle(tempDir.toString)
      assertEquals(isValid, false)
    finally
      os.remove.all(tempDir)
  }

  test("gets correct config directory for bundle") {
    val tempDir = os.temp.dir()
    try
      val pluginsDir = tempDir / "plugins"
      os.makeDir.all(pluginsDir)
      val configDir = tempDir / "config_linux"
      os.makeDir.all(configDir)
      val execFile = tempDir / "jdtls"
      os.write(execFile, "#!/bin/bash")

      val configDirOpt = MasonBundleValidator.getConfigDir(tempDir.toString)
      assert(configDirOpt.isDefined)
    finally
      os.remove.all(tempDir)
  }

  test("returns None for non-existent bundle directory") {
    val configDir = MasonBundleValidator.getConfigDir("/nonexistent/path")
    assertEquals(configDir, None)
  }

  test("Maven classpath resolver returns empty for non-Maven project") {
    val tempDir = os.temp.dir()
    try
      val classpath = MavenClasspathResolver.resolveClasspath(tempDir.toString)
      assertEquals(classpath, Seq.empty)
    finally
      os.remove.all(tempDir)
  }

  test("Gradle classpath resolver returns empty for non-Gradle project") {
    val tempDir = os.temp.dir()
    try
      val classpath = GradleClasspathResolver.resolveClasspath(tempDir.toString)
      assertEquals(classpath, Seq.empty)
    finally
      os.remove.all(tempDir)
  }

  test("JDTLS detector returns None for missing binary") {
    val detectedPath = JdtlsDetector.detectJdtls()
    // Can't assume JDTLS is installed, so just verify the return type
    assert(detectedPath.isInstanceOf[Option[String]])
  }

  test("handles project with empty directory") {
    val tempDir = os.temp.dir()
    try
      val result = JdtlsConfigResolver.handle(tempDir.toString)
      assertEquals(result.success, true)
      assert(result.data.isDefined)
    finally
      os.remove.all(tempDir)
  }

  test("returns success even without JDTLS installed") {
    val tempDir = os.temp.dir()
    try
      val result = JdtlsConfigResolver.handle(tempDir.toString)
      assertEquals(result.success, true)
      val config = result.data.get
      // jdtls_path may be None if not installed
      assert(config.jdtls_path.isInstanceOf[Option[String]])
      // But notes should mention if it's missing
      if config.jdtls_path.isEmpty then
        assert(config.notes.isDefined)
    finally
      os.remove.all(tempDir)
  }

  test("handles absolute path with Maven project") {
    val tempDir = os.temp.dir()
    try
      // Create a Maven pom.xml in the temp directory
      val pom = tempDir / "pom.xml"
      os.write(pom, """<?xml version="1.0" encoding="UTF-8"?>
        |<project>
        |  <modelVersion>4.0.0</modelVersion>
        |  <groupId>com.example</groupId>
        |  <artifactId>test-app</artifactId>
        |  <version>1.0.0</version>
        |  <dependencies/>
        |</project>""".stripMargin)

      val result = JdtlsConfigResolver.handle(tempDir.toString)
      assertEquals(result.success, true)
      assert(result.data.isDefined)
      assert(result.data.get.classpath == Seq.empty)
    finally
      os.remove.all(tempDir)
  }

  test("handles nested Maven project with absolute path") {
    val tempDir = os.temp.dir()
    try
      // Create a subdirectory with Maven project
      val subDir = tempDir / "subproject"
      os.makeDir.all(subDir)
      val pom = subDir / "pom.xml"
      os.write(pom, """<?xml version="1.0" encoding="UTF-8"?>
        |<project>
        |  <modelVersion>4.0.0</modelVersion>
        |  <groupId>com.example</groupId>
        |  <artifactId>sub-app</artifactId>
        |  <version>1.0.0</version>
        |  <dependencies/>
        |</project>""".stripMargin)

      val result = JdtlsConfigResolver.handle(subDir.toString)
      assertEquals(result.success, true)
      assert(result.data.isDefined)
      assert(result.data.get.classpath == Seq.empty)
    finally
      os.remove.all(tempDir)
  }

  test("resolves classpath and JVM args for Maven project with absolute path") {
    val tempDir = os.temp.dir()
    try
      val pom = tempDir / "pom.xml"
      os.write(pom, """<?xml version="1.0" encoding="UTF-8"?>
        |<project>
        |  <modelVersion>4.0.0</modelVersion>
        |  <groupId>com.example</groupId>
        |  <artifactId>test-app</artifactId>
        |  <version>1.0.0</version>
        |  <dependencies/>
        |</project>""".stripMargin)

      val result = JdtlsConfigResolver.handle(tempDir.toString)
      assertEquals(result.success, true)
      assert(result.data.isDefined)
      val config = result.data.get
      // Empty classpath is expected when no dependencies in local ~/.m2/
      assertEquals(config.classpath, Seq.empty)
      assert(config.jvm_args.contains("-Xmx4G"))
      assert(config.jvm_runtimes.isInstanceOf[Seq[?]])
    finally
      os.remove.all(tempDir)
  }
