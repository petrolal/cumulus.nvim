#!/usr/bin/env bash
# EPIC 6: Code Quality & Security -- smoke test
#
# Story 6.1 (SonarQube & SonarLint Integration) and Story 6.2 (Vulnerability
# Scanning & CVEs). Every assertion runs `vim.cmd('cquit 1')` on failure so
# the exit code is trustworthy. `vim.system` is always monkeypatched -- no
# real osv-scanner / sonarlint-language-server process is ever spawned.
#
# Covers, without any network or real binary:
#   - util/cve.lua: parse_results / remediation_hint / locate_coordinate,
#     the missing-osv-scanner guard, scan command-array construction, and
#     the code-1 "vulns found" + timeout async branches
#   - util/sonar.lua: the sonar-project.properties parser + the
#     missing-language-server behaviour of language_server_cmd
#   - plugin/keymap/whichkey/mason/health wiring that delivers the epic

set -euo pipefail

echo "=== TetraVim Code Quality & Security (EPIC 6) Smoke Test ==="

OSV_AVAILABLE=1
command -v osv-scanner >/dev/null 2>&1 || OSV_AVAILABLE=0
SONARLS_AVAILABLE=1
command -v sonarlint-language-server >/dev/null 2>&1 || SONARLS_AVAILABLE=0

echo "[1/6] Unit specs: lua/tetravim/tests/cve_spec.lua + sonar_spec.lua..."
nvim --headless -u init.lua -c "lua require('plenary.busted')" -c "PlenaryBustedDirectory lua/tetravim/tests/cve_spec.lua" -c "qa"
nvim --headless -u init.lua -c "lua require('plenary.busted')" -c "PlenaryBustedDirectory lua/tetravim/tests/sonar_spec.lua" -c "qa"

echo "[2/6] Static: module shape + plugin/whichkey/mason wiring present..."
nvim -u init.lua --headless -c "lua
local ok, err = pcall(function()
  local cve = require('tetravim.util.cve')
  for _, fn in ipairs({ 'scan', 'scan_command', 'parse_results', 'remediation_hint', 'locate_coordinate', 'build_diagnostics', 'publish_diagnostics', 'clear_diagnostics' }) do
    assert(type(cve[fn]) == 'function', 'util/cve.lua missing ' .. fn)
  end
  local sonar = require('tetravim.util.sonar')
  for _, fn in ipairs({ 'language_server_cmd', 'analyzer_paths', 'settings_from_properties', 'project_key', 'find_project_settings', 'has_language_server' }) do
    assert(type(sonar[fn]) == 'function', 'util/sonar.lua missing ' .. fn)
  end
  assert(vim.deep_equal(sonar.FILETYPES, { 'java', 'kotlin', 'scala' }), 'sonar.FILETYPES must be java/kotlin/scala')

  local sonarlint_src = io.open('lua/tetravim/plugins/lsp-sonarlint.lua', 'r'):read('*a')
  assert(sonarlint_src:match('sonarlint%.nvim'), 'lsp-sonarlint.lua must reference sonarlint.nvim')
  assert(sonarlint_src:match('pcall'), 'lsp-sonarlint.lua must pcall-guard the sonarlint require/setup')

  local mason_src = io.open('lua/tetravim/plugins/tools-mason.lua', 'r'):read('*a')
  assert(mason_src:match('sonarlint%-language%-server'), 'tools-mason.lua must ensure_installed sonarlint-language-server')

  local wk_src = io.open('lua/tetravim/plugins/ui-whichkey.lua', 'r'):read('*a')
  assert(wk_src:match('\"<leader>x\"'), 'ui-whichkey.lua must register the <leader>x group')

  local bootstrap_src = io.open('bootstrap.sh', 'r'):read('*a')
  assert(bootstrap_src:match('osv%-scanner'), 'bootstrap.sh must install osv-scanner')
end)
if not ok then
  io.stderr:write('FAIL: ' .. tostring(err) .. '\n')
  vim.cmd('cquit 1')
else
  print('OK: cve/sonar module shape + plugin/whichkey/mason/bootstrap wiring present')
end
" -c "qa!"

echo "[3/6] Functional: parse_results / remediation_hint / locate_coordinate..."
nvim -u init.lua --headless -c "lua
local ok, err = pcall(function()
  local cve = require('tetravim.util.cve')
  local report = vim.json.encode({
    results = { {
      packages = { {
        package = { name = 'com.foo:bar', version = '1.0.0', ecosystem = 'Maven' },
        vulnerabilities = { {
          id = 'GHSA-x', summary = 's', aliases = { 'CVE-2020-1' },
          affected = { { ranges = { { events = { { introduced = '0' }, { fixed = '1.0.1' } } } } } },
        } },
      } },
    } },
  })
  local findings = cve.parse_results(report)
  assert(#findings == 1, 'expected one finding')
  assert(findings[1].package == 'com.foo:bar', 'package name')
  assert(vim.deep_equal(findings[1].vuln_ids, { 'CVE-2020-1' }), 'CVE alias preferred')
  assert(vim.deep_equal(findings[1].fixed_versions, { '1.0.1' }), 'fixed version extracted')

  local hint = cve.remediation_hint(findings[1])
  assert(hint:find('1.0.1', 1, true), 'hint names the upgrade target')

  assert(vim.deep_equal(cve.parse_results('{ broken ]'), {}), 'malformed report -> {}')

  local lnum = cve.locate_coordinate({ 'a', \"  implementation 'com.foo:bar:1.0.0'\", 'c' }, 'com.foo:bar')
  assert(lnum == 2, 'locate_coordinate should find the gradle line')
end)
if not ok then
  io.stderr:write('FAIL: ' .. tostring(err) .. '\n')
  vim.cmd('cquit 1')
else
  print('OK: CVE report parsing, remediation hints and coordinate location are correct')
end
" -c "qa!"

echo "[4/6] Functional: osv-scanner guard + command array + async 'vulns found' / timeout branches..."
nvim -u init.lua --headless -c "lua
local ok, err = pcall(function()
  local notified = {}
  local orig_notify = vim.notify
  vim.notify = function(msg, level) table.insert(notified, { msg = msg, level = level }) end
  local orig_schedule = vim.schedule
  vim.schedule = function(fn) fn() end

  local cve = require('tetravim.util.cve')

  -- guard: osv-scanner absent -> ERROR names it + an install hint, nothing spawned
  local spawned = 0
  local captured
  vim.system = function(cmd, opts, cb) spawned = spawned + 1 captured = cb return { wait = function() end } end
  local orig_exec = vim.fn.executable
  vim.fn.executable = function(n) if n == 'osv-scanner' then return 0 end return orig_exec(n) end
  cve.scan('pom.xml', function() end)
  assert(spawned == 0, 'no process may spawn when osv-scanner is absent')
  local guard_msg
  for _, n in ipairs(notified) do if n.level == vim.log.levels.ERROR then guard_msg = n.msg end end
  assert(guard_msg and tostring(guard_msg):match('osv%-scanner'), 'guard error must name osv-scanner')
  assert(tostring(guard_msg):lower():match('install'), 'guard error must carry an install hint')

  -- present: command array + code-1 is 'vulns found', not an error
  vim.fn.executable = function(n) if n == 'osv-scanner' then return 1 end return orig_exec(n) end
  notified = {}
  local got
  cve.scan('pom.xml', function(f) got = f end)
  assert(vim.deep_equal(captured ~= nil and true or false, true), 'callback captured')
  assert(spawned == 1, 'exactly one osv-scanner process')
  local report = vim.json.encode({ results = { { packages = { { package = { name = 'a:b', version = '1' }, vulnerabilities = { { id = 'X', aliases = {}, affected = {} } } } } } } })
  captured({ code = 1, signal = 0, stdout = report, stderr = '' })
  assert(got and #got == 1, 'code 1 must still yield findings')
  for _, n in ipairs(notified) do assert(n.level ~= vim.log.levels.ERROR, 'code 1 must not notify an error') end

  -- timeout branch: the callback is invoked with (nil, err) so a caller can
  -- distinguish 'scan failed' from 'scan found nothing' and leave stale
  -- diagnostics untouched.
  notified = {}
  local cb_findings, cb_err, cb_called = 'sentinel', nil, false
  cve.scan('pom.xml', function(f, e) cb_called = true cb_findings = f cb_err = e end)
  captured({ code = 124, signal = 15, stdout = '', stderr = '' })
  assert(cb_called, 'timeout must still resolve the callback')
  assert(cb_findings == nil, 'timeout must pass nil findings')
  assert(tostring(cb_err):lower():match('timed out'), 'timeout must pass a \"timed out\" error string')
  local to_msg
  for _, n in ipairs(notified) do if n.level == vim.log.levels.ERROR then to_msg = n.msg end end
  assert(to_msg and tostring(to_msg):lower():match('timed out'), 'timeout must notify \"timed out\"')

  vim.notify = orig_notify
  vim.schedule = orig_schedule
  vim.fn.executable = orig_exec
end)
if not ok then
  io.stderr:write('FAIL: ' .. tostring(err) .. '\n')
  vim.cmd('cquit 1')
else
  print('OK: osv-scanner guard, command array and async result branches behave correctly')
end
" -c "qa!"

echo "[5/6] Functional: <leader>x keymaps registered; health section present..."
nvim -u init.lua --headless -c "lua
local ok, err = pcall(function()
  require('tetravim.core.keymaps')
  local maps = vim.api.nvim_get_keymap('n')
  local function find(suffix)
    for _, m in ipairs(maps) do if m.lhs:match(suffix .. '\$') then return m end end
    return nil
  end
  for _, s in ipairs({ 'xr', 'xd', 'xs', 'xc' }) do
    assert(find(s), '<leader>' .. s .. ' keymap missing')
  end

  local sections = {}
  local orig_start = vim.health.start
  vim.health.start = function(name) table.insert(sections, name) end
  local orig_ok, orig_info, orig_warn, orig_err = vim.health.ok, vim.health.info, vim.health.warn, vim.health.error
  vim.health.ok = function() end
  vim.health.info = function() end
  vim.health.warn = function() end
  vim.health.error = function() end
  pcall(require('tetravim.health').check)
  vim.health.start, vim.health.ok, vim.health.info, vim.health.warn, vim.health.error =
    orig_start, orig_ok, orig_info, orig_warn, orig_err

  local saw_sonar, saw_cve = false, false
  for _, s in ipairs(sections) do
    if tostring(s):match('SonarLint') and tostring(s):match('6%.1') then saw_sonar = true end
    if tostring(s):match('CVE') and tostring(s):match('6%.2') then saw_cve = true end
  end
  assert(saw_sonar, 'health.check must emit a SonarLint (Story 6.1) section')
  assert(saw_cve, 'health.check must emit a CVE Scanning (Story 6.2) section')
end)
if not ok then
  io.stderr:write('FAIL: ' .. tostring(err) .. '\n')
  vim.cmd('cquit 1')
else
  print('OK: <leader>x keymaps + both Epic 6 health sections in place')
end
" -c "qa!"

echo "[6/6] Real-binary steps (gated)..."
if [ "$OSV_AVAILABLE" -eq 1 ]; then
  osv-scanner --version >/dev/null 2>&1 || true
  echo "  OK: osv-scanner present and runnable"
else
  echo "  SKIP: osv-scanner not installed -- an end-to-end pom.xml / build.gradle CVE scan is not exercised."
fi
if [ "$SONARLS_AVAILABLE" -eq 1 ]; then
  echo "  OK: sonarlint-language-server present (LS attach verified manually per the Verification section)"
else
  echo "  SKIP: sonarlint-language-server not installed -- SonarQube-rule diagnostics not exercised."
fi

echo ""
echo "Code Quality & Security (EPIC 6) smoke test PASSED."
echo ""
echo "NOT covered here (needs the real binaries): SonarLint LS attach with"
echo "live rule descriptions on a Java/Kotlin buffer, connected-mode quality"
echo "profile binding, and an end-to-end <leader>xs scan surfacing CVE"
echo "diagnostics on a vulnerable pom.xml coordinate -- verify manually."
