package cumulus.workspace

import os.Path
import scala.xml.{XML, Node}
import scala.collection.mutable
import java.io.File

/**
 * Resolves Maven project classpath by parsing pom.xml and scanning ~/.m2/repository/.
 */
object MavenClasspathResolver:

  /**
   * Resolve classpath for a Maven project.
   * Parses pom.xml and scans ~/.m2/repository/ for dependency jars.
   *
   * @param projectRoot The root directory of the Maven project
   * @return Seq[String] of jar file paths
   */
  def resolveClasspath(projectRoot: String): Seq[String] =
    try
      val rootPath = Path(projectRoot)
      val pomFile = rootPath / "pom.xml"

      if !os.exists(pomFile) || !os.isFile(pomFile) then
        return Seq.empty

      // Parse pom.xml to get dependencies
      val pom = XML.loadFile(new File(pomFile.toString))
      val dependencies = extractDependencies(pom)

      if dependencies.isEmpty then
        return Seq.empty

      // Scan ~/.m2/repository for matching jars
      val home = sys.env.getOrElse("HOME", System.getProperty("user.home"))
      val m2Repo = Path(home) / ".m2" / "repository"

      if !os.exists(m2Repo) || !os.isDir(m2Repo) then
        return Seq.empty

      val jars = mutable.ListBuffer[String]()
      for dep <- dependencies do
        val jarPath = findJarInRepository(m2Repo, dep)
        jarPath.foreach(jars += _)

      jars.toSeq
    catch
      case _: Exception => Seq.empty

  /**
   * Extract dependencies from pom.xml.
   * Returns tuple of (groupId, artifactId, version).
   */
  private def extractDependencies(pom: scala.xml.Elem): Seq[(String, String, String)] =
    try
      val dependencies = mutable.ListBuffer[(String, String, String)]()

      // Extract from <dependencies> block
      val depNodes = (pom \ "dependencies" \ "dependency")
      for depNode <- depNodes do
        val groupId = (depNode \ "groupId").text.trim
        val artifactId = (depNode \ "artifactId").text.trim
        val version = (depNode \ "version").text.trim
        val scope = (depNode \ "scope").text.trim

        // Skip test and provided scopes
        if groupId.nonEmpty && artifactId.nonEmpty && version.nonEmpty &&
          scope != "test" && scope != "provided" then
          dependencies += ((groupId, artifactId, version))


      dependencies.toSeq
    catch
      case _: Exception => Seq.empty

  /**
   * Find a jar in ~/.m2/repository for a given dependency.
   * Converts groupId to directory path and looks for artifactId-version.jar.
   */
  private def findJarInRepository(m2Repo: Path, dep: (String, String, String)): Option[String] =
    try
      val (groupId, artifactId, version) = dep

      // Convert groupId (com.example) to path (com/example)
      val groupPath = groupId.replace(".", "/")

      // Build expected jar path: com/example/artifact/version/artifact-version.jar
      val jarPath = m2Repo / groupPath / artifactId / version / s"$artifactId-$version.jar"

      if os.exists(jarPath) && os.isFile(jarPath) then
        Some(jarPath.toString)
      else
        None
    catch
      case _: Exception => None
