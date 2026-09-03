-- TetraVim notification helpers
--
-- Thin, consistent wrappers around vim.notify so every subsystem emits messages
-- under one title and level vocabulary.

local M = {}

--- Standardized notification dispatcher with default title and level mapping.
---@param msg string Message text
---@param level? number vim.log.levels level (default: INFO)
---@param title? string Notification title (default: "TetraVim")
---@param opts? table Additional notification options (e.g. id, timeout)
function M.notify(msg, level, title, opts)
  level = level or vim.log.levels.INFO
  title = title or "TetraVim"
  opts = vim.tbl_extend("force", { title = title }, opts or {})
  vim.notify(msg, level, opts)
end

--- Standardized info notification.
---@param msg string Message text
---@param title? string Notification title (default: "TetraVim")
---@param opts? table Additional notification options
function M.notify_info(msg, title, opts)
  M.notify(msg, vim.log.levels.INFO, title, opts)
end

--- Standardized warning notification.
---@param msg string Message text
---@param title? string Notification title (default: "TetraVim")
---@param opts? table Additional notification options
function M.notify_warn(msg, title, opts)
  M.notify(msg, vim.log.levels.WARN, title, opts)
end

--- Standardized error notification.
---@param msg string Message text
---@param title? string Notification title (default: "TetraVim")
---@param opts? table Additional notification options
function M.notify_err(msg, title, opts)
  M.notify(msg, vim.log.levels.ERROR, title, opts)
end

return M
