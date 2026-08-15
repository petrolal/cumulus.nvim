-- Cumulus High-Speed Log File Indexer (SPEC-022)

local M = {}

function M.index_current_buffer()
  local bufnr = vim.api.nvim_get_current_buf()
  local lines = vim.api.nvim_buf_get_lines(bufnr, 0, -1, false)
  local content = table.concat(lines, "\n")

  local engine = require("cumulus.util.engine")
  local entries = engine.is_available() and engine.index_log(content) or nil

  if not entries or #entries == 0 then
    vim.notify("No ERROR/WARN messages found in log buffer", vim.log.levels.INFO)
    return
  end

  vim.ui.select(entries, {
    prompt = "Jump to Log Entry:",
    format_item = function(item)
      return string.format("Line %d [%s] %s", item.line, item.level, item.message)
    end,
  }, function(choice)
    if choice then
      vim.api.nvim_win_set_cursor(0, { choice.line, 0 })
    end
  end)
end

return M
