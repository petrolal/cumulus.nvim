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
    elseif machine == "x86_64" then
      return "cumulus-engine-darwin-x86_64", nil
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
  local cleanup_done = false

  -- Helper to ensure temp cleanup happens once
  local function cleanup_temp()
    if not cleanup_done then
      pcall(vim.fn.delete, temp_dir, "rf")
      cleanup_done = true
    end
  end

  vim.notify(string.format("[cumulus] Downloading %s...", target_name), vim.log.levels.INFO)

  -- Step 1: Download checksums; Step 2: Download binary
  vim.system({ "curl", "-fsSL", base_url .. "checksums.sha256", "-o", temp_checksums }, {}, function(res_sum)
    if res_sum.code ~= 0 then
      vim.schedule(function()
        vim.notify("[cumulus] Checksum download failed", vim.log.levels.ERROR)
        cb(false, "checksum download")
        cleanup_temp()
      end)
      return
    end
    vim.system({ "curl", "-fsSL", base_url .. target_name, "-o", temp_bin }, {}, function(res_bin)
      if res_bin.code ~= 0 then
        vim.schedule(function()
          vim.notify("[cumulus] Binary download failed", vim.log.levels.ERROR)
          cb(false, "binary download")
          cleanup_temp()
        end)
        return
      end

      -- Step 3: Extract checksum; Step 4: Verify
      local f = io.open(temp_checksums, "r")
      local checksum_content = f and f:read("*a") or ""
      if f then f:close() end
      local expected_hash = nil
      for line in checksum_content:gmatch("[^\r\n]+") do
        local h, fn = line:match("^%s*([a-fA-F0-9]+)%s+%*?([%w%.%-_]+)")
        if fn == target_name then expected_hash = (h and h:lower() and #h > 0) and h:lower() or nil; break end
      end
      -- BUG FIX: Validate non-empty checksum before comparison
      if not expected_hash or expected_hash == "" then
        vim.schedule(function()
          vim.notify("[cumulus] No checksum found for target binary", vim.log.levels.ERROR)
          cb(false, "checksum not found")
          cleanup_temp()
        end)
        return
      end

      -- BUG FIX: Check for both sha256sum and shasum (macOS fallback)
      local hash_cmd = nil
      if vim.fn.executable("sha256sum") == 1 then
        hash_cmd = "sha256sum"
      elseif vim.fn.executable("shasum") == 1 then
        hash_cmd = { "shasum", "-a", "256" }
      else
        vim.schedule(function()
          vim.notify("[cumulus] sha256sum or shasum required (brew install coreutils on macOS)", vim.log.levels.ERROR)
          cb(false, "sha256sum missing")
          cleanup_temp()
        end)
        return
      end

      local hash_args = type(hash_cmd) == "table" and hash_cmd or { hash_cmd }
      table.insert(hash_args, temp_bin)

      vim.system(hash_args, { text = true }, function(res_hash)
        if res_hash.code ~= 0 or not res_hash.stdout then
          vim.schedule(function()
            vim.notify("[cumulus] Hash computation failed", vim.log.levels.ERROR)
            cb(false, "checksum error")
            cleanup_temp()
          end)
          return
        end
        local actual_hash = (res_hash.stdout:match("^%s*([a-fA-F0-9]+)") or ""):lower()
        if actual_hash ~= expected_hash then
          vim.schedule(function()
            vim.notify(
              string.format("[cumulus] SHA-256 mismatch (expected %s, got %s)", expected_hash:sub(1, 8), actual_hash:sub(1, 8)),
              vim.log.levels.ERROR
            )
            cb(false, "verification failed")
            cleanup_temp()
          end)
          return
        end

        -- Step 5: Install verified binary
        local dest_dir = vim.fn.stdpath("data") .. "/cumulus/bin"
        vim.fn.mkdir(dest_dir, "p")
        local dest_bin = dest_dir .. "/cumulus-engine"
        pcall(vim.fn.delete, dest_bin)
        local uv = vim.uv or vim.loop
        local ok, err = uv.fs_rename(temp_bin, dest_bin)

        -- BUG FIX: Add fallback copy if rename fails (cross-filesystem edge case)
        if not ok then
          vim.notify(string.format("[cumulus] Rename failed (%s), attempting copy...", err or "unknown"), vim.log.levels.WARN)
          local copy_ok = pcall(function()
            local src = io.open(temp_bin, "rb")
            if not src then error("Cannot open source file") end
            local src_data = src:read("*a")
            src:close()
            if not src_data or #src_data == 0 then error("Source file is empty") end
            local dest = io.open(dest_bin, "wb")
            if not dest then error("Cannot open destination file") end
            local written = dest:write(src_data)
            dest:close()
            if not written or written == 0 then error("Failed to write destination file") end
          end)
          if not copy_ok then
            vim.schedule(function()
              vim.notify(string.format("[cumulus] Install failed: rename and copy both failed"), vim.log.levels.ERROR)
              cb(false, "install failed")
              cleanup_temp()
            end)
            return
          end
        end

        cleanup_temp()
        pcall(uv.fs_chmod, dest_bin, 493)
        M.invalidate_cache()
        vim.schedule(function()
          vim.notify(string.format("[cumulus] Installed to %s", dest_bin), vim.log.levels.INFO)
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

-- ==============================================================================
-- Wrapper Function Consolidation: 1-3 line pass-throughs with minimal IPC marshaling
-- ==============================================================================

-- Table-driven wrapper generation for simple pass-throughs
-- Format: { func_name = "engine-command" } for stdin-based or { func_name = { "cmd", "--flag" } } for args-based
local function _register_simple_wrappers()
  local stdin_wrappers = {
    parse_build_log = function(tool, log_content) return call_engine_command({ "parse-build-log", "--tool", tool }, log_content, "parse-build-log") end,
    parse_stacktrace = function(log_content) return call_engine_command({ "parse-stacktrace" }, log_content, "parse-stacktrace") end,
    parse_test_output = function(log_content) return call_engine_command({ "parse-test-output" }, log_content, "parse-test-output") end,
    parse_checkstyle = function(xml_content) return call_engine_command({ "parse-checkstyle" }, xml_content, "parse-checkstyle") end,
    index_log = function(log_content) return call_engine_command({ "index-log" }, log_content, "index-log") end,
    validate_k8s_manifest = function(yaml_content) return call_engine_command({ "validate-k8s-manifest" }, yaml_content, "validate-k8s-manifest") end,
    parse_git_conflicts = function(content) return call_engine_command({ "parse-git-conflicts" }, content, "parse-git-conflicts") end,
  }
  local file_wrappers = {
    parse_coverage = function(file_path) return call_engine_command({ "parse-coverage", "--file", file_path }, nil, "parse-coverage") end,
    validate_migrations = function(dir_path) return call_engine_command({ "validate-migrations", "--dir", dir_path }, nil, "validate-migrations") end,
    resolve_deps = function(file_path) return call_engine_command({ "resolve-deps", "--file", file_path }, nil, "resolve-deps") end,
    compute_build_order = function(dir_path) return call_engine_command({ "compute-build-order", "--dir", dir_path }, nil, "compute-build-order") end,
    verify_gradle_wrapper = function(dir_path) return call_engine_command({ "verify-gradle-wrapper", "--dir", dir_path }, nil, "verify-gradle-wrapper") end,
    sanitize_session = function(file_path) return call_engine_command({ "session-sanitize", "--file", file_path }, nil, "session-sanitize") end,
    detect_springboot_app = function(dir_path) return call_engine_command({ "detect-springboot-app", "--dir", dir_path }, nil, "detect-springboot-app") end,
    check_dep_versions = function(file_path) return call_engine_command({ "check-dep-versions", "--file", file_path }, nil, "check-dep-versions") end,
  }
  for name, fn in pairs(stdin_wrappers) do M[name] = fn end
  for name, fn in pairs(file_wrappers) do M[name] = fn end
end
_register_simple_wrappers()

function M.ping()
  return call_engine_command({ "ping" }, nil, "ping")
end

function M.detect_test_context(file_path, cursor_line)
  return call_engine_command({ "detect-test-context", "--file", file_path, "--line", tostring(cursor_line) }, nil, "detect-test-context")
end

function M.check_jdtls_sync(dir_path, start_time)
  return call_engine_command({ "check-jdtls-sync", "--dir", dir_path, "--start-time", tostring(start_time) }, nil, "check-jdtls-sync")
end

function M.resolve_stacktrace_symbol(line_text, dir_path)
  return call_engine_command({ "resolve-stacktrace-symbol", "--line", line_text, "--dir", dir_path }, nil, "resolve-stacktrace-symbol")
end

-- Post-processing wrappers: minimal logic with schema validation
local function _validate_response(res, key)
  if res and type(res) == "table" and res[key] then
    return (type(res[key]) == "table") and res[key] or nil
  end
  return nil
end

function M.parse_pom_goals(pom_path)
  if not pom_path or pom_path == "" then return nil end
  local res = call_engine_command({ "parse-pom", "--file", pom_path }, nil, "parse-pom")
  return _validate_response(res, "goals")
end

function M.parse_gradle_tasks(content)
  if not content or content == "" then return nil end
  local res = call_engine_command({ "parse-gradle-tasks" }, content, "parse-gradle-tasks")
  return _validate_response(res, "tasks")
end

function M.parse_modules(tool, file_path)
  if not file_path or file_path == "" then return nil end
  local res = call_engine_command({ "parse-modules", "--tool", tool, "--file", file_path }, nil, "parse-modules")
  return _validate_response(res, "modules")
end

function M.generate_java_header(file_path)
  local res = call_engine_command({ "generate-java-header", "--file", file_path }, nil, "generate-java-header")
  if not res or type(res) ~= "table" then return nil end
  if not res.class_declaration then return nil end
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

function M.extract_endpoints(dir_path)
  if not dir_path or dir_path == "" then return nil end
  local res = call_engine_command({ "extract-endpoints", "--dir", dir_path }, nil, "extract-endpoints")
  return _validate_response(res, "endpoints")
end

function M.parse_spring_beans(dir_path)
  if not dir_path or dir_path == "" then return nil end
  local res = call_engine_command({ "parse-spring-beans", "--dir", dir_path }, nil, "parse-spring-beans")
  return _validate_response(res, "beans")
end

function M.optimize_imports(code_content)
  if not code_content or code_content == "" then return nil end
  local res = call_engine_command({ "optimize-imports" }, code_content, "optimize-imports")
  return _validate_response(res, "imports")
end

function M.extract_codelens(file_path)
  if not file_path or file_path == "" then return nil end
  local res = call_engine_command({ "extract-codelens", "--file", file_path }, nil, "extract-codelens")
  return _validate_response(res, "items")
end

function M.resolve_modules(dir_path)
  dir_path = dir_path or vim.fn.getcwd()
  if dir_path == "" or vim.fn.isdirectory(dir_path) == 0 then return nil end
  local res = call_engine_command({ "resolve-modules", "--dir", dir_path }, nil, "resolve-modules", { silent = true })
  return _validate_response(res, "modules")
end

function M.discover_jdk(version, opts)
  local args = { "discover-jdk" }
  if version then table.insert(args, "--version"); table.insert(args, tostring(version)) end
  return call_engine_command(args, nil, "discover-jdk", opts)
end

function M.discover_build_tool(dir_path, opts)
  dir_path = dir_path or "."
  if dir_path == "" or vim.fn.isdirectory(dir_path) == 0 then return nil end
  local res = call_engine_command({ "discover-build-tool", "--dir", dir_path }, nil, "discover-build-tool", vim.tbl_extend("force", { silent = true }, opts or {}))
  if res then res.tool = res.tool or res.build_tool; res.build_tool = res.build_tool or res.tool end
  return res
end

function M.discover_workspace(dir_path, opts)
  dir_path = dir_path or "."
  if dir_path == "" or vim.fn.isdirectory(dir_path) == 0 then return nil end
  local res = call_engine_command({ "discover-workspace", "--dir", dir_path }, nil, "discover-workspace", vim.tbl_extend("force", { silent = true }, opts or {}))
  if res then res.tool = res.tool or res.build_tool; res.build_tool = res.build_tool or res.tool end
  return res
end

function M.assemble_test_command(opts)
  opts = opts or {}
  if opts.dir and (opts.dir == "" or vim.fn.isdirectory(opts.dir) == 0) then return nil end
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
  if ok then return response else return nil end
end

-- DevOps/Cloud Infrastructure wrappers (consolidated with file-path guards)
local function _make_file_wrapper(cmd, ctx)
  return function(file_path, opts)
    if not file_path or file_path == "" then return nil end
    return call_engine_command({ cmd, "--file", file_path }, nil, ctx, opts or {})
  end
end

function M.resolve_formatter(file_path, opts)
  if not file_path or file_path == "" then return nil end
  return call_engine_command({ "resolve-formatter", "--file", file_path }, nil, "resolve-formatter", opts or {})
end

function M.discover_devops_roots(path, opts)
  path = path or vim.fn.getcwd()
  if path == "" or vim.fn.isdirectory(path) == 0 then return nil end
  return call_engine_command({ "discover-devops-roots", "--path", path }, nil, "discover-devops-roots", vim.tbl_extend("force", { silent = true }, opts or {}))
end

function M.generate_dap_config(dir, opts)
  dir = dir or vim.fn.getcwd()
  if dir == "" or vim.fn.isdirectory(dir) == 0 then return nil end
  return call_engine_command({ "generate-dap-config", "--dir", dir }, nil, "generate-dap-config", vim.tbl_extend("force", { silent = true }, opts or {}))
end

function M.generate_theme_highlights(provider, opts)
  if not provider or provider == "" then return nil end
  return call_engine_command({ "generate-theme-highlights", provider }, nil, "generate-theme-highlights", opts or {})
end

function M.inspect_cfn_template(f, o) return _make_file_wrapper("cfn-inspect", "cfn-inspect")(f, o) end
function M.validate_cfn_template(f, o) return _make_file_wrapper("cfn-validate", "cfn-validate")(f, o) end
function M.inspect_ansible_playbook(f, o) return _make_file_wrapper("ansible-inspect", "ansible-inspect")(f, o) end
function M.inspect_terraform(f, o) return _make_file_wrapper("tf-inspect", "tf-inspect")(f, o) end
function M.parse_terraform_security(f, o) return _make_file_wrapper("tf-security-parse", "tf-security-parse")(f, o) end
function M.validate_docker(f, o) return _make_file_wrapper("docker-validate", "docker-validate")(f, o) end
function M.inspect_helm_chart(f, o) return _make_file_wrapper("helm-inspect", "helm-inspect")(f, o) end
function M.validate_ansible_playbook(f, o) return _make_file_wrapper("ansible-validate", "ansible-validate")(f, o) end
function M.parse_ansible_inventory(f, o) return _make_file_wrapper("ansible-inventory-parse", "ansible-inventory-parse")(f, o) end

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
  -- BUG FIX: Wrap engine call in pcall to catch errors and prevent crash
  local ok, result = pcall(call_engine_command, { "classify-workspace", "--dir", dir }, nil, "classify-workspace", opts)
  if not ok then
    if not opts.silent then
      vim.notify("[cumulus] Workspace classification failed: " .. tostring(result), vim.log.levels.WARN)
    end
    return nil
  end
  if result then
    local elapsed_ms = (vim.loop.now() or 0) - start_time
    _cache_metrics.total_engine_calls_ms = _cache_metrics.total_engine_calls_ms + elapsed_ms
    _cache_metrics.num_engine_calls = _cache_metrics.num_engine_calls + 1
    _workspace_cache[cache_key] = { classification = result, timestamp = vim.loop.now() or 0, hit_count = 0 }
  end
  return result
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

--- Re-export run_term from ui module for backward compatibility
--- Callers in devops.lua and jvm.lua still use require("cumulus.util.engine").run_term()
M.run_term = require("cumulus.util.ui").run_term

return M



