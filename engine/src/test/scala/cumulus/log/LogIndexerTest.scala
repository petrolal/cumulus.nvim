package cumulus.log

import munit.FunSuite
import java.io.{File, PrintWriter}

class LogIndexerTest extends FunSuite:

  test("extractSeverity: finds ERROR") {
    val line = "2026-08-14T10:30:45Z [ERROR] Something went wrong"
    val result = LogIndexer.extractSeverity(line)
    assert(result.isDefined)
    assert(result.get == "ERROR")
  }

  test("extractSeverity: finds WARN") {
    val line = "2026-08-14T10:30:45Z [WARN] Warning message"
    val result = LogIndexer.extractSeverity(line)
    assert(result.isDefined)
    assert(result.get == "WARN")
  }

  test("extractSeverity: finds FATAL") {
    val line = "FATAL: Critical error occurred"
    val result = LogIndexer.extractSeverity(line)
    assert(result.isDefined)
    assert(result.get == "FATAL")
  }

  test("extractSeverity: finds SEVERE") {
    val line = "Jul 14, 2026 10:30:45 AM SEVERE: Severe error"
    val result = LogIndexer.extractSeverity(line)
    assert(result.isDefined)
    assert(result.get == "SEVERE")
  }

  test("extractSeverity: returns None for INFO") {
    val line = "2026-08-14T10:30:45Z [INFO] Information message"
    val result = LogIndexer.extractSeverity(line)
    assert(result.isEmpty)
  }

  test("extractSeverity: returns None for DEBUG") {
    val line = "DEBUG: Debug information"
    val result = LogIndexer.extractSeverity(line)
    assert(result.isEmpty)
  }

  test("extractSeverity: is case-insensitive") {
    val line1 = "error: Something failed"
    val line2 = "Error: Something failed"
    val line3 = "ERROR: Something failed"
    assert(LogIndexer.extractSeverity(line1).isDefined)
    assert(LogIndexer.extractSeverity(line2).isDefined)
    assert(LogIndexer.extractSeverity(line3).isDefined)
  }

  test("extractTimestamp: extracts ISO 8601 timestamp") {
    val line = "2026-08-14T10:30:45Z [ERROR] Error message"
    val result = LogIndexer.extractTimestamp(line)
    assert(result.isDefined)
    assert(result.get == "2026-08-14T10:30:45Z")
  }

  test("extractTimestamp: extracts ISO 8601 timestamp with offset") {
    val line = "2026-08-14T10:30:45+02:00 [ERROR] Error message"
    val result = LogIndexer.extractTimestamp(line)
    assert(result.isDefined)
    assert(result.get == "2026-08-14T10:30:45+02:00")
  }

  test("extractTimestamp: returns None if no timestamp") {
    val line = "[ERROR] Error message without timestamp"
    val result = LogIndexer.extractTimestamp(line)
    assert(result.isEmpty)
  }

  test("indexLogContent: indexes errors in content") {
    val content =
      """2026-08-14T10:30:45Z [INFO] Starting application
        |2026-08-14T10:30:46Z [ERROR] Connection failed
        |2026-08-14T10:30:47Z [WARN] Retrying connection
        |2026-08-14T10:30:48Z [ERROR] Final error""".stripMargin
    val entries = LogIndexer.indexLogContent(content)
    assert(entries.length == 3)
    assert(entries(0).lineNumber == 2)
    assert(entries(0).severity == "ERROR")
    assert(entries(1).lineNumber == 3)
    assert(entries(1).severity == "WARN")
    assert(entries(2).lineNumber == 4)
    assert(entries(2).severity == "ERROR")
  }

  test("indexLogContent: extracts timestamps") {
    val content = "2026-08-14T10:30:45Z [ERROR] Test error"
    val entries = LogIndexer.indexLogContent(content)
    assert(entries.length == 1)
    assert(entries(0).timestamp.isDefined)
    assert(entries(0).timestamp.get == "2026-08-14T10:30:45Z")
  }

  test("indexLogContent: handles empty content") {
    val content = ""
    val entries = LogIndexer.indexLogContent(content)
    assert(entries.isEmpty)
  }

  test("indexLogContent: handles content with no severity lines") {
    val content =
      """2026-08-14T10:30:45Z [INFO] Info message
        |2026-08-14T10:30:46Z [DEBUG] Debug message
        |Some other text""".stripMargin
    val entries = LogIndexer.indexLogContent(content)
    assert(entries.isEmpty)
  }

  test("indexLogFile: indexes file") {
    val tempFile = File.createTempFile("test", ".log")
    try
      val writer = new PrintWriter(tempFile)
      writer.println("2026-08-14T10:30:45Z [INFO] Starting")
      writer.println("2026-08-14T10:30:46Z [ERROR] Error occurred")
      writer.println("2026-08-14T10:30:47Z [WARN] Warning")
      writer.close()

      val entries = LogIndexer.indexLogFile(tempFile.getAbsolutePath())
      assert(entries.length == 2)
      assert(entries(0).lineNumber == 2)
      assert(entries(0).severity == "ERROR")
      assert(entries(1).lineNumber == 3)
      assert(entries(1).severity == "WARN")
    finally
      tempFile.delete()
  }

  test("indexLogFile: throws exception for non-existent file") {
    val exception = intercept[Exception] {
      LogIndexer.indexLogFile("/nonexistent/path/file.log")
    }
    assert(exception.getMessage.contains("File not found"))
  }

  test("indexLogContent: handles multiple severity levels") {
    val content =
      """[ERROR] First error
        |[WARN] First warning
        |[FATAL] Critical error
        |[SEVERE] Severe issue""".stripMargin
    val entries = LogIndexer.indexLogContent(content)
    assert(entries.length == 4)
    assert(entries(0).severity == "ERROR")
    assert(entries(1).severity == "WARN")
    assert(entries(2).severity == "FATAL")
    assert(entries(3).severity == "SEVERE")
  }

  test("indexLogContent: includes full line as message") {
    val line = "2026-08-14T10:30:45Z [ERROR] Detailed error message"
    val content = line
    val entries = LogIndexer.indexLogContent(content)
    assert(entries.length == 1)
    assert(entries(0).message == line)
  }

  test("indexLogContent: handles different line endings") {
    val content = "[ERROR] Error1\r\n[ERROR] Error2\n[ERROR] Error3"
    val entries = LogIndexer.indexLogContent(content)
    assert(entries.length == 3)
    assert(entries(0).lineNumber == 1)
    assert(entries(1).lineNumber == 2)
    assert(entries(2).lineNumber == 3)
  }

  test("indexLogFile: processes large file efficiently") {
    val tempFile = File.createTempFile("large", ".log")
    try
      val writer = new PrintWriter(tempFile)
      // Create 1000 lines with 100 errors mixed in
      for (i <- 0 until 1000) {
        if i % 10 == 0 then
          writer.println(s"Line $i: [ERROR] Error at line $i")
        else
          writer.println(s"Line $i: [INFO] Normal log")
      }
      writer.close()

      val entries = LogIndexer.indexLogFile(tempFile.getAbsolutePath())
      assert(entries.length == 100)
      assert(entries.forall(_.severity == "ERROR"))
    finally
      tempFile.delete()
  }
