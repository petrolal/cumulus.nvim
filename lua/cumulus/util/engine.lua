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

-- ==============================================================================
-- Binary Cache
-- ==============================================================================
local cached_bin = nil
local cache_expires_at = nil
local CACHE_TTL_MS = 300000 -- 5 minutes: refresh cache on rebuild/reinstall during dev

-- ==============================================================================
-- Workspace Classification Cache & Metrics
-- ==============================================================================
-- NOTE: Cache can grow unbounded (one entry per unique workspace directory).
-- Future enhancement: implement LRU eviction policy if memory becomes an issue.
local _workspace_cache = {}
-- NOTE: hit_count is tracked per cache entry for potential future eviction policy.
-- Current usage: tracking reuse patterns within a session.
local _cache_metrics = {
  cache_hits = 0,
  cache_misses = 0,
  total_engine_calls_ms = 0,
  num_engine_calls = 0,
  session_start_time = vim.loop.now(),
}

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

--- Assert that the engine binary is available, fail-fast with category-specific error.
---@param category string Feature category: "jvm-build", "devops", "theme", "testing", "kubernetes"
---@error Raises error with installation guidance if engine binary not found
function M.assert_available(category)
  if M.is_available() then
    return
  end

  local category_names = {
    ["jvm-build"] = "JVM build",
    ["devops"] = "DevOps",
    ["theme"] = "Theme",
    ["testing"] = "Test",
    ["kubernetes"] = "Kubernetes",
  }

  local category_label = category_names[category] or category

  local error_msg = string.format(
    "cumulus-engine binary not found for [%s] operations\n" ..
    "\n" ..
    "Build with:\n" ..
    "  cd /path/to/cumulus.nvim/engine && sbt nativeImage\n" ..
    "\n" ..
    "Or install via:\n" ..
    "  :CumulusInstallEngine\n" ..
    "\n" ..
    "Docs: https://github.com/petrolal/cumulus.nvim/docs/engine-setup",
    category_label
  )

  vim.notify(error_msg, vim.log.levels.ERROR)
  error(error_msg)
end

--- Setup load-time availability check and cache invalidation autocmds.
---@private
local function setup_availability_check()
  -- Initial check at module load
  if not M.is_available() then
    vim.notify("cumulus-engine: binary not found (will check on first use)", vim.log.levels.INFO)
  end

  -- Register autocmds for workspace cache invalidation and binary re-detection
  local group = vim.api.nvim_create_augroup("cumulus_engine", { clear = true })

  vim.api.nvim_create_autocmd("DirChanged", {
    group = group,
    callback = function()
      M.invalidate_cache()
      M.invalidate_workspace_cache()
    end,
  })

  vim.api.nvim_create_autocmd("VimEnter", {
    group = group,
    callback = function()
      M.invalidate_cache()
      M.invalidate_workspace_cache()
    end,
  })

  vim.api.nvim_create_autocmd("SessionLoadPost", {
    group = group,
    callback = function()
      M.invalidate_cache()
      M.invalidate_workspace_cache()
    end,
  })
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

--- Downloads and installs cumulus-engine binary with SHA-256 verification
---@param opts? { url?: string, callback?: fun(success: boolean, err_or_path: string) }
function M.install(opts, callback)
  if type(opts) == "function" then callback = opts; opts = {} end
  opts = opts or {}
  local cb = callback or opts.callback or function() end

  local target_name, err = M.detect_platform()
  if not target_name then
    vim.notify(string.format("[cumulus] %s", err or "Unsupported platform"), vim.log.levels.WARN)
    cb(false, err)
    return
  end
  if vim.fn.executable("curl") ~= 1 then
    vim.notify("[cumulus] 'curl' required for download", vim.log.levels.ERROR)
    cb(false, "curl not found")
    return
  end

  local base_url = opts.url or "https://github.com/petrolal/cumulus.nvim/releases/latest/download/"
  if not base_url:match("/$") then base_url = base_url .. "/" end

  local temp_dir = vim.fn.tempname()
  vim.fn.mkdir(temp_dir, "p")
  local temp_bin = temp_dir .. "/" .. target_name
  local temp_checksums = temp_dir .. "/checksums.sha256"

  vim.notify(string.format("[cumulus] Downloading %s...", target_name), vim.log.levels.INFO)

  -- Step 1: Download checksums; Step 2: Download binary
  vim.system({ "curl", "-fsSL", base_url .. "checksums.sha256", "-o", temp_checksums }, {}, function(res_sum)
    if res_sum.code ~= 0 then
      vim.schedule(function() vim.notify("[cumulus] Checksum download failed", vim.log.levels.ERROR); cb(false, "checksum download") end)
      pcall(vim.fn.delete, temp_dir, "rf")
      return
    end
    vim.system({ "curl", "-fsSL", base_url .. target_name, "-o", temp_bin }, {}, function(res_bin)
      if res_bin.code ~= 0 then
        vim.schedule(function() vim.notify("[cumulus] Binary download failed", vim.log.levels.ERROR); cb(false, "binary download") end)
        pcall(vim.fn.delete, temp_dir, "rf")
        return
      end

      -- Step 3: Extract checksum; Step 4: Verify
      local f = io.open(temp_checksums, "r")
      local checksum_content = f and f:read("*a") or ""
      if f then f:close() end
      local expected_hash = nil
      for line in checksum_content:gmatch("[^\r\n]+") do
        local h, fn = line:match("^%s*([a-fA-F0-9]+)%s+%*?([%w%.%-_]+)")
        if fn == target_name then expected_hash = h and h:lower() or nil; break end
      end
      if not expected_hash then
        vim.schedule(function() vim.notify("[cumulus] No checksum found", vim.log.levels.ERROR); cb(false, "checksum not found") end)
        pcall(vim.fn.delete, temp_dir, "rf")
        return
      end

      if vim.fn.executable("sha256sum") ~= 1 then
        vim.schedule(function() vim.notify("[cumulus] sha256sum required (brew install coreutils on macOS)", vim.log.levels.ERROR); cb(false, "sha256sum missing") end)
        pcall(vim.fn.delete, temp_dir, "rf")
        return
      end

      vim.system({ "sha256sum", temp_bin }, { text = true }, function(res_hash)
        if res_hash.code ~= 0 or not res_hash.stdout then
          vim.schedule(function() vim.notify("[cumulus] Checksum failed", vim.log.levels.ERROR); cb(false, "checksum error") end)
          pcall(vim.fn.delete, temp_dir, "rf")
          return
        end
        local actual_hash = (res_hash.stdout:match("^%s*([a-fA-F0-9]+)") or ""):lower()
        if actual_hash ~= expected_hash then
          vim.schedule(function() vim.notify("[cumulus] SHA-256 mismatch", vim.log.levels.ERROR); cb(false, "verification failed") end)
          pcall(vim.fn.delete, temp_dir, "rf")
          return
        end

        -- Step 5: Install verified binary
        local dest_dir = vim.fn.stdpath("data") .. "/cumulus/bin"
        vim.fn.mkdir(dest_dir, "p")
        local dest_bin = dest_dir .. "/cumulus-engine"
        pcall(vim.fn.delete, dest_bin)
        local uv = vim.uv or vim.loop
        local ok, err = uv.fs_rename(temp_bin, dest_bin)
        pcall(vim.fn.delete, temp_dir, "rf")
        if not ok then
          vim.schedule(function() vim.notify(string.format("[cumulus] Install failed: %s", err or "unknown"), vim.log.levels.ERROR); cb(false, "install failed") end)
          return
        end
        pcall(uv.fs_chmod, dest_bin, 493)
        M.invalidate_cache()
        vim.schedule(function() vim.notify(string.format("[cumulus] Installed to %s", dest_bin), vim.log.levels.INFO); cb(true, dest_bin) end)
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

-- ==============================================================================
-- Wrapper Function Consolidation: 1-3 line pass-throughs with minimal IPC marshaling
-- ==============================================================================

--- Thin wrapper generator: creates 1-3 line pass-through functions
---@param cmd string Engine command name (e.g., "parse-pom")
---@param arg_builder fun(...)? Optional function to build args from params
---@private
local function _make_wrapper(cmd, arg_builder)
  return function(...)
    local args = arg_builder and arg_builder(...) or { cmd }
    if not arg_builder then table.insert(args, 1, cmd) end
    return call_engine_command(args, nil, cmd)
  end
end

-- Simple direct pass-throughs (stdin-based or minimal args)
function M.ping()
  return call_engine_command({ "ping" }, nil, "ping")
end

function M.parse_build_log(tool, log_content)
  return call_engine_command({ "parse-build-log", "--tool", tool }, log_content, "parse-build-log")
end

function M.parse_stacktrace(log_content)
  return call_engine_command({ "parse-stacktrace" }, log_content, "parse-stacktrace")
end

function M.parse_test_output(log_content)
  return call_engine_command({ "parse-test-output" }, log_content, "parse-test-output")
end

function M.parse_checkstyle(xml_content)
  return call_engine_command({ "parse-checkstyle" }, xml_content, "parse-checkstyle")
end

function M.parse_coverage(file_path)
  return call_engine_command({ "parse-coverage", "--file", file_path }, nil, "parse-coverage")
end

function M.validate_migrations(dir_path)
  return call_engine_command({ "validate-migrations", "--dir", dir_path }, nil, "validate-migrations")
end

function M.index_log(log_content)
  return call_engine_command({ "index-log" }, log_content, "index-log")
end

function M.validate_k8s_manifest(yaml_content)
  return call_engine_command({ "validate-k8s-manifest" }, yaml_content, "validate-k8s-manifest")
end

function M.parse_git_conflicts(content)
  return call_engine_command({ "parse-git-conflicts" }, content, "parse-git-conflicts")
end

function M.detect_test_context(file_path, cursor_line)
  return call_engine_command({ "detect-test-context", "--file", file_path, "--line", tostring(cursor_line) }, nil, "detect-test-context")
end

function M.resolve_deps(file_path)
  return call_engine_command({ "resolve-deps", "--file", file_path }, nil, "resolve-deps")
end

function M.compute_build_order(dir_path)
  return call_engine_command({ "compute-build-order", "--dir", dir_path }, nil, "compute-build-order")
end

function M.check_jdtls_sync(dir_path, start_time)
  return call_engine_command({ "check-jdtls-sync", "--dir", dir_path, "--start-time", tostring(start_time) }, nil, "check-jdtls-sync")
end

function M.verify_gradle_wrapper(dir_path)
  return call_engine_command({ "verify-gradle-wrapper", "--dir", dir_path }, nil, "verify-gradle-wrapper")
end

function M.sanitize_session(file_path)
  return call_engine_command({ "session-sanitize", "--file", file_path }, nil, "session-sanitize")
end

function M.detect_springboot_app(dir_path)
  return call_engine_command({ "detect-springboot-app", "--dir", dir_path }, nil, "detect-springboot-app")
end

function M.resolve_stacktrace_symbol(line_text, dir_path)
  return call_engine_command({ "resolve-stacktrace-symbol", "--line", line_text, "--dir", dir_path }, nil, "resolve-stacktrace-symbol")
end

function M.check_dep_versions(file_path)
  return call_engine_command({ "check-dep-versions", "--file", file_path }, nil, "check-dep-versions") or {}
end

-- Post-processing wrappers: minimal logic only
function M.parse_pom_goals(pom_path)
  local res = call_engine_command({ "parse-pom", "--file", pom_path }, nil, "parse-pom")
  return res and (res.goals or res) or nil
end

function M.parse_gradle_tasks(content)
  local res = call_engine_command({ "parse-gradle-tasks" }, content, "parse-gradle-tasks")
  return res and (res.tasks or res) or nil
end

function M.parse_modules(tool, file_path)
  local res = call_engine_command({ "parse-modules", "--tool", tool, "--file", file_path }, nil, "parse-modules")
  return res and (res.modules or res) or nil
end

function M.generate_java_header(file_path)
  local res = call_engine_command({ "generate-java-header", "--file", file_path }, nil, "generate-java-header")
  if not res or (type(res) ~= "table" or (#res == 0 and not res.class_declaration)) then return nil end
  if res.class_declaration then
    local lines = {}
    if res.package_name and res.package_name ~= "" then
      table.insert(lines, "package " .. res.package_name .. ";")
      table.insert(lines, "")
    end
    local decl = res.class_declaration:gsub("%s*{%s*}", "")
    table.insert(lines, decl .. " {")
    table.insert(lines, "    ")
    table.insert(lines, "}")
    return lines
  end
  return res
end

function M.extract_endpoints(dir_path)
  local res = call_engine_command({ "extract-endpoints", "--dir", dir_path }, nil, "extract-endpoints")
  return res and (res.endpoints or res) or nil
end

function M.parse_spring_beans(dir_path)
  local res = call_engine_command({ "parse-spring-beans", "--dir", dir_path }, nil, "parse-spring-beans")
  return res and (res.beans or res) or nil
end

function M.optimize_imports(code_content)
  local res = call_engine_command({ "optimize-imports" }, code_content, "optimize-imports")
  return res and (res.imports or res) or nil
end

function M.extract_codelens(file_path)
  local res = call_engine_command({ "extract-codelens", "--file", file_path }, nil, "extract-codelens")
  return res and (res.items or res) or nil
end

function M.resolve_modules(dir_path)
  local res = call_engine_command({ "resolve-modules", "--dir", dir_path or vim.fn.getcwd() }, nil, "resolve-modules", { silent = true })
  return res and (res.modules or res) or nil
end

function M.discover_jdk(version, opts)
  local args = { "discover-jdk" }
  if version then table.insert(args, "--version"); table.insert(args, tostring(version)) end
  return call_engine_command(args, nil, "discover-jdk", opts)
end

function M.discover_build_tool(dir_path, opts)
  local res = call_engine_command({ "discover-build-tool", "--dir", dir_path or "." }, nil, "discover-build-tool", vim.tbl_extend("force", { silent = true }, opts or {}))
  if res then res.tool = res.tool or res.build_tool; res.build_tool = res.build_tool or res.tool end
  return res
end

function M.discover_workspace(dir_path, opts)
  local res = call_engine_command({ "discover-workspace", "--dir", dir_path or "." }, nil, "discover-workspace", vim.tbl_extend("force", { silent = true }, opts or {}))
  if res then res.tool = res.tool or res.build_tool; res.build_tool = res.build_tool or res.tool end
  return res
end

function M.assemble_test_command(opts)
  opts = opts or {}
  local args = { "assemble-test-command" }
  if opts.tool then table.insert(args, "--tool"); table.insert(args, opts.tool) end
  if opts["class"] then table.insert(args, "--class"); table.insert(args, opts["class"]) end
  if opts.method then table.insert(args, "--method"); table.insert(args, opts.method) end
  if opts.dir then table.insert(args, "--dir"); table.insert(args, opts.dir) end
  return call_engine_command(args, nil, "assemble-test-command")
end

function M.assemble_command(intent, opts)
  if not intent or not intent.tool or not intent.action then return nil end
  return call_engine_command({ "assemble-command" }, vim.json.encode(intent), "assemble-command", opts or {})
end

function M.manage_theme(action, opts)
  opts = opts or {}
  local args = { "manage-theme", "--action", action }
  if opts.theme then table.insert(args, "--theme"); table.insert(args, opts.theme) end
  if opts.variant then table.insert(args, "--variant"); table.insert(args, opts.variant) end
  if opts.file then table.insert(args, "--file"); table.insert(args, opts.file) end
  return call_engine_command(args, nil, "manage-theme")
end

function M.check_network(host)
  local result = call_engine_command({ "check-network", "--host", host or "repo.maven.apache.org:443" }, nil, "check-network")
  return result and result.online or nil
end

function M.check_health()
  local ok, response = pcall(call_engine_command, { "check-health" }, nil, "check-health", { silent = true })
  return ok and response or nil
end

-- CFN/CloudFormation and DevOps wrappers (minimal file-path guards only)
function M.inspect_cfn_template(file_path, opts)
  if not file_path or file_path == "" then return nil end
  return call_engine_command({ "cfn-inspect", "--file", file_path }, nil, "cfn-inspect", opts or {})
end

function M.validate_cfn_template(file_path, opts)
  if not file_path or file_path == "" then return nil end
  return call_engine_command({ "cfn-validate", "--file", file_path }, nil, "cfn-validate", opts or {})
end

function M.inspect_ansible_playbook(file_path, opts)
  if not file_path or file_path == "" then return nil end
  return call_engine_command({ "ansible-inspect", "--file", file_path }, nil, "ansible-inspect", opts or {})
end

function M.inspect_terraform(file_path, opts)
  if not file_path or file_path == "" then return nil end
  return call_engine_command({ "tf-inspect", "--file", file_path }, nil, "tf-inspect", opts or {})
end

function M.parse_terraform_security(file_path, opts)
  if not file_path or file_path == "" then return nil end
  return call_engine_command({ "tf-security-parse", "--file", file_path }, nil, "tf-security-parse", opts or {})
end

function M.validate_docker(file_path, opts)
  if not file_path or file_path == "" then return nil end
  return call_engine_command({ "docker-validate", "--file", file_path }, nil, "docker-validate", opts or {})
end

function M.inspect_helm_chart(file_path, opts)
  if not file_path or file_path == "" then return nil end
  return call_engine_command({ "helm-inspect", "--file", file_path }, nil, "helm-inspect", opts or {})
end

function M.validate_ansible_playbook(file_path, opts)
  if not file_path or file_path == "" then return nil end
  return call_engine_command({ "ansible-validate", "--file", file_path }, nil, "ansible-validate", opts or {})
end

function M.parse_ansible_inventory(file_path, opts)
  if not file_path or file_path == "" then return nil end
  return call_engine_command({ "ansible-inventory-parse", "--file", file_path }, nil, "ansible-inventory-parse", opts or {})
end

function M.run_command(subcommand, args, stdin, opts)
  local full_args = { subcommand }
  if args then for _, arg in ipairs(args) do table.insert(full_args, arg) end end
  return call_engine_command(full_args, stdin, subcommand, opts)
end

--- Classify workspace topology with session-scoped caching
---@param dir? string Root workspace directory (defaults to cwd)
---@param opts? { silent?: boolean }
---@return { root: string, primary_type: string, project_types: string[], submodules: table[], has_spring: boolean, iac_types: string[], is_multi_module: boolean }|nil
function M.classify_workspace(dir, opts)
  dir = dir or vim.fn.getcwd()
  local cache_key = vim.fs.normalize(dir)
  if not cache_key then return nil end
  if _workspace_cache[cache_key] and type(_workspace_cache[cache_key]) == "table" then
    _cache_metrics.cache_hits = _cache_metrics.cache_hits + 1
    local entry = _workspace_cache[cache_key]
    entry.hit_count = (entry.hit_count or 0) + 1
    return entry.classification
  end
  _cache_metrics.cache_misses = _cache_metrics.cache_misses + 1
  opts = vim.tbl_extend("force", { silent = true }, opts or {})
  local start_time = vim.loop.now() or 0
  local result = call_engine_command({ "classify-workspace", "--dir", dir }, nil, "classify-workspace", opts)
  if result then
    local elapsed_ms = (vim.loop.now() or 0) - start_time
    _cache_metrics.total_engine_calls_ms = _cache_metrics.total_engine_calls_ms + elapsed_ms
    _cache_metrics.num_engine_calls = _cache_metrics.num_engine_calls + 1
    _workspace_cache[cache_key] = { classification = result, timestamp = vim.loop.now() or 0, hit_count = 0 }
  end
  return result
end

function M.resolve_formatter(file_path, opts)
  if not file_path or file_path == "" then return nil end
  return call_engine_command({ "resolve-formatter", "--file", file_path }, nil, "resolve-formatter", opts or {})
end

function M.discover_devops_roots(path, opts)
  return call_engine_command({ "discover-devops-roots", "--path", path or vim.fn.getcwd() }, nil, "discover-devops-roots", vim.tbl_extend("force", { silent = true }, opts or {}))
end

function M.generate_dap_config(dir, opts)
  return call_engine_command({ "generate-dap-config", "--dir", dir or vim.fn.getcwd() }, nil, "generate-dap-config", vim.tbl_extend("force", { silent = true }, opts or {}))
end

function M.generate_theme_highlights(provider, opts)
  if not provider or provider == "" then return nil end
  return call_engine_command({ "generate-theme-highlights", provider }, nil, "generate-theme-highlights", opts or {})
end

-- ==============================================================================
-- ⭐ Consolidated UI Pickers & Actions (Story 13.1)
-- ==============================================================================

--- Select a Spring bean from the workspace dependency graph and navigate to its source.
function M.select_bean()
  M.assert_available("jvm-build")
  local cwd = vim.fn.getcwd()
  local beans = M.parse_spring_beans(cwd)
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
  M.assert_available("jvm-build")
  local cwd = vim.fn.getcwd()
  local eps = M.extract_endpoints(cwd)
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
  M.assert_available("jvm-build")
  local bufnr = vim.api.nvim_get_current_buf()
  local lines = vim.api.nvim_buf_get_lines(bufnr, 0, -1, false)
  local content = table.concat(lines, "\n")

  local new_lines = M.optimize_imports(content)
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
  M.assert_available("kubernetes")
  local bufnr = vim.api.nvim_get_current_buf()
  local lines = vim.api.nvim_buf_get_lines(bufnr, 0, -1, false)
  local content = table.concat(lines, "\n")

  local issues = M.validate_k8s_manifest(content)
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
  M.assert_available("jvm-build")
  local cwd = vim.fn.getcwd()
  dir = dir or (cwd .. "/src/main/resources/db/migration")

  local issues = M.validate_migrations(dir)
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
  M.assert_available("jvm-build")
  local bufnr = vim.api.nvim_get_current_buf()
  local lines = vim.api.nvim_buf_get_lines(bufnr, 0, -1, false)
  local content = table.concat(lines, "\n")

  local blocks = M.parse_git_conflicts(content)
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
  M.assert_available("jvm-build")
  xml_path = xml_path or (vim.fn.getcwd() .. "/target/site/jacoco/jacoco.xml")

  local entries = M.parse_coverage(xml_path)
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
  M.assert_available("jvm-build")
  local bufnr = vim.api.nvim_get_current_buf()
  local lines = vim.api.nvim_buf_get_lines(bufnr, 0, -1, false)
  local content = table.concat(lines, "\n")

  local entries = M.index_log(content)
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
  M.assert_available("jvm-build")
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
--- Requires Snacks.terminal plugin to be loaded.
---@param cmd string|string[] Command string or command argv list to execute
---@param opts? { cwd?: string, timeout?: number, title?: string, on_exit?: fun(code: number), on_stdout?: fun(data: string[]), on_stderr?: fun(data: string[]) }
---@error Raises error if Snacks plugin is not loaded
function M.run_term(cmd, opts)
  opts = opts or {}
  local snacks = _G.Snacks or package.loaded["snacks"]

  if not snacks or not snacks.terminal then
    local err_msg = "Snacks plugin (terminal feature) is required for this operation. " ..
      "Install it via your plugin manager or disable terminal commands."
    M.notify_err(err_msg)
    error(err_msg)
  end

  local term_cwd = opts.cwd or vim.fn.getcwd()
  snacks.terminal(cmd, { cwd = term_cwd })
end

-- ==============================================================================
-- Workspace Cache Management & Metrics
-- ==============================================================================

--- Invalidate workspace classification cache for a specific path or all paths
---@param path? string Optional absolute directory path to invalidate; if nil, clears all cache
function M.invalidate_workspace_cache(path)
  if path then
    local cache_key = vim.fs.normalize(path)
    -- CRITICAL: normalize can return nil; only invalidate if we got a valid key
    if cache_key then
      _workspace_cache[cache_key] = nil
    end
  else
    -- Clear entire cache if no path specified
    _workspace_cache = {}
  end
end

--- Retrieve cache hit/miss metrics and performance statistics
---@return { cache_hits: number, cache_misses: number, hit_ratio: string, avg_latency_ms: string, session_uptime_ms: number }
function M.get_workspace_cache_metrics()
  local total_calls = _cache_metrics.cache_hits + _cache_metrics.cache_misses
  local hit_ratio = "N/A"
  if total_calls > 0 then
    hit_ratio = string.format("%.1f%%", (_cache_metrics.cache_hits / total_calls) * 100)
  end

  local avg_latency = "N/A"
  if _cache_metrics.num_engine_calls > 0 then
    avg_latency = string.format("%.1f ms", _cache_metrics.total_engine_calls_ms / _cache_metrics.num_engine_calls)
  end

  -- CRITICAL: session_start_time could be nil; guard with default 0
  local session_uptime = (vim.loop.now() or 0) - (_cache_metrics.session_start_time or 0)

  return {
    cache_hits = _cache_metrics.cache_hits,
    cache_misses = _cache_metrics.cache_misses,
    hit_ratio = hit_ratio,
    avg_latency_ms = avg_latency,
    session_uptime_ms = session_uptime,
  }
end

-- ==============================================================================
-- User Commands
-- ==============================================================================

--- Register user commands for cache management and diagnostics
--- Register user commands for cache management and diagnostics
--- Wrapped in pcall to handle idempotency (multiple module reloads)
local function register_commands()
  local ok, err = pcall(function()
    vim.api.nvim_create_user_command("CumulusMetrics", function()
      local metrics = M.get_workspace_cache_metrics()
      local output = {
        "=== Cumulus Engine Metrics ===",
        string.format("Cache Hits: %d", metrics.cache_hits),
        string.format("Cache Misses: %d", metrics.cache_misses),
        string.format("Hit Ratio: %s", metrics.hit_ratio),
        string.format("Avg Engine Call Latency: %s", metrics.avg_latency_ms),
        string.format("Session Uptime: %.1f s", metrics.session_uptime_ms / 1000),
      }
      for _, line in ipairs(output) do
        vim.notify(line, vim.log.levels.INFO)
      end
    end, { desc = "Display Cumulus engine cache metrics and performance stats" })

    vim.api.nvim_create_user_command("CumulusRefresh", function()
      M.invalidate_workspace_cache()
      vim.notify("Workspace classification cache cleared", vim.log.levels.INFO)
    end, { desc = "Invalidate workspace classification cache" })
  end)

  if not ok then
    vim.notify(
      string.format("[cumulus] Failed to register commands: %s", err),
      vim.log.levels.WARN
    )
  end
end

--- Setup load-time availability check and cache invalidation autocmds
--- Cache is invalidated on:
--- - VimEnter: session start
--- - DirChanged: user changed directory
--- - SessionLoadPost: session restore
--- NOTE: Users can manually clear cache with :CumulusRefresh for project topology changes
setup_availability_check()
pcall(register_commands)

return M



