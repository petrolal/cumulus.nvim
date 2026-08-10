-- Cumulus Java/Kotlin Import Optimizer (SPEC-023)

local M = {}

function M.run()
  local bufnr = vim.api.nvim_get_current_buf()
  local lines = vim.api.nvim_buf_get_lines(bufnr, 0, -1, false)
  local content = table.concat(lines, "\n")

  local rust = require("cumulus.util.rust")
  local new_lines = rust.is_available() and rust.optimize_imports(content) or nil

  if new_lines and #new_lines > 0 then
    vim.api.nvim_buf_set_lines(bufnr, 0, -1, false, new_lines)
    vim.notify("Imports optimized successfully", vim.log.levels.INFO)
  else
    vim.notify("Import optimization unchanged", vim.log.levels.INFO)
  end
end

return M
