-- lua/tetravim/core/health.lua
-- Health JSON module for TetraVim

local M = {}

--- Return health information as JSON string
function M.json()
  local neovim_version = vim.version()
  local version_str = string.format("%d.%d.%d", neovim_version.major, neovim_version.minor, neovim_version.patch)

  -- LSP client names
  local lsp_clients = vim.lsp.get_active_clients()
  local client_names = {}
  for _, client in ipairs(lsp_clients) do
    table.insert(client_names, client.name)
  end

  -- Plugin count via lazy.nvim core config if available
  local plugin_count = 0
  local ok, lazy_cfg = pcall(require, "lazy.core.config")
  if ok and lazy_cfg.plugins then
    for _ in pairs(lazy_cfg.plugins) do
      plugin_count = plugin_count + 1
    end
  end

  -- Pending async tasks count (placeholder using joblist)
  local async_tasks = #vim.fn.joblist()

  local telemetry_enabled = vim.g.tetravim_telemetry_enabled == true

  local health_tbl = {
    neovim_version = version_str,
    lsp_clients = client_names,
    plugin_count = plugin_count,
    pending_async_tasks = async_tasks,
    telemetry_enabled = telemetry_enabled,
  }

  return vim.json.encode(health_tbl)
end

-- Register user command
vim.api.nvim_create_user_command("CheckHealthJson", function()
  vim.api.nvim_out_write(M.json() .. "\n")
end, { desc = "Echo health information as JSON" })

return M
