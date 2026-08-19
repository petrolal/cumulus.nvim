package cumulus.lsp

import munit.FunSuite
import cumulus.protocol.LspCapability

class LspCapabilityDetectorTest extends FunSuite:

  test("LspCapabilityDetector: Java JDTLS returns capabilities") {
    val capabilities = LspCapabilityDetector.detectCapabilities("jdtls", "java")
    assert(capabilities.nonEmpty)
    assert(capabilities.map(_.name).contains("textDocument/completion"))
  }

  test("LspCapabilityDetector: Java JDTLS includes all standard capabilities") {
    val capabilities = LspCapabilityDetector.detectCapabilities("jdtls", "java")
    val capNames = capabilities.map(_.name)
    assert(capNames.contains("textDocument/completion"))
    assert(capNames.contains("textDocument/hover"))
    assert(capNames.contains("textDocument/definition"))
    assert(capNames.contains("textDocument/references"))
    assert(capNames.contains("textDocument/rename"))
  }

  test("LspCapabilityDetector: Kotlin language-server returns capabilities") {
    val capabilities = LspCapabilityDetector.detectCapabilities("kotlin-language-server", "kotlin")
    assert(capabilities.nonEmpty)
    assert(capabilities.map(_.name).contains("textDocument/completion"))
  }

  test("LspCapabilityDetector: Scala Metals returns capabilities") {
    val capabilities = LspCapabilityDetector.detectCapabilities("metals", "scala")
    assert(capabilities.nonEmpty)
    assert(capabilities.map(_.name).contains("textDocument/completion"))
  }

  test("LspCapabilityDetector: Scala Metals includes Scala-specific capabilities") {
    val capabilities = LspCapabilityDetector.detectCapabilities("metals", "scala")
    val capNames = capabilities.map(_.name)
    assert(capNames.contains("scala/superMethodHierarchy"))
    assert(capNames.contains("scala/worksheetPublishOutput"))
  }

  test("LspCapabilityDetector: Groovy LSP returns capabilities") {
    val capabilities = LspCapabilityDetector.detectCapabilities("groovy-lsp", "groovy")
    assert(capabilities.nonEmpty)
    assert(capabilities.map(_.name).contains("textDocument/completion"))
  }

  test("LspCapabilityDetector: Unknown language returns empty") {
    val capabilities = LspCapabilityDetector.detectCapabilities("unknown-lsp", "unknown")
    assert(capabilities.isEmpty)
  }

  test("LspCapabilityDetector: Unknown LSP for known language returns empty") {
    val capabilities = LspCapabilityDetector.detectCapabilities("unknown-lsp", "java")
    assert(capabilities.isEmpty)
  }

  test("LspCapabilityDetector: All capabilities have supported=true") {
    val capabilities = LspCapabilityDetector.detectCapabilities("jdtls", "java")
    assert(capabilities.forall(_.supported == true))
  }

  test("LspCapabilityDetector: Capability serialization to JSON") {
    val capabilities = LspCapabilityDetector.detectCapabilities("jdtls", "java")
    val json = upickle.default.write(capabilities)
    assert(json.nonEmpty)
    assert(json.contains("textDocument/completion"))
  }

  test("LspCapabilityDetector: Case-insensitive LSP name") {
    val cap1 = LspCapabilityDetector.detectCapabilities("JDTLS", "java")
    val cap2 = LspCapabilityDetector.detectCapabilities("jdtls", "java")
    val cap3 = LspCapabilityDetector.detectCapabilities("JdtLs", "java")
    assert(cap1.length == cap2.length)
    assert(cap2.length == cap3.length)
  }

  test("LspCapabilityDetector: Case-insensitive language name") {
    val cap1 = LspCapabilityDetector.detectCapabilities("jdtls", "JAVA")
    val cap2 = LspCapabilityDetector.detectCapabilities("jdtls", "Java")
    val cap3 = LspCapabilityDetector.detectCapabilities("jdtls", "java")
    assert(cap1.length == cap2.length)
    assert(cap2.length == cap3.length)
  }

  test("LspCapabilityDetector: Java has 15+ capabilities for JDTLS") {
    val capabilities = LspCapabilityDetector.detectCapabilities("jdtls", "java")
    assert(capabilities.length >= 15)
  }

  test("LspCapabilityDetector: Scala has 15+ capabilities for Metals") {
    val capabilities = LspCapabilityDetector.detectCapabilities("metals", "scala")
    assert(capabilities.length >= 15)
  }
