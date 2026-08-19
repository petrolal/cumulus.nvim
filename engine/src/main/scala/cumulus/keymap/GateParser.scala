package cumulus.keymap

/**
 * AST nodes for gate expressions
 */
sealed trait GateExpr
case class EqualityExpr(variable: String, value: String) extends GateExpr
case class NotExpr(expr: GateExpr) extends GateExpr
case class AndExpr(left: GateExpr, right: GateExpr) extends GateExpr
case class OrExpr(left: GateExpr, right: GateExpr) extends GateExpr

/**
 * Token types for gate expression lexer
 */
sealed trait Token
case class VariableToken(name: String) extends Token
case class EqualsToken() extends Token
case class ValueToken(value: String) extends Token
case class NotToken() extends Token
case class AndToken() extends Token
case class OrToken() extends Token
case class EOFToken() extends Token

/**
 * GateParser: Tokenizes and parses gate expressions into an AST.
 *
 * Grammar (with precedence: NOT > AND > OR):
 *   expr    := orExpr
 *   orExpr  := andExpr ('OR' andExpr)*
 *   andExpr := notExpr ('AND' notExpr)*
 *   notExpr := 'NOT' notExpr | primaryExpr
 *   primaryExpr := VARIABLE '=' VALUE
 *
 * Examples:
 *   filetype=java
 *   filetype=java AND project_type=maven
 *   NOT debug_mode
 *   a AND b OR c  (evaluates as (a AND b) OR c)
 */
object GateParser:

  /**
   * Tokenize a gate expression string
   */
  private def tokenize(expr: String): Either[String, Seq[Token]] =
    try
      val tokens = scala.collection.mutable.ListBuffer[Token]()
      var i = 0
      val trimmed = expr.trim

      while i < trimmed.length do
        val ch = trimmed(i)

        if ch.isWhitespace then
          i += 1
        else if ch == '=' then
          tokens += EqualsToken()
          i += 1
        else
          // Try to parse keywords and identifiers
          val remaining = trimmed.substring(i)

          if remaining.startsWith("NOT") && (i + 3 >= trimmed.length || trimmed(i + 3).isWhitespace) then
            tokens += NotToken()
            i += 3
          else if remaining.startsWith("AND") && (i + 3 >= trimmed.length || trimmed(i + 3).isWhitespace) then
            tokens += AndToken()
            i += 3
          else if remaining.startsWith("OR") && (i + 2 >= trimmed.length || trimmed(i + 2).isWhitespace) then
            tokens += OrToken()
            i += 2
          else
            // Parse variable or value identifier (alphanumeric + underscore)
            val start = i
            while i < trimmed.length && (trimmed(i).isLetterOrDigit || trimmed(i) == '_') do
              i += 1

            if i > start then
              val identifier = trimmed.substring(start, i)
              // Look back to see if this should be a variable or value
              if tokens.nonEmpty && tokens.last.isInstanceOf[EqualsToken] then
                tokens += ValueToken(identifier)
              else
                tokens += VariableToken(identifier)
            else
              return Left(s"Unexpected character at position $i: '$ch'")

      tokens += EOFToken()
      Right(tokens.toSeq)
    catch
      case e: Exception =>
        Left(s"Tokenization error: ${e.getMessage}")

  /**
   * Parse tokens into an AST
   */
  private def parseTokens(tokens: Seq[Token]): Either[String, GateExpr] =
    val parser = TokenParser(tokens)
    parser.parseExpr()

  /**
   * Wrapper class for stateful token parsing
   */
  private class TokenParser(tokens: Seq[Token]):
    private var pos = 0

    def parseExpr(): Either[String, GateExpr] =
      for
        result <- parseOrExpr()
        finalResult <- if peek().isInstanceOf[EOFToken] then
          Right(result)
        else
          Left(s"Unexpected token after expression: ${peek()}")
      yield finalResult

    private def parseOrExpr(): Either[String, GateExpr] =
      for
        left <- parseAndExpr()
        result <- parseOrExprRest(left)
      yield result

    private def parseOrExprRest(left: GateExpr): Either[String, GateExpr] =
      if peek().isInstanceOf[OrToken] then
        consume() // consume OR
        for
          right <- parseAndExpr()
          result <- parseOrExprRest(OrExpr(left, right))
        yield result
      else
        Right(left)

    private def parseAndExpr(): Either[String, GateExpr] =
      for
        left <- parseNotExpr()
        result <- parseAndExprRest(left)
      yield result

    private def parseAndExprRest(left: GateExpr): Either[String, GateExpr] =
      if peek().isInstanceOf[AndToken] then
        consume() // consume AND
        for
          right <- parseNotExpr()
          result <- parseAndExprRest(AndExpr(left, right))
        yield result
      else
        Right(left)

    private def parseNotExpr(): Either[String, GateExpr] =
      peek() match
        case _: NotToken =>
          consume() // consume NOT
          for
            expr <- parseNotExpr()
          yield NotExpr(expr)
        case _ =>
          parsePrimaryExpr()

    private def parsePrimaryExpr(): Either[String, GateExpr] =
      peek() match
        case v: VariableToken =>
          consume() // consume variable
          peek() match
            case _: EqualsToken =>
              consume() // consume equals
              peek() match
                case val_token: ValueToken =>
                  consume() // consume value
                  Right(EqualityExpr(v.name, val_token.value))
                case _: EOFToken | _: AndToken | _: OrToken =>
                  // Empty value after equals (e.g., "buftype=")
                  Right(EqualityExpr(v.name, ""))
                case _ =>
                  Left(s"Expected value after '=' at position $pos")
            case _ =>
              // Bare variable is treated as checking if it's "true"
              Right(EqualityExpr(v.name, "true"))
        case _: EOFToken =>
          Left("Unexpected end of expression")
        case token =>
          Left(s"Unexpected token at position $pos: $token")

    private def peek(): Token =
      if pos < tokens.length then tokens(pos) else EOFToken()

    private def consume(): Unit =
      pos += 1

  /**
   * Parse a gate expression string into an AST.
   * Returns Either[String, GateExpr] where Left contains the error message.
   */
  def parse(expr: String): Either[String, GateExpr] =
    for
      tokens <- tokenize(expr)
      result <- if tokens.isEmpty || tokens.forall(_.isInstanceOf[EOFToken]) then
        Left("Empty expression")
      else
        parseTokens(tokens)
    yield result
