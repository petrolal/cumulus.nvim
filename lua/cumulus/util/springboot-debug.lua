-- Cumulus Spring Boot Debug Configuration (SPEC-006)
-- Detects Spring Boot applications and launches debugging with DAP integration.

local M = {}

--- Launch Spring Boot application with debug configuration
function M.launch_debug()
  local rust = require("cumulus.util.rust")
  local cwd = vim.fn.getcwd()

  local config = rust.detect_springboot_app(cwd)
  if not config then
    vim.notify("Failed to detect Spring Boot application", vim.log.levels.ERROR)
    return
  end

  if config.build_tool == "unknown" then
    vim.notify("No Maven or Gradle build file found", vim.log.levels.WARN)
    return
  end

  local ok, dap = pcall(require, "dap")
  if not ok then
    vim.notify("nvim-dap is not installed", vim.log.levels.ERROR)
    return
  end

  local dap_config = {
    type = "java",
    name = "Spring Boot Debug (" .. config.project_name .. ")",
    request = "launch",
    mainClass = config.main_class,
    projectName = config.project_name,
    cwd = cwd,
    console = "integratedTerminal",
    preLaunchTask = config.build_tool == "maven" and "maven: clean package" or "gradle: clean build",
  }

  if config.build_tool == "maven" then
    dap_config.args = "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005"
  elseif config.build_tool == "gradle" then
    dap_config.args = "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005"
  end

  if #config.profiles > 0 then
    dap_config.env = { SPRING_PROFILES_ACTIVE = table.concat(config.profiles, ",") }
  end

  if not dap.configurations.java then
    dap.configurations.java = {}
  end

  table.insert(dap.configurations.java, dap_config)

  dap.continue()
end

--- Inject Spring Boot DAP configurations (called from ftplugin/java.lua)
---@param root_dir string Root project directory
function M.setup_springboot_dap(root_dir)
  local rust = require("cumulus.util.rust")

  local config = rust.detect_springboot_app(root_dir)
  if not config or config.main_class == "" then
    return
  end

  local ok, dap = pcall(require, "dap")
  if not ok then
    return
  end

  if not dap.configurations.java then
    dap.configurations.java = {}
  end

  local dap_config = {
    type = "java",
    name = "Spring Boot: " .. config.project_name,
    request = "launch",
    mainClass = config.main_class,
    projectName = config.project_name,
    cwd = root_dir,
    console = "integratedTerminal",
  }

  table.insert(dap.configurations.java, dap_config)
end

return M
