package cumulus.code

import java.nio.file.{Paths, Files}
import java.io.File

/**
 * JavaHeaderGenerator: Infers package name from directory structure and generates Java class boilerplate.
 */
object JavaHeaderGenerator:

  /**
   * Generate Java header for a given file path.
   * Infers package from directory structure and returns package statement and class declaration.
   */
  def generateHeader(filePath: String): JavaHeader =
    val file = new File(filePath)
    if !file.exists() then
      throw Exception(s"File not found: $filePath")

    // Extract class name from filename (remove .java or .kt extension)
    val className = file.getName match
      case name if name.endsWith(".java") => name.substring(0, name.length - 5)
      case name if name.endsWith(".kt") => name.substring(0, name.length - 3)
      case name => name

    // Infer package from directory structure
    val absolutePath = file.getAbsolutePath
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

    // Try to find the source root and extract package
    val sourceRoots = Seq(
      "/src/main/java/",
      "/src/main/kotlin/",
      "/src/test/java/",
      "/src/test/kotlin/",
      "/src/"
    )

    sourceRoots.foreach { root =>
      if path.contains(root) then
        val startIdx = path.indexOf(root) + root.length
        val endIdx = path.lastIndexOf('/')
        if startIdx <= endIdx then
          val packagePath = path.substring(startIdx, endIdx)
          return packagePath.replace('/', '.')
    }

    // If no standard source root found, try generic "src" folder
    if path.contains("/src/") then
      val srcIdx = path.indexOf("/src/") + 5
      val endIdx = path.lastIndexOf('/')
      if srcIdx <= endIdx then
        val packagePath = path.substring(srcIdx, endIdx)
        return packagePath.replace('/', '.')

    // Default: empty package (no package statement)
    ""
