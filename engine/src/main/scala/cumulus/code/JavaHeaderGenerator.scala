package cumulus.code

import os.Path

/**
 * JavaHeaderGenerator: Infers package name from directory structure and generates Java class boilerplate.
 */
object JavaHeaderGenerator:

  /**
   * Generate Java header for a given file path.
   * Infers package from directory structure and returns package statement and class declaration.
   */
  def generateHeader(filePath: String): JavaHeader =
    val p = Path(filePath, os.pwd)
    val fileName = p.last

    // Extract class name from filename (remove .java or .kt extension)
    val className = fileName match
      case name if name.endsWith(".java") => name.substring(0, name.length - 5)
      case name if name.endsWith(".kt") => name.substring(0, name.length - 3)
      case name => name

    // Infer package from directory structure
    val absolutePath = p.toString
    val packageName = inferPackageFromPath(absolutePath)

    // Generate class declaration
    val classDeclaration = s"public class $className { }"

    JavaHeader(
      package_name = packageName,
      class_name = className,
      class_declaration = classDeclaration
    )

  /**
   * Infer package name from file path.
   * Supports these directory patterns:
   * - /src/main/java/com/example/MyClass.java -> com.example
   * - /src/test/java/com/example/MyClass.java -> com.example
   * - /src/main/kotlin/com/example/MyClass.kt -> com.example
   * - /src/com/example/MyClass.java -> com.example (generic src)
   * - /path/to/src/com/example/MyClass.java -> com.example
   */
  private def inferPackageFromPath(filePath: String): String =
    val path = filePath.replace('\\', '/') // Normalize Windows paths

    // Try to find the innermost source root and extract package
    val sourceRoots = Seq(
      "/src/main/java/",
      "/src/main/kotlin/",
      "/src/test/java/",
      "/src/test/kotlin/",
      "/src/"
    )

    sourceRoots.collectFirst {
      case root if path.contains(root) =>
        val startIdx = path.lastIndexOf(root) + root.length
        val endIdx = path.lastIndexOf('/')
        if startIdx <= endIdx then
          val packagePath = path.substring(startIdx, endIdx)
          packagePath.replace('/', '.').stripPrefix(".").stripSuffix(".")
        else
          ""
    }.getOrElse("")

/**
 * FileScaffolder: Infers package name from directory structure, detects language,
 * generates boilerplate templates, and optionally creates files.
 */
object FileScaffolder:

  // List of known source root suffixes ordered from most specific to least specific
  private val KnownSourceRoots: Seq[String] = Seq(
    "/src/commonMain/kotlin/",
    "/src/commonMain/resources/",
    "/src/commonTest/kotlin/",
    "/src/commonTest/resources/",
    "/src/jvmMain/kotlin/",
    "/src/jvmMain/java/",
    "/src/jvmTest/kotlin/",
    "/src/jvmTest/java/",
    "/src/androidMain/kotlin/",
    "/src/androidMain/java/",
    "/src/androidTest/kotlin/",
    "/src/androidTest/java/",
    "/src/iosMain/kotlin/",
    "/src/iosTest/kotlin/",
    "/src/desktopMain/kotlin/",
    "/src/desktopTest/kotlin/",
    "/src/jsMain/kotlin/",
    "/src/jsTest/kotlin/",
    "/src/nativeMain/kotlin/",
    "/src/nativeTest/kotlin/",
    "/src/main/kotlin/",
    "/src/main/java/",
    "/src/main/scala/",
    "/src/main/groovy/",
    "/src/test/kotlin/",
    "/src/test/java/",
    "/src/test/scala/",
    "/src/test/groovy/",
    "/src/it/scala/",
    "/src/it/java/",
    "/src/it/kotlin/",
    "/src/"
  )

  /**
   * Infer package name and source root from file path.
   * Returns (packageName, sourceRootOpt)
   */
  def inferPackageAndRoot(filePath: String): (String, Option[String]) =
    val normalizedPath = filePath.replace('\\', '/')
    val lastSlashIdx = normalizedPath.lastIndexOf('/')
    val dirPath = if lastSlashIdx >= 0 then normalizedPath.substring(0, lastSlashIdx) else ""

    var bestMatch: Option[(String, String)] = None // (sourceRootPrefix, relativePackagePath)

    for root <- KnownSourceRoots do
      if bestMatch.isEmpty then
        // Case 1: Exact match or starts with root without leading slash (e.g. "src/main/java/...")
        val rootNoLeadingSlash = root.stripPrefix("/")
        if dirPath.startsWith(rootNoLeadingSlash) then
          val rootPath = rootNoLeadingSlash.stripSuffix("/")
          val afterRoot = dirPath.substring(rootNoLeadingSlash.length).stripPrefix("/")
          bestMatch = Some((rootPath, afterRoot))
        else
          // Case 2: Contains "/src/main/java/" or ends with "/src/main/java"
          val rootWithSlash = "/" + rootNoLeadingSlash
          val idx = dirPath.lastIndexOf(rootWithSlash.stripSuffix("/"))
          if idx >= 0 then
            val rootEnd = idx + rootWithSlash.stripSuffix("/").length
            val rootPath = dirPath.substring(0, rootEnd)
            val afterRoot = if dirPath.length > rootEnd then dirPath.substring(rootEnd).stripPrefix("/") else ""
            bestMatch = Some((rootPath, afterRoot))

    bestMatch match
      case Some((rootPath, relPkgPath)) =>
        val pkg = if relPkgPath.isEmpty then "" else relPkgPath.replace('/', '.').stripPrefix(".").stripSuffix(".")
        (pkg, Some(rootPath))
      case None =>
        ("", None)



  /**
   * Detect language from file path extension.
   */
  def detectLanguage(filePath: String): Option[String] =
    val lastDot = filePath.lastIndexOf('.')
    if lastDot < 0 then None
    else
      filePath.substring(lastDot + 1).toLowerCase match
        case "java" => Some("java")
        case "kt" | "kts" => Some("kotlin")
        case "scala" | "sc" => Some("scala")
        case "groovy" | "gvy" => Some("groovy")
        case _ => None

  /**
   * Resolve default template kind for a language.
   */
  def defaultTemplate(language: String): String =
    language match
      case "java" => "class"
      case "kotlin" => "class"
      case "scala" => "class"
      case "groovy" => "class"
      case _ => "class"

  /**
   * Extract type name from file name (stripping extension).
   */
  def extractTypeName(filePath: String): String =
    val normalized = filePath.replace('\\', '/')
    val fileName = normalized.substring(normalized.lastIndexOf('/') + 1)
    val dotIdx = fileName.indexOf('.')
    if dotIdx > 0 then fileName.substring(0, dotIdx)
    else fileName

  /**
   * Generate boilerplate code based on language, template, package name, and type name.
   */
  def generateBoilerplate(language: String, template: String, packageName: String, typeName: String): String =
    val pkgHeaderJava = if packageName.nonEmpty then s"package $packageName;\n\n" else ""
    val pkgHeaderKotlin = if packageName.nonEmpty then s"package $packageName\n\n" else ""
    val pkgHeaderScala = if packageName.nonEmpty then s"package $packageName\n\n" else ""

    val normTemplate = template.toLowerCase.replace('_', '-')

    language match
      case "java" =>
        normTemplate match
          case "interface" =>
            s"${pkgHeaderJava}public interface $typeName {\n}\n"
          case "record" =>
            s"${pkgHeaderJava}public record $typeName(\n) {\n}\n"
          case "enum" =>
            s"${pkgHeaderJava}public enum $typeName {\n}\n"
          case "class" | _ =>
            s"${pkgHeaderJava}public class $typeName {\n}\n"

      case "kotlin" =>
        normTemplate match
          case "data-class" | "data_class" | "dataclass" =>
            s"${pkgHeaderKotlin}data class $typeName(\n    val id: String\n)\n"
          case "interface" =>
            s"${pkgHeaderKotlin}interface $typeName {\n}\n"
          case "object" =>
            s"${pkgHeaderKotlin}object $typeName {\n}\n"
          case "enum" | "enum-class" =>
            s"${pkgHeaderKotlin}enum class $typeName {\n}\n"
          case "class" | _ =>
            s"${pkgHeaderKotlin}class $typeName {\n}\n"

      case "scala" =>
        normTemplate match
          case "trait" | "interface" =>
            s"${pkgHeaderScala}trait $typeName {\n}\n"
          case "object" =>
            s"${pkgHeaderScala}object $typeName {\n}\n"
          case "enum" =>
            s"${pkgHeaderScala}enum $typeName {\n}\n"
          case "case-class" | "case_class" =>
            s"${pkgHeaderScala}case class $typeName()\n"
          case "class" | _ =>
            s"${pkgHeaderScala}class $typeName {\n}\n"

      case "groovy" =>
        normTemplate match
          case "interface" =>
            s"${pkgHeaderJava}interface $typeName {\n}\n"
          case "enum" =>
            s"${pkgHeaderJava}enum $typeName {\n}\n"
          case "class" | _ =>
            s"${pkgHeaderJava}class $typeName {\n}\n"

      case _ =>
        s"${pkgHeaderJava}public class $typeName {\n}\n"

  /**
   * Scaffold a file given filePath, optional template, and optional create flag.
   */
  def scaffold(filePath: String, templateOpt: Option[String], create: Boolean = false): cumulus.protocol.CumulusResponse[cumulus.protocol.ScaffoldResult] =
    if filePath == null || filePath.trim.isEmpty then
      return cumulus.protocol.CumulusResponse(
        success = false,
        data = None,
        error = Some("Missing required argument: --file"),
        error_code = Some(cumulus.protocol.CumulusError.INVALID_INPUT.toString)
      )

    val cleanPath = filePath.trim
    val langOpt = detectLanguage(cleanPath)
    val language = langOpt.getOrElse("java")
    val typeName = extractTypeName(cleanPath)

    if typeName.isEmpty then
      return cumulus.protocol.CumulusResponse(
        success = false,
        data = None,
        error = Some(s"Invalid file path: $filePath"),
        error_code = Some(cumulus.protocol.CumulusError.INVALID_INPUT.toString)
      )

    val template = templateOpt.map(_.trim.toLowerCase.replace('_', '-')).filter(_.nonEmpty).getOrElse(defaultTemplate(language))
    val (packageName, sourceRoot) = inferPackageAndRoot(cleanPath)
    val content = generateBoilerplate(language, template, packageName, typeName)

    var fileCreated = false
    if create then
      try
        val p = if cleanPath.startsWith("/") then Path(cleanPath) else Path(cleanPath, os.pwd)
        if os.exists(p) then
          val existingContent = os.read(p)
          if existingContent.trim.nonEmpty then
            return cumulus.protocol.CumulusResponse(
              success = false,
              data = None,
              error = Some(s"File already exists and is non-empty: $cleanPath"),
              error_code = Some(cumulus.protocol.CumulusError.INVALID_INPUT.toString)
            )
        // Ensure parent directory exists and write content
        os.makeDir.all(p / os.up)
        os.write.over(p, content)
        fileCreated = true
      catch
        case e: Exception =>
          return cumulus.protocol.CumulusResponse(
            success = false,
            data = None,
            error = Some(s"Failed to create file: ${e.getMessage}"),
            error_code = Some(cumulus.protocol.CumulusError.INTERNAL_ERROR.toString)
          )

    val result = cumulus.protocol.ScaffoldResult(
      file_path = cleanPath,
      package_name = packageName,
      type_name = typeName,
      template = template,
      content = content,
      source_root = sourceRoot,
      created = fileCreated
    )

    cumulus.protocol.CumulusResponse(
      success = true,
      data = Some(result),
      error = None,
      error_code = None
    )


