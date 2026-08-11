-- Cumulus DAP Stacktrace Drill-Down (SPEC-010)
-- Resolves stacktrace symbols and jumps to source files in DAP REPL/console buffers

local M = {}

--- Extract stacktrace line at cursor position
---@return string|nil The stacktrace line or nil if not found
local function get_stacktrace_line_at_cursor()
  local line = vim.api.nvim_get_current_line()
  if not line or line == "" then
    return nil
  end

  -- Check if line contains stacktrace pattern (at ...)
  if line:match("at%s+") then
    return line
  end

  return nil
end

--- Drill down at current cursor position
function M.drill_down_at_line()
  local line = get_stacktrace_line_at_cursor()
  if not line then
    vim.notify("No stacktrace found at cursor", vim.log.levels.WARN)
    return
  end

  local rust = require("cumulus.util.rust")
  local cwd = vim.fn.getcwd()

  local symbol = rust.resolve_stacktrace_symbol(line, cwd)
  if not symbol then
    vim.notify("Unable to resolve stacktrace symbol", vim.log.levels.WARN)
    return
  end

  -- Open the file and jump to line
  vim.cmd("edit " .. vim.fn.fnameescape(symbol.file_path))
  vim.api.nvim_win_set_cursor(0, { symbol.line, 0 })
  vim.cmd("normal! zz")  -- Center view on cursor
end

return M
