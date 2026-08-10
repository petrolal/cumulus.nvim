-- Cumulus Flyway & Liquibase Migration Validator (SPEC-020)

local M = {}

function M.validate_migrations()
  local rust = require("cumulus.util.rust")
  local cwd = vim.fn.getcwd()
  local dir = cwd .. "/src/main/resources/db/migration"

  local issues = rust.is_available() and rust.validate_migrations(dir) or nil
  if not issues or #issues == 0 then
    vim.notify("Flyway migrations verified — 0 issues found", vim.log.levels.INFO)
    return
  end

  for _, issue in ipairs(issues) do
    local level = issue.severity == "ERROR" and vim.log.levels.ERROR or vim.log.levels.WARN
    vim.notify(string.format("[%s] %s (%s)", issue.severity, issue.message, vim.fn.fnamemodify(issue.file, ":t")), level)
  end
end

return M
