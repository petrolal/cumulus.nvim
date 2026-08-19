package cumulus.keymap

import munit.FunSuite

class GateParserTest extends FunSuite:

  test("parse simple equality expression") {
    val result = GateParser.parse("filetype=java")
    assertEquals(result, Right(EqualityExpr("filetype", "java")))
  }

  test("parse bare variable as boolean check") {
    val result = GateParser.parse("buftype")
    assertEquals(result, Right(EqualityExpr("buftype", "true")))
  }

  test("parse NOT expression with bare variable") {
    val result = GateParser.parse("NOT debug_mode")
    assertEquals(result, Right(NotExpr(EqualityExpr("debug_mode", "true"))))
  }

  test("parse NOT with equality") {
    val result = GateParser.parse("NOT lsp_active=false")
    assertEquals(result, Right(NotExpr(EqualityExpr("lsp_active", "false"))))
  }

  test("parse simple AND expression") {
    val result = GateParser.parse("filetype=java AND project_type=maven")
    assertEquals(result, Right(AndExpr(
      EqualityExpr("filetype", "java"),
      EqualityExpr("project_type", "maven")
    )))
  }

  test("parse simple OR expression") {
    val result = GateParser.parse("mode=normal OR mode=insert")
    assertEquals(result, Right(OrExpr(
      EqualityExpr("mode", "normal"),
      EqualityExpr("mode", "insert")
    )))
  }

  test("parse complex expression with AND and OR") {
    val result = GateParser.parse("a=x AND b=y OR c=z")
    // Should parse as (a AND b) OR c due to AND precedence
    assertEquals(result, Right(OrExpr(
      AndExpr(
        EqualityExpr("a", "x"),
        EqualityExpr("b", "y")
      ),
      EqualityExpr("c", "z")
    )))
  }

  test("parse multiple AND expressions") {
    val result = GateParser.parse("a=x AND b=y AND c=z")
    // Should parse left-to-right: ((a AND b) AND c)
    assertEquals(result, Right(AndExpr(
      AndExpr(
        EqualityExpr("a", "x"),
        EqualityExpr("b", "y")
      ),
      EqualityExpr("c", "z")
    )))
  }

  test("parse multiple OR expressions") {
    val result = GateParser.parse("a=x OR b=y OR c=z")
    // Should parse left-to-right: ((a OR b) OR c)
    assertEquals(result, Right(OrExpr(
      OrExpr(
        EqualityExpr("a", "x"),
        EqualityExpr("b", "y")
      ),
      EqualityExpr("c", "z")
    )))
  }

  test("parse double NOT") {
    val result = GateParser.parse("NOT NOT a=x")
    assertEquals(result, Right(NotExpr(NotExpr(EqualityExpr("a", "x")))))
  }

  test("parse NOT with AND") {
    val result = GateParser.parse("NOT a=x AND b=y")
    // NOT has higher precedence than AND, so: (NOT a=x) AND (b=y)
    assertEquals(result, Right(AndExpr(
      NotExpr(EqualityExpr("a", "x")),
      EqualityExpr("b", "y")
    )))
  }

  test("parse NOT with OR") {
    val result = GateParser.parse("NOT a=x OR b=y")
    // NOT has higher precedence than OR, so: (NOT a=x) OR (b=y)
    assertEquals(result, Right(OrExpr(
      NotExpr(EqualityExpr("a", "x")),
      EqualityExpr("b", "y")
    )))
  }

  test("reject malformed expression - double equals") {
    val result = GateParser.parse("filetype==java")
    assert(result.isLeft)
  }

  test("parse empty value after equals") {
    val result = GateParser.parse("filetype=")
    // Empty value after = should be allowed
    assertEquals(result, Right(EqualityExpr("filetype", "")))
  }

  test("reject malformed expression - missing equals") {
    val result = GateParser.parse("filetype java")
    assert(result.isLeft)
  }

  test("reject empty expression") {
    val result = GateParser.parse("")
    assert(result.isLeft)
  }

  test("reject whitespace-only expression") {
    val result = GateParser.parse("   ")
    assert(result.isLeft)
  }

  test("reject invalid token at start") {
    val result = GateParser.parse("=")
    assert(result.isLeft)
  }

  test("handle whitespace correctly") {
    val result = GateParser.parse("  filetype  =  java  AND  project_type  =  maven  ")
    assertEquals(result, Right(AndExpr(
      EqualityExpr("filetype", "java"),
      EqualityExpr("project_type", "maven")
    )))
  }

  test("parse variable names with underscores") {
    val result = GateParser.parse("project_type=maven AND lsp_active=true")
    assertEquals(result, Right(AndExpr(
      EqualityExpr("project_type", "maven"),
      EqualityExpr("lsp_active", "true")
    )))
  }

  test("parse value with underscores") {
    val result = GateParser.parse("project_type=my_project_type")
    assertEquals(result, Right(EqualityExpr("project_type", "my_project_type")))
  }

  test("parse unknown operator as variable name") {
    // XOR is parsed as a bare variable (boolean check), not an operator
    // So "a=x XOR b=y" parses as: a=x, then tries to parse OR-operator level,
    // but sees VariableToken("XOR") which doesn't match the pattern
    val result = GateParser.parse("a=x XOR b=y")
    // This should fail because after "a=x", the parser is at the OR/AND level
    // and sees "XOR" which is not a known operator
    assert(result.isLeft)
  }

  test("reject trailing operator") {
    val result = GateParser.parse("a=x AND")
    assert(result.isLeft)
  }

  test("reject leading AND/OR") {
    val result = GateParser.parse("AND a=x")
    assert(result.isLeft)
  }

  test("parse case-sensitive keywords") {
    val result = GateParser.parse("not=value")
    // "not" as a variable name (not "NOT" keyword)
    assertEquals(result, Right(EqualityExpr("not", "value")))
  }

  test("parse complex nested expression") {
    val result = GateParser.parse("NOT a=x AND b=y OR NOT c=z")
    // Precedence: NOT > AND > OR
    // (NOT a=x) AND b=y OR (NOT c=z)
    // ((NOT a=x) AND b=y) OR (NOT c=z)
    assertEquals(result, Right(OrExpr(
      AndExpr(
        NotExpr(EqualityExpr("a", "x")),
        EqualityExpr("b", "y")
      ),
      NotExpr(EqualityExpr("c", "z"))
    )))
  }

  test("handle numeric values") {
    val result = GateParser.parse("count=42")
    assertEquals(result, Right(EqualityExpr("count", "42")))
  }

  test("handle boolean-like values") {
    val result = GateParser.parse("lsp_active=true")
    assertEquals(result, Right(EqualityExpr("lsp_active", "true")))
  }

  test("parse all supported variables") {
    val vars = Seq("buftype", "filetype", "mode", "project_type", "lsp_active", "debug_mode", "test_context")
    vars.foreach { varName =>
      val result = GateParser.parse(s"$varName=test")
      assertEquals(result, Right(EqualityExpr(varName, "test")))
    }
  }
