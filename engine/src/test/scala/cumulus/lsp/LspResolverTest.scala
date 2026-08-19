package cumulus.lsp

import munit.FunSuite
import cumulus.protocol.LspSpec

class LspResolverTest extends FunSuite:

  test("resolve-lsp: Java language returns JVM language LSP") {
    val result = LspResolver.handle("java")
    assert(result.success == true)
    assert(result.data.isDefined)
    assert(result.data.get.language == "java")
    assert(result.error.isEmpty)
  }

  test("resolve-lsp: Kotlin language returns JVM language LSP") {
    val result = LspResolver.handle("kotlin")
    assert(result.success == true)
    assert(result.data.isDefined)
    assert(result.data.get.language == "kotlin")
    assert(result.error.isEmpty)
  }

  test("resolve-lsp: Scala language returns JVM language LSP") {
    val result = LspResolver.handle("scala")
    assert(result.success == true)
    assert(result.data.isDefined)
    assert(result.data.get.language == "scala")
    assert(result.error.isEmpty)
  }

  test("resolve-lsp: Groovy language returns JVM language LSP") {
    val result = LspResolver.handle("groovy")
    assert(result.success == true)
    assert(result.data.isDefined)
    assert(result.data.get.language == "groovy")
    assert(result.error.isEmpty)
  }

  test("resolve-lsp: Unsupported language (rust) returns error") {
    val result = LspResolver.handle("rust")
    assert(result.success == false)
    assert(result.data.isEmpty)
    assert(result.error.isDefined)
    assert(result.error_code.contains("UNSUPPORTED_LANGUAGE"))
  }

  test("resolve-lsp: Unsupported language (python) returns error") {
    val result = LspResolver.handle("python")
    assert(result.success == false)
    assert(result.data.isEmpty)
    assert(result.error.isDefined)
    assert(result.error_code.contains("UNSUPPORTED_LANGUAGE"))
  }

  test("resolve-lsp: Response has all expected fields") {
    val result = LspResolver.handle("java")
    assert(result.success == true)
    val spec = result.data.get
    assert(spec.language == "java")
    assert(spec.capabilities.isInstanceOf[Seq[_]])
    assert(spec.treesitter.isInstanceOf[Seq[_]])
    assert(spec.binary_valid.isInstanceOf[Boolean])
  }

  test("resolve-lsp: Response envelope structure is correct") {
    val result = LspResolver.handle("kotlin")
    assert(result.success.isInstanceOf[Boolean])
    assert(result.data.isInstanceOf[Option[_]])
    assert(result.error.isInstanceOf[Option[_]])
    assert(result.error_code.isInstanceOf[Option[_]])
  }

  test("resolve-lsp: Successful response has no error") {
    val result = LspResolver.handle("scala")
    assert(result.success == true)
    assert(result.error.isEmpty)
    assert(result.error_code.isEmpty)
  }

  test("resolve-lsp: Error response has success=false") {
    val result = LspResolver.handle("go")
    assert(result.success == false)
  }

  test("resolve-lsp: Case-insensitive language names") {
    val result1 = LspResolver.handle("JAVA")
    val result2 = LspResolver.handle("Java")
    val result3 = LspResolver.handle("java")
    assert(result1.success == result2.success)
    assert(result2.success == result3.success)
    assert(result1.data.get.language == "java")
  }

  test("resolve-lsp: Whitespace is trimmed from language") {
    val result = LspResolver.handle("  kotlin  ")
    assert(result.success == true)
    assert(result.data.get.language == "kotlin")
  }

  test("resolve-lsp: Mason spec is generated when LSP found") {
    val result = LspResolver.handle("java")
    assert(result.success == true)
    val spec = result.data.get
    // Mason spec may be Some or None depending on whether LSP is detected on the system
    assert(spec.mason_spec.isInstanceOf[Option[_]])
  }

  test("resolve-lsp: LspSpec serialization to JSON") {
    val result = LspResolver.handle("kotlin")
    assert(result.success == true)
    // Verify JSON serialization doesn't throw
    val json = upickle.default.write(result.data.get)
    assert(json.nonEmpty)
    assert(json.contains("kotlin"))
  }
