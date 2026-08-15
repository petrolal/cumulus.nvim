package cumulus.testing

import scala.xml.{XML, NodeSeq}
import scala.util.Try

object TestOutputParser:

  // Compiled regex patterns
  private lazy val gradleTestLinePattern = """(\w+(?:\.\w+)*)\s*>\s*(\w+)\s+(PASSED|FAILED|SKIPPED)""".r

  /**
   * Strip ANSI escape codes from string.
   */
  private def stripAnsi(text: String): String =
    text
      .replaceAll("\u001b\\[[0-9;]*[a-zA-Z]", "")
      .replaceAll("\u001b\\(B", "")
      .replaceAll("\\[[0-9;]*m", "")
      .replaceAll("\\(B", "")

  /**
   * Parse test output from stdin (supports JUnit 5 XML, Maven Surefire text, and Gradle output).
   *
   * @param input The raw test output as a string
   * @return Either an error message or a sequence of parsed TestResult
   */
  def parseTestOutput(input: String): Either[String, Seq[TestResult]] =
    try
      val cleanedInput = input.trim
      if cleanedInput.isEmpty then
        return Right(Seq())

      // Try to parse as JUnit 5 XML first
      if cleanedInput.startsWith("<") || cleanedInput.contains("<testcase") then
        parseJunitXml(cleanedInput)
      else
        // Try Gradle format, then Maven format as fallback
        val gradleResults = parseGradleOutput(cleanedInput)
        if gradleResults.nonEmpty then
          Right(gradleResults)
        else
          val mavenResults = parseMavenOutput(cleanedInput)
          Right(mavenResults)

    catch
      case e: Exception =>
        Left(s"Error parsing test output: ${e.getMessage}")

  /**
   * Parse JUnit 5 XML output.
   * Looks for <testcase> elements with classname, name, and optional <failure> child.
   */
  private def parseJunitXml(input: String): Either[String, Seq[TestResult]] =
    try
      val stripped = stripAnsi(input)
      // Try to parse XML; handle malformed or partial XML gracefully
      val xml = Try {
        XML.loadString(stripped)
      }.getOrElse {
        // Fallback: strip XML declaration and wrap in root element
        val withoutDecl = stripped.replaceAll("<\\?xml[^>]*\\?>", "")
        XML.loadString(s"<root>$withoutDecl</root>")
      }

      val testcases: NodeSeq = xml \\ "testcase"
      val results = testcases.map { tc =>
        val className = (tc \ "@classname").text
        val methodName = (tc \ "@name").text
        val failures = tc \ "failure"
        val errors = tc \ "error"
        val skipped = tc \ "skipped"

        val status = if !failures.isEmpty then
          "FAILED"
        else if !errors.isEmpty then
          "FAILED"
        else if !skipped.isEmpty then
          "SKIPPED"
        else
          "PASSED"

        val message = if !failures.isEmpty then
          Some(failures.text)
        else if !errors.isEmpty then
          Some(errors.text)
        else
          None

        TestResult(
          class_name = className,
          method_name = methodName,
          status = status,
          message = message
        )
      }.toSeq

      Right(results)

    catch
      case e: Exception =>
        Left(s"Failed to parse JUnit XML: ${e.getMessage}")

  /**
   * Parse Gradle test output format.
   * Looks for lines like: "MyTest > testMethod PASSED/FAILED/SKIPPED"
   */
  private def parseGradleOutput(input: String): Seq[TestResult] =
    val lines = input.split("\n")
    val results = scala.collection.mutable.ArrayBuffer[TestResult]()
    var lastFailedIdx: Option[Int] = None
    val failureMessages = scala.collection.mutable.Map[Int, StringBuilder]()

    lines.foreach { line =>
      val cleanedLine = stripAnsi(line).trim
      gradleTestLinePattern.findFirstMatchIn(cleanedLine) match
        case Some(m) =>
          val fullClassName = m.group(1)
          val methodName = m.group(2)
          val status = m.group(3)

          val currentIdx = results.length
          results += TestResult(
            class_name = extractSimpleClassName(fullClassName),
            method_name = methodName,
            status = status,
            message = None
          )
          if status == "FAILED" then
            lastFailedIdx = Some(currentIdx)
            failureMessages(currentIdx) = new StringBuilder()
          else
            lastFailedIdx = None

        case None =>
          // Accumulate failure stacktraces/messages for the last failed test if indented
          if lastFailedIdx.isDefined && cleanedLine.nonEmpty && !cleanedLine.startsWith("BUILD") && !cleanedLine.startsWith("Task :") then
            failureMessages(lastFailedIdx.get).append(cleanedLine).append("\n")
    }

    // Attach accumulated failure messages
    results.zipWithIndex.map { case (r, idx) =>
      if r.status == "FAILED" && failureMessages.contains(idx) && failureMessages(idx).nonEmpty then
        r.copy(message = Some(failureMessages(idx).toString.trim))
      else
        r
    }.toSeq

  /**
   * Parse Maven Surefire test output format.
   * Looks for lines indicating test runs and failures.
   */
  private def parseMavenOutput(input: String): Seq[TestResult] =
    val lines = input.split("\n")
    val results = scala.collection.mutable.ArrayBuffer[TestResult]()

    // Look for test result summaries and failure blocks
    var currentFailureClass: Option[String] = None
    var currentFailureMethod: Option[String] = None
    var currentFailureMessage = scala.collection.mutable.StringBuilder()

    lines.foreach { line =>
      val cleanedLine = stripAnsi(line).trim

      // Check for test failure indicators
      if cleanedLine.contains("FAILURE") || cleanedLine.contains("ERROR") then
        // Extract class and method from failure line: e.g., "com.pkg.SomeClassTest.someMethod" or "SomeClass.someMethod"
        val classMethodPattern = """([A-Za-z0-9_.]+(?:Test|Tests|TestCase|IT|Spec|[A-Z][A-Za-z0-9_]*))\s*\.\s*([A-Za-z0-9_]+)""".r
        classMethodPattern.findFirstMatchIn(cleanedLine).foreach { m =>
          currentFailureClass = Some(extractSimpleClassName(m.group(1)))
          currentFailureMethod = Some(m.group(2))
        }

      // Accumulate failure messages
      if currentFailureClass.isDefined && cleanedLine.nonEmpty then
        currentFailureMessage.append(cleanedLine).append("\n")

      // End of failure block (check for test summary line)
      if cleanedLine.contains("Tests run:") then
        if currentFailureClass.isDefined && currentFailureMethod.isDefined then
          results += TestResult(
            class_name = currentFailureClass.get,
            method_name = currentFailureMethod.get,
            status = "FAILED",
            message = if currentFailureMessage.nonEmpty then Some(currentFailureMessage.toString.trim) else None
          )
        currentFailureClass = None
        currentFailureMethod = None
        currentFailureMessage = scala.collection.mutable.StringBuilder()
    }

    // Flush any remaining failure block at end of output
    if currentFailureClass.isDefined && currentFailureMethod.isDefined then
      results += TestResult(
        class_name = currentFailureClass.get,
        method_name = currentFailureMethod.get,
        status = "FAILED",
        message = if currentFailureMessage.nonEmpty then Some(currentFailureMessage.toString.trim) else None
      )

    results.toSeq

  /**
   * Extract the simple class name from a fully qualified class name.
   * Example: "com.example.MyTest" -> "MyTest"
   */
  private def extractSimpleClassName(fullClassName: String): String =
    fullClassName.split('.').lastOption.getOrElse(fullClassName)

