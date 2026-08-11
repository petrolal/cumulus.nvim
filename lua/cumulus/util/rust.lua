-- Cumulus Rust Helper Integration (SPEC-016 & SPEC-018..025)
--
-- Discovers and executes the compiled `cumulus-core` Rust binary to offload
-- heavy POM parsing, Gradle task extraction, build log parsing, REST endpoint extraction,
-- JaCoCo coverage parsing, Flyway migration validation, Spring Bean dependency graphs,
-- log indexing, import optimization, K8s schema validation, and Git conflict resolution.

local M = {}

local cached_bin = nil
local cache_expires_at = nil
local CACHE_TTL_MS = 300000  -- 5 minutes: refresh cache on rebuild/reinstall during dev

--- Locates the `cumulus-core` binary on $PATH or within the workspace target directory.
---@return string|nil
function M.get_bin()
  local now = vim.loop.now()
  if cached_bin ~= nil and cache_expires_at and now < cache_expires_at then
    return cached_bin ~= false and cached_bin or nil
  end

  if vim.fn.executable("cumulus-core") == 1 then
    cached_bin = "cumulus-core"
    cache_expires_at = now + CACHE_TTL_MS
    return cached_bin
  end

  -- Search relative to Neovim runtimepath / workspace directory
  local script_dir = vim.fn.fnamemodify(debug.getinfo(1, "S").source:sub(2), ":p:h:h:h:h")
  local release_bin = script_dir .. "/crates/cumulus-core/target/release/cumulus-core"
  local debug_bin = script_dir .. "/crates/cumulus-core/target/debug/cumulus-core"

  if vim.fn.executable(release_bin) == 1 then
    cached_bin = release_bin
    cache_expires_at = now + CACHE_TTL_MS
    return cached_bin
  elseif vim.fn.executable(debug_bin) == 1 then
    cached_bin = debug_bin
    cache_expires_at = now + CACHE_TTL_MS
    return cached_bin
  end

  cached_bin = false
  cache_expires_at = now + CACHE_TTL_MS
  return nil
end

--- Returns true if the Rust helper binary is available.
---@return boolean
function M.is_available()
  return M.get_bin() ~= nil
end

--- Invalidate the binary cache (useful for tests or forcing a refresh after rebuild).
function M.invalidate_cache()
  cached_bin = nil
  cache_expires_at = nil
end

--- Safely decode JSON from Rust output and check success flag.
---@param json_str string
---@param context? string Optional context for error messages (e.g., "parse-build-log")
---@param opts? { debug: boolean } Optional options (debug=true logs success responses)
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

--- Internal helper: call Rust command and decode JSON output.
---@param args string[] Command and arguments (e.g., { "parse-build-log", "--tool", "maven" })
---@param stdin? string Optional stdin content
---@param context? string Optional context for error logging
---@param opts? { debug: boolean } Optional options (debug=true logs success responses)
---@return table|nil Decoded JSON output or nil
local function call_rust_command(args, stdin, context, opts)
  local bin = M.get_bin()
  if not bin then
    return nil
  end

  local cmd_args = { bin }
  for _, arg in ipairs(args) do
    table.insert(cmd_args, arg)
  end

  local res = vim.system(cmd_args, { stdin = stdin, text = true }):wait()
  if res.code ~= 0 or res.stdout == "" then
    if res.code ~= 0 then
      vim.notify(
        string.format("[cumulus] command failed with exit code %d", res.code),
        vim.log.levels.WARN
      )
    end
    return nil
  end

  return safe_json_decode(res.stdout, context or args[1], opts)
end

--- Parse Maven goals using Rust helper
---@param pom_path string
---@return string[]|nil
function M.parse_pom_goals(pom_path)
  return call_rust_command({ "parse-pom", "--file", pom_path }, nil, "parse-pom")
end

--- Parse Gradle task list from stdout using Rust helper
---@param content string
---@return string[]|nil
function M.parse_gradle_tasks(content)
  return call_rust_command({ "parse-gradle-tasks" }, content, "parse-gradle-tasks")
end

--- Parse build log content using Rust helper
---@param tool "maven"|"gradle"
---@param log_content string
---@return table[]|nil Array of { file = string, line = number, col = number|nil, message = string, severity = string }
function M.parse_build_log(tool, log_content)
  return call_rust_command({ "parse-build-log", "--tool", tool }, log_content, "parse-build-log")
end

--- Parse Maven/Gradle sub-modules from pom.xml or settings.gradle using Rust helper
---@param tool "maven"|"gradle"
---@param file_path string
---@return table[]|nil Array of { name = string, path = string }
function M.parse_modules(tool, file_path)
  return call_rust_command({ "parse-modules", "--tool", tool, "--file", file_path }, nil, "parse-modules")
end

--- Parse stacktrace log content using Rust helper
---@param log_content string
---@return table[]|nil Array of { class_name = string, method_name = string, file = string, line = number }
function M.parse_stacktrace(log_content)
  return call_rust_command({ "parse-stacktrace" }, log_content, "parse-stacktrace")
end

--- Generate Java package and class header using Rust helper
---@param file_path string
---@return string[]|nil Array of lines
function M.generate_java_header(file_path)
  return call_rust_command({ "generate-java-header", "--file", file_path }, nil, "generate-java-header")
end

--- Parse test log output using Rust helper
---@param log_content string
---@return table[]|nil Array of { test_name = string, class_name = string, status = string, failure_message = string|nil, file = string|nil, line = number|nil }
function M.parse_test_output(log_content)
  return call_rust_command({ "parse-test-output" }, log_content, "parse-test-output")
end

--- Check network connectivity using Rust helper
---@param host? string host:port format (default "repo.maven.apache.org:443")
---@return boolean|nil returns boolean or nil if Rust helper unavailable
function M.check_network(host)
  host = host or "repo.maven.apache.org:443"
  local result = call_rust_command({ "check-network", "--host", host }, nil, "check-network")
  return result and result.online or nil
end

--- Parse Checkstyle XML report using Rust helper
---@param xml_content string
---@return table[]|nil Array of { file = string, line = number, col = number|nil, message = string, severity = string }
function M.parse_checkstyle(xml_content)
  return call_rust_command({ "parse-checkstyle" }, xml_content, "parse-checkstyle")
end

--- Extract REST endpoints from directory using Rust helper
---@param dir_path string
---@return table[]|nil Array of { file = string, line = number, http_method = string, path = string, class_name = string, handler_name = string }
function M.extract_endpoints(dir_path)
  return call_rust_command({ "extract-endpoints", "--dir", dir_path }, nil, "extract-endpoints")
end

--- Parse JaCoCo coverage XML file using Rust helper
---@param file_path string
---@return table[]|nil Array of { file = string, covered_lines = number[], missed_lines = number[] }
function M.parse_coverage(file_path)
  return call_rust_command({ "parse-coverage", "--file", file_path }, nil, "parse-coverage")
end

--- Validate Flyway migration scripts in directory using Rust helper
---@param dir_path string
---@return table[]|nil Array of { file = string, line = number|nil, severity = string, message = string }
function M.validate_migrations(dir_path)
  return call_rust_command({ "validate-migrations", "--dir", dir_path }, nil, "validate-migrations")
end

--- Extract Spring Bean dependencies from directory using Rust helper
---@param dir_path string
---@return table[]|nil Array of { class_name = string, bean_name = string, file = string, line = number, injected_deps = string[] }
function M.parse_spring_beans(dir_path)
  return call_rust_command({ "parse-spring-beans", "--dir", dir_path }, nil, "parse-spring-beans")
end

--- Index log content using Rust helper
---@param log_content string
---@return table[]|nil Array of { line = number, level = string, timestamp = string|nil, message = string }
function M.index_log(log_content)
  return call_rust_command({ "index-log" }, log_content, "index-log")
end

--- Optimize Java/Kotlin imports using Rust helper
---@param code_content string
---@return string[]|nil Array of formatted lines
function M.optimize_imports(code_content)
  return call_rust_command({ "optimize-imports" }, code_content, "optimize-imports")
end

--- Validate K8s manifest content using Rust helper
---@param yaml_content string
---@return table[]|nil Array of { line = number, col = number|nil, message = string, severity = string }
function M.validate_k8s_manifest(yaml_content)
  return call_rust_command({ "validate-k8s-manifest" }, yaml_content, "validate-k8s-manifest")
end

--- Parse Git conflict markers using Rust helper
---@param content string
---@return table[]|nil Array of { start_line = number, sep_line = number, end_line = number, current_header = string, incoming_header = string }
function M.parse_git_conflicts(content)
  return call_rust_command({ "parse-git-conflicts" }, content, "parse-git-conflicts")
end

--- Detect nearest test class and method at a given cursor line in a Java/Kotlin file.
--- Replaces the Lua regex-based detect_test_info() that previously lived in test-runner.lua.
---@param file_path string Absolute path to the Java/Kotlin source file
---@param cursor_line number 1-indexed line number of the cursor
---@return { class_name: string|nil, method_name: string|nil }|nil
function M.detect_test_context(file_path, cursor_line)
  return call_rust_command({ "detect-test-context", "--file", file_path, "--line", tostring(cursor_line) }, nil, "detect-test-context")
end

--- Resolve direct project dependencies using Rust helper (SPEC-026)
---@param file_path string Path to pom.xml or libs.versions.toml
---@return table[]|nil Array of { group = string, artifact = string, version = string, scope = string }
function M.resolve_deps(file_path)
  return call_rust_command({ "resolve-deps", "--file", file_path }, nil, "resolve-deps")
end

--- Extract instant Java & Kotlin CodeLens items using Rust helper (SPEC-027)
---@param file_path string Path to Java or Kotlin source file
---@return table[]|nil Array of { line = number, title = string, command = string, args = string[] }
function M.extract_codelens(file_path)
  return call_rust_command({ "extract-codelens", "--file", file_path }, nil, "extract-codelens")
end

--- Solve multi-module topological build order & DAG using Rust helper (SPEC-028)
---@param dir_path string Root project directory
---@return table[]|nil Array of { step = number, module_name = string, path = string, build_command = string }
function M.compute_build_order(dir_path)
  return call_rust_command({ "compute-build-order", "--dir", dir_path }, nil, "compute-build-order")
end

--- Check JDTLS classpath sync status by scanning build config mtime (SPEC-005)
---@param dir_path string Root project directory
---@param start_time number JDTLS start time (epoch seconds)
---@return { sync_needed: boolean, modified_file: string|nil }|nil
function M.check_jdtls_sync(dir_path, start_time)
  return call_rust_command({ "check-jdtls-sync", "--dir", dir_path, "--start-time", tostring(start_time) }, nil, "check-jdtls-sync")
end

--- Verify Gradle wrapper configuration against CI workflows and SHA-256 (SPEC-012)
---@param dir_path string Root project directory
---@return { local_version: string|nil, ci_version: string|nil, sha256_configured: boolean, sha256_valid: boolean, issues: string[] }|nil
function M.verify_gradle_wrapper(dir_path)
  return call_rust_command({ "verify-gradle-wrapper", "--dir", dir_path }, nil, "verify-gradle-wrapper")
end

--- Sanitize a Neovim session file by removing ephemeral buffers and floating windows (SPEC-030)
---@param file_path string Path to .vim session file
---@return { success: boolean, cleaned_lines: number, total_lines: number }|nil
function M.sanitize_session(file_path)
  return call_rust_command({ "session-sanitize", "--file", file_path }, nil, "session-sanitize")
end

--- Detect Spring Boot application and generate debug configuration (SPEC-006)
---@param dir_path string Root project directory
---@return { main_class: string, project_name: string, build_tool: string, jvm_args: string, profiles: string[] }|nil
function M.detect_springboot_app(dir_path)
  return call_rust_command({ "detect-springboot-app", "--dir", dir_path }, nil, "detect-springboot-app")
end

--- Resolve stacktrace symbol to absolute file path (SPEC-010)
---@param line_text string Stacktrace line (e.g., "at com.example.Service.method(Service.java:42)")
---@param dir_path string Root project directory
---@return { file_path: string, line: number, class_name: string, method_name: string }|nil
function M.resolve_stacktrace_symbol(line_text, dir_path)
  return call_rust_command({ "resolve-stacktrace-symbol", "--line", line_text, "--dir", dir_path }, nil, "resolve-stacktrace-symbol")
end

return M
