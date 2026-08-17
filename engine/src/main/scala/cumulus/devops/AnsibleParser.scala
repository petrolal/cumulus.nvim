package cumulus.devops

import cumulus.protocol.{CumulusError, CumulusResponse}
import os.Path
import scala.collection.mutable.ListBuffer
import scala.util.matching.Regex

/**
 * Ansible playbook parser, offline syntax/lint validator, and inventory graph transformer.
 * Analyzes YAML playbooks and inventory outputs without external heavy reflection-based YAML parsers.
 */
object AnsibleParser:

  /**
   * Inspect an Ansible playbook YAML content and extract plays, target hosts, roles, and tasks.
   */
  def inspectPlaybook(content: String): AnsiblePlaybookInfo =
    val trimmed = content.trim
    if trimmed.isEmpty then return AnsiblePlaybookInfo()

    val lines = content.linesIterator.toList
    val plays = ListBuffer[AnsiblePlayInfo]()

    var currentPlayName = ""
    var currentPlayHosts = "all"
    var currentPlayGatherFacts: Option[Boolean] = None
    var currentPlayRoles = ListBuffer[String]()
    var currentPlayTasks = ListBuffer[AnsibleTaskInfo]()
    var currentPlayLine = 0

    var inRolesSection = false
    var inTasksSection = false
    var currentTaskName = ""
    var currentTaskModule = "command"
    var currentTaskLine = 0
    var inTaskItem = false

    def flushCurrentTask(): Unit =
      if inTaskItem then
        val tName = if currentTaskName.nonEmpty then currentTaskName else currentTaskModule
        currentPlayTasks += AnsibleTaskInfo(tName, currentTaskModule, Some(currentTaskLine))
        inTaskItem = false
        currentTaskName = ""
        currentTaskModule = "command"
        currentTaskLine = 0

    def flushCurrentPlay(): Unit =
      flushCurrentTask()
      if currentPlayLine > 0 || currentPlayName.nonEmpty || currentPlayTasks.nonEmpty || currentPlayRoles.nonEmpty then
        val pName = if currentPlayName.nonEmpty then currentPlayName else s"Play ($currentPlayHosts)"
        plays += AnsiblePlayInfo(
          name = pName,
          hosts = currentPlayHosts,
          gather_facts = currentPlayGatherFacts,
          roles = currentPlayRoles.toSeq,
          tasks = currentPlayTasks.toSeq,
          line = Some(currentPlayLine)
        )
      currentPlayName = ""
      currentPlayHosts = "all"
      currentPlayGatherFacts = None
      currentPlayRoles.clear()
      currentPlayTasks.clear()
      currentPlayLine = 0
      inRolesSection = false
      inTasksSection = false

    val commonModules = Set(
      "ansible.builtin.command", "command",
      "ansible.builtin.shell", "shell",
      "ansible.builtin.copy", "copy",
      "ansible.builtin.template", "template",
      "ansible.builtin.file", "file",
      "ansible.builtin.apt", "apt",
      "ansible.builtin.yum", "yum",
      "ansible.builtin.package", "package",
      "ansible.builtin.service", "service",
      "ansible.builtin.systemd", "systemd",
      "ansible.builtin.git", "git",
      "ansible.builtin.debug", "debug",
      "ansible.builtin.set_fact", "set_fact",
      "ansible.builtin.uri", "uri",
      "ansible.builtin.get_url", "get_url",
      "ansible.builtin.unarchive", "unarchive",
      "ansible.builtin.lineinfile", "lineinfile",
      "ansible.builtin.blockinfile", "blockinfile",
      "ansible.builtin.user", "user",
      "ansible.builtin.group", "group",
      "ansible.builtin.stat", "stat",
      "ansible.builtin.include_tasks", "include_tasks",
      "ansible.builtin.import_tasks", "import_tasks",
      "ansible.builtin.include_role", "include_role",
      "ansible.builtin.import_role", "import_role",
      "ansible.builtin.raw", "raw",
      "ansible.builtin.script", "script",
      "ansible.builtin.assert", "assert",
      "ansible.builtin.fail", "fail",
      "ansible.builtin.pause", "pause",
      "ansible.builtin.wait_for", "wait_for",
      "community.general.", "amazon.aws.", "azure.azcollection.", "kubernetes.core."
    )

    def isModuleKey(key: String): Boolean =
      val k = key.trim
      commonModules.contains(k) || commonModules.exists(prefix => k.startsWith(prefix)) || k.contains(".")

    for (line, idx) <- lines.zipWithIndex do
      val lineNum = idx + 1
      val lineWithoutComment = if line.contains("#") && !line.contains("\"#") && !line.contains("'#") then
        line.substring(0, line.indexOf('#'))
      else line

      val trimmed = lineWithoutComment.trim

      if trimmed.nonEmpty && trimmed != "---" && trimmed != "..." then
        // Detect top-level play item (starts with - )
        if lineWithoutComment.startsWith("-") || (lineWithoutComment.matches("""^\s{0,2}-\s+.*""") && !inTasksSection && !inRolesSection) then
          val playItemContent = trimmed.stripPrefix("-").trim
          if playItemContent.startsWith("hosts:") || playItemContent.startsWith("name:") || playItemContent.startsWith("import_playbook:") then
            flushCurrentPlay()
            currentPlayLine = lineNum
            if playItemContent.startsWith("name:") then
              currentPlayName = playItemContent.stripPrefix("name:").trim.stripPrefix("\"").stripSuffix("\"").stripPrefix("'").stripSuffix("'")
            else if playItemContent.startsWith("hosts:") then
              currentPlayHosts = playItemContent.stripPrefix("hosts:").trim.stripPrefix("\"").stripSuffix("\"").stripPrefix("'").stripSuffix("'")

        if currentPlayLine == 0 && (trimmed.startsWith("hosts:") || trimmed.startsWith("name:")) then
          currentPlayLine = lineNum

        if trimmed.startsWith("hosts:") && !inTasksSection then
          currentPlayHosts = trimmed.stripPrefix("hosts:").trim.stripPrefix("\"").stripSuffix("\"").stripPrefix("'").stripSuffix("'")
        else if trimmed.startsWith("name:") && !inTasksSection && !inRolesSection && currentPlayName.isEmpty then
          currentPlayName = trimmed.stripPrefix("name:").trim.stripPrefix("\"").stripSuffix("\"").stripPrefix("'").stripSuffix("'")
        else if trimmed.startsWith("gather_facts:") then
          val v = trimmed.stripPrefix("gather_facts:").trim.toLowerCase
          currentPlayGatherFacts = Some(v == "true" || v == "yes" || v == "1")
        else if trimmed == "roles:" || trimmed.startsWith("roles:") then
          inRolesSection = true
          inTasksSection = false
          flushCurrentTask()
        else if trimmed == "tasks:" || trimmed.startsWith("tasks:") || trimmed == "pre_tasks:" || trimmed == "post_tasks:" || trimmed == "handlers:" then
          inTasksSection = true
          inRolesSection = false
          flushCurrentTask()
        else if inRolesSection then
          if trimmed.startsWith("-") then
            val roleName = trimmed.stripPrefix("-").trim.stripPrefix("role:").trim.stripPrefix("name:").trim.stripPrefix("\"").stripSuffix("\"").stripPrefix("'").stripSuffix("'")
            if roleName.nonEmpty && !roleName.startsWith("{") then
              currentPlayRoles += roleName
            else if roleName.startsWith("{") && roleName.contains("role:") then
              val rExtracted = roleName.substring(roleName.indexOf("role:") + 5).trim.takeWhile(c => c != ',' && c != '}')
              currentPlayRoles += rExtracted.stripPrefix("\"").stripSuffix("\"").stripPrefix("'").stripSuffix("'").trim
          else if !lineWithoutComment.startsWith(" ") && !lineWithoutComment.startsWith("\t") then
            inRolesSection = false
        else if inTasksSection then
          if trimmed.startsWith("-") then
            flushCurrentTask()
            inTaskItem = true
            currentTaskLine = lineNum
            val taskFirstLine = trimmed.stripPrefix("-").trim
            if taskFirstLine.startsWith("name:") then
              currentTaskName = taskFirstLine.stripPrefix("name:").trim.stripPrefix("\"").stripSuffix("\"").stripPrefix("'").stripSuffix("'")
            else if taskFirstLine.contains(":") then
              val colonIdx = taskFirstLine.indexOf(':')
              val key = taskFirstLine.substring(0, colonIdx).trim
              if isModuleKey(key) || key != "when" && key != "tags" && key != "register" && key != "vars" then
                currentTaskModule = key
          else if inTaskItem then
            if trimmed.startsWith("name:") && currentTaskName.isEmpty then
              currentTaskName = trimmed.stripPrefix("name:").trim.stripPrefix("\"").stripSuffix("\"").stripPrefix("'").stripSuffix("'")
            else if trimmed.contains(":") then
              val colonIdx = trimmed.indexOf(':')
              val key = trimmed.substring(0, colonIdx).trim
              val nonModuleSubKeys = Set("when", "tags", "register", "vars", "become", "become_user", "ignore_errors", "loop", "with_items", "notify", "delegate_to", "changed_when", "failed_when", "state", "path", "src", "dest", "msg", "name", "mode", "owner", "group")
              if isModuleKey(key) && !nonModuleSubKeys.contains(key) then
                currentTaskModule = key
              else if currentTaskModule == "command" && !nonModuleSubKeys.contains(key) && !trimmed.startsWith("  ") then
                currentTaskModule = key
          if !lineWithoutComment.startsWith(" ") && !lineWithoutComment.startsWith("\t") && !lineWithoutComment.startsWith("-") then
            inTasksSection = false
            flushCurrentTask()

    flushCurrentPlay()

    val allHosts = plays.map(_.hosts).filter(_.nonEmpty).distinct.toSeq
    val allRoles = plays.flatMap(_.roles).distinct.toSeq
    val totalTasks = plays.map(_.tasks.length).sum

    AnsiblePlaybookInfo(
      plays = plays.toSeq,
      total_tasks = totalTasks,
      roles = allRoles,
      hosts = allHosts
    )

  /**
   * Validate an Ansible playbook offline.
   * Checks for:
   * 1. Empty playbook
   * 2. Unquoted template expressions starting directly with {{ (e.g. key: {{ var }}) which is invalid YAML syntax in Ansible
   * 3. Plays missing mandatory target 'hosts' declaration
   * 4. Task items missing an executable action / module
   */
  def validatePlaybook(content: String): Seq[AnsibleValidationIssue] =
    val issues = ListBuffer[AnsibleValidationIssue]()
    val trimmed = content.trim
    if trimmed.isEmpty then
      return Seq(AnsibleValidationIssue(line = 1, severity = "ERROR", message = "Ansible playbook is empty"))

    val lines = content.linesIterator.toList

    // Check for unquoted Jinja2 templates at start of YAML value: key: {{ foo }}
    val unquotedJinjaRegex = """^(\s*[-A-Za-z0-9_.]+\s*:\s*)(\{\{.*\}\}\s*)$""".r

    for (line, idx) <- lines.zipWithIndex do
      val lineNum = idx + 1
      val lineClean = if line.contains("#") then line.substring(0, line.indexOf('#')) else line

      unquotedJinjaRegex.findFirstMatchIn(lineClean).foreach { m =>
        val valPart = m.group(2)
        if !valPart.startsWith("\"") && !valPart.startsWith("'") then
          issues += AnsibleValidationIssue(
            line = lineNum,
            col = Some(m.start(2) + 1),
            severity = "ERROR",
            message = "Unquoted template expression. In Ansible YAML, expressions starting with '{{' must be quoted: e.g. key: \"{{ var }}\""
          )
      }

    // Inspect structural elements
    val info = inspectPlaybook(content)

    if info.plays.isEmpty then
      issues += AnsibleValidationIssue(
        line = 1,
        severity = "ERROR",
        message = "No valid plays found in playbook (missing play definitions or tasks)"
      )
    else
      for play <- info.plays do
        if play.hosts == "all" && !content.contains("hosts:") && !content.contains("hosts :") then
          issues += AnsibleValidationIssue(
            line = play.line.getOrElse(1),
            severity = "WARN",
            message = s"Play '${play.name}' does not specify explicit 'hosts' target (defaulting to 'all')"
          )

        for task <- play.tasks do
          if task.module.isEmpty then
            issues += AnsibleValidationIssue(
              line = task.line.getOrElse(play.line.getOrElse(1)),
              severity = "ERROR",
              message = s"Task '${task.name}' has no identifiable module or action"
            )

    issues.toSeq

  /**
   * Parse Ansible inventory graph or JSON output into structured groups.
   */
  def parseInventory(content: String): Seq[AnsibleInventoryGroup] =
    val trimmed = content.trim
    if trimmed.isEmpty then return Seq.empty

    if trimmed.startsWith("{") then
      parseInventoryJson(trimmed)
    else
      parseInventoryGraph(content)

  /**
   * Parse `ansible-inventory --list` JSON output into AnsibleInventoryGroup sequence.
   */
  private def parseInventoryJson(jsonStr: String): Seq[AnsibleInventoryGroup] =
    try
      val parsed = ujson.read(jsonStr)
      val groups = ListBuffer[AnsibleInventoryGroup]()

      parsed.objOpt.foreach { rootObj =>
        rootObj.foreach { case (groupName, gVal) =>
          if groupName != "_meta" then
            val hosts = ListBuffer[String]()
            val children = ListBuffer[String]()
            val vars = collection.mutable.Map[String, String]()

            gVal.objOpt.foreach { gObj =>
              gObj.get("hosts").flatMap(_.arrOpt).foreach { hArr =>
                hosts ++= hArr.flatMap(_.strOpt)
              }
              gObj.get("children").flatMap(_.arrOpt).foreach { cArr =>
                children ++= cArr.flatMap(_.strOpt)
              }
              gObj.get("vars").flatMap(_.objOpt).foreach { vObj =>
                vObj.foreach { case (k, v) =>
                  val vStr = v match {
                    case s: ujson.Str => s.str
                    case other => other.value.toString
                  }
                  vars(k) = vStr
                }
              }
            }

            groups += AnsibleInventoryGroup(
              name = groupName,
              hosts = hosts.toSeq,
              children = children.toSeq,
              vars = vars.toMap
            )
        }
      }

      groups.toSeq
    catch
      case _: Exception => Seq.empty

  /**
   * Parse `ansible-inventory --graph` text output into AnsibleInventoryGroup sequence.
   * Example:
   * @all:
   *   |--@ungrouped:
   *   |--@webservers:
   *   |  |--web1.example.com
   *   |  |--web2.example.com
   */
  private def parseInventoryGraph(content: String): Seq[AnsibleInventoryGroup] =
    val groups = ListBuffer[AnsibleInventoryGroup]()
    val groupMap = collection.mutable.Map[String, (ListBuffer[String], ListBuffer[String])]()

    var currentGroup = "all"
    groupMap(currentGroup) = (ListBuffer[String](), ListBuffer[String]())

    val lines = content.linesIterator.toList

    for line <- lines do
      val trimmed = line.trim
      if trimmed.contains("@") then
        val atIdx = trimmed.indexOf('@')
        val colonIdx = if trimmed.indexOf(':', atIdx) != -1 then trimmed.indexOf(':', atIdx) else trimmed.length
        val gName = trimmed.substring(atIdx + 1, colonIdx).trim
        if gName.nonEmpty then
          if !groupMap.contains(gName) then
            groupMap(gName) = (ListBuffer[String](), ListBuffer[String]())
          if currentGroup != gName && groupMap.contains(currentGroup) then
            val (_, curChildren) = groupMap(currentGroup)
            if !curChildren.contains(gName) then curChildren += gName
          currentGroup = gName
      else if trimmed.contains("|--") || trimmed.contains("`--") then
        val hostPart = trimmed.replaceAll("""^.*[|`]---?""", "").trim
        if hostPart.nonEmpty && !hostPart.startsWith("@") then
          val (curHosts, _) = groupMap.getOrElseUpdate(currentGroup, (ListBuffer[String](), ListBuffer[String]()))
          if !curHosts.contains(hostPart) then curHosts += hostPart

    for ((gName, (hList, cList)) <- groupMap) do
      groups += AnsibleInventoryGroup(
        name = gName,
        hosts = hList.toSeq,
        children = cList.toSeq,
        vars = Map.empty
      )

    groups.toSeq

  /**
   * Inspect Ansible playbook by file path.
   */
  def inspectPlaybookFile(filePath: String): CumulusResponse[AnsiblePlaybookInfo] =
    try
      val pathOpt = try {
        Some(if filePath.startsWith("/") then Path(filePath) else os.pwd / os.RelPath(filePath))
      } catch {
        case _: Exception => None
      }

      pathOpt match
        case None =>
          CumulusResponse(
            success = false,
            data = None,
            error = Some(s"Invalid file path: $filePath"),
            error_code = Some("INVALID_INPUT")
          )
        case Some(path) =>
          if !os.exists(path) then
            return CumulusResponse(
              success = false,
              data = None,
              error = Some(s"File not found: $filePath"),
              error_code = Some("FILE_NOT_FOUND")
            )

          if !os.isFile(path) then
            return CumulusResponse(
              success = false,
              data = None,
              error = Some(s"Path is not a regular file: $filePath"),
              error_code = Some("INVALID_INPUT")
            )

          val content = os.read(path)
          val info = inspectPlaybook(content)
          CumulusResponse(
            success = true,
            data = Some(info),
            error = None,
            error_code = None
          )
    catch
      case e: Exception =>
        CumulusResponse(
          success = false,
          data = None,
          error = Some(s"Error inspecting Ansible playbook: ${e.getMessage}"),
          error_code = Some("INTERNAL_ERROR")
        )

  /**
   * Validate Ansible playbook by file path.
   */
  def validatePlaybookFile(filePath: String): CumulusResponse[Seq[AnsibleValidationIssue]] =
    try
      val pathOpt = try {
        Some(if filePath.startsWith("/") then Path(filePath) else os.pwd / os.RelPath(filePath))
      } catch {
        case _: Exception => None
      }

      pathOpt match
        case None =>
          CumulusResponse(
            success = false,
            data = None,
            error = Some(s"Invalid file path: $filePath"),
            error_code = Some("INVALID_INPUT")
          )
        case Some(path) =>
          if !os.exists(path) then
            return CumulusResponse(
              success = false,
              data = None,
              error = Some(s"File not found: $filePath"),
              error_code = Some("FILE_NOT_FOUND")
            )

          if !os.isFile(path) then
            return CumulusResponse(
              success = false,
              data = None,
              error = Some(s"Path is not a regular file: $filePath"),
              error_code = Some("INVALID_INPUT")
            )

          val content = os.read(path)
          val issues = validatePlaybook(content)
          CumulusResponse(
            success = true,
            data = Some(issues),
            error = None,
            error_code = None
          )
    catch
      case e: Exception =>
        CumulusResponse(
          success = false,
          data = None,
          error = Some(s"Error validating Ansible playbook: ${e.getMessage}"),
          error_code = Some("INTERNAL_ERROR")
        )

  /**
   * Parse Ansible inventory by file path or raw string.
   */
  def parseInventoryFile(filePath: String): CumulusResponse[Seq[AnsibleInventoryGroup]] =
    try
      val pathOpt = try {
        Some(if filePath.startsWith("/") then Path(filePath) else os.pwd / os.RelPath(filePath))
      } catch {
        case _: Exception => None
      }

      pathOpt match
        case None =>
          CumulusResponse(
            success = false,
            data = None,
            error = Some(s"Invalid file path: $filePath"),
            error_code = Some("INVALID_INPUT")
          )
        case Some(path) =>
          if !os.exists(path) then
            return CumulusResponse(
              success = false,
              data = None,
              error = Some(s"File not found: $filePath"),
              error_code = Some("FILE_NOT_FOUND")
            )

          val content = os.read(path)
          val groups = parseInventory(content)
          CumulusResponse(
            success = true,
            data = Some(groups),
            error = None,
            error_code = None
          )
    catch
      case e: Exception =>
        CumulusResponse(
          success = false,
          data = None,
          error = Some(s"Error parsing Ansible inventory: ${e.getMessage}"),
          error_code = Some("INTERNAL_ERROR")
        )
