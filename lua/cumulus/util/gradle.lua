-- Cumulus Gradle Build Integration
--
-- Architecture: Lua is a bridge only. All parsing and analysis is done by
-- the cumulus-engine Scala binary. This file handles Neovim UI/terminal wiring.

local M = {}

M.offline_mode = false

function M.toggle_offline_mode()
  M.offline_mode = not M.offline_mode
  vim.notify(
    "Gradle Offline Mode: " .. (M.offline_mode and "ENABLED (--offline)" or "DISABLED"),
    vim.log.levels.INFO
  )
end

function M.find_gradle(buf)
  local dir = (buf and vim.api.nvim_buf_is_valid(buf) and vim.fs.dirname(vim.api.nvim_buf_get_name(buf)))
    or vim.fn.expand("%:p:h")
  if dir == "" or not dir then
    dir = vim.fn.getcwd()
  end

  local engine = require("cumulus.util.engine")
  if engine.is_available() then
    local topo = engine.classify_workspace(dir)
    if topo then
      if topo.primary_type == "gradle" then
        return true
      end
      if topo.project_types then
        for _, ptype in ipairs(topo.project_types) do
          if ptype == "gradle" then
            return true
          end
        end
      end
    end
    local res = engine.discover_build_tool(dir) or engine.discover_build_tool(vim.fn.getcwd())
    if res and (res.tool == "gradle" or res.build_tool == "gradle") then
      return true
    end
  end

  local patterns = { "build.gradle", "build.gradle.kts", "settings.gradle", "settings.gradle.kts", "gradlew" }
  return vim.fs.root(dir, patterns) ~= nil or vim.fs.root(vim.fn.getcwd(), patterns) ~= nil
end

function M.get_gradle_cmd()
  local cwd = vim.fn.getcwd()
  local gradlew = cwd .. "/gradlew"
  local base = "gradle"
  if vim.fn.filereadable(gradlew) == 1 then
    if vim.fn.executable(gradlew) == 0 then
      vim.fn.system({ "chmod", "+x", gradlew })
    end
    base = "./gradlew"
  end

  local engine = require("cumulus.util.engine")
  if M.offline_mode or (engine.is_available() and engine.check_network() == false) then
    return base .. " --offline"
  end

  return base
end

function M.run_gradle_cmd(cmd)
  if not M.find_gradle() then
    require("cumulus.util.engine").notify_warn("No build.gradle found in project", "Cumulus JVM")
    return
  end

  local base_cmd = M.get_gradle_cmd()
  if cmd:sub(1, 7) == "gradle " then
    cmd = base_cmd .. cmd:sub(7)
  end

  require("cumulus.util.engine").run_term(cmd, { title = "Cumulus Gradle" })
end

function M.sync_dependencies()
  if not M.find_gradle() then
    return
  end

  local base_cmd = M.get_gradle_cmd()
  require("cumulus.util.sync-runner").run({
    cmd = { base_cmd, "-q", "dependencies" },
    notify_id = "cumulus_gradle_sync",
    tool_label = "Gradle",
    base_cmd = base_cmd,
  })
end

--- Get Gradle tasks for the current project.
--- Task output parsing is done by the cumulus-engine Scala binary (parse-gradle-tasks) with fallback.
function M.get_gradle_tasks()
  local base_cmd = M.get_gradle_cmd()
  local output = vim.fn.system(base_cmd .. " tasks --all")

  if vim.v.shell_error ~= 0 or not output or output == "" then
    vim.notify("Failed to fetch Gradle tasks", vim.log.levels.ERROR)
    return {}
  end

  local engine = require("cumulus.util.engine")
  if engine.is_available() then
    local tasks = engine.parse_gradle_tasks(output)
    if tasks and #tasks > 0 then
      return tasks
    end
  end

  -- Fallback task extraction from `./gradlew tasks` output
  local fallback_tasks = {}
  local seen = {}
  for line in output:gmatch("[^\r\n]+") do
    local trimmed = vim.trim(line)
    if trimmed ~= "" and not trimmed:match("^%-+$") and not trimmed:match("^//") and not trimmed:match("^#") then
      local task_name = trimmed:match("^([%a%d_%-:]+)%s+%-")
      if not task_name and not trimmed:match("%s") and not trimmed:match(":") and not trimmed:match("tasks$") then
        task_name = trimmed:match("^([%a%d_%-]+)$")
      end
      if task_name and not seen[task_name] and not task_name:match("tasks$") and not task_name:match("Tasks$") then
        seen[task_name] = true
        table.insert(fallback_tasks, task_name)
      end
    end
  end

  if #fallback_tasks > 0 then
    table.sort(fallback_tasks)
    return fallback_tasks
  end

  vim.notify("cumulus-engine: failed to parse Gradle tasks", vim.log.levels.ERROR)
  return {}
end

function M.run_gradle_task()
  if not M.find_gradle() then
    vim.notify("No build.gradle or build.gradle.kts found in project", vim.log.levels.WARN)
    return
  end

  vim.notify("Loading Gradle tasks...", vim.log.levels.INFO)
  local tasks = M.get_gradle_tasks()
  if #tasks == 0 then
    vim.notify("No Gradle tasks found", vim.log.levels.WARN)
    return
  end

  local base_cmd = M.get_gradle_cmd()
  vim.ui.select(tasks, {
    prompt = "Select Gradle Task:",
    format_item = function(item)
      return base_cmd .. " " .. item
    end,
  }, function(choice)
    if choice then
      M.run_gradle_cmd(base_cmd .. " " .. choice)
    end
  end)
end

--- Get direct Gradle project dependencies (version catalog) via engine helper.
---@param catalog_path? string
---@return table[]|nil
function M.get_dependencies(catalog_path)
  catalog_path = catalog_path or (vim.fn.getcwd() .. "/gradle/libs.versions.toml")
  if vim.fn.filereadable(catalog_path) == 0 then
    catalog_path = vim.fn.getcwd() .. "/libs.versions.toml"
  end
  local engine = require("cumulus.util.engine")
  if engine.is_available() then
    return engine.resolve_deps(catalog_path)
  end
  return nil
end

return M
