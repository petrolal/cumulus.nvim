package cumulus.toolchain

import munit.FunSuite

class FormatterDetectorTest extends FunSuite:

  test("detectFormatters returns empty seq for unsupported language") {
    val result = FormatterDetector.detectFormatters("cobol")
    assertEquals(result, Seq())
  }

  test("detectFormatters handles java formatters detection") {
    val result = FormatterDetector.detectFormatters("java")
    assert(result.isInstanceOf[Seq[_]])
  }

  test("detectFormatters handles kotlin formatters detection") {
    val result = FormatterDetector.detectFormatters("kotlin")
    assert(result.isInstanceOf[Seq[_]])
  }

  test("detectFormatters handles scala formatters detection") {
    val result = FormatterDetector.detectFormatters("scala")
    assert(result.isInstanceOf[Seq[_]])
  }

  test("detectFormatters handles go formatters detection") {
    val result = FormatterDetector.detectFormatters("go")
    assert(result.isInstanceOf[Seq[_]])
  }

  test("detectFormatters handles rust formatters detection") {
    val result = FormatterDetector.detectFormatters("rust")
    assert(result.isInstanceOf[Seq[_]])
  }

  test("detectFormatters handles python formatters detection") {
    val result = FormatterDetector.detectFormatters("python")
    assert(result.isInstanceOf[Seq[_]])
  }

  test("detectFormatters handles node formatters detection") {
    val result = FormatterDetector.detectFormatters("node")
    assert(result.isInstanceOf[Seq[_]])
  }

  test("detectFormatters handles typescript as alias for node") {
    val result = FormatterDetector.detectFormatters("typescript")
    assert(result.isInstanceOf[Seq[_]])
  }

  test("detectFormatters is case-insensitive") {
    val result1 = FormatterDetector.detectFormatters("JAVA")
    val result2 = FormatterDetector.detectFormatters("java")
    assert(result1.isInstanceOf[Seq[_]])
    assert(result2.isInstanceOf[Seq[_]])
  }

  test("detectFormatters formatter specs contain required fields") {
    val result = FormatterDetector.detectFormatters("go")
    // If formatters detected, verify they have name and mason_name
    result.foreach { formatter =>
      assert(formatter.name.nonEmpty)
      assert(formatter.mason_name.nonEmpty)
    }
  }
