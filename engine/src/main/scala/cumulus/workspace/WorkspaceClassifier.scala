package cumulus.workspace

import os.Path
import scala.collection.mutable
import cumulus.protocol.{CumulusResponse, CumulusError}

object WorkspaceClassifier:

  private val ignoredDirectories = Set(
    ".git",
    "node_modules",
    ".terraform",
    "target",
    "build",
    ".gradle",
    ".idea",
    ".bloop",
    ".metals",
    ".cache",
    "dist",
    "out",
    ".svn",
    ".hg"
  )

  private val jvmMarkerMap = Map(
    "pom.xml" -> "maven",
    "build.gradle" -> "gradle",
    "build.gradle.kts" -> "gradle",
    "settings.gradle" -> "gradle",
    "settings.gradle.kts" -> "gradle",
    "build.sbt" -> "sbt"
  )

  private val iacMarkerMap = Map(
    "main.tf" -> "terraform",
    "terragrunt.hcl" -> "terraform",
    "template.yaml" -> "sam",
    "template.yml" -> "sam",
    "samconfig.toml" -> "sam",
    "ansible.cfg" -> "ansible",
    "playbook.yml" -> "ansible",
    "playbook.yaml" -> "ansible",
    "site.yml" -> "ansible",
    "site.yaml" -> "ansible",
    "hosts" -> "ansible",
    "inventory.ini" -> "ansible",
    "inventory.yaml" -> "ansible",
    "inventory.yml" -> "ansible",
    "Dockerfile" -> "docker",
    "docker-compose.yml" -> "docker",
    "docker-compose.yaml" -> "docker",
    "Chart.yaml" -> "helm"
  )

  /**
   * Classify the workspace topology for a directory.
   *
   * @param dirPath The directory to scan
   * @return CumulusResponse containing WorkspaceTopology
   */
  def classifyWorkspace(dirPath: String): CumulusResponse[WorkspaceTopology] =
    try
      val root = Path(dirPath, os.pwd)
      if !os.exists(root) || !os.isDir(root) then
        return CumulusResponse(
          success = false,
          data = None,
          error = Some(s"Directory not found: $dirPath"),
          error_code = Some("FILE_NOT_FOUND")
        )

      val topology = scanTopology(root)
      CumulusResponse(
        success = true,
        data = Some(topology),
        error = None,
        error_code = None
      )
    catch
      case e: Exception =>
        CumulusResponse(
          success = false,
          data = None,
          error = Some(s"Error classifying workspace: ${e.getMessage}"),
          error_code = Some("INTERNAL_ERROR")
        )

  private def scanTopology(root: Path): WorkspaceTopology =
    val submodules = mutable.ListBuffer[ProjectSubmodule]()
    val detectedProjectTypes = mutable.Set[String]()
    val detectedIacTypes = mutable.Set[String]()
    var hasSpring = false

    // 1. Check root directory directly for marker files & wrappers
    val rootFiles = if os.exists(root) && os.isDir(root) then
      try os.list(root).filter(os.isFile).map(_.last).toSet
      catch case _: Exception => Set.empty[String]
    else Set.empty[String]

    val rootJvmTypes = detectJvmTypesInFiles(rootFiles)
    val rootIacTypes = detectIacTypesInFiles(rootFiles)

    // Check if root has spring indicators
    if detectSpringInDir(root) then
      hasSpring = true

    // 2. Discover subprojects and nested modules (capped depth, ignoring build/cache dirs)
    val discoveredDirs = scanDirectories(root, maxDepth = 4)

    for dir <- discoveredDirs do
      val filesInDir = try os.list(dir).filter(os.isFile).map(_.last).toSet catch case _: Exception => Set.empty[String]
      val relPath = dir.relativeTo(root).toString
      val dirName = dir.last

      if !hasSpring && detectSpringInDir(dir) then
        hasSpring = true

      // Check for JVM submodule
      val jvmTypes = detectJvmTypesInFiles(filesInDir)
      for jvmType <- jvmTypes do
        val (wrapperOpt, hasWrapper) = checkWrapper(dir, jvmType)
        submodules += ProjectSubmodule(
          name = dirName,
          path = relPath,
          project_type = jvmType,
          build_tool = Some(jvmType),
          has_wrapper = hasWrapper
        )
        detectedProjectTypes += jvmType

      // Check for IaC submodule (e.g. terraform, ansible, helm, docker)
      val iacTypes = detectIacTypesInFiles(filesInDir)
      for iacType <- iacTypes do
        detectedIacTypes += iacType
        detectedProjectTypes += iacType
        if !jvmTypes.contains(iacType) then
          submodules += ProjectSubmodule(
            name = dirName,
            path = relPath,
            project_type = iacType,
            build_tool = None,
            has_wrapper = false
          )

    // Merge root-level detections
    detectedProjectTypes ++= rootJvmTypes
    detectedProjectTypes ++= rootIacTypes
    detectedIacTypes ++= rootIacTypes

    // Multi-module detection:
    val isMultiModule = submodules.size > 1 || (submodules.nonEmpty && detectedProjectTypes.exists(t => t == "maven" || t == "gradle" || t == "sbt")) || detectRootMultiModule(root, rootFiles)

    // Determine primary_type
    val primaryType = determinePrimaryType(detectedProjectTypes.toSet, detectedIacTypes.toSet)

    WorkspaceTopology(
      root = root.toString,
      primary_type = primaryType,
      project_types = detectedProjectTypes.toSeq.sorted,
      submodules = submodules.toSeq.sortBy(_.path),
      has_spring = hasSpring,
      iac_types = detectedIacTypes.toSeq.sorted,
      is_multi_module = isMultiModule
    )

  /**
   * Bounded directory traversal skipping noise and cache directories.
   */
  private def scanDirectories(root: Path, maxDepth: Int): Seq[Path] =
    val result = mutable.ListBuffer[Path]()

    def walk(current: Path, depth: Int): Unit =
      if depth <= maxDepth && os.exists(current) && os.isDir(current) then
        val entries = try os.list(current) catch case _: Exception => Seq.empty
        for entry <- entries if os.isDir(entry) do
          val dirName = entry.last
          if !ignoredDirectories.contains(dirName) && !dirName.startsWith(".") then
            // Check if this directory contains any marker files
            val entryFiles = try os.list(entry).filter(os.isFile).map(_.last).toSet catch case _: Exception => Set.empty[String]
            val hasMarkers = jvmMarkerMap.keys.exists(entryFiles.contains) ||
              iacMarkerMap.keys.exists(entryFiles.contains) ||
              entryFiles.exists(_.endsWith(".tf")) ||
              entryFiles.exists(_.endsWith(".tofu")) ||
              entryFiles.exists(f => f.startsWith("Dockerfile"))

            if hasMarkers then
              result += entry

            // Recurse into children
            walk(entry, depth + 1)

    walk(root, 1)
    result.toSeq

  private def detectJvmTypesInFiles(files: Set[String]): Set[String] =
    val types = mutable.Set[String]()
    if files.contains("pom.xml") then types += "maven"
    if files.contains("build.gradle") || files.contains("build.gradle.kts") || files.contains("settings.gradle") || files.contains("settings.gradle.kts") then types += "gradle"
    if files.contains("build.sbt") then types += "sbt"
    types.toSet

  private def detectIacTypesInFiles(files: Set[String]): Set[String] =
    val types = mutable.Set[String]()
    if files.contains("main.tf") || files.contains("terragrunt.hcl") || files.exists(_.endsWith(".tf")) then types += "terraform"
    if files.contains("template.yaml") || files.contains("template.yml") || files.contains("samconfig.toml") then types += "sam"
    if files.contains("ansible.cfg") || files.contains("playbook.yml") || files.contains("playbook.yaml") || files.contains("site.yml") || files.contains("site.yaml") || files.contains("hosts") || files.contains("inventory.ini") || files.contains("inventory.yaml") || files.contains("inventory.yml") then types += "ansible"
    if files.contains("Dockerfile") || files.contains("docker-compose.yml") || files.contains("docker-compose.yaml") || files.exists(_.startsWith("Dockerfile")) then types += "docker"
    if files.contains("Chart.yaml") then types += "helm"
    types.toSet

  private def checkWrapper(dir: Path, buildTool: String): (Option[String], Boolean) =
    buildTool match
      case "maven" =>
        val mvnw = dir / "mvnw"
        if os.exists(mvnw) && os.isFile(mvnw) then (Some(mvnw.toString), true)
        else (None, false)
      case "gradle" =>
        val gradlew = dir / "gradlew"
        if os.exists(gradlew) && os.isFile(gradlew) then (Some(gradlew.toString), true)
        else (None, false)
      case _ => (None, false)

  private def detectSpringInDir(dir: Path): Boolean =
    try
      // Check for application.yml or application.properties in dir or dir/src/main/resources
      val appYml = dir / "application.yml"
      val appProps = dir / "application.properties"
      val resAppYml = dir / "src" / "main" / "resources" / "application.yml"
      val resAppProps = dir / "src" / "main" / "resources" / "application.properties"
      if (os.exists(appYml) && os.isFile(appYml)) ||
         (os.exists(appProps) && os.isFile(appProps)) ||
         (os.exists(resAppYml) && os.isFile(resAppYml)) ||
         (os.exists(resAppProps) && os.isFile(resAppProps)) then
        return true

      // Check for pom.xml containing spring or spring-boot
      val pom = dir / "pom.xml"
      if os.exists(pom) && os.isFile(pom) then
        val content = os.read(pom)
        if content.contains("spring-boot") || content.contains("org.springframework") then
          return true

      // Check for build.gradle or build.gradle.kts containing spring
      val bg = dir / "build.gradle"
      val bgKts = dir / "build.gradle.kts"
      if os.exists(bg) && os.isFile(bg) then
        val content = os.read(bg)
        if content.contains("org.springframework") || content.contains("spring-boot") || content.contains("org.springframework.boot") then
          return true
      if os.exists(bgKts) && os.isFile(bgKts) then
        val content = os.read(bgKts)
        if content.contains("org.springframework") || content.contains("spring-boot") || content.contains("org.springframework.boot") then
          return true

      false
    catch
      case _: Exception => false

  private def detectRootMultiModule(root: Path, rootFiles: Set[String]): Boolean =
    try
      if rootFiles.contains("pom.xml") then
        val pom = root / "pom.xml"
        if os.exists(pom) && os.isFile(pom) then
          val content = os.read(pom)
          val modulesPattern = """(?s)<modules>.*?<module>.*?</module>.*?</modules>""".r
          if modulesPattern.findFirstIn(content).isDefined then return true

      if rootFiles.contains("settings.gradle") then
        val settings = root / "settings.gradle"
        if os.exists(settings) && os.isFile(settings) then
          val content = os.read(settings)
          if content.matches("""(?s).*\binclude\s+['"]?[\w:/-]+['"]?.*""") then return true

      if rootFiles.contains("settings.gradle.kts") then
        val settingsKts = root / "settings.gradle.kts"
        if os.exists(settingsKts) && os.isFile(settingsKts) then
          val content = os.read(settingsKts)
          if content.matches("""(?s).*\binclude\s*\(\s*['"]?[\w:/-]+['"]?.*""") || content.matches("""(?s).*\binclude\s+['"]?[\w:/-]+['"]?.*""") then return true

      false
    catch
      case _: Exception => false

  private def determinePrimaryType(projectTypes: Set[String], iacTypes: Set[String]): String =
    val jvmTypes = projectTypes.intersect(Set("maven", "gradle", "sbt"))
    val hasJvm = jvmTypes.nonEmpty
    val hasIac = iacTypes.nonEmpty

    if hasJvm && hasIac then
      "polyglot"
    else if jvmTypes.size > 1 then
      "polyglot"
    else if hasJvm then
      jvmTypes.head
    else if hasIac then
      if iacTypes.size > 1 then "devops"
      else iacTypes.head
    else
      "unknown"
