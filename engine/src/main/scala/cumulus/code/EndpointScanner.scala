package cumulus.code

import os.Path
import scala.io.Source
import scala.util.Using
import scala.collection.mutable

/**
 * EndpointScanner: Detects REST endpoints in Java/Kotlin source files.
 * Supports both Spring (@GetMapping, @PostMapping, etc.) and JAX-RS (@Path, @GET, etc.) annotations.
 */
object EndpointScanner:

  // Compiled regex patterns for Spring annotations
  private lazy val springGetMappingPattern = """@GetMapping\s*(?:\(\s*(?:value\s*=\s*)?["']([^"']*)["'])?""".r
  private lazy val springPostMappingPattern = """@PostMapping\s*(?:\(\s*(?:value\s*=\s*)?["']([^"']*)["'])?""".r
  private lazy val springPutMappingPattern = """@PutMapping\s*(?:\(\s*(?:value\s*=\s*)?["']([^"']*)["'])?""".r
  private lazy val springDeleteMappingPattern = """@DeleteMapping\s*(?:\(\s*(?:value\s*=\s*)?["']([^"']*)["'])?""".r
  private lazy val springPatchMappingPattern = """@PatchMapping\s*(?:\(\s*(?:value\s*=\s*)?["']([^"']*)["'])?""".r
  private lazy val springRequestMappingPattern = """@RequestMapping\s*(?:\(\s*(?:value\s*=\s*)?["']([^"']*)["'])?""".r

  // Compiled regex patterns for JAX-RS annotations
  private lazy val jaxrsPathPattern = """@Path\s*\(\s*["']([^"']*)["']\s*\)""".r
  private lazy val jaxrsGetPattern = """@GET\b""".r
  private lazy val jaxrsPostPattern = """@POST\b""".r
  private lazy val jaxrsPutPattern = """@PUT\b""".r
  private lazy val jaxrsDeletePattern = """@DELETE\b""".r
  private lazy val jaxrsPatchPattern = """@PATCH\b""".r

  // Pattern to extract package name
  private lazy val packagePattern = """package\s+([\w.]+);""".r

  // Pattern to extract class name and declaration
  private lazy val classDeclarationPattern = """class\s+(\w+)\s*(?:\{|<|extends)""".r

  // Pattern to extract method names
  private lazy val methodDeclarationPattern = """(?:public|private|protected)?\s*(?:static)?\s*(?:\w+(?:<[^>]*>)?)\s+(\w+)\s*\(""".r

  /**
   * Scan a directory for endpoints in all Java and Kotlin source files.
   */
  def scanEndpoints(dirPath: String): Seq[Endpoint] =
    val dir = Path(dirPath)
    if !os.exists(dir) then
      throw Exception(s"Directory not found: $dirPath")

    val endpoints = mutable.ArrayBuffer[Endpoint]()
    scanDirectory(dir, endpoints)
    endpoints.toSeq

  /**
   * Recursively scan directory for source files.
   */
  private def scanDirectory(dir: Path, endpoints: mutable.ArrayBuffer[Endpoint]): Unit =
    try
      os.walk(dir, maxDepth = 100).filter { file =>
        (file.last.endsWith(".java") || file.last.endsWith(".kt")) &&
        !file.last.startsWith(".")
      }.foreach { file =>
        try
          scanFile(file.toString, endpoints)
        catch
          case e: Exception =>
            // Skip files that can't be read
      }
    catch
      case e: Exception =>
        // Continue on directory traversal errors

  /**
   * Scan a single source file for endpoints.
   */
  private def scanFile(filePath: String, endpoints: mutable.ArrayBuffer[Endpoint]): Unit =
    Using(Source.fromFile(filePath, "UTF-8")) { source =>
      val lines = source.getLines().toSeq

      // Extract package name
      val packageName = lines
        .find(line => packagePattern.matches(line))
        .flatMap { line =>
          packagePattern.findFirstMatchIn(line).map(_.group(1))
        }
        .getOrElse("")

      // Find class declarations with line numbers
      val classDeclarations = lines.zipWithIndex
        .filter { case (line, _) =>
          classDeclarationPattern.findFirstIn(line).isDefined
        }
        .map { case (line, idx) =>
          val className = classDeclarationPattern.findFirstMatchIn(line).map(_.group(1)).getOrElse("")
          val fullName = if packageName.nonEmpty then s"$packageName.$className" else className
          (fullName, idx, line)
        }

      if classDeclarations.nonEmpty then
        // Process each class
        classDeclarations.foreach { case (fullClassName, classLineIdx, classLine) =>
          // Find class-level @RequestMapping or @Path base path
          val classBasePath = extractClassBasePath(lines, classLineIdx)

          // Find the end of this class (next class or end of file)
          val nextClassIdx = classDeclarations
            .filter { case (_, idx, _) => idx > classLineIdx }
            .headOption
            .map(_._2)
            .getOrElse(lines.length)

          // Scan for methods with endpoint annotations in this class
          val classLines = lines.slice(classLineIdx, nextClassIdx)

          // Find method annotations and their corresponding methods
          classLines.zipWithIndex.foreach { case (line, relIdx) =>
            val absoluteIdx = classLineIdx + relIdx

            // Check for Spring annotations with method names
            scanSpringEndpoints(line, classLines, relIdx, classBasePath, fullClassName, absoluteIdx + 1, endpoints)

            // Check for JAX-RS annotations
            scanJaxrsEndpoints(line, classLines, relIdx, classBasePath, fullClassName, absoluteIdx + 1, endpoints)
          }
        }
    }.recover { case _ =>
      // File read error, skip
    }

  /**
   * Extract class-level base path from @RequestMapping or @Path annotation.
   */
  private def extractClassBasePath(lines: Seq[String], classLineIdx: Int): String =
    // Look backwards from class declaration for annotations
    if classLineIdx > 0 then
      val searchStart = Math.max(0, classLineIdx - 10)
      val preClassLines = lines.slice(searchStart, classLineIdx)

      preClassLines.reverse.find { line =>
        springRequestMappingPattern.findFirstIn(line).isDefined ||
        jaxrsPathPattern.findFirstIn(line).isDefined
      }.flatMap { line =>
        springRequestMappingPattern.findFirstMatchIn(line)
          .map(_.group(1))
          .orElse {
            jaxrsPathPattern.findFirstMatchIn(line).map(_.group(1))
          }
      }.getOrElse("")
    else
      ""

  /**
   * Scan for Spring endpoint annotations and extract endpoint information.
   */
  private def scanSpringEndpoints(
    line: String,
    classLines: Seq[String],
    relIdx: Int,
    classBasePath: String,
    fullClassName: String,
    lineNumber: Int,
    endpoints: mutable.ArrayBuffer[Endpoint]
  ): Unit =
    val (method, pathOpt) = line match
      case l if springGetMappingPattern.findFirstIn(l).isDefined =>
        ("GET", springGetMappingPattern.findFirstMatchIn(l).flatMap(m => Option(m.group(1))))
      case l if springPostMappingPattern.findFirstIn(l).isDefined =>
        ("POST", springPostMappingPattern.findFirstMatchIn(l).flatMap(m => Option(m.group(1))))
      case l if springPutMappingPattern.findFirstIn(l).isDefined =>
        ("PUT", springPutMappingPattern.findFirstMatchIn(l).flatMap(m => Option(m.group(1))))
      case l if springDeleteMappingPattern.findFirstIn(l).isDefined =>
        ("DELETE", springDeleteMappingPattern.findFirstMatchIn(l).flatMap(m => Option(m.group(1))))
      case l if springPatchMappingPattern.findFirstIn(l).isDefined =>
        ("PATCH", springPatchMappingPattern.findFirstMatchIn(l).flatMap(m => Option(m.group(1))))
      case _ => ("", None)

    if method.nonEmpty then
      // Find the method name in the next few lines
      val methodLineSearch = classLines.drop(relIdx + 1).zipWithIndex.find { case (l, _) =>
        methodDeclarationPattern.findFirstIn(l).isDefined
      }

      methodLineSearch.foreach { case (l, methodOffset) =>
        val handlerName = methodDeclarationPattern.findFirstMatchIn(l).map(_.group(1)).getOrElse("unknown")
        val methodPath = pathOpt.getOrElse("")
        val fullPath = combinePaths(classBasePath, methodPath)
        val methodLineNumber = lineNumber + methodOffset + 1

        endpoints += Endpoint(
          path = fullPath,
          http_method = method,
          class_name = fullClassName,
          handler_name = handlerName,
          line_number = methodLineNumber
        )
      }

  /**
   * Scan for JAX-RS endpoint annotations and extract endpoint information.
   */
  private def scanJaxrsEndpoints(
    line: String,
    classLines: Seq[String],
    relIdx: Int,
    classBasePath: String,
    fullClassName: String,
    lineNumber: Int,
    endpoints: mutable.ArrayBuffer[Endpoint]
  ): Unit =
    val method = line match
      case l if jaxrsGetPattern.findFirstIn(l).isDefined => "GET"
      case l if jaxrsPostPattern.findFirstIn(l).isDefined => "POST"
      case l if jaxrsPutPattern.findFirstIn(l).isDefined => "PUT"
      case l if jaxrsDeletePattern.findFirstIn(l).isDefined => "DELETE"
      case l if jaxrsPatchPattern.findFirstIn(l).isDefined => "PATCH"
      case _ => ""

    if method.nonEmpty then
      // For JAX-RS, path is in @Path annotation
      // Look for @Path on same line or before
      val pathOpt = if line.contains("@Path") then
        jaxrsPathPattern.findFirstMatchIn(line).map(_.group(1))
      else
        // Look backwards in class lines for @Path (but skip class-level @Path)
        classLines.slice(Math.max(0, relIdx - 5), relIdx).reverse
          .find(jaxrsPathPattern.findFirstIn(_).isDefined)
          .flatMap(jaxrsPathPattern.findFirstMatchIn(_).map(_.group(1)))

      // Find the method name in the next few lines
      val methodLineSearch = classLines.drop(relIdx + 1).zipWithIndex.find { case (l, _) =>
        methodDeclarationPattern.findFirstIn(l).isDefined
      }

      methodLineSearch.foreach { case (l, methodOffset) =>
        val handlerName = methodDeclarationPattern.findFirstMatchIn(l).map(_.group(1)).getOrElse("unknown")
        val methodPath = pathOpt.getOrElse("")
        val fullPath = combinePaths(classBasePath, methodPath)
        val methodLineNumber = lineNumber + methodOffset + 1

        endpoints += Endpoint(
          path = fullPath,
          http_method = method,
          class_name = fullClassName,
          handler_name = handlerName,
          line_number = methodLineNumber
        )
      }

  /**
   * Combine class-level and method-level paths.
   */
  private def combinePaths(classPath: String, methodPath: String): String =
    val cp = if classPath.isEmpty then "/" else if classPath.startsWith("/") then classPath else "/" + classPath
    val mp = if methodPath.isEmpty then "" else if methodPath.startsWith("/") then methodPath else "/" + methodPath

    if mp.isEmpty then cp
    else if cp == "/" then mp
    else cp + mp
