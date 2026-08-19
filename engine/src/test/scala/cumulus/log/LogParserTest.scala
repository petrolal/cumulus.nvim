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

  // SBT tests
  test("parseLog: extracts SBT error diagnostic") {
    val logContent = "[error] File.scala:42: test error message"
    val diagnostics = LogParser.parseLog(logContent)
    assert(diagnostics.length == 1)
    assert(diagnostics(0).file == "File.scala")
    assert(diagnostics(0).line == 42)
    assert(diagnostics(0).col == 1)
    assert(diagnostics(0).severity == "ERROR")
    assert(diagnostics(0).message == "test error message")
  }

  test("parseLog: extracts SBT warn diagnostic") {
    val logContent = "[warn] Test.scala:100: test warning"
    val diagnostics = LogParser.parseLog(logContent)
    assert(diagnostics.length == 1)
    assert(diagnostics(0).severity == "WARN")
    assert(diagnostics(0).line == 100)
  }

  test("parseLog: extracts SBT with path") {
    val logContent = "[error] /home/user/project/src/Main.scala:99: undefined reference"
    val diagnostics = LogParser.parseLog(logContent)
    assert(diagnostics.length == 1)
    assert(diagnostics(0).file == "/home/user/project/src/Main.scala")
    assert(diagnostics(0).line == 99)
    assert(diagnostics(0).col == 1)
  }

  // Kotlin tests
  test("parseLog: extracts kotlinc error diagnostic with column") {
    val logContent = "Main.kt:25:15: error: type mismatch"
    val diagnostics = LogParser.parseLog(logContent)
    assert(diagnostics.length == 1)
    assert(diagnostics(0).file == "Main.kt")
    assert(diagnostics(0).line == 25)
    assert(diagnostics(0).col == 15)
    assert(diagnostics(0).severity == "ERROR")
    assert(diagnostics(0).message == "type mismatch")
  }

  test("parseLog: extracts kotlinc warning diagnostic") {
    val logContent = "app/Utils.kt:88:5: warning: unused variable"
    val diagnostics = LogParser.parseLog(logContent)
    assert(diagnostics.length == 1)
    assert(diagnostics(0).file == "app/Utils.kt")
    assert(diagnostics(0).line == 88)
    assert(diagnostics(0).col == 5)
    assert(diagnostics(0).severity == "WARN")
  }

  // Scalac tests
  test("parseLog: extracts scalac error diagnostic") {
    val logContent = "Compiler.scala:77: error: value foo is not a member of String"
    val diagnostics = LogParser.parseLog(logContent)
    assert(diagnostics.length == 1)
    assert(diagnostics(0).file == "Compiler.scala")
    assert(diagnostics(0).line == 77)
    assert(diagnostics(0).col == 1)
    assert(diagnostics(0).severity == "ERROR")
    assert(diagnostics(0).message == "value foo is not a member of String")
  }

  test("parseLog: extracts scalac warning diagnostic") {
    val logContent = "Test.scala:55: warning: pattern matching is not exhaustive"
    val diagnostics = LogParser.parseLog(logContent)
    assert(diagnostics.length == 1)
    assert(diagnostics(0).file == "Test.scala")
    assert(diagnostics(0).line == 55)
    assert(diagnostics(0).severity == "WARN")
  }

  test("parseLog: extracts scalac with path") {
    val logContent = "src/main/scala/MyClass.scala:200: error: not found: type HashMap"
    val diagnostics = LogParser.parseLog(logContent)
    assert(diagnostics.length == 1)
    assert(diagnostics(0).file == "src/main/scala/MyClass.scala")
    assert(diagnostics(0).line == 200)
  }

  // Cargo tests
  test("parseLog: extracts Cargo error with context line") {
    val logContent =
      """error[E0425]: cannot find value `x` in this scope
        | --> src/main.rs:10:5""".stripMargin
    val diagnostics = LogParser.parseLog(logContent)
    assert(diagnostics.length == 1)
    assert(diagnostics(0).file == "src/main.rs")
    assert(diagnostics(0).line == 10)
    assert(diagnostics(0).col == 5)
    assert(diagnostics(0).severity == "ERROR")
    assert(diagnostics(0).message == "cannot find value `x` in this scope")
  }

  test("parseLog: extracts multiple Cargo errors") {
    val logContent =
      """error[E0425]: cannot find value `x` in this scope
        | --> src/main.rs:10:5
        |error[E0308]: mismatched types
        | --> src/lib.rs:20:10""".stripMargin
    val diagnostics = LogParser.parseLog(logContent)
    assert(diagnostics.length == 2)
    assert(diagnostics(0).file == "src/main.rs")
    assert(diagnostics(0).line == 10)
    assert(diagnostics(1).file == "src/lib.rs")
    assert(diagnostics(1).line == 20)
  }

  // Mixed language tests
  test("parseLog: handles mixed Maven and SBT errors") {
    val logContent =
      """[ERROR] /path/to/File.java:10 Maven error
        |[error] Test.scala:20: SBT error
        |Some info line
        |[WARN] /path/to/File.java:30 Another Maven warning""".stripMargin
    val diagnostics = LogParser.parseLog(logContent)
    assert(diagnostics.length == 3)
    assert(diagnostics(0).file.contains("File.java"))
    assert(diagnostics(1).file == "Test.scala")
    assert(diagnostics(2).file.contains("File.java"))
  }

  test("parseLog: handles mixed Gradle and Kotlin errors") {
    val logContent =
      """File.java:10:5: error: test error
        |Main.kt:20:15: error: kotlin error
        |Something.scala:30: error: scala error""".stripMargin
    val diagnostics = LogParser.parseLog(logContent)
    assert(diagnostics.length == 3)
    assert(diagnostics(0).file == "File.java")
    assert(diagnostics(1).file == "Main.kt")
    assert(diagnostics(2).file == "Something.scala")
  }

  test("parseLog: handles mixed Maven, Gradle, SBT, and Cargo errors") {
    val logContent =
      """[ERROR] /path/Maven.java:5 Maven error
        |Test.scala:15: error: SBT error
        |File.java:25:10: error: Gradle error
        |error[E0001]: Cargo error
        | --> src/main.rs:35:5""".stripMargin
    val diagnostics = LogParser.parseLog(logContent)
    assert(diagnostics.length == 4)
    assert(diagnostics(0).file.contains("Maven"))
    assert(diagnostics(1).file == "Test.scala")
    assert(diagnostics(2).file == "File.java")
    assert(diagnostics(3).file == "src/main.rs")
  }

  // Edge case tests
  test("parseLog: handles ANSI codes in SBT format") {
    val logContent = "[1;31m[error][0m Test.scala:42: error with ANSI"
    val diagnostics = LogParser.parseLog(logContent)
    assert(diagnostics.length == 1)
    assert(diagnostics(0).severity == "ERROR")
    assert(diagnostics(0).line == 42)
  }

  test("parseLog: handles ANSI codes in Kotlin format") {
    val logContent = "[1;31mMain.kt:25:15: error: type mismatch[0m"
    val diagnostics = LogParser.parseLog(logContent)
    assert(diagnostics.length == 1)
    assert(diagnostics(0).file == "Main.kt")
    assert(diagnostics(0).severity == "ERROR")
  }

  test("parseLog: handles ANSI codes in Cargo format") {
    val logContent =
      """[1;31merror[E0425][0m: cannot find value
        | [1;36m-->[0m src/main.rs:10:5""".stripMargin
    val diagnostics = LogParser.parseLog(logContent)
    assert(diagnostics.length == 1)
    assert(diagnostics(0).file == "src/main.rs")
  }

  test("parseLog: handles multiline Cargo errors with extra output") {
    val logContent =
      """error[E0425]: cannot find value `x` in this scope
        | --> src/main.rs:10:5
        |  |
        |10 |    let y = x + 1;
        |   |         ^ not found in this scope
        |error[E0308]: mismatched types
        | --> src/lib.rs:20:10""".stripMargin
    val diagnostics = LogParser.parseLog(logContent)
    // Should match first error, skip intervening context, then match second error
    assert(diagnostics.length == 2)
    assert(diagnostics(0).file == "src/main.rs")
    assert(diagnostics(0).line == 10)
    assert(diagnostics(1).file == "src/lib.rs")
    assert(diagnostics(1).line == 20)
  }

  test("parseLog: returns empty sequence for empty file") {
    val logContent = ""
    val diagnostics = LogParser.parseLog(logContent)
    assert(diagnostics.isEmpty)
  }

  test("parseLog: skips lines without any error patterns") {
    val logContent =
      """BUILD STARTED
        |Processing dependencies
        |Compiling sources
        |All done!""".stripMargin
    val diagnostics = LogParser.parseLog(logContent)
    assert(diagnostics.isEmpty)
  }

  test("parseLog: handles missing file paths gracefully") {
    val logContent = "error: some error without file path"
    val diagnostics = LogParser.parseLog(logContent)
    // Should not crash, should return empty or skip the line
    assert(diagnostics.isEmpty)
  }
