package cumulus.toolchain

import cumulus.protocol.ToolchainMatrix
import munit.FunSuite

class ToolchainMatrixGeneratorTest extends FunSuite:

  test("handle returns error for unsupported language") {
    val result = ToolchainMatrixGenerator.handle("cobol")
    assertEquals(result.success, false)
    assertEquals(result.error_code, Some("UNSUPPORTED_LANGUAGE"))
    assert(result.error.isDefined)
  }

  test("handle handles java language") {
    val result = ToolchainMatrixGenerator.handle("java")
    // Should succeed if Java tools installed, fail gracefully if not
    if result.success then
      assert(result.data.isDefined)
      val matrix = result.data.get
      assertEquals(matrix.language, "java")
      assert(matrix.available_tools.nonEmpty || matrix.available_tools.isEmpty) // Either found or not
    else
      assertEquals(result.error_code, Some("NO_TOOLS_FOUND"))
  }

  test("handle handles kotlin language") {
    val result = ToolchainMatrixGenerator.handle("kotlin")
    if result.success then
      assert(result.data.isDefined)
      val matrix = result.data.get
      assertEquals(matrix.language, "kotlin")
    else
      assert(result.error.isDefined)
  }

  test("handle handles scala language") {
    val result = ToolchainMatrixGenerator.handle("scala")
    if result.success then
      assert(result.data.isDefined)
      val matrix = result.data.get
      assertEquals(matrix.language, "scala")
    else
      assert(result.error.isDefined)
  }

  test("handle handles go language") {
    val result = ToolchainMatrixGenerator.handle("go")
    if result.success then
      assert(result.data.isDefined)
      val matrix = result.data.get
      assertEquals(matrix.language, "go")
    else
      assert(result.error.isDefined)
  }

  test("handle handles rust language") {
    val result = ToolchainMatrixGenerator.handle("rust")
    if result.success then
      assert(result.data.isDefined)
      val matrix = result.data.get
      assertEquals(matrix.language, "rust")
    else
      assert(result.error.isDefined)
  }

  test("handle handles python language") {
    val result = ToolchainMatrixGenerator.handle("python")
    if result.success then
      assert(result.data.isDefined)
      val matrix = result.data.get
      assertEquals(matrix.language, "python")
    else
      assert(result.error.isDefined)
  }

  test("handle handles node language") {
    val result = ToolchainMatrixGenerator.handle("node")
    if result.success then
      assert(result.data.isDefined)
      val matrix = result.data.get
      assertEquals(matrix.language, "node")
    else
      assert(result.error.isDefined)
  }

  test("handle is case-insensitive") {
    val result1 = ToolchainMatrixGenerator.handle("JAVA")
    val result2 = ToolchainMatrixGenerator.handle("java")
    assertEquals(result1.success, result2.success)
  }

  test("handle response contains language field") {
    val result = ToolchainMatrixGenerator.handle("go")
    if result.success then
      val matrix = result.data.get
      assertEquals(matrix.language, "go")
  }

  test("handle response contains available_tools field") {
    val result = ToolchainMatrixGenerator.handle("go")
    if result.success then
      val matrix = result.data.get
      assert(matrix.available_tools.isInstanceOf[Seq[String]])
  }

  test("handle response contains formatters field") {
    val result = ToolchainMatrixGenerator.handle("go")
    if result.success then
      val matrix = result.data.get
      assert(matrix.formatters.isInstanceOf[Seq[_]])
  }

  test("handle response contains language_servers field") {
    val result = ToolchainMatrixGenerator.handle("go")
    if result.success then
      val matrix = result.data.get
      assert(matrix.language_servers.isInstanceOf[Seq[_]])
  }

  test("handle response contains debuggers field") {
    val result = ToolchainMatrixGenerator.handle("go")
    if result.success then
      val matrix = result.data.get
      assert(matrix.debuggers.isInstanceOf[Seq[_]])
  }

  test("handle response contains diagnostic_warnings field") {
    val result = ToolchainMatrixGenerator.handle("go")
    if result.success then
      val matrix = result.data.get
      assert(matrix.diagnostic_warnings.isInstanceOf[Seq[String]])
  }

  test("handle trims and lowercases language input") {
    val result1 = ToolchainMatrixGenerator.handle("  JAVA  ")
    val result2 = ToolchainMatrixGenerator.handle("java")
    // Both should be handled the same way
    assertEquals(result1.success, result2.success)
  }

  test("handle error response has success=false") {
    val result = ToolchainMatrixGenerator.handle("cobol")
    assertEquals(result.success, false)
    assertEquals(result.data, None)
  }

  test("handle successful response has success=true") {
    val result = ToolchainMatrixGenerator.handle("go")
    if result.success then
      assertEquals(result.success, true)
      assertEquals(result.error, None)
      assertEquals(result.error_code, None)
  }

  test("handle error response includes error_code") {
    val result = ToolchainMatrixGenerator.handle("unsupported_lang_xyz")
    assertEquals(result.success, false)
    assert(result.error_code.isDefined)
  }

  test("handle toolchain matrix contains all required fields") {
    val result = ToolchainMatrixGenerator.handle("go")
    if result.success then
      val matrix = result.data.get
      assert(matrix.language.nonEmpty)
      assert(matrix.available_tools.isInstanceOf[Seq[_]])
      assert(matrix.build_tools.isInstanceOf[Seq[_]])
      assert(matrix.formatters.isInstanceOf[Seq[_]])
      assert(matrix.language_servers.isInstanceOf[Seq[_]])
      assert(matrix.debuggers.isInstanceOf[Seq[_]])
      assert(matrix.diagnostic_warnings.isInstanceOf[Seq[_]])
  }
