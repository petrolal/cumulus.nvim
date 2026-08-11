-- Cumulus Rust Helper Integration (SPEC-016 & SPEC-018..025)
--
-- Discovers and executes the compiled `cumulus-core` Rust binary to offload
-- heavy POM parsing, Gradle task extraction, build log parsing, REST endpoint extraction,
-- JaCoCo coverage parsing, Flyway migration validation, Spring Bean dependency graphs,
-- log indexing, import optimization, K8s schema validation, and Git conflict resolution.

local M = {}

local cached_bin = nil

--- Locates the `cumulus-core` binary on $PATH or within the workspace target directory.
---@return string|nil
function M.get_bin()
  if cached_bin ~= nil then
    return cached_bin ~= false and cached_bin or nil
  end

  if vim.fn.executable("cumulus-core") == 1 then
    cached_bin = "cumulus-core"
    return cached_bin
  end

  -- Search relative to Neovim runtimepath / workspace directory
  local script_dir = vim.fn.fnamemodify(debug.getinfo(1, "S").source:sub(2), ":p:h:h:h:h")
  local release_bin = script_dir .. "/crates/cumulus-core/target/release/cumulus-core"
  local debug_bin = script_dir .. "/crates/cumulus-core/target/debug/cumulus-core"

  if vim.fn.executable(release_bin) == 1 then
    cached_bin = release_bin
    return cached_bin
  elseif vim.fn.executable(debug_bin) == 1 then
    cached_bin = debug_bin
    return cached_bin
  end

  cached_bin = false
  return nil
end

--- Returns true if the Rust helper binary is available.
---@return boolean
function M.is_available()
  return M.get_bin() ~= nil
end

--- Parse Maven goals using Rust helper
---@param pom_path string
---@return string[]|nil
function M.parse_pom_goals(pom_path)
  local bin = M.get_bin()
  if not bin then
    return nil
  end

  local res = vim.system({ bin, "parse-pom", "--file", pom_path }, { text = true }):wait()
  if res.code == 0 and res.stdout ~= "" then
    local ok, parsed = pcall(vim.json.decode, res.stdout)
    if ok and type(parsed) == "table" then
      return parsed
    end
  end

  return nil
end

--- Parse Gradle task list from stdout using Rust helper
---@param content string
---@return string[]|nil
function M.parse_gradle_tasks(content)
  local bin = M.get_bin()
  if not bin then
    return nil
  end

  local res = vim.system({ bin, "parse-gradle-tasks" }, { stdin = content, text = true }):wait()
  if res.code == 0 and res.stdout ~= "" then
    local ok, parsed = pcall(vim.json.decode, res.stdout)
    if ok and type(parsed) == "table" then
      return parsed
    end
  end

  return nil
end

--- Parse build log content using Rust helper
---@param tool "maven"|"gradle"
---@param log_content string
---@return table[]|nil Array of { file = string, line = number, col = number|nil, message = string, severity = string }
function M.parse_build_log(tool, log_content)
  local bin = M.get_bin()
  if not bin then
    return nil
  end

  local res = vim.system({ bin, "parse-build-log", "--tool", tool }, { stdin = log_content, text = true }):wait()
  if res.code == 0 and res.stdout ~= "" then
    local ok, parsed = pcall(vim.json.decode, res.stdout)
    if ok and type(parsed) == "table" then
      return parsed
    end
  end

  return nil
end

--- Parse Maven/Gradle sub-modules from pom.xml or settings.gradle using Rust helper
---@param tool "maven"|"gradle"
---@param file_path string
---@return table[]|nil Array of { name = string, path = string }
function M.parse_modules(tool, file_path)
  local bin = M.get_bin()
  if not bin then
    return nil
  end

  local res = vim.system({ bin, "parse-modules", "--tool", tool, "--file", file_path }, { text = true }):wait()
  if res.code == 0 and res.stdout ~= "" then
    local ok, parsed = pcall(vim.json.decode, res.stdout)
    if ok and type(parsed) == "table" then
      return parsed
    end
  end

  return nil
end

--- Parse stacktrace log content using Rust helper
---@param log_content string
---@return table[]|nil Array of { class_name = string, method_name = string, file = string, line = number }
function M.parse_stacktrace(log_content)
  local bin = M.get_bin()
  if not bin then
    return nil
  end

  local res = vim.system({ bin, "parse-stacktrace" }, { stdin = log_content, text = true }):wait()
  if res.code == 0 and res.stdout ~= "" then
    local ok, parsed = pcall(vim.json.decode, res.stdout)
    if ok and type(parsed) == "table" then
      return parsed
    end
  end

  return nil
end

--- Generate Java package and class header using Rust helper
---@param file_path string
---@return string[]|nil Array of lines
function M.generate_java_header(file_path)
  local bin = M.get_bin()
  if not bin then
    return nil
  end

  local res = vim.system({ bin, "generate-java-header", "--file", file_path }, { text = true }):wait()
  if res.code == 0 and res.stdout ~= "" then
    local ok, parsed = pcall(vim.json.decode, res.stdout)
    if ok and type(parsed) == "table" then
      return parsed
    end
  end

  return nil
end

--- Parse test log output using Rust helper
---@param log_content string
---@return table[]|nil Array of { test_name = string, class_name = string, status = string, failure_message = string|nil, file = string|nil, line = number|nil }
function M.parse_test_output(log_content)
  local bin = M.get_bin()
  if not bin then
    return nil
  end

  local res = vim.system({ bin, "parse-test-output" }, { stdin = log_content, text = true }):wait()
  if res.code == 0 and res.stdout ~= "" then
    local ok, parsed = pcall(vim.json.decode, res.stdout)
    if ok and type(parsed) == "table" then
      return parsed
    end
  end

  return nil
end

--- Check network connectivity using Rust helper
---@param host? string host:port format (default "repo.maven.apache.org:443")
---@return boolean|nil returns boolean or nil if Rust helper unavailable
function M.check_network(host)
  local bin = M.get_bin()
  if not bin then
    return nil
  end

  host = host or "repo.maven.apache.org:443"
  local res = vim.system({ bin, "check-network", "--host", host }, { text = true }):wait()
  if res.code == 0 and res.stdout ~= "" then
    local ok, parsed = pcall(vim.json.decode, res.stdout)
    if ok and type(parsed) == "table" and parsed.online ~= nil then
      return parsed.online
    end
  end

  return nil
end

--- Parse Checkstyle XML report using Rust helper
---@param xml_content string
---@return table[]|nil Array of { file = string, line = number, col = number|nil, message = string, severity = string }
function M.parse_checkstyle(xml_content)
  local bin = M.get_bin()
  if not bin then
    return nil
  end

  local res = vim.system({ bin, "parse-checkstyle" }, { stdin = xml_content, text = true }):wait()
  if res.code == 0 and res.stdout ~= "" then
    local ok, parsed = pcall(vim.json.decode, res.stdout)
    if ok and type(parsed) == "table" then
      return parsed
    end
  end

  return nil
end

--- Extract REST endpoints from directory using Rust helper
---@param dir_path string
---@return table[]|nil Array of { file = string, line = number, http_method = string, path = string, class_name = string, handler_name = string }
function M.extract_endpoints(dir_path)
  local bin = M.get_bin()
  if not bin then
    return nil
  end

  local res = vim.system({ bin, "extract-endpoints", "--dir", dir_path }, { text = true }):wait()
  if res.code == 0 and res.stdout ~= "" then
    local ok, parsed = pcall(vim.json.decode, res.stdout)
    if ok and type(parsed) == "table" then
      return parsed
    end
  end

  return nil
end

--- Parse JaCoCo coverage XML file using Rust helper
---@param file_path string
---@return table[]|nil Array of { file = string, covered_lines = number[], missed_lines = number[] }
function M.parse_coverage(file_path)
  local bin = M.get_bin()
  if not bin then
    return nil
  end

  local res = vim.system({ bin, "parse-coverage", "--file", file_path }, { text = true }):wait()
  if res.code == 0 and res.stdout ~= "" then
    local ok, parsed = pcall(vim.json.decode, res.stdout)
    if ok and type(parsed) == "table" then
      return parsed
    end
  end

  return nil
end

--- Validate Flyway migration scripts in directory using Rust helper
---@param dir_path string
---@return table[]|nil Array of { file = string, line = number|nil, severity = string, message = string }
function M.validate_migrations(dir_path)
  local bin = M.get_bin()
  if not bin then
    return nil
  end

  local res = vim.system({ bin, "validate-migrations", "--dir", dir_path }, { text = true }):wait()
  if res.code == 0 and res.stdout ~= "" then
    local ok, parsed = pcall(vim.json.decode, res.stdout)
    if ok and type(parsed) == "table" then
      return parsed
    end
  end

  return nil
end

--- Extract Spring Bean dependencies from directory using Rust helper
---@param dir_path string
---@return table[]|nil Array of { class_name = string, bean_name = string, file = string, line = number, injected_deps = string[] }
function M.parse_spring_beans(dir_path)
  local bin = M.get_bin()
  if not bin then
    return nil
  end

  local res = vim.system({ bin, "parse-spring-beans", "--dir", dir_path }, { text = true }):wait()
  if res.code == 0 and res.stdout ~= "" then
    local ok, parsed = pcall(vim.json.decode, res.stdout)
    if ok and type(parsed) == "table" then
      return parsed
    end
  end

  return nil
end

--- Index log content using Rust helper
---@param log_content string
---@return table[]|nil Array of { line = number, level = string, timestamp = string|nil, message = string }
function M.index_log(log_content)
  local bin = M.get_bin()
  if not bin then
    return nil
  end

  local res = vim.system({ bin, "index-log" }, { stdin = log_content, text = true }):wait()
  if res.code == 0 and res.stdout ~= "" then
    local ok, parsed = pcall(vim.json.decode, res.stdout)
    if ok and type(parsed) == "table" then
      return parsed
    end
  end

  return nil
end

--- Optimize Java/Kotlin imports using Rust helper
---@param code_content string
---@return string[]|nil Array of formatted lines
function M.optimize_imports(code_content)
  local bin = M.get_bin()
  if not bin then
    return nil
  end

  local res = vim.system({ bin, "optimize-imports" }, { stdin = code_content, text = true }):wait()
  if res.code == 0 and res.stdout ~= "" then
    local ok, parsed = pcall(vim.json.decode, res.stdout)
    if ok and type(parsed) == "table" then
      return parsed
    end
  end

  return nil
end

--- Validate K8s manifest content using Rust helper
---@param yaml_content string
---@return table[]|nil Array of { line = number, col = number|nil, message = string, severity = string }
function M.validate_k8s_manifest(yaml_content)
  local bin = M.get_bin()
  if not bin then
    return nil
  end

  local res = vim.system({ bin, "validate-k8s-manifest" }, { stdin = yaml_content, text = true }):wait()
  if res.code == 0 and res.stdout ~= "" then
    local ok, parsed = pcall(vim.json.decode, res.stdout)
    if ok and type(parsed) == "table" then
      return parsed
    end
  end

  return nil
end

--- Parse Git conflict markers using Rust helper
---@param content string
---@return table[]|nil Array of { start_line = number, sep_line = number, end_line = number, current_header = string, incoming_header = string }
function M.parse_git_conflicts(content)
  local bin = M.get_bin()
  if not bin then
    return nil
  end

  local res = vim.system({ bin, "parse-git-conflicts" }, { stdin = content, text = true }):wait()
  if res.code == 0 and res.stdout ~= "" then
    local ok, parsed = pcall(vim.json.decode, res.stdout)
    if ok and type(parsed) == "table" then
      return parsed
    end
  end

  return nil
end

--- Detect nearest test class and method at a given cursor line in a Java/Kotlin file.
--- Replaces the Lua regex-based detect_test_info() that previously lived in test-runner.lua.
---@param file_path string Absolute path to the Java/Kotlin source file
---@param cursor_line number 1-indexed line number of the cursor
---@return { class_name: string|nil, method_name: string|nil }|nil
function M.detect_test_context(file_path, cursor_line)
  local bin = M.get_bin()
  if not bin then
    error("cumulus-core binary not found — cannot detect test context")
  end

  local res = vim.system(
    { bin, "detect-test-context", "--file", file_path, "--line", tostring(cursor_line) },
    { text = true }
  ):wait()

  if res.code == 0 and res.stdout ~= "" then
    local ok, parsed = pcall(vim.json.decode, res.stdout)
    if ok and type(parsed) == "table" then
      return parsed
    end
  end

  return nil
end

--- Resolve direct project dependencies using Rust helper (SPEC-026)
---@param file_path string Path to pom.xml or libs.versions.toml
---@return table[]|nil Array of { group = string, artifact = string, version = string, scope = string }
function M.resolve_deps(file_path)
  local bin = M.get_bin()
  if not bin then
    return nil
  end

  local res = vim.system({ bin, "resolve-deps", "--file", file_path }, { text = true }):wait()
  if res.code == 0 and res.stdout ~= "" then
    local ok, parsed = pcall(vim.json.decode, res.stdout)
    if ok and type(parsed) == "table" then
      return parsed
    end
  end

  return nil
end

--- Extract instant Java & Kotlin CodeLens items using Rust helper (SPEC-027)
---@param file_path string Path to Java or Kotlin source file
---@return table[]|nil Array of { line = number, title = string, command = string, args = string[] }
function M.extract_codelens(file_path)
  local bin = M.get_bin()
  if not bin then
    return nil
  end

  local res = vim.system({ bin, "extract-codelens", "--file", file_path }, { text = true }):wait()
  if res.code == 0 and res.stdout ~= "" then
    local ok, parsed = pcall(vim.json.decode, res.stdout)
    if ok and type(parsed) == "table" then
      return parsed
    end
  end

  return nil
end

--- Solve multi-module topological build order & DAG using Rust helper (SPEC-028)
---@param dir_path string Root project directory
---@return table[]|nil Array of { step = number, module_name = string, path = string, build_command = string }
function M.compute_build_order(dir_path)
  local bin = M.get_bin()
  if not bin then
    return nil
  end

  local res = vim.system({ bin, "compute-build-order", "--dir", dir_path }, { text = true }):wait()
  if res.code == 0 and res.stdout ~= "" then
    local ok, parsed = pcall(vim.json.decode, res.stdout)
    if ok and type(parsed) == "table" then
      return parsed
    end
  end

  return nil
end

return M
