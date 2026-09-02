-- TetraVim Spring Boot Debug Configuration (SPEC-006)
-- Detects Spring Boot applications and launches debugging with DAP integration.

local M = {}

--- Launch Spring Boot application with debug configuration
function M.launch_debug()
  local engine = require("tetravim.util.engine")
  local cwd = vim.fn.getcwd()

  local dap_result = engine.generate_dap_config(cwd)
  if not dap_result or not dap_result.launch then
    vim.notify("Failed to generate debug configuration", vim.log.levels.ERROR)
    return
  end

  local ok, dap = pcall(require, "dap")
  if not ok then
    vim.notify("nvim-dap is not installed", vim.log.levels.ERROR)
    return
  end

  if not dap.configurations.java then
    dap.configurations.java = {}
  end

  table.insert(dap.configurations.java, dap_result.launch)

  dap.continue()
end

--- Inject Spring Boot DAP configurations (called from ftplugin/java.lua)
---@param root_dir string Root project directory
function M.setup_springboot_dap(root_dir)
  local engine = require("tetravim.util.engine")

  local dap_result = engine.generate_dap_config(root_dir)
  if not dap_result or not dap_result.launch then
    return
  end

  local ok, dap = pcall(require, "dap")
  if not ok then
    return
  end

  if not dap.configurations.java then
    dap.configurations.java = {}
  end

  if dap_result.configurations and #dap_result.configurations > 0 then
    for _, config in ipairs(dap_result.configurations) do
      table.insert(dap.configurations.java, config)
    end
  else
    table.insert(dap.configurations.java, dap_result.launch)
    if dap_result.attach then
      table.insert(dap.configurations.java, dap_result.attach)
    end
  end
end

return M
