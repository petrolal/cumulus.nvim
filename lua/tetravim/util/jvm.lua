-- TetraVim JVM Platform Keymap Suite
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
  local ok, engine = pcall(require, "tetravim.util.engine")
  if ok and engine.notify_warn then
    engine.notify_warn(msg, "TetraVim JVM")
  else
    vim.notify(msg, vim.log.levels.WARN, { title = "TetraVim JVM" })
  end
end

local function notify_info(msg)
  vim.notify(msg, vim.log.levels.INFO, { title = "TetraVim JVM" })
end

--- WhichKey specification for the JVM platform keymap hierarchy
function M.whichkey_spec()
  return {
    { "<leader>j", group = "jvm platform", icon = "☕ " },
    { "<leader>jb", group = "build & tasks", icon = "󰒓 " },
    { "<leader>jt", group = "test runner", icon = "󰙨 " },
    { "<leader>jc", group = "code coverage", icon = "📊 " },
    { "<leader>jr", group = "run & execute", icon = "󰐊 " },
    { "<leader>js", group = "spring & frameworks", icon = "󱎘 " },
    { "<leader>jx", group = "refactor & jdtls", icon = "󰨞 " },
    { "<leader>jp", group = "profiling", icon = "⚡ " },
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
  local engine = require("tetravim.util.engine")

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

  -- Detect concrete test source roots under `cwd` (single- or multi-module
  -- layouts) so a "run all tests" action can target those directories
  -- instead of forcing neotest to discover the entire working tree.
  ---@param cwd string
  ---@return string[] roots
  local function detect_test_roots(cwd)
    local roots = {}
    local direct = {
      "src/test/java",
      "src/test/kotlin",
      "src/test/groovy",
      "src/test/scala",
      "src/integrationTest/java",
      "src/integrationTest/kotlin",
    }
    for _, rel in ipairs(direct) do
      local p = cwd .. "/" .. rel
      if vim.fn.isdirectory(p) == 1 then
        table.insert(roots, p)
      end
    end
    if #roots == 0 then
      -- Multi-module fallback: one level of sub-projects only (bounded).
      for _, pattern in ipairs({ "/*/src/test/java", "/*/src/test/kotlin" }) do
        for _, p in ipairs(vim.fn.glob(cwd .. pattern, false, true)) do
          if vim.fn.isdirectory(p) == 1 then
            table.insert(roots, p)
          end
        end
      end
    end
    return roots
  end

  -- Helper function to run tests via engine APIs
  ---@param mode "nearest"|"class"|"all"
  local function run_tests(mode)
    local cwd = vim.fn.getcwd()
    local build_result = engine.discover_build_tool(cwd)

    if not build_result or not build_result.tool then
      engine.notify_warn("No Maven or Gradle project found in current directory", "TetraVim JVM")
      return
    end

    local tool = build_result.tool
    if tool ~= "maven" and tool ~= "gradle" then
      engine.notify_warn("Unsupported build tool: " .. tostring(tool), "TetraVim JVM")
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
      engine.notify_warn("Failed to assemble test command", "TetraVim JVM")
      return
    end

    local cmd = assembled.command
    engine.notify_info("Running tests: " .. cmd, "TetraVim Test")

    -- Run command in terminal and capture output
    local output_lines = {}
    engine.run_term(cmd, {
      title = "TetraVim Test",
      on_error = function(error)
        engine.notify_warn("Test execution failed: " .. (error or "unknown error"), "TetraVim Test")
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
              { id = "tetravim_test_run" }
            )
          else
            vim.notify(
              string.format("Test Suite: All %d tests PASSED", passed),
              vim.log.levels.INFO,
              { id = "tetravim_test_run" }
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
      engine.notify_warn("No Maven, Gradle, or SBT project found in current directory", "TetraVim JVM")
      return
    end

    local tool = build_result.tool
    if tool == "maven" then
      local base_cmd = get_build_cmd(get_mvn_cmd(), M.offline_mode)
      engine.run_term(base_cmd .. " clean compile", { title = "TetraVim Maven" })
    elseif tool == "gradle" then
      local base_cmd = get_build_cmd(get_gradle_cmd(), M.offline_mode)
      engine.run_term(base_cmd .. " clean compile", { title = "TetraVim Gradle" })
    else
      engine.notify_warn("Unknown build tool: " .. tostring(tool), "TetraVim JVM")
    end
  end, { desc = "Build: Clean Compile" })

  map("n", "<leader>jbg", function()
    local cwd = vim.fn.getcwd()
    local build_result = engine.discover_build_tool(cwd)

    if not build_result or not build_result.tool or build_result.tool ~= "gradle" then
      engine.notify_warn("No Gradle project found in current directory", "TetraVim JVM")
      return
    end

    -- Run gradle tasks --all to get full task list
    local base_cmd = get_gradle_cmd()
    local output = vim.fn.system(base_cmd .. " tasks --all")

    if vim.v.shell_error ~= 0 or not output or output == "" then
      engine.notify_warn("Failed to fetch Gradle tasks", "TetraVim JVM")
      return
    end

    -- Parse tasks using engine
    local tasks = engine.parse_gradle_tasks(output)
    if not tasks or #tasks == 0 then
      engine.notify_warn("No Gradle tasks found or parse failed", "TetraVim JVM")
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
        engine.run_term(cmd_prefix .. " " .. choice, { title = "TetraVim Gradle" })
      end
    end)
  end, { desc = "Gradle: Select & Run Task" })

  map("n", "<leader>jbm", function()
    local cwd = vim.fn.getcwd()
    local build_result = engine.discover_build_tool(cwd)

    if not build_result or not build_result.tool or build_result.tool ~= "maven" then
      engine.notify_warn("No Maven project found in current directory", "TetraVim JVM")
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
      engine.notify_warn("No pom.xml found in project", "TetraVim JVM")
      return
    end

    -- Parse goals using engine
    vim.notify("Loading Maven goals...", vim.log.levels.INFO)
    local goals = engine.parse_pom_goals(vim.fn.fnamemodify(pom_path, ":p"))

    if not goals or #goals == 0 then
      engine.notify_warn("No Maven goals found or parse failed", "TetraVim JVM")
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
        engine.run_term(cmd_prefix .. " " .. choice, { title = "TetraVim Maven" })
      end
    end)
  end, { desc = "Maven: Select & Run Goal" })

  map("n", "<leader>jbo", function()
    M.offline_mode = not M.offline_mode
    local status = M.offline_mode and "ENABLED" or "DISABLED"
    local flags = M.offline_mode and "(-o / --offline)" or ""
    vim.notify("Offline Mode: " .. status .. " " .. flags, vim.log.levels.INFO)
  end, { desc = "Toggle Offline Mode (-o / --offline)" })

  local function resync_dependencies()
    local sync_state = require("tetravim.util.build-sync-state")
    sync_state.reset()
    sync_state.run()
  end

  map("n", "<leader>jbS", resync_dependencies, { desc = "Resync Dependencies (Maven/Gradle)" })

  -- 2. Test Runner (<leader>jt)
  map("n", "<leader>jta", function()
    local ok, neotest = pcall(require, "neotest")
    if not ok then
      run_tests("all")
      return
    end
    local cwd = vim.fn.getcwd()
    local roots = detect_test_roots(cwd)
    if #roots == 0 then
      engine.notify_warn("No test source roots detected; running the build tool's full test suite", "TetraVim Test")
      run_tests("all")
      return
    end
    for _, root in ipairs(roots) do
      pcall(function()
        neotest.run.run(root)
      end)
    end
  end, { desc = "Run All Tests in Workspace" })

  map("n", "<leader>jtt", function()
    local ok, neotest = pcall(require, "neotest")
    if not ok then
      run_tests("nearest")
      return
    end
    pcall(function()
      neotest.run.run()
    end)
  end, { desc = "Run Nearest Test Method" })

  map("n", "<leader>jtc", function()
    local file = vim.api.nvim_buf_get_name(0)
    if not file or file == "" or vim.bo.buftype ~= "" then
      engine.notify_warn("Current buffer is not a runnable file", "TetraVim Test")
      return
    end
    local ok, neotest = pcall(require, "neotest")
    if not ok then
      run_tests("class")
      return
    end
    pcall(function()
      neotest.run.run(file)
    end)
  end, { desc = "Run Current Test Class / File" })

  map("n", "<leader>jts", function()
    local ok, neotest = pcall(require, "neotest")
    if ok then
      pcall(function()
        neotest.summary.toggle()
      end)
    else
      engine.notify_warn("neotest is not available", "TetraVim Test")
    end
  end, { desc = "Toggle Test Summary Tree" })

  map("n", "<leader>jto", function()
    local ok, neotest = pcall(require, "neotest")
    if ok then
      pcall(function()
        neotest.output_panel.toggle()
      end)
    else
      engine.notify_warn("neotest is not available", "TetraVim Test")
    end
  end, { desc = "Toggle Test Output Panel" })

  map("n", "<leader>jtd", function()
    local ok, neotest = pcall(require, "neotest")
    if not ok then
      engine.notify_warn("neotest is not available", "TetraVim Test")
      return
    end
    local dap_ok, _ = pcall(require, "dap")
    if not dap_ok then
      engine.notify_warn("DAP debugger is not configured", "TetraVim Test")
      return
    end
    local call_ok, err = pcall(function()
      neotest.run.run({ strategy = "dap" })
    end)
    if not call_ok then
      engine.notify_warn("Failed to debug nearest test: " .. tostring(err), "TetraVim Test")
    end
  end, { desc = "Debug Nearest Test (DAP)" })

  map("n", "<leader>jtp", function()
    local ok, jdtls = pcall(require, "jdtls")
    if ok then
      jdtls.pick_test()
    else
      notify_error("jdtls is not loaded")
    end
  end, { desc = "JDTLS: Pick & Run Test" })

  -- Code Coverage (<leader>jc)
  map("n", "<leader>jcl", function()
    require("tetravim.util.coverage").load()
  end, { desc = "Load JaCoCo Coverage Report" })

  map("n", "<leader>jcx", function()
    -- Hide the overlays but keep the parsed report so <leader>jct can
    -- toggle it back without re-reading the JaCoCo XML.
    require("tetravim.util.coverage").clear(false)
  end, { desc = "Clear Coverage Overlays" })

  map("n", "<leader>jct", function()
    require("tetravim.util.coverage").toggle()
  end, { desc = "Toggle Coverage Display" })

  map("n", "<leader>jcs", function()
    require("tetravim.util.coverage").summary()
  end, { desc = "Show Coverage Summary" })

  -- 3. Run & Execute (<leader>jr)
  -- NOTE: Framework detection (Spring Boot vs Quarkus) remains in Lua for fast local detection.
  -- Test execution detection is delegated to engine via engine.assemble_test_command().
  -- This split reflects a pragmatic optimization: framework detection is lightweight (pom.xml
  -- scan), while test execution coordination is complex (multiple frameworks, runner options).
  map("n", "<leader>jrs", function()
    local cwd = vim.fn.getcwd()
    local build_result = engine.discover_build_tool(cwd)

    if not build_result or not build_result.tool then
      engine.notify_warn("No Maven or Gradle project found in current directory", "TetraVim JVM")
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
      engine.run_term(base_cmd .. " " .. goal, { title = "TetraVim Maven" })
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
      engine.run_term(base_cmd .. " " .. task, { title = "TetraVim Gradle" })
    else
      engine.notify_warn("Unsupported build tool: " .. tostring(tool), "TetraVim JVM")
    end
  end, { desc = "Run Spring Boot / Quarkus App" })

  map("n", "<leader>jrg", function()
    local file_path = vim.fn.expand("%:p")
    if not file_path or file_path == "" then
      engine.notify_warn("Current buffer is not a file", "TetraVim JVM")
      return
    end
    vim.cmd("update")
    engine.run_term("groovy " .. vim.fn.shellescape(file_path), { title = "TetraVim JVM" })
  end, { desc = "Groovy: Run Current Script" })

  map("n", "<leader>jrd", function()
    require("tetravim.util.springboot-debug").launch_debug()
  end, { desc = "Debug: Launch Spring Boot (DAP)" })

  -- 4. Spring Boot & Frameworks (<leader>js)
  map("n", "<leader>jse", function()
    require("tetravim.util.spring-picker").pick_endpoint()
  end, { desc = "Spring: Select REST Endpoint" })

  map("n", "<leader>jsb", function()
    require("tetravim.util.spring-picker").pick_bean()
  end, { desc = "Spring: Select Bean Dependency" })

  map("n", "<leader>jsd", function()
    require("tetravim.util.spring-picker").detect_app()
  end, { desc = "Spring: Detect Boot App" })

  map("n", "<leader>jsm", function()
    vim.notify("Use <leader>db to explore database schemas and migrations via Dadbod UI", vim.log.levels.INFO)
  end, { desc = "Flyway: Database Explorer" })

  -- 5. Refactoring & JDTLS (<leader>jx)
  map("n", "<leader>jxo", function()
    require("tetravim.util.engine").optimize_imports_buffer()
  end, { desc = "Optimize Java/Kotlin Imports (JDTLS/LSP)" })

  map("n", "<leader>jxH", function()
    local clients = vim.lsp.get_clients({ name = "jdtls" })
    if #clients > 0 then
      vim.notify("JDTLS is active and connected to project", vim.log.levels.INFO)
    else
      vim.notify("JDTLS is not active for this buffer", vim.log.levels.WARN)
    end
  end, { desc = "JDTLS: Check Client Status" })

  -- 6. Profiling (<leader>jp)
  map("n", "<leader>jps", function()
    require("tetravim.util.profiling").start()
  end, { desc = "Profiler: Start" })

  map("n", "<leader>jpx", function()
    require("tetravim.util.profiling").stop()
  end, { desc = "Profiler: Stop" })

  map("n", "<leader>jpv", function()
    require("tetravim.util.profiling").view()
  end, { desc = "Profiler: View Flamegraph" })

  -- 7. Dependencies (<leader>jd)
  map("n", "<leader>jdu", function()
    vim.notify("Use <leader>jds to resync dependencies via Maven/Gradle", vim.log.levels.INFO)
  end, { desc = "Check Dependency Versions" })

  map("n", "<leader>jds", resync_dependencies, { desc = "Maven/Gradle: Resync Dependencies" })

  -- 8. Environment & Diagnostics (<leader>ji)
  map("n", "<leader>jii", function()
    local clients = vim.lsp.get_clients()
    local client_names = {}
    for _, c in ipairs(clients) do
      table.insert(client_names, c.name)
    end
    local ver = vim.version()
    local msg = string.format(
      "TetraVim JVM Environment\nActive LSPs: %s\nNeovim: v%d.%d.%d",
      #client_names > 0 and table.concat(client_names, ", ") or "None",
      ver.major,
      ver.minor,
      ver.patch
    )
    vim.notify(msg, vim.log.levels.INFO)
  end, { desc = "JVM Environment: LSP Status" })

  map("n", "<leader>jid", "<cmd>Mason<cr>", { desc = "Mason Package Manager" })
  map("n", "<leader>jih", "<cmd>checkhealth tetravim<cr>", { desc = "TetraVim Health Check" })

  -- Dynamic WhichKey registration if already loaded
  local ok, wk = pcall(require, "which-key")
  if ok and wk.add then
    pcall(wk.add, M.whichkey_spec())
  end
end

--- Locate Java 21 JDK installation path across common system locations and SDKMAN
---@return string|nil java21_home Path to Java 21 installation or nil if not found
function M.find_java21_home()
  local patterns = {
    "/usr/lib/jvm/java-21-openjdk*",
    "/usr/lib/jvm/java-21*",
    "/usr/lib/jvm/jdk-21*",
    vim.fn.expand("~/.sdkman/candidates/java/21*"),
    "/usr/lib/jvm/default-java",
  }
  for _, pat in ipairs(patterns) do
    local candidates = vim.fn.glob(pat, false, true)
    if #candidates > 0 and vim.fn.isdirectory(candidates[1]) == 1 then
      return candidates[1]
    end
  end
  return nil
end

--- Check if a given directory is a JVM project (Maven, Gradle, or SBT)
---@param root string|number|nil Directory to check (defaults to cwd)
---@return boolean
function M.is_jvm_project(root)
  if type(root) == "number" then
    return false
  end
  root = root or vim.fn.getcwd()
  if type(root) ~= "string" or root == "" then
    return false
  end
  return vim.fn.glob(root .. "/pom.xml") ~= ""
    or vim.fn.glob(root .. "/build.gradle") ~= ""
    or vim.fn.glob(root .. "/build.gradle.kts") ~= ""
    or vim.fn.glob(root .. "/build.sbt") ~= ""
end

return M
