-- Cumulus UI Utilities (moved from engine.lua)
--
-- Notification dispatchers and terminal orchestration.
-- This module is NOT part of the IPC bridge; it provides UI layer helpers.

local M = {}

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

return M
