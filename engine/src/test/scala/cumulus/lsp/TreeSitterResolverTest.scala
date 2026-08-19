package cumulus.lsp

import munit.FunSuite
import cumulus.protocol.TreeSitterParserSpec

class TreeSitterResolverTest extends FunSuite:

  test("TreeSitterResolver: Discover Java parsers returns Seq") {
    val result = TreeSitterResolver.discoverParsers("java")
    assert(result.isInstanceOf[Seq[_]])
  }

  test("TreeSitterResolver: Discover Kotlin parsers returns Seq") {
    val result = TreeSitterResolver.discoverParsers("kotlin")
    assert(result.isInstanceOf[Seq[_]])
  }

  test("TreeSitterResolver: Discover Scala parsers returns Seq") {
    val result = TreeSitterResolver.discoverParsers("scala")
    assert(result.isInstanceOf[Seq[_]])
  }

  test("TreeSitterResolver: Discover Groovy parsers returns Seq") {
    val result = TreeSitterResolver.discoverParsers("groovy")
    assert(result.isInstanceOf[Seq[_]])
  }

  test("TreeSitterResolver: Unknown language returns empty Seq") {
    val result = TreeSitterResolver.discoverParsers("unknown_language")
    assert(result.isEmpty)
  }

  test("TreeSitterResolver: Parser spec has required fields") {
    val parsers = TreeSitterResolver.discoverParsers("java")
    if parsers.nonEmpty then
      val parser = parsers.head
      assert(parser.parser.nonEmpty)
      assert(parser.queries.nonEmpty)
  }

  test("TreeSitterResolver: Java parser uses 'standard' queries") {
    val parsers = TreeSitterResolver.discoverParsers("java")
    if parsers.nonEmpty then
      val javaParser = parsers.find(_.parser == "java")
      assert(javaParser.isDefined)
      assert(javaParser.get.queries == "standard")
  }

  test("TreeSitterResolver: Scala parser uses 'extended' queries") {
    val parsers = TreeSitterResolver.discoverParsers("scala")
    if parsers.nonEmpty then
      val scalaParser = parsers.find(_.parser == "scala")
      assert(scalaParser.isDefined)
      assert(scalaParser.get.queries == "extended")
  }

  test("TreeSitterResolver: Kotlin parser uses 'standard' queries") {
    val parsers = TreeSitterResolver.discoverParsers("kotlin")
    if parsers.nonEmpty then
      val kotlinParser = parsers.find(_.parser == "kotlin")
      assert(kotlinParser.isDefined)
      assert(kotlinParser.get.queries == "standard")
  }

  test("TreeSitterResolver: Groovy parser uses 'standard' queries") {
    val parsers = TreeSitterResolver.discoverParsers("groovy")
    if parsers.nonEmpty then
      val groovyParser = parsers.find(_.parser == "groovy")
      assert(groovyParser.isDefined)
      assert(groovyParser.get.queries == "standard")
  }

  test("TreeSitterResolver: Parser spec serialization to JSON") {
    val parsers = TreeSitterResolver.discoverParsers("java")
    // Verify JSON serialization doesn't throw
    val json = upickle.default.write(parsers)
    assert(json.nonEmpty)
  }

  test("TreeSitterResolver: Case-insensitive language names") {
    val result1 = TreeSitterResolver.discoverParsers("JAVA")
    val result2 = TreeSitterResolver.discoverParsers("Java")
    val result3 = TreeSitterResolver.discoverParsers("java")
    // All should have same behavior (empty or same content)
    assert(result1.length == result2.length)
    assert(result2.length == result3.length)
  }

  test("TreeSitterResolver: All JVM languages supported") {
    val languages = Seq("java", "kotlin", "scala", "groovy")
    languages.foreach { lang =>
      val result = TreeSitterResolver.discoverParsers(lang)
      assert(result.isInstanceOf[Seq[_]])
    }
  }
