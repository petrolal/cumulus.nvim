-- Cumulus JVM Project & Keymap Suite Manager
--
-- Architecture: Fast detection of whether a workspace belongs to a JVM-based
-- project (Maven, Gradle, SBT, or JVM language files). When in a JVM project,
-- registers the global JVM platform keymaps and WhichKey specs.

local M = {}

M.keymaps_registered = false

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

--- Check if the workspace or current buffer belongs to a JVM project.
---@param buf_or_dir? number|string Buffer number or directory path
---@return boolean true if the workspace/buffer is a JVM project
function M.is_jvm_project(buf_or_dir)
  local dir
  if type(buf_or_dir) == "number" and vim.api.nvim_buf_is_valid(buf_or_dir) then
    local ft = vim.bo[buf_or_dir].filetype
    if ft == "java" or ft == "kotlin" or ft == "scala" or ft == "groovy" or ft == "sbt" then
      return true
    end
    dir = vim.fs.dirname(vim.api.nvim_buf_get_name(buf_or_dir))
  elseif type(buf_or_dir) == "string" and buf_or_dir ~= "" then
    dir = buf_or_dir
  else
    local cur_buf = vim.api.nvim_get_current_buf()
    if vim.api.nvim_buf_is_valid(cur_buf) then
      local ft = vim.bo[cur_buf].filetype
      if ft == "java" or ft == "kotlin" or ft == "scala" or ft == "groovy" or ft == "sbt" then
        return true
      end
    end
    dir = vim.fn.getcwd()
  end

  if not dir or dir == "" then
    dir = vim.fn.getcwd()
  end

  local ok, engine = pcall(require, "cumulus.util.engine")
  if ok and engine.is_available and engine.is_available() then
    local topo = engine.classify_workspace(dir)
    if topo then
      if topo.primary_type == "maven" or topo.primary_type == "gradle" or topo.primary_type == "sbt" then
        return true
      end
      if topo.has_spring then
        return true
      end
      if topo.project_types then
        for _, ptype in ipairs(topo.project_types) do
          if ptype == "maven" or ptype == "gradle" or ptype == "sbt" then
            return true
          end
        end
      end
      if topo.primary_type == "devops" or (topo.primary_type == "unknown" and (not topo.project_types or #topo.project_types == 0)) then
        return false
      end
    end

    local res = engine.discover_build_tool(dir) or engine.discover_build_tool(vim.fn.getcwd())
    if res and (res.tool == "maven" or res.tool == "gradle" or res.tool == "sbt" or
                res.build_tool == "maven" or res.build_tool == "gradle" or res.build_tool == "sbt") then
      return true
    end
  end

  local jvm_markers = {
    "pom.xml",
    "mvnw",
    "build.gradle",
    "build.gradle.kts",
    "settings.gradle",
    "settings.gradle.kts",
    "gradlew",
    "build.sbt",
  }

  if vim.fs.root(dir, jvm_markers) or vim.fs.root(vim.fn.getcwd(), jvm_markers) then
    return true
  end

  return false
end

--- Register all global JVM platform keymaps and WhichKey group specs
function M.setup_keymaps()
  if M.keymaps_registered then
    return
  end
  M.keymaps_registered = true

  local map = vim.keymap.set

  -- 1. Build & Tasks (<leader>jb)
  map("n", "<leader>jbc", function()
    local maven = require("cumulus.util.maven")
    local gradle = require("cumulus.util.gradle")
    if maven.find_pom() then
      maven.run_maven_cmd(maven.get_mvn_cmd() .. " clean compile")
    elseif gradle.find_gradle() then
      gradle.run_gradle_cmd(gradle.get_gradle_cmd() .. " clean compile")
    else
      vim.notify("No pom.xml or build.gradle found in project", vim.log.levels.WARN)
    end
  end, { desc = "Build: Clean Compile" })

  map("n", "<leader>jbg", function()
    require("cumulus.util.gradle").run_gradle_task()
  end, { desc = "Gradle: Select & Run Task" })

  map("n", "<leader>jbm", function()
    require("cumulus.util.maven").run_maven_goal()
  end, { desc = "Maven: Select & Run Goal" })

  map("n", "<leader>jbo", function()
    local maven = require("cumulus.util.maven")
    local gradle = require("cumulus.util.gradle")
    maven.toggle_offline_mode()
    gradle.toggle_offline_mode()
  end, { desc = "Toggle Offline Mode (-o / --offline)" })

  map("n", "<leader>jbS", function()
    local sync_state = require("cumulus.util.build-sync-state")
    sync_state.reset()
    sync_state.run()
  end, { desc = "Resync Dependencies (Maven/Gradle)" })

  -- 2. Test Runner (<leader>jt)
  map("n", "<leader>jta", function()
    require("cumulus.util.test-runner").run_test("all")
  end, { desc = "Run All Tests in Workspace" })

  map("n", "<leader>jtt", function()
    require("cumulus.util.test-runner").run_test("nearest")
  end, { desc = "Run Nearest Test Method" })

  map("n", "<leader>jtc", function()
    require("cumulus.util.test-runner").run_test("class")
  end, { desc = "Run Current Test Class" })

  map("n", "<leader>jtp", function()
    local ok, jdtls = pcall(require, "jdtls")
    if ok then
      jdtls.pick_test()
    else
      vim.notify("jdtls is not loaded", vim.log.levels.WARN)
    end
  end, { desc = "JDTLS: Pick & Run Test" })

  -- 3. Run & Execute (<leader>jr)
  map("n", "<leader>jrs", function()
    local maven = require("cumulus.util.maven")
    local gradle = require("cumulus.util.gradle")
    if maven.find_pom() then
      local pom_path = vim.fn.findfile("pom.xml", vim.fn.getcwd() .. ";")
      local pom_content = pom_path ~= "" and table.concat(vim.fn.readfile(pom_path), "\n") or ""
      if pom_content:match("quarkus%-maven%-plugin") then
        maven.run_maven_cmd(maven.get_mvn_cmd() .. " quarkus:dev")
      else
        maven.run_maven_cmd(maven.get_mvn_cmd() .. " spring-boot:run")
      end
    elseif gradle.find_gradle() then
      local gradle_file = vim.fn.findfile("build.gradle", vim.fn.getcwd() .. ";")
      local gradle_kts = vim.fn.findfile("build.gradle.kts", vim.fn.getcwd() .. ";")
      local g_path = gradle_file ~= "" and gradle_file or gradle_kts
      local g_content = g_path ~= "" and table.concat(vim.fn.readfile(g_path), "\n") or ""
      if g_content:match("quarkus") then
        gradle.run_gradle_cmd(gradle.get_gradle_cmd() .. " quarkusDev")
      else
        gradle.run_gradle_cmd(gradle.get_gradle_cmd() .. " bootRun")
      end
    else
      require("cumulus.util.engine").notify_warn("No pom.xml or build.gradle found in project", "Cumulus JVM")
    end
  end, { desc = "Run Spring Boot / Quarkus App" })

  map("n", "<leader>jrg", function()
    vim.cmd("update")
    require("cumulus.util.engine").run_term("groovy " .. vim.fn.shellescape(vim.fn.expand("%:p")), { title = "Cumulus JVM" })
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
      vim.notify("jdtls is not loaded", vim.log.levels.WARN)
    end
  end, { desc = "JDTLS: Extract Variable" })

  map("n", "<leader>jxc", function()
    local ok, jdtls = pcall(require, "jdtls")
    if ok then
      jdtls.extract_constant()
    else
      vim.notify("jdtls is not loaded", vim.log.levels.WARN)
    end
  end, { desc = "JDTLS: Extract Constant" })

  map("v", "<leader>jxm", function()
    local ok, jdtls = pcall(require, "jdtls")
    if ok then
      jdtls.extract_method(true)
    else
      vim.notify("jdtls is not loaded", vim.log.levels.WARN)
    end
  end, { desc = "JDTLS: Extract Method" })

  map("n", "<leader>jxo", function()
    require("cumulus.util.engine").optimize_imports_buffer()
  end, { desc = "Optimize Java/Kotlin Imports" })

  map("n", "<leader>jxH", function()
    local engine = require("cumulus.util.engine")
    if not _G.cumulus_jdtls_start_time then
      vim.notify("JDTLS not started yet", vim.log.levels.WARN)
      return
    end
    local cwd = vim.fn.getcwd()
    local status = engine.check_jdtls_sync(cwd, _G.cumulus_jdtls_start_time)
    if status and status.sync_needed then
      vim.notify(
        "JDTLS classpath is stale (modified: " .. (status.modified_file or "unknown") .. "). Run dependency sync and JdtRestart.",
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
