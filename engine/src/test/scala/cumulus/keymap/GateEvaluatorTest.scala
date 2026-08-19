package cumulus.keymap

import munit.FunSuite

class GateEvaluatorTest extends FunSuite:

  // Simple equality tests
  test("evaluate simple equality - match") {
    val context = Map("buftype" -> "")
    val expr = EqualityExpr("buftype", "")
    val result = GateEvaluator.evaluate(expr, context)
    assertEquals(result, Right(true))
  }

  test("evaluate simple equality - no match") {
    val context = Map("filetype" -> "python")
    val expr = EqualityExpr("filetype", "java")
    val result = GateEvaluator.evaluate(expr, context)
    assertEquals(result, Right(false))
  }

  test("evaluate simple equality - match") {
    val context = Map("filetype" -> "java")
    val expr = EqualityExpr("filetype", "java")
    val result = GateEvaluator.evaluate(expr, context)
    assertEquals(result, Right(true))
  }

  test("evaluate simple equality with numbers") {
    val context = Map("count" -> "42")
    val expr = EqualityExpr("count", "42")
    val result = GateEvaluator.evaluate(expr, context)
    assertEquals(result, Right(true))
  }

  // Boolean AND tests
  test("evaluate AND - both true") {
    val context = Map("a" -> "true", "b" -> "true")
    val expr = AndExpr(EqualityExpr("a", "true"), EqualityExpr("b", "true"))
    val result = GateEvaluator.evaluate(expr, context)
    assertEquals(result, Right(true))
  }

  test("evaluate AND - left true, right false") {
    val context = Map("a" -> "true", "b" -> "false")
    val expr = AndExpr(EqualityExpr("a", "true"), EqualityExpr("b", "true"))
    val result = GateEvaluator.evaluate(expr, context)
    assertEquals(result, Right(false))
  }

  test("evaluate AND - left false, right true") {
    val context = Map("a" -> "false", "b" -> "true")
    val expr = AndExpr(EqualityExpr("a", "true"), EqualityExpr("b", "true"))
    val result = GateEvaluator.evaluate(expr, context)
    assertEquals(result, Right(false))
  }

  test("evaluate AND - both false") {
    val context = Map("a" -> "false", "b" -> "false")
    val expr = AndExpr(EqualityExpr("a", "true"), EqualityExpr("b", "true"))
    val result = GateEvaluator.evaluate(expr, context)
    assertEquals(result, Right(false))
  }

  // Boolean OR tests
  test("evaluate OR - both true") {
    val context = Map("a" -> "true", "b" -> "true")
    val expr = OrExpr(EqualityExpr("a", "true"), EqualityExpr("b", "true"))
    val result = GateEvaluator.evaluate(expr, context)
    assertEquals(result, Right(true))
  }

  test("evaluate OR - left true, right false") {
    val context = Map("a" -> "true", "b" -> "false")
    val expr = OrExpr(EqualityExpr("a", "true"), EqualityExpr("b", "true"))
    val result = GateEvaluator.evaluate(expr, context)
    assertEquals(result, Right(true))
  }

  test("evaluate OR - left false, right true") {
    val context = Map("a" -> "false", "b" -> "true")
    val expr = OrExpr(EqualityExpr("a", "true"), EqualityExpr("b", "true"))
    val result = GateEvaluator.evaluate(expr, context)
    assertEquals(result, Right(true))
  }

  test("evaluate OR - both false") {
    val context = Map("a" -> "false", "b" -> "false")
    val expr = OrExpr(EqualityExpr("a", "true"), EqualityExpr("b", "true"))
    val result = GateEvaluator.evaluate(expr, context)
    assertEquals(result, Right(false))
  }

  // NOT tests
  test("evaluate NOT - true becomes false") {
    val context = Map("a" -> "true")
    val expr = NotExpr(EqualityExpr("a", "true"))
    val result = GateEvaluator.evaluate(expr, context)
    assertEquals(result, Right(false))
  }

  test("evaluate NOT - false becomes true") {
    val context = Map("a" -> "false")
    val expr = NotExpr(EqualityExpr("a", "true"))
    val result = GateEvaluator.evaluate(expr, context)
    assertEquals(result, Right(true))
  }

  test("evaluate double NOT") {
    val context = Map("a" -> "true")
    val expr = NotExpr(NotExpr(EqualityExpr("a", "true")))
    val result = GateEvaluator.evaluate(expr, context)
    assertEquals(result, Right(true))
  }

  // Precedence tests
  test("evaluate precedence - AND before OR") {
    // a AND b OR c, when a=true, b=true, c=false
    // Should evaluate as (a AND b) OR c = (true AND true) OR false = true OR false = true
    val context = Map("a" -> "true", "b" -> "true", "c" -> "false")
    val expr = OrExpr(
      AndExpr(EqualityExpr("a", "true"), EqualityExpr("b", "true")),
      EqualityExpr("c", "true")
    )
    val result = GateEvaluator.evaluate(expr, context)
    assertEquals(result, Right(true))
  }

  test("evaluate precedence - NOT before AND") {
    // NOT a AND b, when a=true, b=true
    // Should evaluate as (NOT a) AND b = (NOT true) AND true = false AND true = false
    val context = Map("a" -> "true", "b" -> "true")
    val expr = AndExpr(
      NotExpr(EqualityExpr("a", "true")),
      EqualityExpr("b", "true")
    )
    val result = GateEvaluator.evaluate(expr, context)
    assertEquals(result, Right(false))
  }

  // Context variable tests
  test("evaluate with boolean context") {
    val context = Map("lsp_active" -> true)
    val expr = EqualityExpr("lsp_active", "true")
    val result = GateEvaluator.evaluate(expr, context)
    assertEquals(result, Right(true))
  }

  test("evaluate with boolean context - false") {
    val context = Map("lsp_active" -> false)
    val expr = EqualityExpr("lsp_active", "true")
    val result = GateEvaluator.evaluate(expr, context)
    assertEquals(result, Right(false))
  }

  test("evaluate with numeric context") {
    val context = Map("count" -> 42)
    val expr = EqualityExpr("count", "42")
    val result = GateEvaluator.evaluate(expr, context)
    assertEquals(result, Right(true))
  }

  // Edge cases from spec
  test("spec case: simple equality gate - buftype empty") {
    val context = Map("buftype" -> "")
    val expr = EqualityExpr("buftype", "")
    val result = GateEvaluator.evaluate(expr, context)
    assertEquals(result, Right(true))
  }

  test("spec case: multi-condition AND gate") {
    val context = Map("filetype" -> "java", "project_type" -> "maven")
    val expr = AndExpr(
      EqualityExpr("filetype", "java"),
      EqualityExpr("project_type", "maven")
    )
    val result = GateEvaluator.evaluate(expr, context)
    assertEquals(result, Right(true))
  }

  test("spec case: multi-condition AND gate - mismatched") {
    val context = Map("filetype" -> "java", "project_type" -> "gradle")
    val expr = AndExpr(
      EqualityExpr("filetype", "java"),
      EqualityExpr("project_type", "maven")
    )
    val result = GateEvaluator.evaluate(expr, context)
    assertEquals(result, Right(false))
  }

  test("spec case: OR gate with mixed match") {
    val context = Map("mode" -> "normal", "lsp_active" -> false)
    val expr = OrExpr(
      EqualityExpr("mode", "insert"),
      EqualityExpr("lsp_active", "true")
    )
    val result = GateEvaluator.evaluate(expr, context)
    assertEquals(result, Right(false))
  }

  test("spec case: NOT gate negation") {
    val context = Map("debug_mode" -> false)
    val expr = NotExpr(EqualityExpr("debug_mode", "true"))
    val result = GateEvaluator.evaluate(expr, context)
    assertEquals(result, Right(true))
  }

  test("spec case: complex precedence - a AND b OR c") {
    // a AND b OR c = (a AND b) OR c
    // When a=true, b=true, c=false: (true AND true) OR false = true OR false = true
    val context = Map("a" -> true, "b" -> true, "c" -> false)
    val expr = OrExpr(
      AndExpr(EqualityExpr("a", "true"), EqualityExpr("b", "true")),
      EqualityExpr("c", "false")
    )
    val result = GateEvaluator.evaluate(expr, context)
    assertEquals(result, Right(true))
  }

  // Error cases
  test("error - unknown variable") {
    val context = Map("filetype" -> "java")
    val expr = EqualityExpr("unknown_var", "foo")
    val result = GateEvaluator.evaluate(expr, context)
    assert(result.isLeft)
    assert(result.swap.getOrElse("").contains("Unknown gate variable"))
  }

  test("error - missing variable in context") {
    val context = Map("filetype" -> "java")
    val expr = EqualityExpr("project_type", "maven")
    val result = GateEvaluator.evaluate(expr, context)
    assert(result.isLeft)
    assert(result.swap.getOrElse("").contains("Unknown gate variable"))
  }

  // Complex real-world scenarios
  test("real world: java project with maven") {
    val context = Map(
      "filetype" -> "java",
      "project_type" -> "maven",
      "lsp_active" -> true,
      "mode" -> "normal"
    )
    val expr = AndExpr(
      EqualityExpr("filetype", "java"),
      EqualityExpr("project_type", "maven")
    )
    val result = GateEvaluator.evaluate(expr, context)
    assertEquals(result, Right(true))
  }

  test("real world: python file - should not show java keymaps") {
    val context = Map("filetype" -> "python", "project_type" -> "maven")
    val expr = AndExpr(
      EqualityExpr("filetype", "java"),
      EqualityExpr("project_type", "maven")
    )
    val result = GateEvaluator.evaluate(expr, context)
    assertEquals(result, Right(false))
  }

  test("real world: show keymaps when lsp active or in test mode") {
    val context = Map("lsp_active" -> true, "test_context" -> false)
    val expr = OrExpr(
      EqualityExpr("lsp_active", "true"),
      EqualityExpr("test_context", "true")
    )
    val result = GateEvaluator.evaluate(expr, context)
    assertEquals(result, Right(true))
  }

  test("real world: hide keymaps when debug mode is off") {
    val context = Map("debug_mode" -> false)
    val expr = EqualityExpr("debug_mode", "true")
    val result = GateEvaluator.evaluate(expr, context)
    assertEquals(result, Right(false))
  }

  test("handle mixed boolean and string contexts") {
    val context = Map(
      "filetype" -> "java",
      "lsp_active" -> true,
      "mode" -> "normal"
    )
    val expr = AndExpr(
      AndExpr(
        EqualityExpr("filetype", "java"),
        EqualityExpr("lsp_active", "true")
      ),
      EqualityExpr("mode", "normal")
    )
    val result = GateEvaluator.evaluate(expr, context)
    assertEquals(result, Right(true))
  }

  test("handle integer context values") {
    val context = Map("count" -> 42)
    val expr = EqualityExpr("count", "42")
    val result = GateEvaluator.evaluate(expr, context)
    assertEquals(result, Right(true))
  }

  test("handle double context values") {
    val context = Map("ratio" -> 3.14)
    val expr = EqualityExpr("ratio", "3.14")
    val result = GateEvaluator.evaluate(expr, context)
    assertEquals(result, Right(true))
  }

  test("handle long context values") {
    val context = Map("bignum" -> 999999999L)
    val expr = EqualityExpr("bignum", "999999999")
    val result = GateEvaluator.evaluate(expr, context)
    assertEquals(result, Right(true))
  }

  test("handle null context values") {
    val context = Map("nullable" -> null)
    val expr = EqualityExpr("nullable", "")
    val result = GateEvaluator.evaluate(expr, context)
    assertEquals(result, Right(true))
  }

  test("handle list values as strings") {
    val context = Map("items" -> List("a", "b", "c"))
    val expr = EqualityExpr("items", "List(a, b, c)")
    val result = GateEvaluator.evaluate(expr, context)
    // Lists are converted via toString()
    assert(result.isRight)
  }

  test("type coercion: boolean to string") {
    val context = Map("flag" -> true)
    val expr = EqualityExpr("flag", "true")
    val result = GateEvaluator.evaluate(expr, context)
    assertEquals(result, Right(true))
  }

  test("type mismatch: expected different number") {
    val context = Map("count" -> 42)
    val expr = EqualityExpr("count", "43")
    val result = GateEvaluator.evaluate(expr, context)
    assertEquals(result, Right(false))
  }

  test("empty value matches empty string") {
    val context = Map("buftype" -> "")
    val expr = EqualityExpr("buftype", "")
    val result = GateEvaluator.evaluate(expr, context)
    assertEquals(result, Right(true))
  }

  test("empty value does not match false") {
    val context = Map("flag" -> false)
    val expr = EqualityExpr("flag", "")
    val result = GateEvaluator.evaluate(expr, context)
    assertEquals(result, Right(false))
  }

  test("empty value does not match zero") {
    val context = Map("count" -> 0)
    val expr = EqualityExpr("count", "")
    val result = GateEvaluator.evaluate(expr, context)
    assertEquals(result, Right(false))
  }
