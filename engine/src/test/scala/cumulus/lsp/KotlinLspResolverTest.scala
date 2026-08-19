package cumulus.lsp

import munit.FunSuite
import cumulus.protocol.{KotlinLspConfig, CumulusResponse}
import os.Path

class KotlinLspResolverTest extends FunSuite:

  test("handle returns error when project directory does not exist") {
    val nonexistent = "/tmp/nonexistent-cumulus-test-" + System.currentTimeMillis()
    val res = KotlinLspResolver.handle(Some(nonexistent))
    assert(!res.success)
    assert(res.data.isEmpty)
    assertEquals(res.error, Some("Directory not found"))
    assertEquals(res.error_code, Some("FILE_NOT_FOUND"))
  }

  test("handle returns empty classpath and source roots for empty project") {
    val tmpDir = os.temp.dir(prefix = "empty-kt-proj")
    try
      val res = KotlinLspResolver.handle(Some(tmpDir.toString))
      assert(res.success)
      assert(res.data.isDefined)
      val config = res.data.get
      assertEquals(config.classpath, Seq.empty)
      assertEquals(config.source_roots, Seq.empty)
      assertEquals(config.is_cached, false)
    finally
      os.remove.all(tmpDir)
  }

  test("handle discovers Kotlin and KMP / Android source roots") {
    val tmpDir = os.temp.dir(prefix = "kt-sources-proj")
    try
      val mainKt = tmpDir / "src" / "main" / "kotlin"
      val testKt = tmpDir / "src" / "test" / "kotlin"
      val mainJava = tmpDir / "src" / "main" / "java"
      val commonMainKt = tmpDir / "src" / "commonMain" / "kotlin"
      val jvmMainKt = tmpDir / "src" / "jvmMain" / "kotlin"
      val androidMainKt = tmpDir / "src" / "androidMain" / "kotlin"

      os.makeDir.all(mainKt)
      os.makeDir.all(testKt)
      os.makeDir.all(mainJava)
      os.makeDir.all(commonMainKt)
      os.makeDir.all(jvmMainKt)
      os.makeDir.all(androidMainKt)

      val res = KotlinLspResolver.handle(Some(tmpDir.toString))
      assert(res.success)
      val config = res.data.get
      val expected = Seq(
        mainKt.toString,
        testKt.toString,
        mainJava.toString,
        commonMainKt.toString,
        jvmMainKt.toString,
        androidMainKt.toString
      ).sorted
      assertEquals(config.source_roots.sorted, expected)
    finally
      os.remove.all(tmpDir)
  }

  test("handle caches classpath and source roots on first run, returns is_cached=true on second run") {
    val tmpDir = os.temp.dir(prefix = "kt-cache-proj")
    val tmpCache = os.temp.dir(prefix = "kt-cache-store") / "test-cache.json"
    try
      val buildFile = tmpDir / "build.gradle.kts"
      os.write(buildFile, "plugins { kotlin(\"jvm\") version \"1.9.22\" }\n")
      val mainKt = tmpDir / "src" / "main" / "kotlin"
      os.makeDir.all(mainKt)

      // First run: uncached
      val res1 = KotlinLspResolver.handle(
        projectRootOpt = Some(tmpDir.toString),
        sanitizeCache = false,
        customCacheFile = Some(tmpCache)
      )
      assert(res1.success)
      val cfg1 = res1.data.get
      assertEquals(cfg1.is_cached, false)
      assertEquals(cfg1.source_roots, Seq(mainKt.toString))

      // Second run: cached
      val res2 = KotlinLspResolver.handle(
        projectRootOpt = Some(tmpDir.toString),
        sanitizeCache = false,
        customCacheFile = Some(tmpCache)
      )
      assert(res2.success)
      val cfg2 = res2.data.get
      assertEquals(cfg2.is_cached, true)
      assertEquals(cfg2.source_roots, Seq(mainKt.toString))
    finally
      os.remove.all(tmpDir)
      if (os.exists(tmpCache / os.up)) os.remove.all(tmpCache / os.up)
  }

  test("handle invalidates cache when companion build definition (settings.gradle.kts) mtime changes") {
    val tmpDir = os.temp.dir(prefix = "kt-inval-proj")
    val tmpCache = os.temp.dir(prefix = "kt-cache-store") / "test-cache.json"
    try
      val buildFile = tmpDir / "build.gradle.kts"
      val settingsFile = tmpDir / "settings.gradle.kts"
      os.write(buildFile, "plugins { kotlin(\"jvm\") version \"1.9.22\" }")
      os.write(settingsFile, "rootProject.name = \"test\"")
      val mainKt = tmpDir / "src" / "main" / "kotlin"
      os.makeDir.all(mainKt)

      // First run
      val res1 = KotlinLspResolver.handle(
        projectRootOpt = Some(tmpDir.toString),
        customCacheFile = Some(tmpCache)
      )
      assert(res1.success)
      assertEquals(res1.data.get.is_cached, false)

      // Second run: cached
      val res2 = KotlinLspResolver.handle(
        projectRootOpt = Some(tmpDir.toString),
        customCacheFile = Some(tmpCache)
      )
      assert(res2.success)
      assertEquals(res2.data.get.is_cached, true)

      // Modify companion settings file mtime
      Thread.sleep(100)
      os.write.over(settingsFile, "rootProject.name = \"test-updated\"")

      // Third run: cache invalidated due to companion settings file mtime change
      val res3 = KotlinLspResolver.handle(
        projectRootOpt = Some(tmpDir.toString),
        customCacheFile = Some(tmpCache)
      )
      assert(res3.success)
      assertEquals(res3.data.get.is_cached, false)
    finally
      os.remove.all(tmpDir)
      if (os.exists(tmpCache / os.up)) os.remove.all(tmpCache / os.up)
  }

  test("handle with sanitizeCache=true prunes invalid cache entries") {
    val tmpCache = os.temp.dir(prefix = "kt-cache-store") / "test-cache.json"
    try
      // Seed cache with a nonexistent project and a valid project
      val validProj = os.temp.dir(prefix = "kt-valid-proj")
      val validBuild = validProj / "build.gradle"
      os.write(validBuild, "plugins { id 'org.jetbrains.kotlin.jvm' version '1.9.22' }")

      val nonexistentProj = "/tmp/nonexistent-proj-" + System.currentTimeMillis()
      val nonexistentBuild = nonexistentProj + "/build.gradle"

      KotlinLspCache.put(
        projectRoot = nonexistentProj,
        buildFilePath = nonexistentBuild,
        buildFileMtime = 1000L,
        classpath = Seq("/nonexistent/dep.jar"),
        sourceRoots = Seq("/nonexistent/src"),
        cacheFile = tmpCache
      )

      KotlinLspCache.put(
        projectRoot = validProj.toString,
        buildFilePath = validBuild.toString,
        buildFileMtime = os.mtime(validBuild),
        classpath = Seq("/valid/dep.jar"),
        sourceRoots = Seq(validProj.toString),
        cacheFile = tmpCache
      )

      val storeBefore = KotlinLspCache.loadCache(tmpCache)
      assertEquals(storeBefore.entries.size, 2)

      // Run sanitize
      val res = KotlinLspResolver.handle(
        projectRootOpt = Some(validProj.toString),
        sanitizeCache = true,
        customCacheFile = Some(tmpCache)
      )
      assert(res.success)
      assertEquals(res.data.get.cache_sanitized, true)

      val storeAfter = KotlinLspCache.loadCache(tmpCache)
      assertEquals(storeAfter.entries.size, 1)
      assert(storeAfter.entries.contains(validProj.toString))
      assert(!storeAfter.entries.contains(nonexistentProj))

      os.remove.all(validProj)
    finally
      if (os.exists(tmpCache / os.up)) os.remove.all(tmpCache / os.up)
  }

  test("KotlinLspConfig serializes to and from JSON with uPickle") {
    val config = KotlinLspConfig(
      server_path = Some("/usr/local/bin/kotlin-language-server"),
      server_valid = true,
      classpath = Seq("/path/to/a.jar", "/path/to/b.jar"),
      source_roots = Seq("/path/to/src/main/kotlin"),
      compiler_version = Some("1.9.22"),
      is_cached = false,
      cache_sanitized = false,
      diagnostics = Some(Seq("Note 1"))
    )

    val json = upickle.default.write(config)
    assert(json.contains("/usr/local/bin/kotlin-language-server"))
    assert(json.contains("1.9.22"))
    assert(json.contains("is_cached"))

    val deserialized = upickle.default.read[KotlinLspConfig](json)
    assertEquals(deserialized.server_path, config.server_path)
    assertEquals(deserialized.classpath, config.classpath)
    assertEquals(deserialized.source_roots, config.source_roots)
    assertEquals(deserialized.compiler_version, config.compiler_version)
    assertEquals(deserialized.is_cached, config.is_cached)
  }
