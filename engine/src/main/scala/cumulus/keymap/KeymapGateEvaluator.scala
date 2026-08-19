package cumulus.keymap

import cumulus.protocol.{CumulusResponse, CumulusError, GateContext, GateEvalResult}
import upickle.default.ReadWriter

/**
 * KeymapGateEvaluator: Orchestrates gate expression parsing, validation, and evaluation.
 *
 * Workflow:
 * 1. Parse the gate expression string into an AST using GateParser
 * 2. Validate that all required context variables are present
 * 3. Convert GateContext to a Map for evaluation
 * 4. Evaluate the AST against the context using GateEvaluator
 * 5. Return a CumulusResponse with the gate_active boolean result
 */
object KeymapGateEvaluator:

  /**
   * Handle the eval-keymap-gates subcommand
   *
   * @param gateExpr The gate expression string (e.g. "filetype=java AND project_type=maven")
   * @param context The GateContext containing variable values
   * @return CumulusResponse[GateEvalResult] with success flag and gate_active boolean
   */
  def handle(gateExpr: String, context: GateContext): CumulusResponse[GateEvalResult] =
    try
      // Parse the gate expression
      GateParser.parse(gateExpr) match
        case Left(parseError) =>
          CumulusResponse(
            success = false,
            data = None,
            error = Some(s"Invalid gate expression syntax: $parseError"),
            error_code = Some(CumulusError.INVALID_INPUT.toString)
          )

        case Right(ast) =>
          // Extract all variables mentioned in the AST
          val requiredVariables = extractVariables(ast)

          // Validate that all required variables are present in context
          val missingVars = requiredVariables.filter { varName =>
            !getContextValue(varName, context).isDefined
          }

          if missingVars.nonEmpty then
            CumulusResponse(
              success = false,
              data = None,
              error = Some(s"Missing required variable${if missingVars.size > 1 then "s" else ""}: ${missingVars.mkString(", ")}"),
              error_code = Some(CumulusError.INVALID_INPUT.toString)
            )
          else
            // Convert context to a map for evaluation
            val contextMap = contextToMap(context)

            // Evaluate the expression
            GateEvaluator.evaluate(ast, contextMap) match
              case Left(evalError) =>
                CumulusResponse(
                  success = false,
                  data = None,
                  error = Some(s"Gate evaluation error: $evalError"),
                  error_code = Some(CumulusError.INVALID_INPUT.toString)
                )

              case Right(gateActive) =>
                CumulusResponse(
                  success = true,
                  data = Some(GateEvalResult(gate_active = gateActive)),
                  error = None,
                  error_code = None
                )
    catch
      case e: Exception =>
        CumulusResponse(
          success = false,
          data = None,
          error = Some(s"Internal error: ${e.getMessage}"),
          error_code = Some(CumulusError.INTERNAL_ERROR.toString)
        )

  /**
   * Extract all variable names mentioned in an AST
   */
  private def extractVariables(expr: GateExpr): Set[String] =
    expr match
      case EqualityExpr(variable, _) =>
        Set(variable)

      case NotExpr(inner) =>
        extractVariables(inner)

      case AndExpr(left, right) =>
        extractVariables(left) ++ extractVariables(right)

      case OrExpr(left, right) =>
        extractVariables(left) ++ extractVariables(right)

  /**
   * Get the value of a context variable
   */
  private def getContextValue(varName: String, context: GateContext): Option[Any] =
    varName match
      case "buftype" => context.buftype.map(identity)
      case "filetype" => context.filetype.map(identity)
      case "mode" => context.mode.map(identity)
      case "project_type" => context.project_type.map(identity)
      case "lsp_active" => context.lsp_active.map(identity)
      case "debug_mode" => context.debug_mode.map(identity)
      case "test_context" => context.test_context.map(identity)
      case _ => None

  /**
   * Convert GateContext to a Map for evaluation
   */
  private def contextToMap(context: GateContext): Map[String, Any] =
    Map(
      "buftype" -> context.buftype.getOrElse(""),
      "filetype" -> context.filetype.getOrElse(""),
      "mode" -> context.mode.getOrElse(""),
      "project_type" -> context.project_type.getOrElse(""),
      "lsp_active" -> context.lsp_active.getOrElse(false),
      "debug_mode" -> context.debug_mode.getOrElse(false),
      "test_context" -> context.test_context.getOrElse(false)
    )
