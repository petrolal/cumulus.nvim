-- Cumulus Maven Build Integration
--
-- Architecture: Lua is a bridge only. All parsing and analysis is done by
-- the cumulus-engine Scala binary. This file handles Neovim UI/terminal wiring.

local M = {}

M.offline_mode = false

function M.toggle_offline_mode()
  M.offline_mode = not M.offline_mode
  vim.notify(
    "Maven Offline Mode: " .. (M.offline_mode and "ENABLED (-o)" or "DISABLED"),
    vim.log.levels.INFO
  )
end

function M.find_pom(buf)
  local dir = (buf and vim.api.nvim_buf_is_valid(buf) and vim.fs.dirname(vim.api.nvim_buf_get_name(buf)))
    or vim.fn.expand("%:p:h")
  if dir == "" or not dir then
    dir = vim.fn.getcwd()
  end

  local engine = require("cumulus.util.engine")
  if engine.is_available() then
    local topo = engine.classify_workspace(dir)
    if topo then
      if topo.primary_type == "maven" then
        return true
      end
      if topo.project_types then
        for _, ptype in ipairs(topo.project_types) do
          if ptype == "maven" then
            return true
          end
        end
      end
    end
    local res = engine.discover_build_tool(dir) or engine.discover_build_tool(vim.fn.getcwd())
    if res and (res.tool == "maven" or res.build_tool == "maven") then
      return true
    end
  end

  local patterns = { "pom.xml", "mvnw" }
  return vim.fs.root(dir, patterns) ~= nil or vim.fs.root(vim.fn.getcwd(), patterns) ~= nil
end

function M.get_mvn_cmd()
  local cwd = vim.fn.getcwd()
  local mvnw = cwd .. "/mvnw"
  local base = "mvn"
  if vim.fn.filereadable(mvnw) == 1 then
    if vim.fn.executable(mvnw) == 0 then
      vim.fn.system({ "chmod", "+x", mvnw })
    end
    base = "./mvnw"
  end

  local engine = require("cumulus.util.engine")
  if M.offline_mode or (engine.is_available() and engine.check_network() == false) then
    return base .. " -o"
  end

  return base
end

function M.run_maven_cmd(cmd)
  if not M.find_pom() then
    require("cumulus.util.engine").notify_warn("No pom.xml found in project", "Cumulus JVM")
    return
  end

  local base_cmd = M.get_mvn_cmd()
  if cmd:sub(1, 4) == "mvn " then
    cmd = base_cmd .. cmd:sub(4)
  end

  require("cumulus.util.engine").run_term(cmd, { title = "Cumulus Maven" })
end

function M.sync_dependencies()
  if not M.find_pom() then
    return
  end

  local base_cmd = M.get_mvn_cmd()
  require("cumulus.util.sync-runner").run({
    cmd = { base_cmd, "-q", "dependency:resolve" },
    notify_id = "cumulus_maven_sync",
    tool_label = "Maven",
    base_cmd = base_cmd,
  })
end

--- Get Maven goals for the current project.
--- All pom.xml parsing is done by the cumulus-engine Scala binary (parse-pom subcommand).
function M.get_maven_goals()
  local cwd = vim.fn.getcwd()
  local pom_path = vim.fn.findfile("pom.xml", cwd .. ";")
  if pom_path == "" then
    local current_file = vim.fn.expand("%:p:h")
    if current_file ~= "" then
      pom_path = vim.fn.findfile("pom.xml", current_file .. ";")
    end
  end

  if pom_path == "" then
    return {}
  end

  local engine = require("cumulus.util.engine")
  if engine.is_available() then
    local goals = engine.parse_pom_goals(vim.fn.fnamemodify(pom_path, ":p"))
    if goals and #goals > 0 then
      return goals
    end
  end

  -- Fallback default lifecycle and common plugin goals
  local default_goals = {
    "clean",
    "validate",
    "compile",
    "test",
    "package",
    "verify",
    "install",
    "deploy",
    "clean compile",
    "clean test",
    "clean package",
    "clean install",
    "spring-boot:run",
    "quarkus:dev",
  }
  return default_goals
end

function M.run_maven_goal()
  if not M.find_pom() then
    vim.notify("No pom.xml found in project", vim.log.levels.WARN)
    return
  end

  vim.notify("Loading Maven goals...", vim.log.levels.INFO)
  local goals = M.get_maven_goals()
  local base_cmd = M.get_mvn_cmd()

  vim.ui.select(goals, {
    prompt = "Select Maven Goal:",
    format_item = function(item)
      return base_cmd .. " " .. item
    end,
  }, function(choice)
    if choice then
      M.run_maven_cmd(base_cmd .. " " .. choice)
    end
  end)
end

--- Get direct Maven project dependencies via engine helper.
---@param pom_path? string
---@return table[]|nil
function M.get_dependencies(pom_path)
  pom_path = pom_path or (vim.fn.getcwd() .. "/pom.xml")
  local engine = require("cumulus.util.engine")
  if engine.is_available() then
    return engine.resolve_deps(pom_path)
  end
  return nil
end

return M
