-- Cumulus Spring Bean Dependency Graph Generator (SPEC-021)

local M = {}

function M.select_bean()
  local engine = require("cumulus.util.engine")
  local cwd = vim.fn.getcwd()

  local beans = engine.is_available() and engine.parse_spring_beans(cwd) or nil
  if not beans or #beans == 0 then
    vim.notify("No Spring stereotypes (@Component, @Service, etc.) found", vim.log.levels.WARN)
    return
  end

  vim.ui.select(beans, {
    prompt = "Select Spring Bean:",
    format_item = function(item)
      local deps = #item.injected_deps > 0 and (" -> [" .. table.concat(item.injected_deps, ", ") .. "]") or ""
      return string.format("%s (%s)%s", item.bean_name, item.class_name, deps)
    end,
  }, function(choice)
    if choice and choice.file then
      vim.cmd("edit " .. vim.fn.fnameescape(choice.file))
      vim.api.nvim_win_set_cursor(0, { choice.line, 0 })
    end
  end)
end

return M
