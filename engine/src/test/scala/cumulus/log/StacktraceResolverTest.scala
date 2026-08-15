package cumulus.log

import munit.FunSuite
import java.io.{File, PrintWriter}
import scala.util.Try

class StacktraceResolverTest extends FunSuite:

  test("parseStackFrame: parses simple frame") {
    val frameStr = "at com.example.Service.method(Service.java:123)"
    val result = StacktraceResolver.parseStackFrame(frameStr)
    assert(result.isDefined)
    assert(result.get.className == "com.example.Service")
    assert(result.get.methodName == "method")
    assert(result.get.file == "Service.java")
    assert(result.get.line == 123)
  }

  test("parseStackFrame: parses frame with inner class") {
    val frameStr = "at com.example.Outer$Inner.method(Inner.java:50)"
    val result = StacktraceResolver.parseStackFrame(frameStr)
    assert(result.isDefined)
    assert(result.get.className == "com.example.Outer$Inner")
    assert(result.get.methodName == "method")
    assert(result.get.file == "Inner.java")
    assert(result.get.line == 50)
  }

  test("parseStackFrame: parses deeply nested class") {
    val frameStr = "at com.example.package.subpkg.MyClass.testMethod(MyClass.java:999)"
    val result = StacktraceResolver.parseStackFrame(frameStr)
    assert(result.isDefined)
    assert(result.get.className == "com.example.package.subpkg.MyClass")
    assert(result.get.methodName == "testMethod")
  }

  test("parseStackFrame: returns None for invalid format") {
    val frameStr = "invalid frame format"
    val result = StacktraceResolver.parseStackFrame(frameStr)
    assert(result.isEmpty)
  }

  test("parseStackFrame: handles frame with 'at' prefix") {
    val frameStr = "  at java.lang.Exception.fillInStackTrace(Exception.java:500)"
    val result = StacktraceResolver.parseStackFrame(frameStr)
    assert(result.isDefined)
    assert(result.get.className == "java.lang.Exception")
  }

  test("resolveStackFrame: returns error for non-existent workspace") {
    val frame = StackFrame(
      className = "com.example.Service",
      methodName = "method",
      file = "Service.java",
      line = 123
    )
    val result = StacktraceResolver.resolveStackFrame(frame, "/nonexistent/workspace")
    assert(result.isLeft)
    assert(result.left.get.contains("not found"))
  }

  test("resolveStacktrace: parses multiple frames") {
    val stacktrace =
      """at com.example.Service.method1(Service.java:100)
        |at com.example.Controller.method2(Controller.java:200)
        |at java.lang.Thread.run(Thread.java:745)""".stripMargin
    val result = StacktraceResolver.resolveStacktrace(stacktrace, "/nonexistent")
    assert(result.isLeft)
    // Should still parse frames but fail to resolve
  }

  test("resolveStackFrame: strips inner class from file path") {
    // Create temporary workspace structure
    val tempDir = new File(System.getProperty("java.io.tmpdir"), "test-workspace-inner")
    tempDir.mkdirs()
    val srcMain = new File(tempDir, "src/main/java/com/example")
    srcMain.mkdirs()

    try
      // Create a Java file for the outer class
      val file = new File(srcMain, "Outer.java")
      file.createNewFile()

      val frame = StackFrame(
        className = "com.example.Outer$Inner",
        methodName = "method",
        file = "Outer.java",
        line = 50
      )
      val result = StacktraceResolver.resolveStackFrame(frame, tempDir.getAbsolutePath())
      assert(result.isRight)
      assert(result.right.get.contains("Outer.java"))
    finally
      // Cleanup
      recursiveDelete(tempDir)
  }

  test("resolveStackFrame: looks in multiple source directories") {
    val tempDir = new File(System.getProperty("java.io.tmpdir"), "test-workspace-multi")
    tempDir.mkdirs()

    try
      // Create multiple source directories
      val srcMain = new File(tempDir, "src/main/java/com/example")
      val srcTest = new File(tempDir, "src/test/java/com/example")
      srcMain.mkdirs()
      srcTest.mkdirs()

      // Create test file in test directory
      val testFile = new File(srcTest, "TestClass.java")
      testFile.createNewFile()

      val frame = StackFrame(
        className = "com.example.TestClass",
        methodName = "testMethod",
        file = "TestClass.java",
        line = 100
      )
      val result = StacktraceResolver.resolveStackFrame(frame, tempDir.getAbsolutePath())
      assert(result.isRight)
      assert(result.right.get.contains("TestClass.java"))
    finally
      // Cleanup
      recursiveDelete(tempDir)
  }

  test("resolveStackFrame: tries .kt extension for Kotlin files") {
    val tempDir = new File(System.getProperty("java.io.tmpdir"), "test-workspace-kotlin")
    tempDir.mkdirs()

    try
      val srcMain = new File(tempDir, "src/main/kotlin/com/example")
      srcMain.mkdirs()
      val kotlinFile = new File(srcMain, "Service.kt")
      kotlinFile.createNewFile()

      val frame = StackFrame(
        className = "com.example.Service",
        methodName = "method",
        file = "Service.kt",
        line = 123
      )
      val result = StacktraceResolver.resolveStackFrame(frame, tempDir.getAbsolutePath())
      assert(result.isRight)
      assert(result.right.get.contains("Service.kt"))
    finally
      // Cleanup
      recursiveDelete(tempDir)
  }

  private def recursiveDelete(file: File): Unit =
    if file.isDirectory then
      file.listFiles().foreach(f => recursiveDelete(f))
    file.delete()
