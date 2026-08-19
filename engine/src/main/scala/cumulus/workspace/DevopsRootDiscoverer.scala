package cumulus.workspace

import os.Path
import scala.collection.mutable
import cumulus.protocol.{CumulusResponse, CumulusError}

object DevopsRootDiscoverer:

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

  /**
   * Discover DevOps & IaC tool roots given a file or directory path.
   *
   * Performs dual-directional discovery:
   * 1. Upward search from start path to locate the workspace root.
   * 2. Bounded downward scan from workspace root to discover all tool roots (Terraform, SAM, Ansible, Docker, Helm).
   *
   * @param startPath Starting file or directory path
   * @return CumulusResponse containing DevopsRoots
   */
  def discoverDevopsRoots(startPath: String): CumulusResponse[DevopsRoots] =
    try
      val target = Path(startPath, os.pwd)
      if !os.exists(target) then
        return CumulusResponse(
          success = false,
          data = None,
          error = Some(s"Path not found: $startPath"),
          error_code = Some("FILE_NOT_FOUND")
        )

      val startDir = if os.isFile(target) then
        if target.segmentCount > 0 then target / os.up else target
      else target

      val workspaceRoot = findWorkspaceRoot(startDir)
      val roots = scanDevopsRoots(workspaceRoot)

      CumulusResponse(
        success = true,
        data = Some(roots),
        error = None,
        error_code = None
      )
    catch
      case e: Exception =>
        CumulusResponse(
          success = false,
          data = None,
          error = Some(s"Error discovering devops roots: ${e.getMessage}"),
          error_code = Some("INTERNAL_ERROR")
        )

  private def findWorkspaceRoot(start: Path): Path =
    var current = start
    var foundGitRoot: Option[Path] = None
    var highestMarkerRoot: Option[Path] = None
    var keepScanning = true

    while keepScanning do
      if isWorkspaceMarkerDir(current) then
        highestMarkerRoot = Some(current)
      if os.exists(current / ".git") then
        foundGitRoot = Some(current)

      if current.segmentCount == 0 then
        keepScanning = false
      else
        val parent = current / os.up
        if parent == current then
          keepScanning = false
        else
          current = parent

    foundGitRoot
      .orElse(highestMarkerRoot)
      .getOrElse(start)

  private def isWorkspaceMarkerDir(dir: Path): Boolean =
    if !os.exists(dir) || !os.isDir(dir) then false
    else
      val rootMarkers = Set(
        ".git",
        "pom.xml",
        "build.gradle",
        "build.gradle.kts",
        "settings.gradle",
        "settings.gradle.kts",
        "build.sbt",
        "package.json",
        "Cargo.toml",
        "go.mod",
        "Makefile",
        ".mvn",
        ".gradle"
      )
      val files = try os.list(dir).filter(os.isFile).map(_.last).toSet catch case _: Exception => Set.empty[String]
      val subdirs = try os.list(dir).filter(os.isDir).map(_.last).toSet catch case _: Exception => Set.empty[String]

      rootMarkers.exists(m => files.contains(m) || subdirs.contains(m)) ||
      hasDevopsMarkers(files, subdirs)

  private def hasDevopsMarkers(files: Set[String], subdirs: Set[String]): Boolean =
    files.contains("main.tf") ||
    files.contains("terragrunt.hcl") ||
    files.exists(f => f.endsWith(".tf") || f.endsWith(".tofu")) ||
    files.contains("template.yaml") ||
    files.contains("template.yml") ||
    files.contains("samconfig.toml") ||
    files.contains("ansible.cfg") ||
    files.contains("playbook.yml") ||
    files.contains("playbook.yaml") ||
    files.contains("site.yml") ||
    files.contains("site.yaml") ||
    files.contains("hosts") ||
    files.contains("inventory.ini") ||
    files.contains("inventory.yaml") ||
    files.contains("inventory.yml") ||
    subdirs.contains("roles") ||
    subdirs.contains("group_vars") ||
    subdirs.contains("host_vars") ||
    files.contains("Dockerfile") ||
    files.contains("docker-compose.yml") ||
    files.contains("docker-compose.yaml") ||
    files.contains("compose.yml") ||
    files.contains("compose.yaml") ||
    files.exists(f => f.startsWith("Dockerfile")) ||
    files.contains("Chart.yaml") ||
    files.contains("Chart.yml")

  private def scanDevopsRoots(workspaceRoot: Path): DevopsRoots =
    val terraformRoots = mutable.ListBuffer[String]()
    val samRoots = mutable.ListBuffer[String]()
    val ansibleRoots = mutable.ListBuffer[String]()
    val dockerRoots = mutable.ListBuffer[String]()
    val helmRoots = mutable.ListBuffer[String]()

    def inspectDir(dir: Path): Unit =
      val files = try os.list(dir).filter(os.isFile).map(_.last).toSet catch case _: Exception => Set.empty[String]
      val subdirs = try os.list(dir).filter(os.isDir).map(_.last).toSet catch case _: Exception => Set.empty[String]

      // 1. Terraform / OpenTofu
      if files.contains("main.tf") || files.contains("terragrunt.hcl") || files.exists(f => f.endsWith(".tf") || f.endsWith(".tofu")) then
        terraformRoots += dir.toString

      // 2. SAM / CloudFormation
      if files.contains("template.yaml") || files.contains("template.yml") || files.contains("samconfig.toml") then
        samRoots += dir.toString

      // 3. Ansible
      if files.contains("ansible.cfg") || files.contains("playbook.yml") || files.contains("playbook.yaml") ||
         files.contains("site.yml") || files.contains("site.yaml") || files.contains("hosts") ||
         files.contains("inventory.ini") || files.contains("inventory.yaml") || files.contains("inventory.yml") ||
         subdirs.contains("roles") || subdirs.contains("group_vars") || subdirs.contains("host_vars") then
        ansibleRoots += dir.toString

      // 4. Docker
      if files.contains("Dockerfile") || files.contains("docker-compose.yml") || files.contains("docker-compose.yaml") ||
         files.contains("compose.yml") || files.contains("compose.yaml") || files.exists(f => f.startsWith("Dockerfile")) then
        dockerRoots += dir.toString

      // 5. Helm
      if files.contains("Chart.yaml") || files.contains("Chart.yml") then
        helmRoots += dir.toString

    def walk(current: Path, depth: Int, maxDepth: Int): Unit =
      if depth <= maxDepth && os.exists(current) && os.isDir(current) then
        inspectDir(current)
        val entries = try os.list(current) catch case _: Exception => Seq.empty
        for entry <- entries if os.isDir(entry) do
          val dirName = entry.last
          if !ignoredDirectories.contains(dirName) && !dirName.startsWith(".") then
            walk(entry, depth + 1, maxDepth)

    walk(workspaceRoot, depth = 1, maxDepth = 6)

    DevopsRoots(
      workspace_root = workspaceRoot.toString,
      terraform = terraformRoots.distinct.sorted.toSeq,
      sam = samRoots.distinct.sorted.toSeq,
      ansible = ansibleRoots.distinct.sorted.toSeq,
      docker = dockerRoots.distinct.sorted.toSeq,
      helm = helmRoots.distinct.sorted.toSeq
    )
