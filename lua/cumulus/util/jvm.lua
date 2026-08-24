-- Cumulus JVM Platform Keymap Suite
--
-- Architecture: Pure keymap registration for JVM platform operations (Maven, Gradle, SBT).
-- Keymaps are registered unconditionally; error handling is delegated to the Scala engine
-- via engine APIs called on keypress. Build tool detection and goal/task extraction
-- are delegated entirely to the Scala engine.

local M = {}

M.keymaps_registered = false
M.offline_mode = false

-- Consistent notification helper for JVM operations
local function notify_error(msg)
  engine.notify_warn(msg, "Cumulus JVM")
end

local function notify_info(msg)
  vim.notify(msg, vim.log.levels.INFO, { title = "Cumulus JVM" })
end

--- WhichKey specification for the JVM platform keymap hierarchy
function M.whichkey_spec()
  return {
    { "<leader>j", group = "jvm platform", icon = "☕ " },
    { "<leader>jb", group = "build & tasks", icon = "󰒓 " },
    { "<leader>jt", group = "test runner", icon = "󰙨 " },
    { "<leader>jr", group = "run & execute", icon = "󰐊 " },
    { "<leader>js", group = "spring & frameworks", icon = "󱎘 " },
    { "<leader>jx", group = "refactor & jdtls", icon = "󰨞 " },
    { "<leader>jd", group = "dependencies", icon = "📦 " },
    { "<leader>ji", group = "engine & info", icon = "ℹ " },
  }
end

--- Register all global JVM platform keymaps and WhichKey group specs
function M.setup_keymaps()
  if M.keymaps_registered then
    return
  end
  M.keymaps_registered = true

  local map = vim.keymap.set
  local engine = require("cumulus.util.engine")

  -- Helper function to get the appropriate build command with offline flag
  local function get_build_cmd(base_cmd, offline)
    -- Check network availability unless offline mode is already enabled
    if not offline and engine.is_available() then
      local network_ok = engine.check_network()
      if network_ok == false then
        offline = true
      end
    end

    if offline then
      if base_cmd:match("mvn") then
        return base_cmd .. " -o"
      elseif base_cmd:match("gradle") then
        return base_cmd .. " --offline"
      end
    end
    return base_cmd
  end

  -- Helper function to get mvnw or system mvn
  local function get_mvn_cmd()
    local cwd = vim.fn.getcwd()
    local mvnw = cwd .. "/mvnw"
    if vim.fn.filereadable(mvnw) == 1 then
      if vim.fn.executable(mvnw) == 0 then
        vim.fn.system({ "chmod", "+x", mvnw })
      end
      return "./mvnw"
    end
    return "mvn"
  end

  -- Helper function to get gradlew or system gradle
  local function get_gradle_cmd()
    local cwd = vim.fn.getcwd()
    local gradlew = cwd .. "/gradlew"
    if vim.fn.filereadable(gradlew) == 1 then
      if vim.fn.executable(gradlew) == 0 then
        vim.fn.system({ "chmod", "+x", gradlew })
      end
      return "./gradlew"
    end
    return "gradle"
  end

  -- Helper function to run tests via engine APIs
  ---@param mode "nearest"|"class"|"all"
  local function run_tests(mode)
    local cwd = vim.fn.getcwd()
    local build_result = engine.discover_build_tool(cwd)

    if not build_result or not build_result.tool then
      engine.notify_warn("No Maven or Gradle project found in current directory", "Cumulus JVM")
      return
    end

    local tool = build_result.tool
    if tool ~= "maven" and tool ~= "gradle" then
      engine.notify_warn("Unsupported build tool: " .. tostring(tool), "Cumulus JVM")
      return
    end

    -- Detect test context from current buffer and cursor
    local bufnr = vim.api.nvim_get_current_buf()
    local file_path = vim.api.nvim_buf_get_name(bufnr)
    local cursor_line = vim.api.nvim_win_get_cursor(0)[1]
    local test_info = engine.detect_test_context(file_path, cursor_line)
    if not test_info then
      test_info = {}
    end

    -- Prepare command assembly options
    local class_arg = (mode == "nearest" or mode == "class") and test_info.class_name or nil
    local method_arg = (mode == "nearest") and test_info.method_name or nil
    if class_arg then
      class_arg = vim.fn.shellescape(class_arg)
    end
    if method_arg then
      method_arg = vim.fn.shellescape(method_arg)
    end

    -- Assemble test command via engine
    local assembled = engine.assemble_test_command({
      tool = tool,
      ["class"] = class_arg,
      method = method_arg,
      dir = cwd,
    })

    if not assembled or not assembled.command then
      engine.notify_warn("Failed to assemble test command", "Cumulus JVM")
      return
    end

    local cmd = assembled.command
    engine.notify_info("Running tests: " .. cmd, "Cumulus Test")

    -- Run command in terminal and capture output
    local output_lines = {}
    engine.run_term(cmd, {
      title = "Cumulus Test",
      on_error = function(error)
        engine.notify_warn("Test execution failed: " .. (error or "unknown error"), "Cumulus Test")
      end,
      on_stdout = function(data)
        for _, line in ipairs(data) do
          if line ~= "" then
            table.insert(output_lines, line)
          end
        end
      end,
      on_stderr = function(data)
        for _, line in ipairs(data) do
          if line ~= "" then
            table.insert(output_lines, line)
          end
        end
      end,
      on_exit = function()
        vim.schedule(function()
          -- Safety check: verify Neovim is still running (callback may fire after quit)
          if not vim.api.nvim_get_current_buf then
            return
          end

          local log = table.concat(output_lines, "\n")
          -- Parse test output and notify results
          local entries = engine.parse_test_output(log)
          if not entries or #entries == 0 then
            return
          end

          local failures, passed = 0, 0
          for _, entry in ipairs(entries) do
            if entry.status == "FAILED" then
              failures = failures + 1
            elseif entry.status == "PASSED" then
              passed = passed + 1
            end
          end

          if failures > 0 then
            vim.notify(
              string.format("Test Suite: %d FAILED, %d PASSED", failures, passed),
              vim.log.levels.ERROR,
              { id = "cumulus_test_run" }
            )
          else
            vim.notify(
              string.format("Test Suite: All %d tests PASSED", passed),
              vim.log.levels.INFO,
              { id = "cumulus_test_run" }
            )
          end
        end)
      end,
    })
  end

  -- 1. Build & Tasks (<leader>jb)
  map("n", "<leader>jbc", function()
    local cwd = vim.fn.getcwd()
    local build_result = engine.discover_build_tool(cwd)

    if not build_result or not build_result.tool then
      engine.notify_warn("No Maven, Gradle, or SBT project found in current directory", "Cumulus JVM")
      return
    end

    local tool = build_result.tool
    if tool == "maven" then
      local base_cmd = get_build_cmd(get_mvn_cmd(), M.offline_mode)
      engine.run_term(base_cmd .. " clean compile", { title = "Cumulus Maven" })
    elseif tool == "gradle" then
      local base_cmd = get_build_cmd(get_gradle_cmd(), M.offline_mode)
      engine.run_term(base_cmd .. " clean compile", { title = "Cumulus Gradle" })
    else
      engine.notify_warn("Unknown build tool: " .. tostring(tool), "Cumulus JVM")
    end
  end, { desc = "Build: Clean Compile" })

  map("n", "<leader>jbg", function()
    local cwd = vim.fn.getcwd()
    local build_result = engine.discover_build_tool(cwd)

    if not build_result or not build_result.tool or build_result.tool ~= "gradle" then
      engine.notify_warn("No Gradle project found in current directory", "Cumulus JVM")
      return
    end

    -- Run gradle tasks --all to get full task list
    local base_cmd = get_gradle_cmd()
    local output = vim.fn.system(base_cmd .. " tasks --all")

    if vim.v.shell_error ~= 0 or not output or output == "" then
      engine.notify_warn("Failed to fetch Gradle tasks", "Cumulus JVM")
      return
    end

    -- Parse tasks using engine
    local tasks = engine.parse_gradle_tasks(output)
    if not tasks or #tasks == 0 then
      engine.notify_warn("No Gradle tasks found or parse failed", "Cumulus JVM")
      return
    end

    vim.notify("Loading Gradle tasks...", vim.log.levels.INFO)
    local cmd_prefix = get_build_cmd(base_cmd, M.offline_mode)

    vim.ui.select(tasks, {
      prompt = "Select Gradle Task:",
      format_item = function(item)
        return cmd_prefix .. " " .. item
      end,
    }, function(choice)
      if choice then
        engine.run_term(cmd_prefix .. " " .. choice, { title = "Cumulus Gradle" })
      end
    end)
  end, { desc = "Gradle: Select & Run Task" })

  map("n", "<leader>jbm", function()
    local cwd = vim.fn.getcwd()
    local build_result = engine.discover_build_tool(cwd)

    if not build_result or not build_result.tool or build_result.tool ~= "maven" then
      engine.notify_warn("No Maven project found in current directory", "Cumulus JVM")
      return
    end

    -- Find pom.xml file
    local pom_path = vim.fn.findfile("pom.xml", cwd .. ";")
    if pom_path == "" then
      local current_file = vim.fn.expand("%:p:h")
      if current_file ~= "" then
        pom_path = vim.fn.findfile("pom.xml", current_file .. ";")
      end
    end

    if pom_path == "" then
      engine.notify_warn("No pom.xml found in project", "Cumulus JVM")
      return
    end

    -- Parse goals using engine
    vim.notify("Loading Maven goals...", vim.log.levels.INFO)
    local goals = engine.parse_pom_goals(vim.fn.fnamemodify(pom_path, ":p"))

    if not goals or #goals == 0 then
      engine.notify_warn("No Maven goals found or parse failed", "Cumulus JVM")
      return
    end

    local base_cmd = get_mvn_cmd()
    local cmd_prefix = get_build_cmd(base_cmd, M.offline_mode)

    vim.ui.select(goals, {
      prompt = "Select Maven Goal:",
      format_item = function(item)
        return cmd_prefix .. " " .. item
      end,
    }, function(choice)
      if choice then
        engine.run_term(cmd_prefix .. " " .. choice, { title = "Cumulus Maven" })
      end
    end)
  end, { desc = "Maven: Select & Run Goal" })

  map("n", "<leader>jbo", function()
    M.offline_mode = not M.offline_mode
    local status = M.offline_mode and "ENABLED" or "DISABLED"
    local flags = M.offline_mode and "(-o / --offline)" or ""
    vim.notify("Offline Mode: " .. status .. " " .. flags, vim.log.levels.INFO)
  end, { desc = "Toggle Offline Mode (-o / --offline)" })

  map("n", "<leader>jbS", function()
    local sync_state = require("cumulus.util.build-sync-state")
    sync_state.reset()
    sync_state.run()
  end, { desc = "Resync Dependencies (Maven/Gradle)" })

  -- 2. Test Runner (<leader>jt)
  map("n", "<leader>jta", function()
    run_tests("all")
  end, { desc = "Run All Tests in Workspace" })

  map("n", "<leader>jtt", function()
    run_tests("nearest")
  end, { desc = "Run Nearest Test Method" })

  map("n", "<leader>jtc", function()
    run_tests("class")
  end, { desc = "Run Current Test Class" })

  map("n", "<leader>jtp", function()
    local ok, jdtls = pcall(require, "jdtls")
    if ok then
      jdtls.pick_test()
    else
      notify_error("jdtls is not loaded")
    end
  end, { desc = "JDTLS: Pick & Run Test" })

  -- 3. Run & Execute (<leader>jr)
  -- NOTE: Framework detection (Spring Boot vs Quarkus) remains in Lua for fast local detection.
  -- Test execution detection is delegated to engine via engine.assemble_test_command().
  -- This split reflects a pragmatic optimization: framework detection is lightweight (pom.xml
  -- scan), while test execution coordination is complex (multiple frameworks, runner options).
  map("n", "<leader>jrs", function()
    local cwd = vim.fn.getcwd()
    local build_result = engine.discover_build_tool(cwd)

    if not build_result or not build_result.tool then
      engine.notify_warn("No Maven or Gradle project found in current directory", "Cumulus JVM")
      return
    end

    local tool = build_result.tool

    if tool == "maven" then
      -- Check if it's Quarkus or Spring Boot (lightweight local detection)
      local pom_path = vim.fn.findfile("pom.xml", cwd .. ";")
      if pom_path == "" then
        pom_path = vim.fn.findfile("pom.xml", vim.fn.expand("%:p:h") .. ";")
      end

      local is_quarkus = false
      if pom_path ~= "" then
        local ok, lines = pcall(vim.fn.readfile, pom_path)
        if ok and lines then
          local pom_content = table.concat(lines, "\n")
          is_quarkus = pom_content:match("quarkus%-maven%-plugin") ~= nil
        end
      end

      local base_cmd = get_build_cmd(get_mvn_cmd(), M.offline_mode)
      local goal = is_quarkus and "quarkus:dev" or "spring-boot:run"
      engine.run_term(base_cmd .. " " .. goal, { title = "Cumulus Maven" })
    elseif tool == "gradle" then
      -- Check if it's Quarkus or Spring Boot
      local gradle_file = vim.fn.findfile("build.gradle", cwd .. ";")
      local gradle_kts = vim.fn.findfile("build.gradle.kts", cwd .. ";")
      local g_path = gradle_file ~= "" and gradle_file or gradle_kts

      local is_quarkus = false
      if g_path ~= "" then
        local ok, lines = pcall(vim.fn.readfile, g_path)
        if ok and lines then
          local g_content = table.concat(lines, "\n")
          is_quarkus = g_content:match("quarkus") ~= nil
        end
      end

      local base_cmd = get_build_cmd(get_gradle_cmd(), M.offline_mode)
      local task = is_quarkus and "quarkusDev" or "bootRun"
      engine.run_term(base_cmd .. " " .. task, { title = "Cumulus Gradle" })
    else
      engine.notify_warn("Unsupported build tool: " .. tostring(tool), "Cumulus JVM")
    end
  end, { desc = "Run Spring Boot / Quarkus App" })

  map("n", "<leader>jrg", function()
    local file_path = vim.fn.expand("%:p")
    if not file_path or file_path == "" then
      engine.notify_warn("Current buffer is not a file", "Cumulus JVM")
      return
    end
    vim.cmd("update")
    engine.run_term("groovy " .. vim.fn.shellescape(file_path), { title = "Cumulus JVM" })
  end, { desc = "Groovy: Run Current Script" })

  map("n", "<leader>jrd", function()
    require("cumulus.util.springboot-debug").launch_debug()
  end, { desc = "Debug: Launch Spring Boot (DAP)" })

  -- 4. Spring Boot & Frameworks (<leader>js)
  map("n", "<leader>jse", function()
    require("cumulus.util.engine").select_endpoint()
  end, { desc = "Spring: Select REST Endpoint" })

  map("n", "<leader>jsb", function()
    require("cumulus.util.engine").select_bean()
  end, { desc = "Spring: Select Bean Dependency" })

  map("n", "<leader>jsm", function()
    require("cumulus.util.engine").validate_migrations_action()
  end, { desc = "Flyway: Validate Migrations" })

  -- 5. Refactoring & JDTLS (<leader>jx)
  map("n", "<leader>jxv", function()
    local ok, jdtls = pcall(require, "jdtls")
    if ok then
      jdtls.extract_variable()
    else
      notify_error("jdtls is not loaded")
    end
  end, { desc = "JDTLS: Extract Variable" })

  map("n", "<leader>jxc", function()
    local ok, jdtls = pcall(require, "jdtls")
    if ok then
      jdtls.extract_constant()
    else
      notify_error("jdtls is not loaded")
    end
  end, { desc = "JDTLS: Extract Constant" })

  map("v", "<leader>jxm", function()
    local ok, jdtls = pcall(require, "jdtls")
    if ok then
      jdtls.extract_method(true)
    else
      notify_error("jdtls is not loaded")
    end
  end, { desc = "JDTLS: Extract Method" })

  map("n", "<leader>jxo", function()
    require("cumulus.util.engine").optimize_imports_buffer()
  end, { desc = "Optimize Java/Kotlin Imports" })

  map("n", "<leader>jxH", function()
    local engine = require("cumulus.util.engine")
    if not _G.cumulus_jdtls_start_time then
      notify_error("JDTLS not started yet")
      return
    end
    local cwd = vim.fn.getcwd()
    local status = engine.check_jdtls_sync(cwd, _G.cumulus_jdtls_start_time)
    if status and status.sync_needed then
      vim.notify(
        "JDTLS classpath is stale (modified: "
          .. (status.modified_file or "unknown")
          .. "). Run dependency sync and JdtRestart.",
        vim.log.levels.WARN
      )
    else
      vim.notify("JDTLS classpath is in sync", vim.log.levels.INFO)
    end
  end, { desc = "JDTLS: Check Classpath Sync" })

  -- 6. Dependencies (<leader>jd)
  map("n", "<leader>jdu", function()
    local filepath = vim.api.nvim_buf_get_name(0)
    local engine = require("cumulus.util.engine")
    if engine.is_available() then
      local lenses = engine.check_dep_versions(filepath)
      if lenses and #lenses > 0 then
        vim.notify(string.format("Found %d dependencies to check", #lenses), vim.log.levels.INFO)
      else
        vim.notify("No dependencies found or already up-to-date", vim.log.levels.INFO)
      end
    end
  end, { desc = "Check Dependency Versions" })

  map("n", "<leader>jds", function()
    local sync_state = require("cumulus.util.build-sync-state")
    sync_state.reset()
    sync_state.run()
  end, { desc = "Maven/Gradle: Resync Dependencies" })

  -- 7. Engine & Diagnostics (<leader>ji)
  map("n", "<leader>jii", function()
    local engine = require("cumulus.util.engine")
    local p = engine.ping()
    if p then
      vim.notify(
        string.format("Cumulus Engine Active\nVersion: %s\nScala: %s\nCommit: %s", p.version, p.scala, p.commit),
        vim.log.levels.INFO
      )
    else
      vim.notify("Cumulus Engine is not active", vim.log.levels.WARN)
    end
  end, { desc = "Cumulus Engine: Status & Ping" })

  map("n", "<leader>jid", "<cmd>CumulusInstallEngine<cr>", { desc = "Cumulus Engine: Download Binary" })
  map("n", "<leader>jih", "<cmd>checkhealth cumulus<cr>", { desc = "Cumulus Health Check" })

  -- Dynamic WhichKey registration if already loaded
  local ok, wk = pcall(require, "which-key")
  if ok and wk.add then
    pcall(wk.add, M.whichkey_spec())
  end
end

return M
