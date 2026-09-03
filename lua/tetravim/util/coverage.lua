-- TetraVim Code Coverage Module (SPEC-1.3: Visual Test Runner & Coverage)
--
-- Native Lua JaCoCo XML coverage parser and buffer overlay manager.
-- Highlights covered, uncovered, and partially covered lines using signs
-- and diagnostics without external binary dependencies.

local M = {}

local coverage_ns = vim.api.nvim_create_namespace("tetravim_coverage")
local SIGN_GROUP = "tetravim_coverage_signs"

M.last_coverage = nil
M.last_report_path = nil
M.is_visible = false

local function ensure_signs_defined()
  local hl_ok = vim.fn.hlexists("DiagnosticSignOk") == 1 and "DiagnosticSignOk" or "DiffAdd"
  local hl_err = vim.fn.hlexists("DiagnosticSignError") == 1 and "DiagnosticSignError" or "DiffDelete"
  local hl_warn = vim.fn.hlexists("DiagnosticSignWarn") == 1 and "DiagnosticSignWarn" or "DiffChange"

  vim.fn.sign_define("TetraVimCoverageCovered", { text = "▎", texthl = hl_ok })
  vim.fn.sign_define("TetraVimCoverageUncovered", { text = "▎", texthl = hl_err })
  vim.fn.sign_define("TetraVimCoveragePartial", { text = "▎", texthl = hl_warn })
end

--- Discover standard JaCoCo XML report paths in workspace
---@param start_dir? string
---@return string|nil
function M.find_report_file(start_dir)
  start_dir = start_dir or vim.fn.getcwd()
  local candidates = {
    start_dir .. "/target/site/jacoco/jacoco.xml",
    start_dir .. "/build/reports/jacoco/test/jacocoTestReport.xml",
    start_dir .. "/target/site/jacoco-aggregate/jacoco.xml",
    start_dir .. "/build/reports/jacoco/test/jacoco.xml",
  }

  for _, path in ipairs(candidates) do
    if vim.fn.filereadable(path) == 1 then
      return path
    end
  end

  -- Fallback search for jacoco*.xml in target/build directories
  local globs = vim.fn.glob(start_dir .. "/**/jacoco*.xml", false, true)
  for _, path in ipairs(globs) do
    if vim.fn.filereadable(path) == 1 and not path:find("/%.") then
      return path
    end
  end

  return nil
end

--- Parse JaCoCo XML string content into structured coverage data
---@param content string
---@param report_path? string
---@return table|nil result, string|nil error
function M.parse_xml(content, report_path)
  if not content or content:match("^%s*$") then
    return nil, "Empty JaCoCo XML file"
  end

  if not content:match("<report") and not content:match("<package") then
    return nil, "Invalid JaCoCo XML report: missing <report> root element"
  end

  local summary = {
    lines_total = 0,
    lines_covered = 0,
    lines_missed = 0,
    lines_partial = 0,
    coverage_pct = 0,
  }
  local files = {}
  local entries = {}

  local current_pkg = ""
  local current_file_rec = nil

  local function flush_current_file()
    if current_file_rec then
      files[current_file_rec.file] = current_file_rec
      table.insert(entries, current_file_rec)
      current_file_rec = nil
    end
  end

  for tag_name, attrs in content:gmatch("<(/?[%w_:-]+)(%s*[^>]*)>") do
    if tag_name == "package" then
      flush_current_file()
      local name = attrs:match('name="([^"]*)"') or ""
      current_pkg = name:gsub("%.", "/")
    elseif tag_name == "/package" then
      flush_current_file()
      current_pkg = ""
    elseif tag_name == "sourcefile" then
      flush_current_file()
      local sf_name = attrs:match('name="([^"]*)"') or ""
      local file_rel = (current_pkg ~= "" and (current_pkg .. "/") or "") .. sf_name
      current_file_rec = {
        package = current_pkg,
        sourcefile = sf_name,
        file = file_rel,
        lines = {},
        covered_lines = {},
        missed_lines = {},
        partial_lines = {},
        lines_total = 0,
        lines_covered = 0,
        lines_missed = 0,
        lines_partial = 0,
      }
    elseif tag_name == "/sourcefile" then
      flush_current_file()
    elseif tag_name == "line" and current_file_rec then
      local nr_str = attrs:match('nr="(%d+)"')
      if nr_str then
        local nr = tonumber(nr_str)
        local ci = tonumber(attrs:match('ci="(%d+)"')) or 0
        local mi = tonumber(attrs:match('mi="(%d+)"')) or 0
        local cb = tonumber(attrs:match('cb="(%d+)"')) or 0
        local mb = tonumber(attrs:match('mb="(%d+)"')) or 0

        local status
        if ci > 0 and mi == 0 and mb == 0 then
          status = "covered"
          table.insert(current_file_rec.covered_lines, nr)
          current_file_rec.lines_covered = current_file_rec.lines_covered + 1
          summary.lines_covered = summary.lines_covered + 1
        elseif (ci > 0 or cb > 0) and (mi > 0 or mb > 0) then
          status = "partial"
          table.insert(current_file_rec.partial_lines, nr)
          current_file_rec.lines_partial = current_file_rec.lines_partial + 1
          summary.lines_partial = summary.lines_partial + 1
        elseif ci == 0 and cb == 0 and (mi > 0 or mb > 0) then
          status = "uncovered"
          table.insert(current_file_rec.missed_lines, nr)
          current_file_rec.lines_missed = current_file_rec.lines_missed + 1
          summary.lines_missed = summary.lines_missed + 1
        elseif ci > 0 then
          status = "covered"
          table.insert(current_file_rec.covered_lines, nr)
          current_file_rec.lines_covered = current_file_rec.lines_covered + 1
          summary.lines_covered = summary.lines_covered + 1
        else
          status = "uncovered"
          table.insert(current_file_rec.missed_lines, nr)
          current_file_rec.lines_missed = current_file_rec.lines_missed + 1
          summary.lines_missed = summary.lines_missed + 1
        end

        current_file_rec.lines[nr] = status
        current_file_rec.lines_total = current_file_rec.lines_total + 1
        summary.lines_total = summary.lines_total + 1
      end
    end
  end

  flush_current_file()

  if summary.lines_total > 0 then
    summary.coverage_pct = math.floor((summary.lines_covered / summary.lines_total) * 10000 + 0.5) / 100
  else
    summary.coverage_pct = 0
  end

  return {
    report_path = report_path,
    summary = summary,
    files = files,
    entries = entries,
  }
end

--- Parse JaCoCo XML report from file path
---@param file_path string
---@return table|nil result, string|nil error
function M.parse(file_path)
  if not file_path or file_path == "" or vim.fn.filereadable(file_path) == 0 then
    return nil, "Coverage file not found: " .. tostring(file_path)
  end

  local ok, lines = pcall(vim.fn.readfile, file_path)
  if not ok or not lines then
    return nil, "Failed to read coverage file: " .. tostring(file_path)
  end

  local content = table.concat(lines, "\n")
  return M.parse_xml(content, file_path)
end

--- Find coverage entry matching the given buffer
---@param bufnr number
---@return table|nil
local function find_file_rec_for_buf(bufnr)
  if not M.last_coverage or not M.last_coverage.files then
    return nil
  end

  local buf_name = vim.api.nvim_buf_get_name(bufnr):gsub("\\", "/")
  if buf_name == "" then
    return nil
  end

  for file_rel, rec in pairs(M.last_coverage.files) do
    if vim.endswith(buf_name, file_rel) or vim.endswith(buf_name, "/" .. rec.sourcefile) then
      return rec
    end
  end

  return nil
end

--- Apply coverage signs and diagnostics overlay to a specific buffer
---@param bufnr number
function M.apply_to_buffer(bufnr)
  if not M.is_visible or not M.last_coverage then
    return
  end
  if not vim.api.nvim_buf_is_valid(bufnr) then
    return
  end

  local rec = find_file_rec_for_buf(bufnr)
  if not rec then
    return
  end

  ensure_signs_defined()

  -- Clear previous coverage signs for this buffer
  vim.fn.sign_unplace(SIGN_GROUP, { buffer = bufnr })

  -- Clear previous coverage diagnostics
  vim.diagnostic.set(coverage_ns, bufnr, {})

  local sign_map = {
    covered = "TetraVimCoverageCovered",
    uncovered = "TetraVimCoverageUncovered",
    partial = "TetraVimCoveragePartial",
  }

  -- Place signs
  for nr, status in pairs(rec.lines) do
    local sign = sign_map[status]
    if sign then
      vim.fn.sign_place(0, SIGN_GROUP, sign, bufnr, { lnum = nr, priority = 10 })
    end
  end

  -- Populate diagnostics for uncovered and partial lines
  local diags = {}
  for _, nr in ipairs(rec.missed_lines) do
    table.insert(diags, {
      lnum = math.max(0, nr - 1),
      col = 0,
      message = "Uncovered line (JaCoCo)",
      severity = vim.diagnostic.severity.WARN,
      source = "JaCoCo",
    })
  end
  for _, nr in ipairs(rec.partial_lines) do
    table.insert(diags, {
      lnum = math.max(0, nr - 1),
      col = 0,
      message = "Partially covered line (JaCoCo)",
      severity = vim.diagnostic.severity.INFO,
      source = "JaCoCo",
    })
  end

  vim.diagnostic.set(coverage_ns, bufnr, diags)
end

--- Apply coverage overlays across all currently loaded valid buffers
function M.apply_to_all_buffers()
  if not M.is_visible or not M.last_coverage then
    return
  end

  ensure_signs_defined()
  for _, bufnr in ipairs(vim.api.nvim_list_bufs()) do
    if vim.api.nvim_buf_is_loaded(bufnr) and vim.api.nvim_buf_is_valid(bufnr) then
      M.apply_to_buffer(bufnr)
    end
  end
end

--- Load JaCoCo coverage report from file path (or auto-discover)
---@param xml_path? string
---@return boolean success, table|string result_or_err
function M.load(xml_path)
  if not xml_path or xml_path == "" then
    xml_path = M.find_report_file()
  end

  if not xml_path or vim.fn.filereadable(xml_path) == 0 then
    local msg = "No JaCoCo coverage report found in standard target/build dirs"
    vim.notify(msg, vim.log.levels.WARN, { title = "TetraVim Coverage" })
    return false, msg
  end

  local ok, res, err = pcall(M.parse, xml_path)
  if not ok or not res then
    local err_msg = tostring(res or err or "Parse failed")
    vim.notify("Failed to parse JaCoCo XML: " .. err_msg, vim.log.levels.ERROR, { title = "TetraVim Coverage" })
    return false, err_msg
  end

  M.last_coverage = res
  M.last_report_path = xml_path
  M.is_visible = true

  M.apply_to_all_buffers()

  local s = res.summary
  vim.notify(
    string.format(
      "JaCoCo coverage loaded: %.1f%% (%d covered, %d missed, %d partial)",
      s.coverage_pct,
      s.lines_covered,
      s.lines_missed,
      s.lines_partial
    ),
    vim.log.levels.INFO,
    { title = "TetraVim Coverage" }
  )

  return true, res
end

--- Clear coverage signs, diagnostics, and overlays from all buffers
---@param reset_data? boolean If false, retains parsed data for toggle
function M.clear(reset_data)
  vim.fn.sign_unplace(SIGN_GROUP)
  vim.diagnostic.reset(coverage_ns)

  for _, bufnr in ipairs(vim.api.nvim_list_bufs()) do
    if vim.api.nvim_buf_is_valid(bufnr) then
      vim.api.nvim_buf_clear_namespace(bufnr, coverage_ns, 0, -1)
    end
  end

  M.is_visible = false

  if reset_data ~= false then
    M.last_coverage = nil
    M.last_report_path = nil
  end
end

--- Toggle coverage overlays on/off
function M.toggle()
  if M.is_visible then
    M.clear(false)
    vim.notify("Coverage overlay hidden", vim.log.levels.INFO, { title = "TetraVim Coverage" })
  else
    if M.last_coverage then
      M.is_visible = true
      M.apply_to_all_buffers()
      vim.notify("Coverage overlay shown", vim.log.levels.INFO, { title = "TetraVim Coverage" })
    else
      M.load()
    end
  end
end

--- Display summary of loaded coverage report
---@return table|nil
function M.summary()
  if not M.last_coverage then
    vim.notify("No coverage report loaded. Use <leader>jcl to load JaCoCo coverage.", vim.log.levels.WARN, {
      title = "TetraVim Coverage",
    })
    return nil
  end

  local s = M.last_coverage.summary
  local msg = string.format(
    "Coverage Summary:\nReport: %s\nCoverage: %.1f%%\nTotal Lines: %d\nCovered: %d\nMissed: %d\nPartial: %d\nFiles: %d",
    M.last_report_path or "unknown",
    s.coverage_pct,
    s.lines_total,
    s.lines_covered,
    s.lines_missed,
    s.lines_partial,
    #M.last_coverage.entries
  )
  vim.notify(msg, vim.log.levels.INFO, { title = "TetraVim Coverage" })
  return s
end

-- Setup buffer autocommands for automatic overlay when opening files
local group = vim.api.nvim_create_augroup("tetravim_coverage_auto", { clear = true })
vim.api.nvim_create_autocmd({ "BufReadPost", "BufEnter" }, {
  group = group,
  callback = function(args)
    if M.is_visible and M.last_coverage then
      M.apply_to_buffer(args.buf)
    end
  end,
})

-- Register user commands
vim.api.nvim_create_user_command("TetraVimCoverageLoad", function(opts)
  local path = opts.args ~= "" and opts.args or nil
  M.load(path)
end, { nargs = "?", complete = "file", desc = "Load JaCoCo code coverage report" })

vim.api.nvim_create_user_command("TetraVimCoverageClear", function()
  M.clear(true)
  vim.notify("JaCoCo coverage cleared", vim.log.levels.INFO, { title = "TetraVim Coverage" })
end, { desc = "Clear JaCoCo code coverage" })

vim.api.nvim_create_user_command("TetraVimCoverageToggle", function()
  M.toggle()
end, { desc = "Toggle JaCoCo code coverage overlay" })

vim.api.nvim_create_user_command("TetraVimCoverageSummary", function()
  M.summary()
end, { desc = "Show JaCoCo code coverage summary" })

return M
