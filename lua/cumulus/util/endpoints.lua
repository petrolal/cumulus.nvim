-- Cumulus Spring Boot & Microservice Endpoint Extractor (SPEC-018)
--
-- Scans workspace for REST endpoints and opens selection UI / Snacks picker.

local M = {}

function M.select_endpoint()
  local rust = require("cumulus.util.rust")
  local cwd = vim.fn.getcwd()

  local eps = rust.is_available() and rust.extract_endpoints(cwd) or nil
  if not eps or #eps == 0 then
    vim.notify("No Spring Boot / JAX-RS endpoints found in project", vim.log.levels.WARN)
    return
  end

  vim.ui.select(eps, {
    prompt = "Spring Boot REST Endpoints:",
    format_item = function(item)
      return string.format("[%s] %s (%s:%d)", item.http_method, item.path, item.class_name, item.line)
    end,
  }, function(choice)
    if choice and choice.file then
      vim.cmd("edit " .. vim.fn.fnameescape(choice.file))
      vim.api.nvim_win_set_cursor(0, { choice.line, 0 })
    end
  end)
end

return M
