#!/usr/bin/env bash
# SPEC-1.3: Visual Test Runner & Coverage -- dedicated behavioral smoke test
#
# Follows the standard pattern using `vim.cmd('cquit 1')` on failure so
# pass/fail is trustworthy in headless mode.

set -e

echo "=== TetraVim Visual Test Runner & Coverage (SPEC-1.3) Smoke Test ==="

echo "[1/3] Static: Neotest plugin specs, coverage module, keymap declarations & which-key..."
nvim -u init.lua --headless -c "lua
local ok, err = pcall(function()
  -- 1. Verify tools-test lazy spec
  local tools_test = require('tetravim.plugins.tools-test')
  assert(type(tools_test) == 'table' and type(tools_test[1]) == 'table', 'tools-test must return a valid lazy spec table')
  local neotest_spec = tools_test[1]
  assert(neotest_spec[1] == 'nvim-neotest/neotest', 'tools-test must declare nvim-neotest/neotest')

  -- Verify dependencies
  local deps = neotest_spec.dependencies or {}
  local has_neotest_java = false
  for _, dep in ipairs(deps) do
    if dep == 'rcasia/neotest-java' then
      has_neotest_java = true
    end
  end
  assert(has_neotest_java, 'tools-test must declare rcasia/neotest-java in dependencies')

  -- Verify ft gating
  local ft_set = {}
  for _, f in ipairs(neotest_spec.ft or {}) do
    ft_set[f] = true
  end
  assert(ft_set.java and not ft_set.kotlin and not ft_set.scala, 'neotest must be ft-gated on java only (no adapter for kotlin/scala)')

  -- Verify keys in tools-test spec
  local test_keys = {}
  for _, k in ipairs(neotest_spec.keys or {}) do
    test_keys[k[1]] = k[2]
  end
  for _, lhs in ipairs({ '<leader>tr', '<leader>tf', '<leader>ts', '<leader>to', '<leader>td' }) do
    assert(type(test_keys[lhs]) == 'function', lhs .. ' missing in tools-test keys table')
  end

  -- 2. Verify coverage module API shape
  local coverage = require('tetravim.util.coverage')
  assert(type(coverage.load) == 'function', 'coverage.load missing')
  assert(type(coverage.clear) == 'function', 'coverage.clear missing')
  assert(type(coverage.toggle) == 'function', 'coverage.toggle missing')
  assert(type(coverage.summary) == 'function', 'coverage.summary missing')
  assert(type(coverage.parse) == 'function', 'coverage.parse missing')
  assert(type(coverage.parse_xml) == 'function', 'coverage.parse_xml missing')
  assert(type(coverage.find_report_file) == 'function', 'coverage.find_report_file missing')

  -- 3. Verify JVM which-key specs and registration
  local jvm = require('tetravim.util.jvm')
  local jvm_wk = jvm.whichkey_spec()
  local jvm_groups = {}
  for _, item in ipairs(jvm_wk) do
    jvm_groups[item[1]] = item.group
  end
  assert(jvm_groups['<leader>jt'] == 'test runner', '<leader>jt group missing in jvm whichkey_spec')
  assert(jvm_groups['<leader>jc'] == 'code coverage', '<leader>jc group missing in jvm whichkey_spec')

  -- 4. Verify global which-key spec includes <leader>t
  local wk_config = require('tetravim.plugins.ui-whichkey')
  local wk_opts = wk_config[1].opts(nil, { spec = {} })
  local global_groups = {}
  for _, item in ipairs(wk_opts.spec) do
    if item[1] and item.group then
      global_groups[item[1]] = item.group
    end
  end
  assert(global_groups['<leader>t'] == 'test runner', '<leader>t missing in ui-whichkey spec')

  -- 5. Verify JVM keymaps registration
  jvm.setup_keymaps()
  for _, lhs in ipairs({ '<leader>jtt', '<leader>jtc', '<leader>jta', '<leader>jts', '<leader>jto', '<leader>jtd', '<leader>jcl', '<leader>jcx', '<leader>jct', '<leader>jcs' }) do
    assert(vim.fn.maparg(lhs, 'n') ~= '', lhs .. ' keymap not registered by jvm.setup_keymaps()')
  end

  -- 6. Verify User Commands
  assert(vim.fn.exists(':TetraVimCoverageLoad') == 2, ':TetraVimCoverageLoad user command not defined')
  assert(vim.fn.exists(':TetraVimCoverageClear') == 2, ':TetraVimCoverageClear user command not defined')
  assert(vim.fn.exists(':TetraVimCoverageToggle') == 2, ':TetraVimCoverageToggle user command not defined')
  assert(vim.fn.exists(':TetraVimCoverageSummary') == 2, ':TetraVimCoverageSummary user command not defined')
end)
if not ok then
  io.stderr:write('FAIL: ' .. tostring(err) .. '\n')
  vim.cmd('cquit 1')
else
  print('OK: Neotest specs, coverage module, keymap declarations & which-key groups verified')
end
" -c "qa!"

echo "[2/3] Behavioral: JaCoCo XML parsing, buffer sign & virtual-text overlays, toggle & clear..."
nvim -u init.lua --headless -c "lua
local ok, err = pcall(function()
  local coverage = require('tetravim.util.coverage')

  local cov_ns = vim.api.nvim_create_namespace('tetravim_coverage')
  local function wait_applied()
    vim.wait(2000, function() return not coverage.is_loading end, 10)
  end
  local function vt_lines(buf)
    local out = {}
    for _, m in ipairs(vim.api.nvim_buf_get_extmarks(buf, cov_ns, 0, -1, { details = true })) do
      if m[4] and m[4].virt_text then out[m[2] + 1] = true end
    end
    return out
  end

  local sample_report = [[<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>
<!DOCTYPE report PUBLIC \"-//JACOCO//DTD Report 1.1//EN\" \"report.dtd\">
<report name=\"test-project\">
  <package name=\"com/example/billing\">
    <sourcefile name=\"InvoiceService.java\">
      <line nr=\"5\" mi=\"0\" ci=\"4\" mb=\"0\" cb=\"0\"/>
      <line nr=\"8\" mi=\"3\" ci=\"0\" mb=\"0\" cb=\"0\"/>
      <line nr=\"12\" mi=\"0\" ci=\"2\" mb=\"1\" cb=\"1\"/>
    </sourcefile>
  </package>
</report>]]

  local tmp_xml = vim.fn.tempname() .. '-jacoco.xml'
  local f = io.open(tmp_xml, 'w')
  f:write(sample_report)
  f:close()

  -- Open buffer matching the covered sourcefile
  vim.cmd('enew')
  local bufnr = vim.api.nvim_get_current_buf()
  local file_path = vim.fn.getcwd() .. '/src/main/java/com/example/billing/InvoiceService.java'
  vim.api.nvim_buf_set_name(bufnr, file_path)
  local buf_lines = {}
  for i = 1, 20 do table.insert(buf_lines, 'line ' .. i) end
  vim.api.nvim_buf_set_lines(bufnr, 0, -1, false, buf_lines)

  -- 1. Load coverage report
  local load_ok, res = coverage.load(tmp_xml)
  assert(load_ok, 'coverage.load failed on valid report')
  assert(coverage.is_visible, 'coverage.is_visible must be true after load')
  wait_applied()

  -- 2. Verify placed signs
  local placed = vim.fn.sign_getplaced(bufnr, { group = 'tetravim_coverage_signs' })
  assert(#placed > 0 and placed[1].signs, 'no signs placed in buffer')
  local sign_map = {}
  for _, s in ipairs(placed[1].signs) do
    sign_map[s.lnum] = s.name
  end
  assert(sign_map[5] == 'TetraVimCoverageCovered', 'line 5 must have TetraVimCoverageCovered sign')
  assert(sign_map[8] == 'TetraVimCoverageUncovered', 'line 8 must have TetraVimCoverageUncovered sign')
  assert(sign_map[12] == 'TetraVimCoveragePartial', 'line 12 must have TetraVimCoveragePartial sign')

  -- 3. Verify end-of-line virtual text on uncovered + partial lines
  --    (the coverage layer no longer publishes vim.diagnostic entries)
  local diags_leaked = vim.diagnostic.get(bufnr, { namespace = cov_ns })
  assert(#diags_leaked == 0, 'coverage must not publish vim.diagnostic entries, got ' .. tostring(#diags_leaked))
  local vt = vt_lines(bufnr)
  assert(vt[8], 'line 8 (uncovered) must have coverage virtual text')
  assert(vt[12], 'line 12 (partial) must have coverage virtual text')
  assert(not vt[5], 'line 5 (covered) must not have coverage virtual text')

  -- 4. Test toggle functionality
  coverage.toggle()
  assert(not coverage.is_visible, 'is_visible must be false after toggle')
  local signs_after_hide = vim.fn.sign_getplaced(bufnr, { group = 'tetravim_coverage_signs' })
  assert(#signs_after_hide[1].signs == 0, 'signs must be removed when coverage is toggled off')

  coverage.toggle()
  assert(coverage.is_visible, 'is_visible must be true after toggle on')
  wait_applied()
  local signs_after_show = vim.fn.sign_getplaced(bufnr, { group = 'tetravim_coverage_signs' })
  assert(#signs_after_show[1].signs == 3, 'signs must be restored when coverage is toggled on')

  -- 5. Test summary functionality
  local summary = coverage.summary()
  assert(type(summary) == 'table', 'summary must return a table')
  assert(summary.lines_total == 3, 'summary.lines_total must be 3')
  assert(summary.lines_covered == 1, 'summary.lines_covered must be 1')
  assert(summary.lines_missed == 1, 'summary.lines_missed must be 1')
  assert(summary.lines_partial == 1, 'summary.lines_partial must be 1')

  -- 6. Test clear functionality
  coverage.clear(true)
  assert(not coverage.is_visible, 'is_visible must be false after clear')
  assert(coverage.last_coverage == nil, 'last_coverage must be nil after clear(true)')
  local signs_after_clear = vim.fn.sign_getplaced(bufnr, { group = 'tetravim_coverage_signs' })
  assert(#signs_after_clear[1].signs == 0, 'signs must be empty after clear')
  assert(next(vt_lines(bufnr)) == nil, 'coverage virtual text must be empty after clear')

  -- 7. Test user command execution
  vim.cmd('TetraVimCoverageLoad ' .. tmp_xml)
  assert(coverage.is_visible, 'TetraVimCoverageLoad must load report')
  wait_applied()
  vim.cmd('TetraVimCoverageToggle')
  assert(not coverage.is_visible, 'TetraVimCoverageToggle must toggle')
  vim.cmd('TetraVimCoverageClear')
  assert(coverage.last_coverage == nil, 'TetraVimCoverageClear must clear')

  -- 8. Test error handling on malformed, empty, and non-existent reports
  local load_missing, err_missing = coverage.load('/non/existent/jacoco.xml')
  assert(not load_missing, 'load on missing file must return false')

  local tmp_empty = vim.fn.tempname() .. '-empty.xml'
  local f_empty = io.open(tmp_empty, 'w')
  f_empty:write('')
  f_empty:close()
  local load_empty, err_empty = coverage.load(tmp_empty)
  assert(not load_empty, 'load on empty file must return false')

  local tmp_bad = vim.fn.tempname() .. '-bad.xml'
  local f_bad = io.open(tmp_bad, 'w')
  f_bad:write('<not>a valid jacoco xml')
  f_bad:close()
  local load_bad, err_bad = coverage.load(tmp_bad)
  assert(not load_bad, 'load on malformed file must return false')

  -- Cleanup
  os.remove(tmp_xml)
  os.remove(tmp_empty)
  os.remove(tmp_bad)
  vim.api.nvim_buf_delete(bufnr, { force = true })
end)
if not ok then
  io.stderr:write('FAIL: ' .. tostring(err) .. '\n')
  vim.cmd('cquit 1')
else
  print('OK: JaCoCo XML parsing, buffer sign & virtual-text overlays, toggle, clear, and error handling verified')
end
" -c "qa!"

echo "[3/3] Behavioral: Neotest keymap handlers & safe execution..."
nvim -u init.lua --headless -c "lua
local ok, err = pcall(function()
  local tools_test = require('tetravim.plugins.tools-test')
  local neotest_spec = tools_test[1]
  local test_keys = {}
  for _, k in ipairs(neotest_spec.keys or {}) do
    test_keys[k[1]] = k[2]
  end

  -- Safe toggle summary & output panel when neotest is loaded
  test_keys['<leader>ts']()
  test_keys['<leader>to']()

  -- Run nearest test on invalid/empty buffer (must warn safely without crashing)
  vim.cmd('enew')
  test_keys['<leader>tr']()
  test_keys['<leader>tf']()

  -- Debug nearest test when DAP is available/unavailable
  test_keys['<leader>td']()
end)
if not ok then
  io.stderr:write('FAIL: ' .. tostring(err) .. '\n')
  vim.cmd('cquit 1')
else
  print('OK: Neotest keymap callbacks executed safely')
end
" -c "qa!"

echo "=========================================="
echo " ALL TEST & COVERAGE VALIDATIONS PASSED!"
echo "=========================================="
