-- Cumulus Multi-Module Git Conflict Resolution & Marker Parser (SPEC-025)

local M = {}

function M.navigate_conflicts()
  local bufnr = vim.api.nvim_get_current_buf()
  local lines = vim.api.nvim_buf_get_lines(bufnr, 0, -1, false)
  local content = table.concat(lines, "\n")

  local engine = require("cumulus.util.engine")
  local blocks = engine.is_available() and engine.parse_git_conflicts(content) or nil

  if not blocks or #blocks == 0 then
    vim.notify("No Git conflict markers (<<<<<<<) found in buffer", vim.log.levels.INFO)
    return
  end

  vim.ui.select(blocks, {
    prompt = "Jump to Git Conflict:",
    format_item = function(item)
      return string.format("Line %d: %s vs %s", item.start_line, item.current_header, item.incoming_header)
    end,
  }, function(choice)
    if choice then
      vim.api.nvim_win_set_cursor(0, { choice.start_line, 0 })
    end
  end)
end

return M
