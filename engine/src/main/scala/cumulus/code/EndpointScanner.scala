package cumulus.code

import os.Path
import scala.collection.mutable

/**
 * EndpointScanner: Detects REST endpoints in Java/Kotlin source files.
 * Supports both Spring (@GetMapping, @PostMapping, etc.) and JAX-RS (@Path, @GET, etc.) annotations.
 */
object EndpointScanner:

  // Compiled regex patterns for Spring annotations
  private lazy val springGetMappingPattern = """@GetMapping\s*(?:\(\s*(?:value\s*=\s*|path\s*=\s*)?["']([^"']*)["'])?""".r
  private lazy val springPostMappingPattern = """@PostMapping\s*(?:\(\s*(?:value\s*=\s*|path\s*=\s*)?["']([^"']*)["'])?""".r
  private lazy val springPutMappingPattern = """@PutMapping\s*(?:\(\s*(?:value\s*=\s*|path\s*=\s*)?["']([^"']*)["'])?""".r
  private lazy val springDeleteMappingPattern = """@DeleteMapping\s*(?:\(\s*(?:value\s*=\s*|path\s*=\s*)?["']([^"']*)["'])?""".r
  private lazy val springPatchMappingPattern = """@PatchMapping\s*(?:\(\s*(?:value\s*=\s*|path\s*=\s*)?["']([^"']*)["'])?""".r
  private lazy val springRequestMappingPattern = """@RequestMapping\s*(?:\(\s*(?:value\s*=\s*|path\s*=\s*)?["']([^"']*)["'])?""".r
  private lazy val springRequestMethodPattern = """method\s*=\s*(?:RequestMethod\.)?([A-Z]+)""".r

  // Compiled regex patterns for JAX-RS annotations
  private lazy val jaxrsPathPattern = """@Path\s*\(\s*["']([^"']*)["']\s*\)""".r
  private lazy val jaxrsGetPattern = """@GET\b""".r
  private lazy val jaxrsPostPattern = """@POST\b""".r
  private lazy val jaxrsPutPattern = """@PUT\b""".r
  private lazy val jaxrsDeletePattern = """@DELETE\b""".r
  private lazy val jaxrsPatchPattern = """@PATCH\b""".r

  // Pattern to extract package name (Java & Kotlin)
  private lazy val packagePattern = """^\s*package\s+([\w.]+)\s*;?""".r

  // Pattern to extract class name and declaration
  private lazy val classDeclarationPattern = """(?:public|protected|private|final|open|abstract|\s)*\bclass\s+(\w+)\s*(?:\{|<|extends|\(|\n)""".r

  // Pattern to extract method names (Java & Kotlin)
  private lazy val javaMethodPattern = """(?:public|private|protected)?\s*(?:static\s+|final\s+)?(?:void|[\w.<>?,\s]+)\s+(\w+)\s*\(""".r
  private lazy val kotlinMethodPattern = """(?:override\s+)?fun\s+(\w+)\s*\(""".r


  /**
   * Scan a directory for endpoints in all Java and Kotlin source files.
   */
  def scanEndpoints(dirPath: String): Seq[Endpoint] =
    val dir = Path(dirPath, os.pwd)
    if !os.exists(dir) || !os.isDir(dir) then
      throw Exception(s"Directory not found: $dirPath")

    val endpoints = mutable.ArrayBuffer[Endpoint]()
    scanDirectory(dir, endpoints)
    endpoints.toSeq

  /**
   * Recursively scan directory for source files.
   */
  private def scanDirectory(dir: Path, endpoints: mutable.ArrayBuffer[Endpoint]): Unit =
    try
      val files = os.walk(dir).filter { file =>
        os.isFile(file) &&
        (file.last.endsWith(".java") || file.last.endsWith(".kt")) &&
        !file.last.startsWith(".")
      }
      for file <- files do
        try
          scanFile(file.toString, endpoints)
        catch
          case _: Exception => // Skip files that can't be read
    catch
      case _: Exception => // Continue on directory traversal errors

  /**
   * Scan a single source file for endpoints.
   */
  private def scanFile(filePath: String, endpoints: mutable.ArrayBuffer[Endpoint]): Unit =
    try
      val p = Path(filePath, os.pwd)
      val lines = os.read.lines(p, charSet = java.nio.charset.StandardCharsets.UTF_8).toList

      // Extract package name
      val packageName = lines
        .find(line => packagePattern.findFirstIn(line).isDefined)
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
    catch
      case _: Exception => // File read error, skip

  /**
   * Extract class-level base path from @RequestMapping or @Path annotation.
   */
  private def extractClassBasePath(lines: Seq[String], classLineIdx: Int): String =
    if classLineIdx > 0 then
      val searchStart = Math.max(0, classLineIdx - 10)
      val preClassLines = lines.slice(searchStart, classLineIdx)

      preClassLines.reverse.find { line =>
        springRequestMappingPattern.findFirstIn(line).isDefined ||
        jaxrsPathPattern.findFirstIn(line).isDefined
      }.flatMap { line =>
        springRequestMappingPattern.findFirstMatchIn(line)
          .flatMap(m => Option(m.group(1)))
          .orElse {
            jaxrsPathPattern.findFirstMatchIn(line).flatMap(m => Option(m.group(1)))
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
      case l if springRequestMappingPattern.findFirstIn(l).isDefined =>
        val httpMethod = springRequestMethodPattern.findFirstMatchIn(l).map(_.group(1)).getOrElse("GET")
        (httpMethod, springRequestMappingPattern.findFirstMatchIn(l).flatMap(m => Option(m.group(1))))
      case _ => ("", None)

    if method.nonEmpty then
      // Find the method name in the next few lines
      val methodLineSearch = classLines.drop(relIdx + 1).zipWithIndex.find { case (l, _) =>
        javaMethodPattern.findFirstIn(l).isDefined || kotlinMethodPattern.findFirstIn(l).isDefined
      }

      methodLineSearch.foreach { case (l, methodOffset) =>
        val handlerName = javaMethodPattern.findFirstMatchIn(l).map(_.group(1))
          .orElse(kotlinMethodPattern.findFirstMatchIn(l).map(_.group(1)))
          .getOrElse("unknown")
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
      // For JAX-RS, method name comes after the HTTP method annotation
      val methodLineSearch = classLines.drop(relIdx + 1).zipWithIndex.find { case (l, _) =>
        javaMethodPattern.findFirstIn(l).isDefined || kotlinMethodPattern.findFirstIn(l).isDefined
      }

      methodLineSearch.foreach { case (l, methodOffset) =>
        // Path is in @Path annotation on same line, or strictly between current annotation and method declaration
        val pathOpt = if line.contains("@Path") then
          jaxrsPathPattern.findFirstMatchIn(line).flatMap(m => Option(m.group(1)))
        else
          // Check lines between annotation and method (e.g. @GET \n @Path("/{id}") \n method)
          val intermediateLines = classLines.slice(relIdx + 1, relIdx + 1 + methodOffset)
          intermediateLines.find(jaxrsPathPattern.findFirstIn(_).isDefined)
            .flatMap(jaxrsPathPattern.findFirstMatchIn(_).flatMap(m => Option(m.group(1))))
            .orElse {
              // Check immediately preceding line (e.g. @Path("/{id}") \n @GET \n method)
              classLines.slice(Math.max(0, relIdx - 2), relIdx).reverse
                .find(jaxrsPathPattern.findFirstIn(_).isDefined)
                .flatMap(jaxrsPathPattern.findFirstMatchIn(_).flatMap(m => Option(m.group(1))))
            }

        val handlerName = javaMethodPattern.findFirstMatchIn(l).map(_.group(1))
          .orElse(kotlinMethodPattern.findFirstMatchIn(l).map(_.group(1)))
          .getOrElse("unknown")
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
    val cp = if classPath == null || classPath.isEmpty then "" else classPath.trim
    val mp = if methodPath == null || methodPath.isEmpty then "" else methodPath.trim

    val cleanCp = if cp.endsWith("/") && cp.length > 1 then cp.dropRight(1) else cp
    val cleanMp = if mp.startsWith("/") then mp else if mp.nonEmpty then "/" + mp else ""

    val combined = if cleanCp.isEmpty && cleanMp.isEmpty then "/"
    else if cleanCp.isEmpty then cleanMp
    else if cleanMp.isEmpty then if cleanCp.startsWith("/") then cleanCp else "/" + cleanCp
    else {
      val base = if cleanCp.startsWith("/") then cleanCp else "/" + cleanCp
      if base == "/" then cleanMp else base + cleanMp
    }

    if combined.isEmpty then "/" else combined

