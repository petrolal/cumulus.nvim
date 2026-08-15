package cumulus.log

import munit.FunSuite
import scala.io.Source
import java.io.{File, PrintWriter}

class LogParserTest extends FunSuite:

  test("stripAnsiCodes: removes ANSI escape codes") {
    val input = "[1;31m[ERROR][0m test message"
    val expected = "[ERROR] test message"
    assert(LogParser.stripAnsiCodes(input) == expected)
  }

  test("stripAnsiCodes: removes character set reset codes") {
    val input = "test(Bdata"
    val expected = "testdata"
    assert(LogParser.stripAnsiCodes(input) == expected)
  }

  test("parseLog: extracts Maven ERROR diagnostic") {
    val logContent = "[ERROR] /path/to/File.java:123 Test error message"
    val diagnostics = LogParser.parseLog(logContent)
    assert(diagnostics.length == 1)
    assert(diagnostics(0).file == "/path/to/File.java")
    assert(diagnostics(0).line == 123)
    assert(diagnostics(0).severity == "ERROR")
    assert(diagnostics(0).message == "Test error message")
  }

  test("parseLog: extracts Maven WARN diagnostic") {
    val logContent = "[WARN] /path/to/File.java:456 Test warning message"
    val diagnostics = LogParser.parseLog(logContent)
    assert(diagnostics.length == 1)
    assert(diagnostics(0).severity == "WARN")
    assert(diagnostics(0).line == 456)
  }

  test("parseLog: converts WARNING to WARN") {
    val logContent = "[WARNING] /path/to/File.java:100 Test message"
    val diagnostics = LogParser.parseLog(logContent)
    assert(diagnostics.length == 1)
    assert(diagnostics(0).severity == "WARN")
  }

  test("parseLog: extracts Gradle ERROR diagnostic with column") {
    val logContent = "File.kt:45:10: error: Test error message"
    val diagnostics = LogParser.parseLog(logContent)
    assert(diagnostics.length == 1)
    assert(diagnostics(0).file == "File.kt")
    assert(diagnostics(0).line == 45)
    assert(diagnostics(0).col == 10)
    assert(diagnostics(0).severity == "ERROR")
  }

  test("parseLog: extracts Gradle ERROR diagnostic without column") {
    val logContent = "File.java:78: error: Test error message"
    val diagnostics = LogParser.parseLog(logContent)
    assert(diagnostics.length == 1)
    assert(diagnostics(0).file == "File.java")
    assert(diagnostics(0).line == 78)
    assert(diagnostics(0).col == 1)
  }

  test("parseLog: handles multiple diagnostics") {
    val logContent =
      """[ERROR] /path/to/File1.java:10 Error 1
        |[WARN] /path/to/File2.java:20 Warning 1
        |Some other log line
        |[ERROR] /path/to/File3.java:30 Error 2""".stripMargin
    val diagnostics = LogParser.parseLog(logContent)
    assert(diagnostics.length == 3)
    assert(diagnostics(0).line == 10)
    assert(diagnostics(1).line == 20)
    assert(diagnostics(2).line == 30)
  }

  test("parseLog: skips non-diagnostic lines") {
    val logContent =
      """Starting build
        |[INFO] Processing files
        |[ERROR] /path/to/File.java:50 Actual error
        |[DEBUG] Some debug info""".stripMargin
    val diagnostics = LogParser.parseLog(logContent)
    assert(diagnostics.length == 1)
    assert(diagnostics(0).line == 50)
  }

  test("parseLog: handles empty log") {
    val logContent = ""
    val diagnostics = LogParser.parseLog(logContent)
    assert(diagnostics.isEmpty)
  }

  test("parseLog: handles ANSI codes in Maven format") {
    val logContent = "[1;31m[ERROR][0m /path/to/File.java:123 Colored error"
    val diagnostics = LogParser.parseLog(logContent)
    assert(diagnostics.length == 1)
    assert(diagnostics(0).severity == "ERROR")
    assert(diagnostics(0).line == 123)
  }

  test("parseFromFile: reads and parses file") {
    val tempFile = File.createTempFile("test", ".log")
    try
      val writer = new PrintWriter(tempFile)
      writer.println("[ERROR] /path/to/File.java:100 Test error")
      writer.close()

      val diagnostics = LogParser.parseFromFile(tempFile.getAbsolutePath())
      assert(diagnostics.length == 1)
      assert(diagnostics(0).line == 100)
    finally
      tempFile.delete()
  }

  test("parseFromFile: throws exception for non-existent file") {
    val exception = intercept[Exception] {
      LogParser.parseFromFile("/nonexistent/path/file.log")
    }
    assert(exception.getMessage.contains("File not found"))
  }

  test("parseFromStdin: parses stdin input") {
    val input = "[ERROR] /path/to/File.java:200 Stdin error"
    val diagnostics = LogParser.parseFromStdin(input)
    assert(diagnostics.length == 1)
    assert(diagnostics(0).line == 200)
  }

  test("parseLog: handles different line endings") {
    val logContent = "[ERROR] /path/to/File.java:10 Error1\r\n[ERROR] /path/to/File.java:20 Error2"
    val diagnostics = LogParser.parseLog(logContent)
    assert(diagnostics.length == 2)
    assert(diagnostics(0).line == 10)
    assert(diagnostics(1).line == 20)
  }
