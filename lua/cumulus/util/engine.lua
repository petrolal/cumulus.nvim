-- Cumulus Scala Engine Integration (SPEC-031)
--
-- Discovers and executes the compiled `cumulus-engine` Scala binary to offload
-- heavy POM parsing, Gradle task extraction, build log parsing, REST endpoint extraction,
-- JaCoCo coverage parsing, Flyway migration validation, Spring Bean dependency graphs,
-- log indexing, import optimization, K8s schema validation, Git conflict resolution,
-- session sanitization, workspace discovery, theme management, test command assembly, etc.

local M = {}

local cached_bin = nil
local cache_expires_at = nil
local CACHE_TTL_MS = 300000 -- 5 minutes: refresh cache on rebuild/reinstall during dev

--- Locates the `cumulus-engine` binary on $PATH, within engine/ target directory, or in user data dir.
---@return string|nil
function M.get_bin()
  local now = vim.loop.now()
  if cached_bin ~= nil and cache_expires_at and now < cache_expires_at then
    return cached_bin ~= false and cached_bin or nil
  end

  -- 1. Check $PATH
  if vim.fn.executable("cumulus-engine") == 1 then
    cached_bin = "cumulus-engine"
    cache_expires_at = now + CACHE_TTL_MS
    return cached_bin
  end

  -- 2. Search relative to Neovim runtimepath / workspace directory (local GraalVM build)
  local script_dir = vim.fn.fnamemodify(debug.getinfo(1, "S").source:sub(2), ":p:h:h:h:h")
  local local_native_bin = script_dir .. "/engine/target/native-image/cumulus-engine"
  local local_graalvm_bin = script_dir .. "/engine/target/graalvm-native-image/cumulus-engine"

  if vim.fn.executable(local_native_bin) == 1 then
    cached_bin = local_native_bin
    cache_expires_at = now + CACHE_TTL_MS
    return cached_bin
  elseif vim.fn.executable(local_graalvm_bin) == 1 then
    cached_bin = local_graalvm_bin
    cache_expires_at = now + CACHE_TTL_MS
    return cached_bin
  end

  -- 3. Check installed binary location (~/.local/share/nvim/cumulus/bin/cumulus-engine)
  local data_bin = vim.fn.stdpath("data") .. "/cumulus/bin/cumulus-engine"
  if vim.fn.executable(data_bin) == 1 then
    cached_bin = data_bin
    cache_expires_at = now + CACHE_TTL_MS
    return cached_bin
  end

  cached_bin = false
  cache_expires_at = now + CACHE_TTL_MS
  return nil
end

--- Returns true if the Scala engine binary is available.
---@return boolean
function M.is_available()
  return M.get_bin() ~= nil
end

--- Invalidate the binary cache (useful for tests or forcing a refresh after rebuild).
function M.invalidate_cache()
  cached_bin = nil
  cache_expires_at = nil
end

--- Detects the host platform OS and architecture for cumulus-engine binary.
---@return string|nil target_name Target binary name (e.g., 'cumulus-engine-linux-x86_64') or nil
---@return string|nil error Error message if platform is unsupported
function M.detect_platform()
  local uv = vim.uv or vim.loop
  local uname = uv.os_uname()
  local sysname = uname.sysname
  local machine = uname.machine

  if sysname == "Linux" then
    if machine == "x86_64" then
      return "cumulus-engine-linux-x86_64", nil
    elseif machine == "aarch64" or machine == "arm64" then
      return "cumulus-engine-linux-aarch64", nil
    end
  elseif sysname == "Darwin" then
    if machine == "arm64" or machine == "aarch64" then
      return "cumulus-engine-darwin-arm64", nil
    end
  end

  return nil, string.format("Unsupported OS/architecture: %s %s", sysname, machine)
end

--- Downloads and installs the pre-built cumulus-engine binary from GitHub Releases.
--- Verifies the SHA-256 checksum against checksums.sha256 before installing and making executable.
---@param opts? { url?: string, callback?: fun(success: boolean, err_or_path: string) }
---@param callback? fun(success: boolean, err_or_path: string)
function M.install(opts, callback)
  if type(opts) == "function" then
    callback = opts
    opts = {}
  end
  opts = opts or {}
  local cb = callback or opts.callback or function() end

  local target_name, err = M.detect_platform()
  if not target_name then
    local msg = string.format("[cumulus] %s", err or "Unsupported platform")
    vim.notify(msg, vim.log.levels.WARN)
    cb(false, msg)
    return
  end

  if vim.fn.executable("curl") ~= 1 then
    local msg = "[cumulus] 'curl' is required to download cumulus-engine but was not found in PATH"
    vim.notify(msg, vim.log.levels.ERROR)
    cb(false, msg)
    return
  end

  local base_url = opts.url or "https://github.com/petrolal/cumulus.nvim/releases/latest/download/"
  if not base_url:match("/$") then
    base_url = base_url .. "/"
  end

  local bin_url = base_url .. target_name
  local checksums_url = base_url .. "checksums.sha256"

  vim.notify(string.format("[cumulus] Downloading %s...", target_name), vim.log.levels.INFO)

  local temp_dir = vim.fn.tempname()
  vim.fn.mkdir(temp_dir, "p")
  local temp_bin = temp_dir .. "/" .. target_name
  local temp_checksums = temp_dir .. "/checksums.sha256"

  local function cleanup()
    pcall(vim.fn.delete, temp_dir, "rf")
  end

  -- Step 1: Download checksums.sha256 manifest
  vim.system({ "curl", "-fsSL", checksums_url, "-o", temp_checksums }, {}, function(res_sum)
    if res_sum.code ~= 0 then
      cleanup()
      local err_msg = string.format("Failed to download checksums manifest from %s", checksums_url)
      vim.schedule(function()
        vim.notify("[cumulus] " .. err_msg, vim.log.levels.ERROR)
        cb(false, err_msg)
      end)
      return
    end

    -- Step 2: Download platform binary
    vim.system({ "curl", "-fsSL", bin_url, "-o", temp_bin }, {}, function(res_bin)
      if res_bin.code ~= 0 then
        cleanup()
        local err_msg = string.format("Failed to download binary from %s", bin_url)
        vim.schedule(function()
          vim.notify("[cumulus] " .. err_msg, vim.log.levels.ERROR)
          cb(false, err_msg)
        end)
        return
      end

      -- Step 3: Parse expected hash from checksums.sha256
      local checksum_content = ""
      local f = io.open(temp_checksums, "r")
      if f then
        checksum_content = f:read("*a") or ""
        f:close()
      end

      local expected_hash = nil
      for line in checksum_content:gmatch("[^\r\n]+") do
        local hash, fname = line:match("^%s*([a-fA-F0-9]+)%s+%*?([%w%.%-_]+)")
        if fname == target_name and hash then
          expected_hash = hash:lower()
          break
        end
      end

      if not expected_hash then
        cleanup()
        local err_msg = string.format("No checksum found for %s in checksums.sha256", target_name)
        vim.schedule(function()
          vim.notify("[cumulus] " .. err_msg, vim.log.levels.ERROR)
          cb(false, err_msg)
        end)
        return
      end

      -- Step 4: Compute actual hash of downloaded binary
      local hash_cmd
      if vim.fn.executable("sha256sum") == 1 then
        hash_cmd = { "sha256sum", temp_bin }
      elseif vim.fn.executable("shasum") == 1 then
        hash_cmd = { "shasum", "-a", "256", temp_bin }
      else
        cleanup()
        local err_msg = "Neither 'sha256sum' nor 'shasum' is available to verify binary integrity"
        vim.schedule(function()
          vim.notify("[cumulus] " .. err_msg, vim.log.levels.ERROR)
          cb(false, err_msg)
        end)
        return
      end

      vim.system(hash_cmd, { text = true }, function(res_hash)
        if res_hash.code ~= 0 or not res_hash.stdout then
          cleanup()
          local err_msg = "Failed to calculate SHA-256 checksum of downloaded binary"
          vim.schedule(function()
            vim.notify("[cumulus] " .. err_msg, vim.log.levels.ERROR)
            cb(false, err_msg)
          end)
          return
        end

        local actual_hash = res_hash.stdout:match("^%s*([a-fA-F0-9]+)")
        if actual_hash then
          actual_hash = actual_hash:lower()
        end

        if actual_hash ~= expected_hash then
          cleanup()
          local err_msg = string.format(
            "SHA-256 checksum verification failed for %s (expected: %s, got: %s)",
            target_name,
            expected_hash,
            actual_hash or "nil"
          )
          vim.schedule(function()
            vim.notify("[cumulus] SHA-256 checksum verification failed", vim.log.levels.ERROR)
            cb(false, err_msg)
          end)
          return
        end

        -- Step 5: Install verified binary to stdpath("data")/cumulus/bin/cumulus-engine
        local dest_dir = vim.fn.stdpath("data") .. "/cumulus/bin"
        vim.fn.mkdir(dest_dir, "p")
        local dest_bin = dest_dir .. "/cumulus-engine"

        pcall(vim.fn.delete, dest_bin)

        local uv = vim.uv or vim.loop
        local ok, _ = uv.fs_rename(temp_bin, dest_bin)
        if not ok then
          local src_f = io.open(temp_bin, "rb")
          if src_f then
            local data = src_f:read("*a")
            src_f:close()
            local dst_f = io.open(dest_bin, "wb")
            if dst_f then
              dst_f:write(data)
              dst_f:close()
              ok = true
            end
          end
        end

        cleanup()

        if not ok then
          local err_msg = "Failed to install binary to " .. dest_bin
          vim.schedule(function()
            vim.notify("[cumulus] " .. err_msg, vim.log.levels.ERROR)
            cb(false, err_msg)
          end)
          return
        end

        pcall(uv.fs_chmod, dest_bin, 493) -- 0755 octal permissions

        M.invalidate_cache()

        vim.schedule(function()
          local succ_msg = string.format("[cumulus] Successfully installed cumulus-engine to %s", dest_bin)
          vim.notify(succ_msg, vim.log.levels.INFO)
          cb(true, dest_bin)
        end)
      end)
    end)
  end)
end

--- Safely decode JSON from Engine output and check success flag.
---@param json_str string
---@param context? string Optional context for error messages
---@param opts? { debug: boolean } Optional options
---@return table|nil Decoded data field, or nil if decode fails or success is false
local function safe_json_decode(json_str, context, opts)
  opts = opts or {}
  local ok, parsed = pcall(vim.json.decode, json_str)
  if not ok or type(parsed) ~= "table" then
    local msg = string.format("[cumulus] JSON decode failed%s: %s",
      context and " (" .. context .. ")" or "",
      tostring(parsed))
    vim.notify(msg, vim.log.levels.WARN)
    return nil
  end

  -- Check success flag in envelope
  if parsed.success == false then
    local error_msg = parsed.error or "Unknown error"
    local msg = string.format("[cumulus] %s%s", error_msg,
      parsed.error_code and (" (" .. parsed.error_code .. ")") or "")
    vim.notify(msg, vim.log.levels.WARN)
    return nil
  end

  -- Log success if debug enabled
  if opts.debug and parsed.success then
    vim.notify(
      string.format("[cumulus] %s succeeded", context or "command"),
      vim.log.levels.DEBUG
    )
  end

  return parsed.data
end

--- Internal helper: call Engine command and decode JSON output.
---@param args string[] Command and arguments (e.g., { "parse-build-log", "--tool", "maven" })
---@param stdin? string Optional stdin content
---@param context? string Optional context for error logging
---@param opts? { debug: boolean } Optional options
---@return table|nil Decoded JSON output or nil
local function call_engine_command(args, stdin, context, opts)
  local bin = M.get_bin()
  if not bin then
    return nil
  end

  local cmd_args = { bin }
  for _, arg in ipairs(args) do
    table.insert(cmd_args, arg)
  end

  local res = vim.system(cmd_args, { stdin = stdin, text = true }):wait()
  if res.code ~= 0 or not res.stdout or res.stdout == "" then
    if res.code ~= 0 then
      vim.notify(
        string.format("[cumulus] engine command failed with exit code %d", res.code),
        vim.log.levels.WARN
      )
    end
    return nil
  end

  return safe_json_decode(res.stdout, context or args[1], opts)
end

--- Ping the engine binary for status, version, commit, and build info
---@return { status: string, version: string, scala: string, commit: string, built: string }|nil
function M.ping()
  return call_engine_command({ "ping" }, nil, "ping")
end

--- Parse Maven goals using Scala engine
---@param pom_path string
---@return string[]|nil
function M.parse_pom_goals(pom_path)
  return call_engine_command({ "parse-pom", "--file", pom_path }, nil, "parse-pom")
end

--- Parse Gradle task list from stdout using Scala engine
---@param content string
---@return string[]|nil
function M.parse_gradle_tasks(content)
  return call_engine_command({ "parse-gradle-tasks" }, content, "parse-gradle-tasks")
end

--- Parse build log content using Scala engine
---@param tool "maven"|"gradle"|"sbt"
---@param log_content string
---@return table[]|nil Array of { file = string, line = number, col = number|nil, message = string, severity = string }
function M.parse_build_log(tool, log_content)
  return call_engine_command({ "parse-build-log", "--tool", tool }, log_content, "parse-build-log")
end

--- Parse Maven/Gradle sub-modules from pom.xml or settings.gradle using Scala engine
---@param tool "maven"|"gradle"
---@param file_path string
---@return table[]|nil Array of { name = string, path = string }
function M.parse_modules(tool, file_path)
  return call_engine_command({ "parse-modules", "--tool", tool, "--file", file_path }, nil, "parse-modules")
end

--- Parse stacktrace log content using Scala engine
---@param log_content string
---@return table[]|nil Array of { class_name = string, method_name = string, file = string, line = number }
function M.parse_stacktrace(log_content)
  return call_engine_command({ "parse-stacktrace" }, log_content, "parse-stacktrace")
end

--- Generate Java package and class header using Scala engine
---@param file_path string
---@return string[]|nil Array of lines
function M.generate_java_header(file_path)
  return call_engine_command({ "generate-java-header", "--file", file_path }, nil, "generate-java-header")
end

--- Parse test log output using Scala engine
---@param log_content string
---@return table[]|nil Array of { test_name = string, class_name = string, status = string, failure_message = string|nil, file = string|nil, line = number|nil }
function M.parse_test_output(log_content)
  return call_engine_command({ "parse-test-output" }, log_content, "parse-test-output")
end

--- Check network connectivity using Scala engine
---@param host? string host:port format (default "repo.maven.apache.org:443")
---@return boolean|nil returns boolean or nil if engine unavailable
function M.check_network(host)
  host = host or "repo.maven.apache.org:443"
  local result = call_engine_command({ "check-network", "--host", host }, nil, "check-network")
  return result and result.online or nil
end

--- Parse Checkstyle XML report using Scala engine
---@param xml_content string
---@return table[]|nil Array of { file = string, line = number, col = number|nil, message = string, severity = string }
function M.parse_checkstyle(xml_content)
  return call_engine_command({ "parse-checkstyle" }, xml_content, "parse-checkstyle")
end

--- Extract REST endpoints from directory using Scala engine
---@param dir_path string
---@return table[]|nil Array of { file = string, line = number, http_method = string, path = string, class_name = string, handler_name = string }
function M.extract_endpoints(dir_path)
  return call_engine_command({ "extract-endpoints", "--dir", dir_path }, nil, "extract-endpoints")
end

--- Parse JaCoCo coverage XML file using Scala engine
---@param file_path string
---@return table[]|nil Array of { file = string, covered_lines = number[], missed_lines = number[] }
function M.parse_coverage(file_path)
  return call_engine_command({ "parse-coverage", "--file", file_path }, nil, "parse-coverage")
end

--- Validate Flyway migration scripts in directory using Scala engine
---@param dir_path string
---@return table[]|nil Array of { file = string, line = number|nil, severity = string, message = string }
function M.validate_migrations(dir_path)
  return call_engine_command({ "validate-migrations", "--dir", dir_path }, nil, "validate-migrations")
end

--- Extract Spring Bean dependencies from directory using Scala engine
---@param dir_path string
---@return table[]|nil Array of { class_name = string, bean_name = string, file = string, line = number, injected_deps = string[] }
function M.parse_spring_beans(dir_path)
  return call_engine_command({ "parse-spring-beans", "--dir", dir_path }, nil, "parse-spring-beans")
end

--- Index log content using Scala engine
---@param log_content string
---@return table[]|nil Array of { line = number, level = string, timestamp = string|nil, message = string }
function M.index_log(log_content)
  return call_engine_command({ "index-log" }, log_content, "index-log")
end

--- Optimize Java/Kotlin imports using Scala engine
---@param code_content string
---@return string[]|nil Array of formatted lines
function M.optimize_imports(code_content)
  return call_engine_command({ "optimize-imports" }, code_content, "optimize-imports")
end

--- Validate K8s manifest content using Scala engine
---@param yaml_content string
---@return table[]|nil Array of { line = number, col = number|nil, message = string, severity = string }
function M.validate_k8s_manifest(yaml_content)
  return call_engine_command({ "validate-k8s-manifest" }, yaml_content, "validate-k8s-manifest")
end

--- Parse Git conflict markers using Scala engine
---@param content string
---@return table[]|nil Array of { start_line = number, sep_line = number, end_line = number, current_header = string, incoming_header = string }
function M.parse_git_conflicts(content)
  return call_engine_command({ "parse-git-conflicts" }, content, "parse-git-conflicts")
end

--- Detect nearest test class and method at a given cursor line in a Java/Kotlin file.
---@param file_path string Absolute path to the Java/Kotlin source file
---@param cursor_line number 1-indexed line number of the cursor
---@return { class_name: string|nil, method_name: string|nil }|nil
function M.detect_test_context(file_path, cursor_line)
  return call_engine_command({ "detect-test-context", "--file", file_path, "--line", tostring(cursor_line) }, nil, "detect-test-context")
end

--- Resolve direct project dependencies using Scala engine
---@param file_path string Path to pom.xml or libs.versions.toml
---@return table[]|nil Array of { group = string, artifact = string, version = string, scope = string }
function M.resolve_deps(file_path)
  return call_engine_command({ "resolve-deps", "--file", file_path }, nil, "resolve-deps")
end

--- Extract instant Java & Kotlin CodeLens items using Scala engine
---@param file_path string Path to Java or Kotlin source file
---@return table[]|nil Array of { line = number, title = string, command = string, args = string[] }
function M.extract_codelens(file_path)
  return call_engine_command({ "extract-codelens", "--file", file_path }, nil, "extract-codelens")
end

--- Solve multi-module topological build order & DAG using Scala engine
---@param dir_path string Root project directory
---@return table[]|nil Array of { step = number, module_name = string, path = string, build_command = string }
function M.compute_build_order(dir_path)
  return call_engine_command({ "compute-build-order", "--dir", dir_path }, nil, "compute-build-order")
end

--- Check JDTLS classpath sync status by scanning build config mtime
---@param dir_path string Root project directory
---@param start_time number JDTLS start time (epoch seconds)
---@return { sync_needed: boolean, modified_file: string|nil }|nil
function M.check_jdtls_sync(dir_path, start_time)
  return call_engine_command({ "check-jdtls-sync", "--dir", dir_path, "--start-time", tostring(start_time) }, nil, "check-jdtls-sync")
end

--- Verify Gradle wrapper configuration against CI workflows and SHA-256
---@param dir_path string Root project directory
---@return { local_version: string|nil, ci_version: string|nil, sha256_configured: boolean, sha256_valid: boolean, issues: string[] }|nil
function M.verify_gradle_wrapper(dir_path)
  return call_engine_command({ "verify-gradle-wrapper", "--dir", dir_path }, nil, "verify-gradle-wrapper")
end

--- Sanitize a Neovim session file by removing ephemeral buffers and floating windows
---@param file_path string Path to .vim session file
---@return { success: boolean, cleaned_lines: number, total_lines: number }|nil
function M.sanitize_session(file_path)
  return call_engine_command({ "session-sanitize", "--file", file_path }, nil, "session-sanitize")
end

--- Detect Spring Boot application and generate debug configuration
---@param dir_path string Root project directory
---@return { main_class: string, project_name: string, build_tool: string, jvm_args: string, profiles: string[] }|nil
function M.detect_springboot_app(dir_path)
  return call_engine_command({ "detect-springboot-app", "--dir", dir_path }, nil, "detect-springboot-app")
end

--- Resolve stacktrace symbol to absolute file path
---@param line_text string Stacktrace line
---@param dir_path string Root project directory
---@return { file_path: string, line: number, class_name: string, method_name: string }|nil
function M.resolve_stacktrace_symbol(line_text, dir_path)
  return call_engine_command({ "resolve-stacktrace-symbol", "--line", line_text, "--dir", dir_path }, nil, "resolve-stacktrace-symbol")
end

--- Check dependency versions and render virtual text hints
---@param file_path string Path to pom.xml, build.gradle, or libs.versions.toml
---@return { group: string, artifact: string, current_version: string, latest_version: string, line: number, age_status: string }[]
function M.check_dep_versions(file_path)
  return call_engine_command({ "check-dep-versions", "--file", file_path }, nil, "check-dep-versions") or {}
end

--- Discover installed JDKs on the host
---@return { jdks: table[], default_jdk: table|nil }|nil
function M.discover_jdk()
  return call_engine_command({ "discover-jdk" }, nil, "discover-jdk")
end

--- Discover build tool for a directory (maven, gradle, sbt)
---@param dir_path? string
---@return { tool: string, root: string, config_file: string, wrapper_available: boolean }|nil
function M.discover_build_tool(dir_path)
  dir_path = dir_path or "."
  return call_engine_command({ "discover-build-tool", "--dir", dir_path }, nil, "discover-build-tool")
end

--- Discover workspace metadata
---@param dir_path? string
---@return { root: string, build_tool: string, modules: string[], is_multi_module: boolean }|nil
function M.discover_workspace(dir_path)
  dir_path = dir_path or "."
  return call_engine_command({ "discover-workspace", "--dir", dir_path }, nil, "discover-workspace")
end

--- Assemble test CLI command for Maven, Gradle, or SBT
---@param opts { tool?: string, class?: string, method?: string, dir?: string }
---@return { command: string, cwd: string }|nil
function M.assemble_test_command(opts)
  opts = opts or {}
  local args = { "assemble-test-command" }
  if opts.tool then table.insert(args, "--tool"); table.insert(args, opts.tool) end
  if opts["class"] then table.insert(args, "--class"); table.insert(args, opts["class"]) end
  if opts.method then table.insert(args, "--method"); table.insert(args, opts.method) end
  if opts.dir then table.insert(args, "--dir"); table.insert(args, opts.dir) end
  return call_engine_command(args, nil, "assemble-test-command")
end

--- Manage cloud theme state
---@param action "get"|"set"
---@param opts? { theme?: string, variant?: string, file?: string }
---@return { theme: string, variant: string|nil }|nil
function M.manage_theme(action, opts)
  opts = opts or {}
  local args = { "manage-theme", "--action", action }
  if opts.theme then table.insert(args, "--theme"); table.insert(args, opts.theme) end
  if opts.variant then table.insert(args, "--variant"); table.insert(args, opts.variant) end
  if opts.file then table.insert(args, "--file"); table.insert(args, opts.file) end
  return call_engine_command(args, nil, "manage-theme")
end

return M
