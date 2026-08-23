package cumulus.build

import os.Path
import scala.util.matching.Regex

/**
 * Centralized command assembler for Maven, Gradle, SBT, Terraform, SAM, Ansible, Docker, and Helm.
 * Constructs exact, platform-correct, escaped CLI command strings, arguments, and recommended working directory.
 */
object CommandAssembler:

  /**
   * Assemble a CLI command based on structured intent.
   *
   * @param intent The structured command intent specification
   * @return Either an error message or the assembled CommandAssemblyResult
   */
  def assemble(intent: CommandIntent): Either[String, CommandAssemblyResult] =
    if intent.tool == null || intent.tool.trim.isEmpty then
      return Left("Missing required parameter: 'tool' must not be empty")
    if intent.action == null || intent.action.trim.isEmpty then
      return Left("Missing required parameter: 'action' must not be empty")

    val tool = intent.tool.trim.toLowerCase
    val action = intent.action.trim.toLowerCase

    val baseDir: Path = intent.dir match
      case Some(d) if d.trim.nonEmpty =>
        try
          val p = Path(d.trim, os.pwd)
          p
        catch
          case e: Exception => os.pwd
      case _ => os.pwd

    tool match
      case "maven" | "mvn" =>
        assembleMaven(intent, action, baseDir)

      case "gradle" | "gradlew" =>
        assembleGradle(intent, action, baseDir)

      case "sbt" =>
        assembleSbt(intent, action, baseDir)

      case "terraform" | "tofu" =>
        assembleTerraform(intent, tool, action, baseDir)

      case "sam" | "cfn" | "cloudformation" =>
        assembleSam(intent, tool, action, baseDir)

      case "ansible" | "ansible-playbook" =>
        assembleAnsible(intent, action, baseDir)

      case "docker" =>
        assembleDocker(intent, action, baseDir)

      case "helm" =>
        assembleHelm(intent, action, baseDir)

      case other =>
        Left(s"Unsupported tool: $other")

  private def assembleMaven(
    intent: CommandIntent,
    action: String,
    baseDir: Path
  ): Either[String, CommandAssemblyResult] =
    val executable = if hasWrapper(baseDir, "mvnw") then "./mvnw" else "mvn"
    val baseArgs = action match
      case "build" =>
        Seq("clean", "package")
      case "test" =>
        intent.target match
          case Some(t) if t.trim.nonEmpty =>
            val cleanTarget = t.trim
            val testArg = if cleanTarget.startsWith("-Dtest=") then cleanTarget else s"-Dtest=$cleanTarget"
            Seq("test", testArg)
          case _ =>
            Seq("test")
      case "clean" => Seq("clean")
      case "compile" => Seq("compile")
      case "package" => Seq("package")
      case "install" => Seq("install")
      case "verify" => Seq("verify")
      case other => Seq(intent.action.trim)

    val targetArgs = if action != "test" then
      intent.target match
        case Some(t) if t.trim.nonEmpty =>
          val trimmed = t.trim
          if trimmed.startsWith("-") then Seq(trimmed)
          else if trimmed.startsWith(":") then Seq("-pl", trimmed)
          else Seq(trimmed)
        case _ => Seq.empty
    else Seq.empty

    val allArgs = baseArgs ++ targetArgs ++ intent.flags
    val commandStr = formatCommand(executable, allArgs)

    Right(CommandAssemblyResult(
      command = commandStr,
      executable = executable,
      args = allArgs,
      cwd = baseDir.toString,
      env = intent.env
    ))

  private def assembleGradle(
    intent: CommandIntent,
    action: String,
    baseDir: Path
  ): Either[String, CommandAssemblyResult] =
    val executable = if hasWrapper(baseDir, "gradlew") then "./gradlew" else "gradle"
    val baseArgs = action match
      case "build" =>
        intent.target match
          case Some(t) if t.trim.nonEmpty =>
            val cleanTarget = t.trim
            if cleanTarget.endsWith("build") then Seq(cleanTarget)
            else Seq(s"$cleanTarget:build")
          case _ =>
            Seq("build")
      case "test" =>
        intent.target match
          case Some(t) if t.trim.nonEmpty =>
            val cleanTarget = t.trim
            if cleanTarget.startsWith("--tests") then
              val parts = cleanTarget.split("\\s+").filter(_.nonEmpty).toSeq
              Seq("test") ++ parts
            else
              Seq("test", "--tests", cleanTarget)
          case _ =>
            Seq("test")
      case "clean" => Seq("clean")
      case "check" => Seq("check")
      case "assemble" => Seq("assemble")
      case "tasks" => Seq("tasks")
      case other => Seq(intent.action.trim)

    val targetArgs = if action != "test" && action != "build" then
      intent.target match
        case Some(t) if t.trim.nonEmpty => Seq(t.trim)
        case _ => Seq.empty
    else Seq.empty

    val allArgs = baseArgs ++ targetArgs ++ intent.flags
    val commandStr = formatCommand(executable, allArgs)

    Right(CommandAssemblyResult(
      command = commandStr,
      executable = executable,
      args = allArgs,
      cwd = baseDir.toString,
      env = intent.env
    ))

  private def assembleSbt(
    intent: CommandIntent,
    action: String,
    baseDir: Path
  ): Either[String, CommandAssemblyResult] =
    val executable = "sbt"
    val baseArgs = action match
      case "build" | "compile" => Seq("compile")
      case "test" =>
        intent.target match
          case Some(t) if t.trim.nonEmpty =>
            Seq(s"testOnly ${t.trim}")
          case _ =>
            Seq("test")
      case "clean" => Seq("clean")
      case "package" => Seq("package")
      case "run" => Seq("run")
      case other => Seq(intent.action.trim)

    val targetArgs = if action != "test" then
      intent.target match
        case Some(t) if t.trim.nonEmpty => Seq(t.trim)
        case _ => Seq.empty
    else Seq.empty

    val allArgs = baseArgs ++ targetArgs ++ intent.flags
    val commandStr = formatCommand(executable, allArgs)

    Right(CommandAssemblyResult(
      command = commandStr,
      executable = executable,
      args = allArgs,
      cwd = baseDir.toString,
      env = intent.env
    ))

  private def assembleTerraform(
    intent: CommandIntent,
    tool: String,
    action: String,
    baseDir: Path
  ): Either[String, CommandAssemblyResult] =
    val executable = if tool == "tofu" then "tofu" else "terraform"
    val (resolvedCwdOrError, args) = intent.target match
      case Some(t) if t.trim.nonEmpty =>
        val trimmed = t.trim
        if trimmed.endsWith(".tf") || trimmed.endsWith(".tofu") || trimmed.endsWith(".tfvars") then
          (Right(baseDir.toString), Seq(action) ++ intent.flags ++ Seq(trimmed))
        else
          // Validate that target path is within project boundary
          val validation = isPathWithinProject(trimmed, baseDir)
          val resolvedCwd = validation.map(_.toString)
          (resolvedCwd, Seq(action) ++ intent.flags)
      case _ =>
        (Right(baseDir.toString), Seq(action) ++ intent.flags)

    resolvedCwdOrError match
      case Left(error) => Left(error)
      case Right(resolvedCwd) =>
        val commandStr = formatCommand(executable, args)
        Right(CommandAssemblyResult(
          command = commandStr,
          executable = executable,
          args = args,
          cwd = resolvedCwd,
          env = intent.env
        ))

  private def assembleSam(
    intent: CommandIntent,
    tool: String,
    action: String,
    baseDir: Path
  ): Either[String, CommandAssemblyResult] =
    if tool == "sam" then
      val executable = "sam"
      val args = action match
        case "validate" =>
          Seq("validate") ++ intent.flags
        case "build" =>
          Seq("build") ++ intent.flags
        case "local invoke" | "local-invoke" | "local_invoke" | "invoke" =>
          val targetArgs = intent.target match
            case Some(t) if t.trim.nonEmpty =>
              val trimmed = t.trim
              if trimmed.endsWith(".yaml") || trimmed.endsWith(".yml") || trimmed.endsWith(".json") then
                Seq("-t", trimmed)
              else
                Seq(trimmed)
            case _ => Seq.empty
          Seq("local", "invoke") ++ targetArgs ++ intent.flags
        case "local start-api" | "local-start-api" | "local_start_api" | "start-api" =>
          Seq("local", "start-api") ++ intent.flags
        case "deploy" =>
          Seq("deploy") ++ intent.flags
        case "package" =>
          Seq("package") ++ intent.flags
        case other =>
          val tokens = other.split("[\\s_-]+").filter(_.nonEmpty).toSeq
          tokens ++ intent.target.map(_.trim).filter(_.nonEmpty).toSeq ++ intent.flags

      val commandStr = formatCommand(executable, args)
      Right(CommandAssemblyResult(
        command = commandStr,
        executable = executable,
        args = args,
        cwd = baseDir.toString,
        env = intent.env
      ))
    else
      // cfn / cloudformation -> aws cloudformation ...
      val executable = "aws"
      val args = action match
        case "validate" =>
          val tmpl = intent.target.map(_.trim).filter(_.nonEmpty).map(t => Seq("--template-body", s"file://$t")).getOrElse(Seq.empty)
          Seq("cloudformation", "validate-template") ++ tmpl ++ intent.flags
        case other =>
          Seq("cloudformation", other) ++ intent.target.map(_.trim).filter(_.nonEmpty).toSeq ++ intent.flags

      val commandStr = formatCommand(executable, args)
      Right(CommandAssemblyResult(
        command = commandStr,
        executable = executable,
        args = args,
        cwd = baseDir.toString,
        env = intent.env
      ))

  private def assembleAnsible(
    intent: CommandIntent,
    action: String,
    baseDir: Path
  ): Either[String, CommandAssemblyResult] =
    action match
      case "run" | "playbook" =>
        val executable = "ansible-playbook"
        val (optionsWithArgs, trailingFlags) = splitAnsibleFlags(intent.flags)
        val targetArg = intent.target.map(_.trim).filter(_.nonEmpty).toSeq
        val allArgs = optionsWithArgs ++ targetArg ++ trailingFlags
        val commandStr = formatCommand(executable, allArgs)
        Right(CommandAssemblyResult(
          command = commandStr,
          executable = executable,
          args = allArgs,
          cwd = baseDir.toString,
          env = intent.env
        ))

      case "syntax-check" =>
        val executable = "ansible-playbook"
        val allArgs = Seq("--syntax-check") ++ intent.target.map(_.trim).filter(_.nonEmpty).toSeq ++ intent.flags
        val commandStr = formatCommand(executable, allArgs)
        Right(CommandAssemblyResult(
          command = commandStr,
          executable = executable,
          args = allArgs,
          cwd = baseDir.toString,
          env = intent.env
        ))

      case "check" | "dry-run" =>
        val executable = "ansible-playbook"
        val allArgs = Seq("--check") ++ intent.target.map(_.trim).filter(_.nonEmpty).toSeq ++ intent.flags
        val commandStr = formatCommand(executable, allArgs)
        Right(CommandAssemblyResult(
          command = commandStr,
          executable = executable,
          args = allArgs,
          cwd = baseDir.toString,
          env = intent.env
        ))

      case "lint" =>
        val executable = "ansible-lint"
        val allArgs = intent.flags ++ intent.target.map(_.trim).filter(_.nonEmpty).toSeq
        val commandStr = formatCommand(executable, allArgs)
        Right(CommandAssemblyResult(
          command = commandStr,
          executable = executable,
          args = allArgs,
          cwd = baseDir.toString,
          env = intent.env
        ))

      case "inventory" =>
        val executable = "ansible-inventory"
        val invFlags = if intent.flags.isEmpty then Seq("--graph") else intent.flags
        val allArgs = invFlags ++ intent.target.map(_.trim).filter(_.nonEmpty).toSeq
        val commandStr = formatCommand(executable, allArgs)
        Right(CommandAssemblyResult(
          command = commandStr,
          executable = executable,
          args = allArgs,
          cwd = baseDir.toString,
          env = intent.env
        ))

      case "doc" =>
        val executable = "ansible-doc"
        val allArgs = intent.target.map(_.trim).filter(_.nonEmpty).toSeq ++ intent.flags
        val commandStr = formatCommand(executable, allArgs)
        Right(CommandAssemblyResult(
          command = commandStr,
          executable = executable,
          args = allArgs,
          cwd = baseDir.toString,
          env = intent.env
        ))

      case "vault" =>
        val executable = "ansible-vault"
        val allArgs = intent.target.map(_.trim).filter(_.nonEmpty).toSeq ++ intent.flags
        val commandStr = formatCommand(executable, allArgs)
        Right(CommandAssemblyResult(
          command = commandStr,
          executable = executable,
          args = allArgs,
          cwd = baseDir.toString,
          env = intent.env
        ))

      case other =>
        val executable = "ansible-playbook"
        val allArgs = Seq(other) ++ intent.target.map(_.trim).filter(_.nonEmpty).toSeq ++ intent.flags
        val commandStr = formatCommand(executable, allArgs)
        Right(CommandAssemblyResult(
          command = commandStr,
          executable = executable,
          args = allArgs,
          cwd = baseDir.toString,
          env = intent.env
        ))

  private def assembleDocker(
    intent: CommandIntent,
    action: String,
    baseDir: Path
  ): Either[String, CommandAssemblyResult] =
    action match
      case "build" =>
        val executable = "docker"
        val fileArg = intent.target match
          case Some(t) if t.trim.nonEmpty =>
            val trimmed = t.trim
            if trimmed.startsWith("-f") then trimmed.split("\\s+").filter(_.nonEmpty).toSeq
            else Seq("-f", trimmed)
          case _ => Seq.empty

        val hasContext = intent.flags.contains(".") || intent.target.contains(".")
        val contextArg = if hasContext then Seq.empty else Seq(".")
        val allArgs = Seq("build") ++ intent.flags ++ fileArg ++ contextArg
        val commandStr = formatCommand(executable, allArgs)
        Right(CommandAssemblyResult(
          command = commandStr,
          executable = executable,
          args = allArgs,
          cwd = baseDir.toString,
          env = intent.env
        ))

      case "run" =>
        val executable = "docker"
        val allArgs = Seq("run") ++ intent.flags ++ intent.target.map(_.trim).filter(_.nonEmpty).toSeq
        val commandStr = formatCommand(executable, allArgs)
        Right(CommandAssemblyResult(
          command = commandStr,
          executable = executable,
          args = allArgs,
          cwd = baseDir.toString,
          env = intent.env
        ))

      case "lint" | "hadolint" =>
        val executable = "hadolint"
        val allArgs = intent.flags ++ intent.target.map(_.trim).filter(_.nonEmpty).toSeq
        val commandStr = formatCommand(executable, allArgs)
        Right(CommandAssemblyResult(
          command = commandStr,
          executable = executable,
          args = allArgs,
          cwd = baseDir.toString,
          env = intent.env
        ))

      case "compose" =>
        val executable = "docker"
        val fileArg = intent.target match
          case Some(t) if t.trim.nonEmpty => Seq("-f", t.trim)
          case _ => Seq.empty
        val allArgs = Seq("compose") ++ fileArg ++ intent.flags
        val commandStr = formatCommand(executable, allArgs)
        Right(CommandAssemblyResult(
          command = commandStr,
          executable = executable,
          args = allArgs,
          cwd = baseDir.toString,
          env = intent.env
        ))

      case other =>
        val executable = "docker"
        val allArgs = Seq(other) ++ intent.target.map(_.trim).filter(_.nonEmpty).toSeq ++ intent.flags
        val commandStr = formatCommand(executable, allArgs)
        Right(CommandAssemblyResult(
          command = commandStr,
          executable = executable,
          args = allArgs,
          cwd = baseDir.toString,
          env = intent.env
        ))

  private def assembleHelm(
    intent: CommandIntent,
    action: String,
    baseDir: Path
  ): Either[String, CommandAssemblyResult] =
    val executable = "helm"
    val allArgs = action match
      case "lint" =>
        val targetArg = intent.target.map(_.trim).filter(_.nonEmpty).getOrElse(".")
        Seq("lint", targetArg) ++ intent.flags
      case "template" =>
        val targetArg = intent.target.map(_.trim).filter(_.nonEmpty).getOrElse(".")
        Seq("template", targetArg) ++ intent.flags
      case "install" =>
        Seq("install") ++ intent.target.map(_.trim).filter(_.nonEmpty).toSeq ++ intent.flags
      case "upgrade" =>
        Seq("upgrade") ++ intent.target.map(_.trim).filter(_.nonEmpty).toSeq ++ intent.flags
      case other =>
        Seq(other) ++ intent.target.map(_.trim).filter(_.nonEmpty).toSeq ++ intent.flags

    val commandStr = formatCommand(executable, allArgs)
    Right(CommandAssemblyResult(
      command = commandStr,
      executable = executable,
      args = allArgs,
      cwd = baseDir.toString,
      env = intent.env
    ))

  /**
   * Split ansible flags into leading options with values (e.g. -i inventory.ini, -e var=val) and trailing flags (e.g. --check, -v)
   */
  private def splitAnsibleFlags(flags: Seq[String]): (Seq[String], Seq[String]) =
    val leadingOptions = collection.mutable.ListBuffer[String]()
    val trailingOptions = collection.mutable.ListBuffer[String]()

    var i = 0
    while i < flags.length do
      val f = flags(i)
      if (f == "-i" || f == "--inventory" || f == "-e" || f == "--extra-vars" || f == "-l" || f == "--limit" || f == "-u" || f == "--user" || f == "-t" || f == "--tags") && i + 1 < flags.length then
        leadingOptions += f
        leadingOptions += flags(i + 1)
        i += 2
      else if f.startsWith("-i=") || f.startsWith("--inventory=") || f.startsWith("-e=") || f.startsWith("--extra-vars=") || f.startsWith("-l=") || f.startsWith("--limit=") then
        leadingOptions += f
        i += 1
      else
        trailingOptions += f
        i += 1

    (leadingOptions.toSeq, trailingOptions.toSeq)

  /**
   * Validate that a target path is within the project boundary.
   * For existing paths, uses toRealPath() to resolve symlinks and traversal sequences.
   * For non-existent paths, uses normalize() + toAbsolutePath() to detect traversal.
   * Then verifies that the resulting path is within the base directory.
   *
   * @param target The target path (relative or absolute)
   * @param baseDir The project base directory that serves as the boundary
   * @return Either an error message or the validated normalized path
   */
  private def isPathWithinProject(target: String, baseDir: Path): Either[String, Path] =
    try
      val targetPath = if target.startsWith("/") then
        Path(target)  // Absolute path
      else
        baseDir / os.SubPath(target.stripPrefix("./"))  // Relative to base

      // Try to resolve real path (handles existing paths and symlinks)
      val resolvedTarget = try
        targetPath.toNIO.toRealPath()
      catch
        // If path doesn't exist, fall back to normalize + absolute path
        case _: Exception => targetPath.toNIO.normalize().toAbsolutePath()

      val realBase = baseDir.toNIO.toRealPath()

      // Check if resolvedTarget is within realBase
      if resolvedTarget.startsWith(realBase) then Right(Path(resolvedTarget))
      else Left("Path traversal detected")
    catch
      case _: Exception => Left("Path traversal detected")

  /**
   * Check if wrapper exists in directory or parent hierarchy.
   */
  private def hasWrapper(startDir: Path, wrapperName: String): Boolean =
    var current: Option[Path] = Some(startDir)
    var found = false
    while current.isDefined && !found do
      val p = current.get
      if os.exists(p / wrapperName) && os.isFile(p / wrapperName) then
        found = true
      current = if p == os.root then None else Some(p / os.up)
    found

  /**
   * Escape a shell argument by escaping backslashes and double quotes, then wrapping in double quotes.
   * This prevents shell metacharacter injection while preserving the argument as a literal string.
   *
   * Escaping order is critical:
   * 1. Escape backslashes first: \ → \\
   * 2. Escape double quotes: " → \"
   * 3. Wrap result in double quotes
   *
   * @param arg The argument to escape
   * @return The escaped and quoted argument
   */
  private def escapeShellArg(arg: String): String =
    val escaped = arg
      .replace("\\", "\\\\")  // Backslash first (order matters!)
      .replace("\"", "\\\"")  // Then double quotes
    s""""$escaped""""

  /**
   * Format command and args into a single shell command string, escaping backslashes and double quotes
   * to prevent command injection attacks via quote-escaped injection.
   * Note: Other shell metacharacters ($, `, |, etc.) within double quotes are still interpreted by the shell.
   */
  private def formatCommand(executable: String, args: Seq[String]): String =
    if args.isEmpty then executable
    else
      val formattedArgs = args.map { arg =>
        // Check if argument needs escaping (contains special chars or spaces)
        val needsEscaping = arg.contains("\\") || arg.contains("\"") || arg.contains(" ")

        if needsEscaping then
          if arg.startsWith("-") && arg.contains("=") then
            // Handle -key="value" pattern: split on =, escape value if needed
            val eqIdx = arg.indexOf('=')
            val key = arg.substring(0, eqIdx)
            val value = arg.substring(eqIdx + 1)
            val needsValueEscaping = value.contains("\\") || value.contains("\"") || value.contains(" ")
            if needsValueEscaping then
              // If value already starts with quote, remove matching quotes before escaping
              val unquotedValue = if (value.startsWith("\"") && value.endsWith("\"") && value.length >= 2) then
                value.substring(1, value.length - 1)
              else if (value.startsWith("'") && value.endsWith("'") && value.length >= 2) then
                value.substring(1, value.length - 1)
              else
                value
              s"""$key=${escapeShellArg(unquotedValue)}"""
            else arg
          else
            // Escape the entire argument if it contains special chars
            escapeShellArg(arg)
        else
          arg
      }
      s"$executable ${formattedArgs.mkString(" ")}"
