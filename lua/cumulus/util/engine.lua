-- Cumulus Scala Engine Integration (SPEC-031)
--
-- Discovers and executes the compiled `cumulus-engine` Scala binary to offload
-- heavy POM parsing, Gradle task extraction, build log parsing, REST endpoint extraction,
-- JaCoCo coverage parsing, Flyway migration validation, Spring Bean dependency graphs,
-- log indexing, import optimization, K8s schema validation, Git conflict resolution,
-- session sanitization, workspace discovery, theme management, test command assembly, etc.
--
-- ============================================================================
-- ENGINE API CONTRACTS - CALLERS MUST VALIDATE RESPONSES
-- ============================================================================
-- Key APIs and their guaranteed return types:
-- - discover_devops_roots(dir, opts) → { terraform: [], sam: [], ansible: [], docker: [], helm: [] }
-- - discover_build_tool(cwd) → { tool: "maven"|"gradle"|nil, ... }
-- - assemble_test_command(opts) → { command: string, ... } or nil
-- - parse_test_output(log) → [ { status: "PASSED"|"FAILED"|..., ... } ] or nil
-- - detect_test_context(file, line) → { class_name: string, method_name: string } or nil
-- - resolve_modules(dir) → [ { name: string, path: string } ] or nil
--
-- IMPORTANT: Always validate responses before use - engine may return nil, empty tables, or
-- unexpected structures. Use type() checks before calling # operator or indexing.
-- ============================================================================

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
---@param opts? { debug?: boolean, silent?: boolean } Optional options
---@return table|nil Decoded data field, or nil if decode fails or success is false
local function safe_json_decode(json_str, context, opts)
  opts = opts or {}
  local ok, parsed = pcall(vim.json.decode, json_str)
  if not ok or type(parsed) ~= "table" then
    if not opts.silent then
      local msg = string.format("[cumulus] JSON decode failed%s: %s",
        context and " (" .. context .. ")" or "",
        tostring(parsed))
      vim.notify(msg, vim.log.levels.WARN)
    end
    return nil
  end

  -- Check success flag in envelope
  if parsed.success == false then
    if not opts.silent then
      local error_msg = parsed.error or "Unknown error"
      local msg = string.format("[cumulus] %s%s", error_msg,
        parsed.error_code and (" (" .. parsed.error_code .. ")") or "")
      vim.notify(msg, vim.log.levels.WARN)
    end
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
---@param opts? { debug?: boolean, silent?: boolean } Optional options
---@return table|nil Decoded JSON output or nil
local function call_engine_command(args, stdin, context, opts)
  opts = opts or {}
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
    if res.code ~= 0 and not opts.silent then
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
  local res = call_engine_command({ "parse-pom", "--file", pom_path }, nil, "parse-pom")
  if not res then
    return nil
  end
  local raw = res.goals or res
  if type(raw) == "table" then
    local list = {}
    for _, g in ipairs(raw) do
      if type(g) == "table" and g.name then
        table.insert(list, g.name)
      elseif type(g) == "string" then
        table.insert(list, g)
      end
    end
    return list
  end
  return nil
end

--- Parse Gradle task list from stdout using Scala engine
---@param content string
---@return string[]|nil
function M.parse_gradle_tasks(content)
  local res = call_engine_command({ "parse-gradle-tasks" }, content, "parse-gradle-tasks")
  if not res then
    return nil
  end
  local raw = res.tasks or res
  if type(raw) == "table" then
    local list = {}
    for _, t in ipairs(raw) do
      if type(t) == "table" and t.name then
        table.insert(list, t.name)
      elseif type(t) == "string" then
        table.insert(list, t)
      end
    end
    return list
  end
  return nil
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
  local res = call_engine_command({ "parse-modules", "--tool", tool, "--file", file_path }, nil, "parse-modules")
  if not res then
    return nil
  end
  return res.modules or res
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
  local res = call_engine_command({ "generate-java-header", "--file", file_path }, nil, "generate-java-header")
  if not res then
    return nil
  end
  if type(res) == "table" and res.class_declaration then
    local lines = {}
    if res.package_name and res.package_name ~= "" then
      table.insert(lines, "package " .. res.package_name .. ";")
      table.insert(lines, "")
    end
    local decl = res.class_declaration
    if decl:match("{\\s*}$") or decl:match("%s*{%s*}") then
      local base_decl = decl:gsub("%s*{%s*}", "")
      table.insert(lines, base_decl .. " {")
      table.insert(lines, "    ")
      table.insert(lines, "}")
    else
      for l in decl:gmatch("[^\r\n]+") do
        table.insert(lines, l)
      end
    end
    return lines
  elseif type(res) == "table" and #res > 0 then
    return res
  end
  return nil
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

--- Perform a comprehensive health check of system binaries, compilers, JDKs, and Gradle wrapper
---@return { binaries: table[], compilers: table[], jdks: table[], gradle_wrapper: table|nil, build_tool: string, engine_available: boolean, notes: string[]|nil }|nil
function M.check_health()
  local ok, response = pcall(call_engine_command, { "check-health" }, nil, "check-health", { silent = true })
  if ok and response then
    return response
  end
  return nil
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
  local res = call_engine_command({ "extract-endpoints", "--dir", dir_path }, nil, "extract-endpoints")
  if not res then
    return nil
  end
  return res.endpoints or res
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
  local res = call_engine_command({ "parse-spring-beans", "--dir", dir_path }, nil, "parse-spring-beans")
  if not res then
    return nil
  end
  return res.beans or res
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
  local res = call_engine_command({ "optimize-imports" }, code_content, "optimize-imports")
  if not res then
    return nil
  end
  return res.imports or res
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
  local res = call_engine_command({ "extract-codelens", "--file", file_path }, nil, "extract-codelens")
  if not res then
    return nil
  end
  return res.items or res
end

--- Resolve all modules in a Maven/Gradle project directory using Scala engine
---@param dir_path string Root project directory
---@return table|nil Module tree with { modules = array of { name = string, path = string, type = string } }
function M.resolve_modules(dir_path)
  dir_path = dir_path or vim.fn.getcwd()
  local res = call_engine_command({ "resolve-modules", "--dir", dir_path }, nil, "resolve-modules", { silent = true })
  if not res then
    return nil
  end
  return res.modules or res
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
---@param version? string|number
---@param opts? { silent?: boolean }
---@return { java_home: string, version: string }|nil
function M.discover_jdk(version, opts)
  opts = opts or {}
  local args = { "discover-jdk" }
  if version then
    table.insert(args, "--version")
    table.insert(args, tostring(version))
  end
  return call_engine_command(args, nil, "discover-jdk", opts)
end

--- Discover build tool for a directory (maven, gradle, sbt)
---@param dir_path? string
---@param opts? { silent?: boolean }
---@return { tool: string, build_tool: string, root: string, config_file: string, wrapper_available: boolean }|nil
function M.discover_build_tool(dir_path, opts)
  dir_path = dir_path or "."
  opts = vim.tbl_extend("force", { silent = true }, opts or {})
  local res = call_engine_command({ "discover-build-tool", "--dir", dir_path }, nil, "discover-build-tool", opts)
  if res then
    res.tool = res.tool or res.build_tool
    res.build_tool = res.build_tool or res.tool
  end
  return res
end

--- Discover workspace metadata
---@param dir_path? string
---@param opts? { silent?: boolean }
---@return { root: string, tool: string, build_tool: string, modules: string[], is_multi_module: boolean }|nil
function M.discover_workspace(dir_path, opts)
  dir_path = dir_path or "."
  opts = vim.tbl_extend("force", { silent = true }, opts or {})
  local res = call_engine_command({ "discover-workspace", "--dir", dir_path }, nil, "discover-workspace", opts)
  if res then
    res.tool = res.tool or res.build_tool
    res.build_tool = res.build_tool or res.tool
  end
  return res
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

--- Assemble CLI command for Maven, Gradle, SBT, Terraform, SAM, Ansible, Docker, or Helm
---@param intent { tool: string, action: string, target?: string, dir?: string, flags?: string[], env?: table<string, string> }
---@param opts? { silent?: boolean }
---@return { command: string, executable: string, args: string[], cwd: string, env: table<string, string> }|nil
function M.assemble_command(intent, opts)
  if not intent or not intent.tool or not intent.action then
    return nil
  end
  opts = opts or {}
  local payload = vim.json.encode(intent)
  return call_engine_command({ "assemble-command" }, payload, "assemble-command", opts)
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

--- Inspect CloudFormation / SAM template via Scala engine
---@param file_path string Path to template YAML/JSON
---@param opts? { silent?: boolean }
---@return { format_version: string|nil, transform: string|nil, description: string|nil, is_sam: boolean, parameters: table[], resources: table[], functions: table[], outputs: string[] }|nil
function M.inspect_cfn_template(file_path, opts)
  if not file_path or file_path == "" then
    return nil
  end
  opts = opts or {}
  return call_engine_command({ "cfn-inspect", "--file", file_path }, nil, "cfn-inspect", opts)
end

--- Validate CloudFormation / SAM template offline via Scala engine
---@param file_path string Path to template YAML/JSON
---@param opts? { silent?: boolean }
---@return { line: number, col: number|nil, severity: string, message: string, logical_id: string|nil }[]|nil
function M.validate_cfn_template(file_path, opts)
  if not file_path or file_path == "" then
    return nil
  end
  opts = opts or {}
  return call_engine_command({ "cfn-validate", "--file", file_path }, nil, "cfn-validate", opts)
end

--- Inspect Ansible playbook via Scala engine
---@param file_path string Path to playbook YAML
---@param opts? { silent?: boolean }
---@return { plays: table[], total_tasks: number, roles: string[], hosts: string[] }|nil
function M.inspect_ansible_playbook(file_path, opts)
  if not file_path or file_path == "" then
    return nil
  end
  opts = opts or {}
  return call_engine_command({ "ansible-inspect", "--file", file_path }, nil, "ansible-inspect", opts)
end

--- Validate Ansible playbook offline via Scala engine
---@param file_path string Path to playbook YAML
---@param opts? { silent?: boolean }
---@return { line: number, col: number|nil, severity: string, message: string }[]|nil
function M.validate_ansible_playbook(file_path, opts)
  if not file_path or file_path == "" then
    return nil
  end
  opts = opts or {}
  return call_engine_command({ "ansible-validate", "--file", file_path }, nil, "ansible-validate", opts)
end

--- Parse Ansible inventory via Scala engine
---@param file_path string Path to inventory file or graph/JSON text
---@param opts? { silent?: boolean }
---@return { name: string, hosts: string[], children: string[], vars: table }[]|nil
function M.parse_ansible_inventory(file_path, opts)
  if not file_path or file_path == "" then
    return nil
  end
  opts = opts or {}
  return call_engine_command({ "ansible-inventory-parse", "--file", file_path }, nil, "ansible-inventory-parse", opts)
end

--- Run an arbitrary engine subcommand
---@param subcommand string Subcommand name
---@param args? string[] Arguments array
---@param stdin? string Optional stdin content
---@param opts? { debug?: boolean, silent?: boolean } Optional options
---@return table|nil
function M.run_command(subcommand, args, stdin, opts)
  local full_args = { subcommand }
  if args then
    for _, arg in ipairs(args) do
      table.insert(full_args, arg)
    end
  end
  return call_engine_command(full_args, stdin, subcommand, opts)
end

--- Classify workspace topology and multi-project structure via Scala engine
---@param dir? string Root workspace directory (defaults to cwd)
---@param opts? { silent?: boolean }
---@return { root: string, primary_type: string, project_types: string[], submodules: table[], has_spring: boolean, iac_types: string[], is_multi_module: boolean }|nil
function M.classify_workspace(dir, opts)
  dir = dir or vim.fn.getcwd()
  opts = vim.tbl_extend("force", { silent = true }, opts or {})
  return call_engine_command({ "classify-workspace", "--dir", dir }, nil, "classify-workspace", opts)
end

--- Resolve formatter for a file by inspecting project configuration
---@param file_path string Path to the file to format
---@param opts? { silent?: boolean }
---@return { formatter: string, config_file: string|nil, args: { name: string, value: string }[], stdin_mode: boolean, notes: string[]|nil }|nil
function M.resolve_formatter(file_path, opts)
  if not file_path or file_path == "" then
    return nil
  end
  opts = opts or {}
  return call_engine_command({ "resolve-formatter", "--file", file_path }, nil, "resolve-formatter", opts)
end

--- Discover DevOps / IaC roots across workspace via Scala engine
---@param path? string File or directory path to discover roots for (defaults to cwd)
---@param opts? { silent?: boolean }
---@return { workspace_root: string, terraform: string[], sam: string[], ansible: string[], docker: string[], helm: string[] }|nil
function M.discover_devops_roots(path, opts)
  path = path or vim.fn.getcwd()
  opts = vim.tbl_extend("force", { silent = true }, opts or {})
  return call_engine_command({ "discover-devops-roots", "--path", path }, nil, "discover-devops-roots", opts)
end

--- Generate DAP debug configuration for Spring Boot or JVM project via Scala engine
---@param dir? string Root project directory (defaults to cwd)
---@param opts? { silent?: boolean }
---@return { launch: table, attach: table, configurations: table[] }|nil
function M.generate_dap_config(dir, opts)
  dir = dir or vim.fn.getcwd()
  opts = vim.tbl_extend("force", { silent = true }, opts or {})
  return call_engine_command({ "generate-dap-config", "--dir", dir }, nil, "generate-dap-config", opts)
end

-- ==============================================================================
-- ⭐ Consolidated UI Pickers & Actions (Story 13.1)
-- ==============================================================================

--- Select a Spring bean from the workspace dependency graph and navigate to its source.
function M.select_bean()
  local cwd = vim.fn.getcwd()
  local beans = M.is_available() and M.parse_spring_beans(cwd) or nil
  if not beans or #beans == 0 then
    vim.notify("No Spring stereotypes (@Component, @Service, etc.) found", vim.log.levels.WARN)
    return
  end

  vim.ui.select(beans, {
    prompt = "Select Spring Bean:",
    format_item = function(item)
      local deps = #item.injected_deps > 0 and (" -> [" .. table.concat(item.injected_deps, ", ") .. "]") or ""
      return string.format("%s (%s)%s", item.bean_name, item.class_name, deps)
    end,
  }, function(choice)
    if choice and choice.file then
      vim.cmd("edit " .. vim.fn.fnameescape(choice.file))
      vim.api.nvim_win_set_cursor(0, { choice.line, 0 })
    end
  end)
end

--- Select a REST endpoint from the workspace and jump to its controller definition.
function M.select_endpoint()
  local cwd = vim.fn.getcwd()
  local eps = M.is_available() and M.extract_endpoints(cwd) or nil
  if not eps or #eps == 0 then
    vim.notify("No Spring Boot / JAX-RS endpoints found in project", vim.log.levels.WARN)
    return
  end

  vim.ui.select(eps, {
    prompt = "Spring Boot REST Endpoints:",
    format_item = function(item)
      return string.format("[%s] %s (%s:%d)", item.http_method, item.path, item.class_name, item.line)
    end,
  }, function(choice)
    if choice and choice.file then
      vim.cmd("edit " .. vim.fn.fnameescape(choice.file))
      vim.api.nvim_win_set_cursor(0, { choice.line, 0 })
    end
  end)
end

--- Optimize Java/Kotlin imports in the current active buffer.
function M.optimize_imports_buffer()
  local bufnr = vim.api.nvim_get_current_buf()
  local lines = vim.api.nvim_buf_get_lines(bufnr, 0, -1, false)
  local content = table.concat(lines, "\n")

  local new_lines = M.is_available() and M.optimize_imports(content) or nil
  if new_lines and #new_lines > 0 then
    vim.api.nvim_buf_set_lines(bufnr, 0, -1, false, new_lines)
    vim.notify("Imports optimized successfully", vim.log.levels.INFO)
  else
    vim.notify("Import optimization unchanged", vim.log.levels.INFO)
  end
end

--- Validate Kubernetes manifest in the current buffer and populate diagnostics.
local k8s_ns = vim.api.nvim_create_namespace("cumulus_k8s_validation")
function M.validate_k8s_manifest_buffer()
  local bufnr = vim.api.nvim_get_current_buf()
  local lines = vim.api.nvim_buf_get_lines(bufnr, 0, -1, false)
  local content = table.concat(lines, "\n")

  local issues = M.is_available() and M.validate_k8s_manifest(content) or nil
  vim.diagnostic.clear(k8s_ns, bufnr)

  if not issues or #issues == 0 then
    vim.notify("Kubernetes manifest structure valid", vim.log.levels.INFO)
    return
  end

  local diags = {}
  for _, issue in ipairs(issues) do
    table.insert(diags, {
      lnum = math.max(0, issue.line - 1),
      col = issue.col and math.max(0, issue.col - 1) or 0,
      message = issue.message,
      severity = vim.diagnostic.severity.ERROR,
      source = "k8s_validator",
    })
  end

  vim.diagnostic.set(k8s_ns, bufnr, diags)
end
-- Alias for spec compatibility
M.validate_manifest = M.validate_k8s_manifest_buffer

--- Validate Flyway migration scripts in default or specified directory.
---@param dir? string Optional migrations directory
function M.validate_migrations_action(dir)
  local cwd = vim.fn.getcwd()
  dir = dir or (cwd .. "/src/main/resources/db/migration")

  local issues = M.is_available() and M.validate_migrations(dir) or nil
  if not issues or #issues == 0 then
    vim.notify("Flyway migrations verified — 0 issues found", vim.log.levels.INFO)
    return
  end

  for _, issue in ipairs(issues) do
    local level = issue.severity == "ERROR" and vim.log.levels.ERROR or vim.log.levels.WARN
    vim.notify(string.format("[%s] %s (%s)", issue.severity, issue.message, vim.fn.fnamemodify(issue.file, ":t")), level)
  end
end

--- Parse Git conflict markers in current buffer and show interactive jump picker.
function M.resolve_git_conflicts()
  local bufnr = vim.api.nvim_get_current_buf()
  local lines = vim.api.nvim_buf_get_lines(bufnr, 0, -1, false)
  local content = table.concat(lines, "\n")

  local blocks = M.is_available() and M.parse_git_conflicts(content) or nil
  if not blocks or #blocks == 0 then
    vim.notify("No Git conflict markers (<<<<<<<) found in buffer", vim.log.levels.INFO)
    return
  end

  vim.ui.select(blocks, {
    prompt = "Jump to Git Conflict:",
    format_item = function(item)
      return string.format("Line %d: %s vs %s", item.start_line, item.current_header, item.incoming_header)
    end,
  }, function(choice)
    if choice then
      vim.api.nvim_win_set_cursor(0, { choice.start_line, 0 })
    end
  end)
end
-- Alias for spec compatibility
M.resolve_conflicts = M.resolve_git_conflicts

--- Load JaCoCo code coverage report and populate diagnostics.
---@param xml_path? string
local coverage_ns = vim.api.nvim_create_namespace("cumulus_coverage")
function M.view_coverage(xml_path)
  xml_path = xml_path or (vim.fn.getcwd() .. "/target/site/jacoco/jacoco.xml")

  local entries = M.is_available() and M.parse_coverage(xml_path) or nil
  if not entries or #entries == 0 then
    vim.notify("No JaCoCo coverage report found at: " .. xml_path, vim.log.levels.WARN)
    return
  end

  vim.diagnostic.clear(coverage_ns)

  for _, entry in ipairs(entries) do
    local bufnr = vim.fn.bufnr(entry.file, false)
    if bufnr ~= -1 and vim.api.nvim_buf_is_valid(bufnr) then
      local diags = {}
      for _, lnr in ipairs(entry.missed_lines) do
        table.insert(diags, {
          lnum = math.max(0, lnr - 1),
          col = 0,
          message = "Uncovered line (JaCoCo)",
          severity = vim.diagnostic.severity.WARN,
          source = "JaCoCo",
        })
      end
      vim.diagnostic.set(coverage_ns, bufnr, diags)
    end
  end

  vim.notify("JaCoCo coverage loaded successfully", vim.log.levels.INFO)
end
M.load_coverage = M.view_coverage

--- Index log content in current buffer and show interactive jump picker for ERROR/WARN lines.
function M.search_indexed_logs()
  local bufnr = vim.api.nvim_get_current_buf()
  local lines = vim.api.nvim_buf_get_lines(bufnr, 0, -1, false)
  local content = table.concat(lines, "\n")

  local entries = M.is_available() and M.index_log(content) or nil
  if not entries or #entries == 0 then
    vim.notify("No ERROR/WARN messages found in log buffer", vim.log.levels.INFO)
    return
  end

  vim.ui.select(entries, {
    prompt = "Jump to Log Entry:",
    format_item = function(item)
      return string.format("Line %d [%s] %s", item.line, item.level, item.message)
    end,
  }, function(choice)
    if choice then
      vim.api.nvim_win_set_cursor(0, { choice.line, 0 })
    end
  end)
end
M.index_current_buffer = M.search_indexed_logs

--- Parse build log content and populate diagnostics across project buffers
--- Consolidates all build error parsing to engine; no Lua fallback parsing remains (SPEC-1.4)
---@param tool "maven"|"gradle"|"sbt"
---@param log_content string Build log text to parse
local build_ns = vim.api.nvim_create_namespace("cumulus_build")
function M.populate_build_diagnostics(tool, log_content)
  if not M.is_available() then
    vim.notify(
      "cumulus-engine binary missing: cannot parse build diagnostics. Build via 'cd engine && sbt graalvm-native-image:packageBin' or run ':CumulusInstallEngine'",
      vim.log.levels.WARN
    )
    return
  end

  local entries = M.parse_build_log(tool, log_content)

  if not entries or #entries == 0 then
    return
  end

  -- Group diagnostics by target file buffer
  local by_file = {}
  for _, entry in ipairs(entries) do
    by_file[entry.file] = by_file[entry.file] or {}
    table.insert(by_file[entry.file], entry)
  end

  for filepath, file_entries in pairs(by_file) do
    local bufnr = vim.fn.bufnr(filepath, true)
    if bufnr ~= -1 and vim.api.nvim_buf_is_valid(bufnr) then
      vim.fn.bufload(bufnr)
      local diagnostics = {}
      for _, e in ipairs(file_entries) do
        table.insert(diagnostics, {
          lnum = math.max(0, e.line - 1),
          col = e.col and math.max(0, e.col - 1) or 0,
          message = e.message,
          severity = vim.diagnostic.severity.ERROR,
          source = "cumulus_build",
        })
      end
      vim.diagnostic.set(build_ns, bufnr, diagnostics)
    end
  end
end

--- Clear build diagnostics for a buffer or all buffers
---@param bufnr? number Optional buffer number; if nil, clears all buffers
function M.clear_build_diagnostics(bufnr)
  if bufnr then
    vim.diagnostic.clear(build_ns, bufnr)
  else
    for _, buf in ipairs(vim.api.nvim_list_bufs()) do
      vim.diagnostic.clear(build_ns, buf)
    end
  end
end

-- ==============================================================================
-- ⭐ Universal Notification & Terminal Bridge (Story 13.2)
-- ==============================================================================

--- Standardized notification dispatcher with default title and level mapping.
---@param msg string Message text
---@param level? number vim.log.levels level (default: INFO)
---@param title? string Notification title (default: "Cumulus")
---@param opts? table Additional notification options (e.g. id, timeout)
function M.notify(msg, level, title, opts)
  level = level or vim.log.levels.INFO
  title = title or "Cumulus"
  opts = vim.tbl_extend("force", { title = title }, opts or {})
  vim.notify(msg, level, opts)
end

--- Standardized info notification.
---@param msg string Message text
---@param title? string Notification title (default: "Cumulus")
---@param opts? table Additional notification options
function M.notify_info(msg, title, opts)
  M.notify(msg, vim.log.levels.INFO, title, opts)
end

--- Standardized warning notification.
---@param msg string Message text
---@param title? string Notification title (default: "Cumulus")
---@param opts? table Additional notification options
function M.notify_warn(msg, title, opts)
  M.notify(msg, vim.log.levels.WARN, title, opts)
end

--- Standardized error notification.
---@param msg string Message text
---@param title? string Notification title (default: "Cumulus")
---@param opts? table Additional notification options
function M.notify_err(msg, title, opts)
  M.notify(msg, vim.log.levels.ERROR, title, opts)
end

--- Run a command in an interactive, non-blocking terminal session.
--- Uses Snacks.terminal when available, falling back gracefully to a bottom split (15split) with terminal keymaps.
---@param cmd string|string[] Command string or command argv list to execute
---@param opts? { cwd?: string, timeout?: number, title?: string, on_exit?: fun(code: number), on_stdout?: fun(data: string[]), on_stderr?: fun(data: string[]) }
function M.run_term(cmd, opts)
  opts = opts or {}
  local term_cwd = opts.cwd or vim.fn.getcwd()
  local timeout = opts.timeout or 3600000 -- default 1 hour
  local title = opts.title or "Cumulus"
  local snacks = _G.Snacks or package.loaded["snacks"]

  if snacks and snacks.terminal then
    snacks.terminal(cmd, { cwd = term_cwd })
    return
  end

  vim.cmd("botright 15split")
  local win = vim.api.nvim_get_current_win()
  local buf = vim.api.nvim_create_buf(false, true)
  vim.api.nvim_win_set_buf(win, buf)

  local job_id = vim.fn.termopen(cmd, {
    cwd = term_cwd,
    on_stdout = function(_, data)
      if opts.on_stdout then
        opts.on_stdout(data)
      end
    end,
    on_stderr = function(_, data)
      if opts.on_stderr then
        opts.on_stderr(data)
      end
    end,
    on_exit = function(_, code)
      if opts.on_exit then
        opts.on_exit(code)
      else
        if code == 0 or code == 130 then
          M.notify_info("Process finished (exit code " .. code .. ")", title)
        else
          M.notify_err("Process exited with code " .. code, title)
        end
      end
    end,
  })

  -- Set timeout timer to prevent indefinite hangs
  if timeout > 0 then
    vim.fn.timer_start(timeout, function()
      if vim.fn.jobwait({ job_id }, 0)[1] == -1 then
        vim.fn.jobstop(job_id)
        M.notify_warn("Process timeout (" .. (timeout / 1000) .. "s), job terminated", title)
      end
    end)
  end

  vim.keymap.set("n", "q", "<cmd>q<cr>", { buffer = buf, silent = true })
  vim.keymap.set("t", "<Esc>", [[<C-\><C-n>]], { buffer = buf, silent = true })
  vim.cmd("startinsert")
end

--- Generate theme highlights for a cloud provider via Scala engine
---@param provider string Cloud provider name: "aws", "azure", "gcp", or "oci"
---@param opts? { silent?: boolean }
---@return { provider: string, base_color: string, highlights: { [string]: { fg?: string, bg?: string, bold?: boolean, italic?: boolean, underline?: boolean } } }|nil
function M.generate_theme_highlights(provider, opts)
  if not provider or provider == "" then
    return nil
  end
  opts = opts or {}
  return call_engine_command({ "generate-theme-highlights", provider }, nil, "generate-theme-highlights", opts)
end

return M



