package cumulus.workspace

import munit.FunSuite

class JdtlsSyncCheckerTest extends FunSuite:

  test("detects stale pom.xml modified after start timestamp") {
    val tempDir = os.temp.dir()
    try
      val pom = tempDir / "pom.xml"
      os.write(pom, "<project></project>")

      val startTime = 1000L // old timestamp
      val status = JdtlsSyncChecker.checkSync(tempDir, startTime)
      assertEquals(status.sync_needed, true)
      assertEquals(status.modified_file, Some("pom.xml"))
    finally
      os.remove.all(tempDir)
  }

  test("reports no sync needed when start timestamp is in the future") {
    val tempDir = os.temp.dir()
    try
      val pom = tempDir / "pom.xml"
      os.write(pom, "<project></project>")

      val futureTime = System.currentTimeMillis() + 100000L
      val status = JdtlsSyncChecker.checkSync(tempDir, futureTime)
      assertEquals(status.sync_needed, false)
      assertEquals(status.modified_file, None)
    finally
      os.remove.all(tempDir)
  }
