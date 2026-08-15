package cumulus.code

import scala.io.Source
import java.io.File
import scala.util.Using

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
  private lazy val packagePattern = """^\s*package\s+([a-zA-Z_][a-zA-Z0-9_.]*)\s*;""".r
  private lazy val classPattern = """^\s*(?:public\s+)?class\s+([a-zA-Z_][a-zA-Z0-9_]*)\s*""".r
  private lazy val fieldPattern = """^\s*(?:private|public|protected)?\s*(?:final\s+)?([a-zA-Z_][a-zA-Z0-9_.<>?,\s]*)\s+([a-zA-Z_][a-zA-Z0-9_]*)\s*(?:;|=)""".r

  /**
   * Parse Spring Beans from a directory.
   * Scans src/main/java, src/main/kotlin, src/test/java, src/test/kotlin for Spring stereotypes.
   *
   * @param dirPath The root directory of the project
   * @return Sequence of detected Spring beans
   */
  def parseSpringBeans(dirPath: String): Seq[SpringBean] =
    val dir = new File(dirPath)
    if !dir.exists() || !dir.isDirectory() then
      throw new Exception(s"Directory not found: $dirPath")

    val beans = scala.collection.mutable.ListBuffer[SpringBean]()

    // Scan all source directories
    val sourceDirs = Seq(
      "src/main/java",
      "src/main/kotlin",
      "src/test/java",
      "src/test/kotlin"
    ).map(p => new File(dir, p)).filter(_.exists())

    for sourceDir <- sourceDirs do
      scanDirectory(sourceDir, beans)

    beans.toSeq

  /**
   * Recursively scan a directory for Java/Kotlin files with Spring stereotypes
   */
  private def scanDirectory(dir: File, beans: scala.collection.mutable.ListBuffer[SpringBean]): Unit =
    if !dir.isDirectory() then
      return

    val files = dir.listFiles()
    if files == null then
      return

    for file <- files do
      if file.isDirectory() then
        scanDirectory(file, beans)
      else if file.getName.endsWith(".java") || file.getName.endsWith(".kt") then
        extractBeansFromFile(file.getAbsolutePath(), beans)

  /**
   * Extract Spring beans from a single source file
   */
  private def extractBeansFromFile(filePath: String, beans: scala.collection.mutable.ListBuffer[SpringBean]): Unit =
    try
      val file = new File(filePath)
      val lines = Using(Source.fromFile(file, "UTF-8")) { source =>
        source.getLines().toList
      }.get

      var currentPackage = ""
      var currentClass = ""
      var currentClassLine = 0
      var currentStereotypeLine = 0
      var currentStereotype: Option[String] = None
      val fieldInjections = scala.collection.mutable.Map[String, (String, Option[String])]() // fieldName -> (type, qualifier)
      var inCurrentClass = false

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
                currentStereotypeLine = lineNumber
              else if servicePattern.findFirstIn(line).isDefined then
                currentStereotype = Some("@Service")
                currentStereotypeLine = lineNumber
              else if repositoryPattern.findFirstIn(line).isDefined then
                currentStereotype = Some("@Repository")
                currentStereotypeLine = lineNumber
              else if controllerPattern.findFirstIn(line).isDefined then
                currentStereotype = Some("@Controller")
                currentStereotypeLine = lineNumber
              else if restControllerPattern.findFirstIn(line).isDefined then
                currentStereotype = Some("@RestController")
                currentStereotypeLine = lineNumber
              else if configurationPattern.findFirstIn(line).isDefined then
                currentStereotype = Some("@Configuration")
                currentStereotypeLine = lineNumber

            // Check for class declaration
            if currentStereotype.isDefined && currentClass.isEmpty then
              classPattern.findFirstMatchIn(line) foreach { m =>
                currentClass = m.group(1)
                currentClassLine = currentStereotypeLine
                inCurrentClass = true
              }

            // Extract field injections (within current class context)
            if inCurrentClass then
              if autowiredPattern.findFirstIn(line).isDefined || injectPattern.findFirstIn(line).isDefined then
                // Next line should contain the field declaration
                if idx + 1 < lines.length then
                  val nextLine = lines(idx + 1)
                  if nextLine != null then
                    extractFieldType(nextLine) foreach { case (fieldName, fieldType) =>
                      val qualifier = qualifierPattern.findFirstMatchIn(line) match
                        case Some(m) => Some(m.group(1))
                        case None => None
                      fieldInjections(fieldName) = (fieldType, qualifier)
                    }

              // End of class detection (simple: opening brace at column 0 after declaration)
              if trimmed.startsWith("}") && currentClass.nonEmpty then
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
