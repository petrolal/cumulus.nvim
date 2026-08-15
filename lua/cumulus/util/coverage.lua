-- Cumulus JaCoCo Code Coverage Module (SPEC-019)

local M = {}

local ns = vim.api.nvim_create_namespace("cumulus_coverage")

function M.load_coverage(xml_path)
  xml_path = xml_path or (vim.fn.getcwd() .. "/target/site/jacoco/jacoco.xml")
  local engine = require("cumulus.util.engine")

  local entries = engine.is_available() and engine.parse_coverage(xml_path) or nil
  if not entries or #entries == 0 then
    vim.notify("No JaCoCo coverage report found at: " .. xml_path, vim.log.levels.WARN)
    return
  end

  vim.diagnostic.clear(ns)

  for _, entry in ipairs(entries) do
    local bufnr = vim.fn.bufnr(entry.file, false)
    if bufnr ~= -1 and vim.api.nvim_buf_is_valid(bufnr) then
      local diags = {}
      for _, lnr in ipairs(entry.missed_lines) do
        table.insert(diags, {
          lnum = math.max(0, lnr - 1),
          col = 0,
          message = "Uncovered line (JaCoCo)",
          severity = vim.diagnostic.severity.WARN,
          source = "JaCoCo",
        })
      end
      vim.diagnostic.set(ns, bufnr, diags)
    end
  end

  vim.notify("JaCoCo coverage loaded successfully", vim.log.levels.INFO)
end

return M
