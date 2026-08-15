-- Cumulus Kubernetes Manifest Schema Validator (SPEC-024)

local M = {}

local ns = vim.api.nvim_create_namespace("cumulus_k8s_validation")

function M.validate_buffer()
  local bufnr = vim.api.nvim_get_current_buf()
  local lines = vim.api.nvim_buf_get_lines(bufnr, 0, -1, false)
  local content = table.concat(lines, "\n")

  local engine = require("cumulus.util.engine")
  local issues = engine.is_available() and engine.validate_k8s_manifest(content) or nil

  vim.diagnostic.clear(ns, bufnr)

  if not issues or #issues == 0 then
    vim.notify("Kubernetes manifest structure valid", vim.log.levels.INFO)
    return
  end

  local diags = {}
  for _, issue in ipairs(issues) do
    table.insert(diags, {
      lnum = math.max(0, issue.line - 1),
      col = issue.col and math.max(0, issue.col - 1) or 0,
      message = issue.message,
      severity = vim.diagnostic.severity.ERROR,
      source = "k8s_validator",
    })
  end

  vim.diagnostic.set(ns, bufnr, diags)
end

return M
