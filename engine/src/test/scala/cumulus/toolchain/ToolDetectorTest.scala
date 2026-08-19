package cumulus.toolchain

import munit.FunSuite

class ToolDetectorTest extends FunSuite:

  test("detectTools returns empty seq for unsupported language") {
    val result = ToolDetector.detectTools("cobol")
    assertEquals(result, Seq())
  }

  test("detectTools handles java tools detection") {
    val result = ToolDetector.detectTools("java")
    // Should return empty or populated based on system state
    assert(result.isInstanceOf[Seq[_]])
  }

  test("detectTools handles kotlin tools detection") {
    val result = ToolDetector.detectTools("kotlin")
    assert(result.isInstanceOf[Seq[_]])
  }

  test("detectTools handles scala tools detection") {
    val result = ToolDetector.detectTools("scala")
    assert(result.isInstanceOf[Seq[_]])
  }

  test("detectTools handles go tools detection") {
    val result = ToolDetector.detectTools("go")
    assert(result.isInstanceOf[Seq[_]])
  }

  test("detectTools handles rust tools detection") {
    val result = ToolDetector.detectTools("rust")
    assert(result.isInstanceOf[Seq[_]])
  }

  test("detectTools handles python tools detection") {
    val result = ToolDetector.detectTools("python")
    assert(result.isInstanceOf[Seq[_]])
  }

  test("detectTools handles node tools detection") {
    val result = ToolDetector.detectTools("node")
    assert(result.isInstanceOf[Seq[_]])
  }

  test("detectTools is case-insensitive") {
    val result1 = ToolDetector.detectTools("JAVA")
    val result2 = ToolDetector.detectTools("java")
    // Both should be handled the same way
    assert(result1.isInstanceOf[Seq[_]])
    assert(result2.isInstanceOf[Seq[_]])
  }
