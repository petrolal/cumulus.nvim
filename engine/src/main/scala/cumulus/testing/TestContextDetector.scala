package cumulus.testing

import os.Path

object TestContextDetector:

  // Compiled regex patterns for test annotations (lazy to avoid GraalVM issues)
  private lazy val testAnnotationPattern = """@(?:Test|ParameterizedTest|RepeatedTest)\b""".r
  private lazy val methodPattern = """(?:(?:public|private|protected|internal|final|open)\s+)*(?:fun\s+|def\s+|(?:void|Unit|String|Boolean|Int|Long|Double|Float|Void|[\w<>,.?\[\]]+)\s+)(\w+)\s*\(""".r
  private lazy val classPattern = """(?:public|protected|private|internal|final|open|abstract|sealed|\s)*\b(?:class|record)\s+(\w+)""".r

  /**
   * Detect the test context (class name and method name) at a given cursor line in a Java or Kotlin file.
   *
   * @param filePath The absolute path to the source file
   * @param lineNumber The cursor line number (1-indexed)
   * @return Either an error message or the detected TestContext
   */
  def detectTestContext(filePath: String, lineNumber: Int): Either[String, TestContext] =
    try
      val p = Path(filePath, os.pwd)
      if !os.exists(p) then
        return Left(s"File not found: $filePath")

      if !filePath.endsWith(".java") && !filePath.endsWith(".kt") then
        return Left(s"Unsupported file type; only .java and .kt files are supported")

      // Read file with UTF-8 encoding using os-lib
      val lines = os.read.lines(p, charSet = java.nio.charset.StandardCharsets.UTF_8).toList

      if lines.isEmpty then
        return Left("File is empty")

      if lineNumber < 1 || lineNumber > lines.length then
        return Left(s"Line number $lineNumber is out of range (1-${lines.length})")

      // Extract class name by scanning forward from the beginning
      val className = extractClassName(lines).getOrElse("UnknownTest")

      // Scan backward from cursor line to find the nearest @Test annotation and its method
      val methodNameOpt = scanBackwardForTestMethod(lines, lineNumber - 1)

      methodNameOpt match
        case Some(methodName) =>
          Right(TestContext(className, methodName))
        case None =>
          Left(s"No @Test, @ParameterizedTest, or @RepeatedTest method found at or before line $lineNumber")

    catch
      case e: Exception =>
        Left(s"Error detecting test context: ${e.getMessage}")

  /**
   * Extract the class name from the source file by finding the first class or record declaration.
   */
  private def extractClassName(lines: List[String]): Option[String] =
    lines.foreach { line =>
      val trimmed = line.trim
      if !trimmed.startsWith("//") && !trimmed.startsWith("/*") then
        classPattern.findFirstMatchIn(line).foreach { m =>
          return Some(m.group(1))
        }
    }
    None

  /**
   * Scan backward from a given line index to find the nearest test method.
   * Returns the method name if found.
   */
  private def scanBackwardForTestMethod(lines: List[String], startIdx: Int): Option[String] =
    // Scan backward to find the nearest @Test annotation
    for i <- startIdx to 0 by -1 do
      val line = lines(i)
      val trimmed = line.trim
      if !trimmed.startsWith("//") && !trimmed.startsWith("/*") then
        if testAnnotationPattern.findFirstIn(line).isDefined then
          // Found annotation, now scan forward to find the method declaration
          val methodNameOpt = scanForwardForMethod(lines, i)
          if methodNameOpt.isDefined then
            return methodNameOpt

    // If we didn't find a test annotation above the cursor, return None
    None

  /**
   * Scan forward from a given annotation line to find the method declaration.
   * Handles multi-line method signatures.
   */
  private def scanForwardForMethod(lines: List[String], annotationIdx: Int): Option[String] =
    // Scan forward up to 10 lines to find the method signature
    for i <- (annotationIdx + 1) until Math.min(annotationIdx + 10, lines.length) do
      val line = lines(i)
      val trimmed = line.trim
      // Skip comment lines
      if !trimmed.startsWith("//") && !trimmed.startsWith("/*") then
        methodPattern.findFirstMatchIn(line).foreach { m =>
          return Some(m.group(1))
        }

    None

