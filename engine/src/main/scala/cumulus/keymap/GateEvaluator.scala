package cumulus.keymap

/**
 * GateEvaluator: Evaluates gate AST expressions against a context map.
 *
 * Supports:
 * - Equality checks: variable=value
 * - Boolean AND, OR, NOT operators with proper precedence
 * - Context variables: buftype, filetype, mode, project_type, lsp_active, debug_mode, test_context
 */
object GateEvaluator:

  /**
   * Evaluate a gate AST expression against a context map.
   *
   * @param expr The parsed gate expression AST
   * @param context Map of variable names to their values (strings or booleans)
   * @return Either[String, Boolean] where Left contains error message
   */
  def evaluate(expr: GateExpr, context: Map[String, Any]): Either[String, Boolean] =
    try
      Right(evalExpr(expr, context))
    catch
      case e: Exception =>
        Left(e.getMessage)

  /**
   * Internal evaluation logic
   */
  private def evalExpr(expr: GateExpr, context: Map[String, Any]): Boolean =
    expr match
      case EqualityExpr(variable, value) =>
        evalEquality(variable, value, context)

      case NotExpr(inner) =>
        !evalExpr(inner, context)

      case AndExpr(left, right) =>
        evalExpr(left, context) && evalExpr(right, context)

      case OrExpr(left, right) =>
        evalExpr(left, context) || evalExpr(right, context)

  /**
   * Evaluate an equality expression against the context
   */
  private def evalEquality(variable: String, value: String, context: Map[String, Any]): Boolean =
    context.get(variable) match
      case Some(contextValue) =>
        // Convert context value to string for comparison
        val contextStr = contextValue match
          case s: String => s
          case b: Boolean => b.toString
          case i: Int => i.toString
          case l: Long => l.toString
          case d: Double => d.toString
          case null => ""
          case other => other.toString

        // Empty value should only match empty context strings
        if value.isEmpty then
          contextStr.isEmpty
        else
          contextStr == value

      case None =>
        // Variable not found in context is a condition error
        throw new IllegalArgumentException(s"Unknown gate variable: $variable")
