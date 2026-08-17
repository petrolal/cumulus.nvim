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

