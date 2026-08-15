package cumulus.code

import os.Path
import scala.collection.mutable

object BeanGraphAnalyzer:

  // Compiled regex patterns for Spring stereotypes
  private lazy val componentPattern = """@Component\b""".r
  private lazy val servicePattern = """@Service\b""".r
  private lazy val repositoryPattern = """@Repository\b""".r
  private lazy val controllerPattern = """@Controller\b""".r
  private lazy val restControllerPattern = """@RestController\b""".r
  private lazy val configurationPattern = """@Configuration\b""".r

  // Patterns for dependency injection
  private lazy val autowiredPattern = """@Autowired\b""".r
  private lazy val injectPattern = """@Inject\b""".r
  private lazy val qualifierPattern = """@Qualifier\s*\(\s*["']([^"']+)["']\s*\)""".r

  // Patterns for parsing source
  private lazy val packagePattern = """^\s*package\s+([a-zA-Z_][a-zA-Z0-9_.]*)\s*;?""".r
  private lazy val classPattern = """(?:public|protected|private|final|open|abstract|\s)*\bclass\s+([a-zA-Z_][a-zA-Z0-9_]*)\s*""".r
  private lazy val fieldPattern = """^\s*(?:private|public|protected)?\s*(?:final\s+)?([a-zA-Z_][a-zA-Z0-9_.<>?,\s]*)\s+([a-zA-Z_][a-zA-Z0-9_]*)\s*(?:;|=)""".r

  /**
   * Parse Spring Beans from a directory.
   * Scans src/main/java, src/main/kotlin, src/test/java, src/test/kotlin for Spring stereotypes.
   *
   * @param dirPath The root directory of the project
   * @return Sequence of detected Spring beans
   */
  def parseSpringBeans(dirPath: String): Seq[SpringBean] =
    val p = Path(dirPath, os.pwd)
    if !os.exists(p) || !os.isDir(p) then
      throw new Exception(s"Directory not found: $dirPath")

    val beans = mutable.ListBuffer[SpringBean]()

    // Scan all source directories
    val sourceDirs = Seq(
      "src/main/java",
      "src/main/kotlin",
      "src/test/java",
      "src/test/kotlin"
    ).map(rel => p / os.RelPath(rel)).filter(os.exists)

    for sourceDir <- sourceDirs do
      scanDirectory(sourceDir, beans)

    beans.toSeq

  /**
   * Recursively scan a directory for Java/Kotlin files with Spring stereotypes
   */
  private def scanDirectory(dir: Path, beans: mutable.ListBuffer[SpringBean]): Unit =
    if !os.isDir(dir) then return

    val files = os.walk(dir).filter(f => os.isFile(f) && (f.last.endsWith(".java") || f.last.endsWith(".kt")))
    for file <- files do
      extractBeansFromFile(file.toString, beans)

  /**
   * Extract Spring beans from a single source file
   */
  private def extractBeansFromFile(filePath: String, beans: mutable.ListBuffer[SpringBean]): Unit =
    try
      val p = Path(filePath, os.pwd)
      val lines = os.read.lines(p, charSet = java.nio.charset.StandardCharsets.UTF_8).toList

      var currentPackage = ""
      var currentClass = ""
      var currentClassLine = 0
      var currentStereotype: Option[String] = None
      val fieldInjections = mutable.Map[String, (String, Option[String])]() // fieldName -> (type, qualifier)
      var inCurrentClass = false
      var braceDepth = 0

      for (line, idx) <- lines.zipWithIndex do
        if line != null then
          val lineNumber = idx + 1
          val trimmed = line.dropWhile(_.isWhitespace)

          // Skip comments
          if !trimmed.startsWith("//") && !line.contains("/*") then
            // Extract package
            if currentPackage.isEmpty then
              packagePattern.findFirstMatchIn(line) foreach { m =>
                currentPackage = m.group(1)
              }

            // Check for stereotypes
            if currentStereotype.isEmpty then
              if componentPattern.findFirstIn(line).isDefined then
                currentStereotype = Some("@Component")
              else if servicePattern.findFirstIn(line).isDefined then
                currentStereotype = Some("@Service")
              else if repositoryPattern.findFirstIn(line).isDefined then
                currentStereotype = Some("@Repository")
              else if controllerPattern.findFirstIn(line).isDefined then
                currentStereotype = Some("@Controller")
              else if restControllerPattern.findFirstIn(line).isDefined then
                currentStereotype = Some("@RestController")
              else if configurationPattern.findFirstIn(line).isDefined then
                currentStereotype = Some("@Configuration")

            // Check for class declaration
            val matchedClass = if currentStereotype.isDefined && !inCurrentClass then
              classPattern.findFirstMatchIn(line).map { m =>
                currentClass = m.group(1)
                currentClassLine = lineNumber
                inCurrentClass = true
                braceDepth = 0
                true
              }.getOrElse(false)
            else false

            // Track brace depth within class
            if inCurrentClass then
              val opens = line.count(_ == '{')
              val closes = line.count(_ == '}')
              braceDepth += opens - closes

              // Check for field injections (within current class context)
              if autowiredPattern.findFirstIn(line).isDefined || injectPattern.findFirstIn(line).isDefined then
                // Check qualifier on same line or next lines
                var foundQualifier = qualifierPattern.findFirstMatchIn(line).map(_.group(1))
                // Search forward up to 3 lines for field declaration and/or @Qualifier
                var scanIdx = idx + 1
                var foundField = false
                while scanIdx < lines.length && scanIdx <= idx + 3 && !foundField do
                  val nextLine = lines(scanIdx)
                  if nextLine != null then
                    val nextTrimmed = nextLine.trim
                    if foundQualifier.isEmpty then
                      qualifierPattern.findFirstMatchIn(nextTrimmed).foreach { qm =>
                        foundQualifier = Some(qm.group(1))
                      }
                    extractFieldType(nextTrimmed) foreach { case (fieldName, fieldType) =>
                      fieldInjections(fieldName) = (fieldType, foundQualifier)
                      foundField = true
                    }
                  scanIdx += 1

              // End of class detection when brace depth returns to 0 (and not on the class opening line)
              if !matchedClass && braceDepth <= 0 && line.contains("}") && currentClass.nonEmpty then
                // Create bean from collected info
                if currentStereotype.isDefined then
                  val fqClassName = if currentPackage.nonEmpty then
                    s"$currentPackage.$currentClass"
                  else
                    currentClass

                  val deps = fieldInjections.toSeq.map { case (name, (tpe, qual)) =>
                    Dependency(
                      field_name = name,
                      field_type = tpe,
                      qualifier = qual
                    )
                  }

                  beans += SpringBean(
                    name = currentClass,
                    class_name = fqClassName,
                    file_path = filePath,
                    line_number = currentClassLine,
                    stereotype = currentStereotype.get,
                    injected_dependencies = deps
                  )
                end if

                // Reset for next class in same file
                currentClass = ""
                currentStereotype = None
                fieldInjections.clear()
                inCurrentClass = false
                braceDepth = 0
    catch
      case _: Exception => // Skip files that can't be parsed

  /**
   * Extract field type from a field declaration line
   * Returns Some((fieldName, fieldType)) if valid
   */
  private def extractFieldType(line: String): Option[(String, String)] =
    try
      fieldPattern.findFirstMatchIn(line) match
        case Some(m) =>
          val fieldType = m.group(1).trim
          val fieldName = m.group(2).trim
          Some((fieldName, fieldType))
        case None => None
    catch
      case _: Exception => None

