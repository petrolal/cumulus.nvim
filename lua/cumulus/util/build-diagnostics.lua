-- Cumulus Build Output Diagnostics Integration (SPEC-004 & SPEC-016)
--
-- Parses build command output (Maven/Gradle) for errors and populates
-- Neovim's diagnostic engine (`"cumulus_build"` namespace), making errors
-- visible in line gutters, statuslines, and Trouble.nvim.

local M = {}

local ns = vim.api.nvim_create_namespace("cumulus_build")

---@class CumulusDiagnosticEntry
---@field file string
---@field line number
---@field col number|nil
---@field message string
---@field severity string

--- Parse build log content and populate diagnostics across project buffers
---@param tool "maven"|"gradle"
---@param log_content string
function M.populate_from_log(tool, log_content)
  local engine = require("cumulus.util.engine")
  if not engine.is_available() then
    vim.notify(
      "cumulus-engine binary missing: cannot parse build diagnostics. Build via 'cd engine && sbt graalvm-native-image:packageBin' or run ':CumulusInstallEngine'",
      vim.log.levels.WARN
    )
    return
  end

  local entries = engine.parse_build_log(tool, log_content)


  if not entries or #entries == 0 then
    return
  end

  -- Group diagnostics by target file buffer
  local by_file = {}
  for _, entry in ipairs(entries) do
    by_file[entry.file] = by_file[entry.file] or {}
    table.insert(by_file[entry.file], entry)
  end

  for filepath, file_entries in pairs(by_file) do
    local bufnr = vim.fn.bufnr(filepath, true)
    if bufnr ~= -1 and vim.api.nvim_buf_is_valid(bufnr) then
      vim.fn.bufload(bufnr)
      local diagnostics = {}
      for _, e in ipairs(file_entries) do
        table.insert(diagnostics, {
          lnum = math.max(0, e.line - 1),
          col = e.col and math.max(0, e.col - 1) or 0,
          message = e.message,
          severity = vim.diagnostic.severity.ERROR,
          source = "cumulus_build",
        })
      end
      vim.diagnostic.set(ns, bufnr, diagnostics)
    end
  end
end

--- Clear build diagnostics for a buffer or all buffers
---@param bufnr? number
function M.clear(bufnr)
  if bufnr then
    vim.diagnostic.clear(ns, bufnr)
  else
    for _, buf in ipairs(vim.api.nvim_list_bufs()) do
      vim.diagnostic.clear(ns, buf)
    end
  end
end

return M
