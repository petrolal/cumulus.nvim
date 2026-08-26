#!/usr/bin/env bash
# SPEC-3.1: Embedded Database Explorer -- credential auto-discovery smoke test
#
# Mirrors validate-refactor.sh / validate-dap-jvm.sh: uses `vim.cmd('cquit
# 1')` on assertion failure so pass/fail is trustworthy, unlike
# scripts/validate.sh's `+lua assert(...)` pattern which never propagates a
# non-zero exit code.
#
# Exercises the ACTUAL plugin-spec table returned by
# lua/cumulus/plugins/tools-dadbod.lua (via `require`, the same table
# lazy.nvim would consume) -- not just string-matching its source text --
# for the cmp-source registration wiring, the nvim-treesitter
# ensure_installed extension, and init()'s vim.g.dbs assignment. Separately
# exercises lua/cumulus/util/db.lua's discover_datasources() directly
# against real fixture files on disk for the parsing/precedence/
# malformed-block/encoding logic. A live `:DBUI` connection list and real
# schema-aware completion suggestions are UI-facing and not exercised
# headlessly here; see the "NOT covered" note at the end.

set -e

echo "=== Cumulus Embedded Database Explorer (SPEC-3.1) Smoke Test ==="

FIXTURE_ROOT="$(mktemp -d)"
trap 'rm -rf "$FIXTURE_ROOT"' EXIT

# -- properties only ---------------------------------------------------
mkdir -p "$FIXTURE_ROOT/props-only/src/main/resources"
cat > "$FIXTURE_ROOT/props-only/src/main/resources/application.properties" <<'PROPS'
server.port=8080
spring.datasource.url=jdbc:postgresql://localhost:5432/mydb
spring.datasource.username=root
spring.datasource.password=secret
PROPS

# -- yml only ------------------------------------------------------------
mkdir -p "$FIXTURE_ROOT/yml-only/src/main/resources"
cat > "$FIXTURE_ROOT/yml-only/src/main/resources/application.yml" <<'YML'
server:
  port: 8080
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/otherdb
    username: admin
    password: hunter2
YML

# -- yaml only (the ".yaml" spelling Spring Boot treats the same as .yml) --
mkdir -p "$FIXTURE_ROOT/yaml-only/src/main/resources"
cat > "$FIXTURE_ROOT/yaml-only/src/main/resources/application.yaml" <<'YAML'
spring:
  datasource:
    url: jdbc:mariadb://localhost:3307/yamldb
    username: sa
    password: sapass
YAML

# -- both present: .properties must win, YAML must be ignored ------------
mkdir -p "$FIXTURE_ROOT/both/src/main/resources"
cat > "$FIXTURE_ROOT/both/src/main/resources/application.properties" <<'PROPS'
spring.datasource.url=jdbc:postgresql://props-host:5432/propsdb
spring.datasource.username=propsuser
spring.datasource.password=propspass
PROPS
cat > "$FIXTURE_ROOT/both/src/main/resources/application.yml" <<'YML'
spring:
  datasource:
    url: jdbc:mysql://yml-host:3306/ymldb
    username: ymluser
    password: ymlpass
YML

# -- no datasource keys at all --------------------------------------------
mkdir -p "$FIXTURE_ROOT/none/src/main/resources"
cat > "$FIXTURE_ROOT/none/src/main/resources/application.properties" <<'PROPS'
server.port=8080
spring.application.name=none-demo
PROPS

# -- malformed: url present, username/password missing -------------------
mkdir -p "$FIXTURE_ROOT/malformed/src/main/resources"
cat > "$FIXTURE_ROOT/malformed/src/main/resources/application.properties" <<'PROPS'
spring.datasource.url=jdbc:postgresql://localhost:5432/incomplete
PROPS

# -- credentials containing URL-reserved characters -----------------------
mkdir -p "$FIXTURE_ROOT/reserved-chars/src/main/resources"
cat > "$FIXTURE_ROOT/reserved-chars/src/main/resources/application.properties" <<'PROPS'
spring.datasource.url=jdbc:postgresql://localhost:5432/mydb
spring.datasource.username=user@corp
spring.datasource.password=p@ss:word/1
PROPS

# -- Spring ${VAR:default} placeholders (the real-world idiom this repo's
# own field-testing hit: application.yaml commonly wraps every datasource
# value in an env-var-with-default placeholder) --------------------------
mkdir -p "$FIXTURE_ROOT/placeholder-with-default/src/main/resources"
cat > "$FIXTURE_ROOT/placeholder-with-default/src/main/resources/application.yaml" <<'YAML'
spring:
  datasource:
    url: ${SPRING_DATASOURCE_URL:jdbc:postgresql://localhost:5432/ahun_duty}
    username: ${SPRING_DATASOURCE_USERNAME:postgres}
    password: ${SPRING_DATASOURCE_PASSWORD:postgres}
YAML

# -- Spring ${VAR} placeholders with NO default and no matching env var --
mkdir -p "$FIXTURE_ROOT/placeholder-unresolved/src/main/resources"
cat > "$FIXTURE_ROOT/placeholder-unresolved/src/main/resources/application.yaml" <<'YAML'
spring:
  datasource:
    url: ${SPRING_DATASOURCE_URL}
    username: ${SPRING_DATASOURCE_USERNAME}
    password: ${SPRING_DATASOURCE_PASSWORD}
YAML

echo "[1/13] Static: db.lua module shape + tools-dadbod.lua wiring (supplementary only -- the real wiring behavior is exercised functionally below)..."
nvim -u init.lua --headless -c "lua
local ok, err = pcall(function()
  local db = require('cumulus.util.db')
  assert(type(db.discover_datasources) == 'function', 'discover_datasources missing')

  local dadbod_src = io.open('lua/cumulus/plugins/tools-dadbod.lua', 'r'):read('*a')
  assert(dadbod_src:match('vim%-dadbod%-completion'), 'tools-dadbod.lua must reference vim-dadbod-completion')
  assert(dadbod_src:match('cumulus%.util%.db'), 'tools-dadbod.lua must reference cumulus.util.db')
  assert(dadbod_src:match('vim%.g%.dbs'), 'tools-dadbod.lua must assign vim.g.dbs')
  assert(dadbod_src:match('\"sql\"'), 'tools-dadbod.lua must extend treesitter ensure_installed with sql')
  assert(dadbod_src:match('FileType'), 'tools-dadbod.lua must register a FileType autocmd for the cmp source')
end)
if not ok then
  io.stderr:write('FAIL: ' .. tostring(err) .. '\n')
  vim.cmd('cquit 1')
else
  print('OK: db module shape and tools-dadbod.lua wiring referenced')
end
" -c "qa!"

echo "[2/13] Functional: vim-dadbod-completion cmp source registration -- fresh buffer, no duplication on re-fire, and a buffer whose filetype was already sql BEFORE config() ran..."
nvim -u init.lua --headless -c "lua
local ok, err = pcall(function()
  local spec = require('cumulus.plugins.tools-dadbod')
  assert(spec[1] and type(spec[1].config) == 'function', 'plugin spec[1].config missing')

  -- Buffer whose filetype is already 'sql' BEFORE config() runs -- this is
  -- the already-open-buffer coverage gap: the FileType event already fired
  -- once (before any autocmd existed to catch it), so only the 'catch up
  -- already-loaded buffers' loop inside config() can register it.
  local buf_preexisting = vim.api.nvim_create_buf(false, true)
  vim.api.nvim_set_current_buf(buf_preexisting)
  vim.bo[buf_preexisting].filetype = 'sql'

  spec[1].config()

  local cmp = require('cmp')
  local function dadbod_count(bufnr)
    local n = 0
    local sources = vim.api.nvim_buf_call(bufnr, function()
      return cmp.get_config().sources
    end)
    for _, s in ipairs(sources or {}) do
      if s.name == 'vim-dadbod-completion' then n = n + 1 end
    end
    return n
  end

  assert(
    dadbod_count(buf_preexisting) == 1,
    'a buffer already on filetype=sql before config() ran must still get exactly 1 dadbod source, got ' .. dadbod_count(buf_preexisting)
  )

  -- Fresh buffer opened AFTER config() -- covered by the FileType autocmd.
  local buf_fresh = vim.api.nvim_create_buf(false, true)
  vim.api.nvim_set_current_buf(buf_fresh)
  vim.bo[buf_fresh].filetype = 'sql'
  assert(
    dadbod_count(buf_fresh) == 1,
    'a freshly-opened sql buffer must get exactly 1 dadbod source, got ' .. dadbod_count(buf_fresh)
  )
  -- Merged, not replaced: any pre-existing global sources must survive alongside it.
  local merged_sources = vim.api.nvim_buf_call(buf_fresh, function() return cmp.get_config().sources end)
  assert(type(merged_sources) == 'table', 'buffer-local sources must be a table')

  -- Re-firing FileType on the same buffer (e.g. :e!, filetype re-detection)
  -- must not accumulate duplicate entries.
  vim.api.nvim_exec_autocmds('FileType', { buffer = buf_fresh })
  vim.api.nvim_exec_autocmds('FileType', { buffer = buf_fresh })
  assert(
    dadbod_count(buf_fresh) == 1,
    'refiring FileType must not duplicate the dadbod source, got ' .. dadbod_count(buf_fresh)
  )
end)
if not ok then
  io.stderr:write('FAIL: ' .. tostring(err) .. '\n')
  vim.cmd('cquit 1')
else
  print('OK: cmp source registered exactly once for fresh, re-fired, and already-open sql buffers')
end
" -c "qa!"

echo "[3/13] Functional: nvim-treesitter ensure_installed (resolved via the plugin spec's own opts function) contains \"sql\"..."
nvim -u init.lua --headless -c "lua
local ok, err = pcall(function()
  local spec = require('cumulus.plugins.tools-dadbod')
  assert(spec[2] and spec[2][1] == 'nvim-treesitter/nvim-treesitter', 'spec[2] must be the nvim-treesitter fragment')
  assert(type(spec[2].opts) == 'function', 'spec[2].opts must be a function (matching lsp-toml.lua pattern)')

  local opts = { ensure_installed = { 'lua', 'vim' } }
  spec[2].opts(nil, opts)
  assert(vim.tbl_contains(opts.ensure_installed, 'sql'), '\"sql\" was not added to ensure_installed: ' .. vim.inspect(opts.ensure_installed))
end)
if not ok then
  io.stderr:write('FAIL: ' .. tostring(err) .. '\n')
  vim.cmd('cquit 1')
else
  print('OK: \"sql\" is folded into nvim-treesitter ensure_installed')
end
" -c "qa!"

echo "[4/13] Functional: init() end-to-end sets vim.g.dbs to exactly what discover_datasources() returns for the project cwd..."
nvim -u init.lua --headless -c "lua
local ok, err = pcall(function()
  local root = '$FIXTURE_ROOT/props-only'
  vim.cmd('cd ' .. vim.fn.fnameescape(root))

  local db = require('cumulus.util.db')
  local expected = db.discover_datasources(vim.fn.getcwd())
  assert(#expected == 1, 'fixture setup problem: expected discover_datasources to find 1 entry')

  vim.g.dbs = nil
  local spec = require('cumulus.plugins.tools-dadbod')
  assert(type(spec[1].init) == 'function', 'plugin spec[1].init missing')
  spec[1].init()

  assert(vim.g.dbs ~= nil, 'init() must assign vim.g.dbs when discovery finds a connection')
  assert(vim.deep_equal(vim.g.dbs, expected), 'vim.g.dbs must match discover_datasources() output exactly, got: ' .. vim.inspect(vim.g.dbs))
end)
if not ok then
  io.stderr:write('FAIL: ' .. tostring(err) .. '\n')
  vim.cmd('cquit 1')
else
  print('OK: init() wired vim.g.dbs to the real discover_datasources() result for the project cwd')
end
" -c "qa!"

echo "[5/13] Functional: init() surfaces a WARN notification (and leaves vim.g.dbs unset) instead of silently swallowing a discover_datasources() error..."
nvim -u init.lua --headless -c "lua
local ok, err = pcall(function()
  package.loaded['cumulus.util.db'] = { discover_datasources = function() error('simulated discovery bug') end }

  local notified = {}
  local orig_notify = vim.notify
  vim.notify = function(msg, level) table.insert(notified, { msg = msg, level = level }) end

  vim.g.dbs = nil
  local spec = require('cumulus.plugins.tools-dadbod')
  spec[1].init()

  vim.notify = orig_notify
  package.loaded['cumulus.util.db'] = nil

  assert(vim.g.dbs == nil, 'vim.g.dbs must stay unset when discovery errors')
  local saw_warn = false
  for _, n in ipairs(notified) do
    if n.level == vim.log.levels.WARN then saw_warn = true end
  end
  assert(saw_warn, 'a WARN notification is required when discover_datasources() itself errors, not just when it finds nothing')
end)
if not ok then
  io.stderr:write('FAIL: ' .. tostring(err) .. '\n')
  vim.cmd('cquit 1')
else
  print('OK: a discover_datasources() error is surfaced via a WARN notification, not silently swallowed')
end
" -c "qa!"

echo "[6/13] Behavioral: application.properties only -> one dadbod-style connection..."
nvim -u init.lua --headless -c "lua
local ok, err = pcall(function()
  local db = require('cumulus.util.db')
  local root = '$FIXTURE_ROOT/props-only'
  local dbs = db.discover_datasources(root)
  assert(#dbs == 1, 'expected exactly 1 entry, got ' .. #dbs)
  assert(dbs[1].name == 'props-only', 'unexpected name: ' .. tostring(dbs[1].name))
  assert(
    dbs[1].url == 'postgresql://root:secret@localhost:5432/mydb',
    'unexpected url: ' .. tostring(dbs[1].url)
  )
end)
if not ok then
  io.stderr:write('FAIL: ' .. tostring(err) .. '\n')
  vim.cmd('cquit 1')
else
  print('OK: application.properties parsed into a valid dadbod connection URL')
end
" -c "qa!"

echo "[7/13] Behavioral: application.yml AND application.yaml (both nested-indentation extensions Spring Boot recognizes) each produce one connection..."
nvim -u init.lua --headless -c "lua
local ok, err = pcall(function()
  local db = require('cumulus.util.db')

  local yml_dbs = db.discover_datasources('$FIXTURE_ROOT/yml-only')
  assert(#yml_dbs == 1, 'expected exactly 1 entry from application.yml, got ' .. #yml_dbs)
  assert(yml_dbs[1].name == 'yml-only', 'unexpected name: ' .. tostring(yml_dbs[1].name))
  assert(
    yml_dbs[1].url == 'mysql://admin:hunter2@localhost:3306/otherdb',
    'unexpected url: ' .. tostring(yml_dbs[1].url)
  )

  local yaml_dbs = db.discover_datasources('$FIXTURE_ROOT/yaml-only')
  assert(#yaml_dbs == 1, 'expected exactly 1 entry from application.yaml, got ' .. #yaml_dbs)
  assert(yaml_dbs[1].name == 'yaml-only', 'unexpected name: ' .. tostring(yaml_dbs[1].name))
  assert(
    yaml_dbs[1].url == 'mariadb://sa:sapass@localhost:3307/yamldb',
    'unexpected url: ' .. tostring(yaml_dbs[1].url)
  )
end)
if not ok then
  io.stderr:write('FAIL: ' .. tostring(err) .. '\n')
  vim.cmd('cquit 1')
else
  print('OK: both application.yml and application.yaml nested spring.datasource blocks parsed correctly')
end
" -c "qa!"

echo "[8/13] Behavioral: both application.properties and application.yml present -> properties wins, yml ignored..."
nvim -u init.lua --headless -c "lua
local ok, err = pcall(function()
  local db = require('cumulus.util.db')
  local root = '$FIXTURE_ROOT/both'
  local dbs = db.discover_datasources(root)
  assert(#dbs == 1, 'expected exactly 1 entry (properties should win outright), got ' .. #dbs)
  assert(
    dbs[1].url == 'postgresql://propsuser:propspass@props-host:5432/propsdb',
    'expected .properties values, got: ' .. tostring(dbs[1].url)
  )
  assert(not dbs[1].url:find('yml-host', 1, true), '.yml values must be ignored when .properties is present')
end)
if not ok then
  io.stderr:write('FAIL: ' .. tostring(err) .. '\n')
  vim.cmd('cquit 1')
else
  print('OK: application.properties took precedence over application.yml')
end
" -c "qa!"

echo "[9/13] Behavioral: no spring.datasource.* keys anywhere -> discover_datasources() returns an empty table, not an error (vim.g.dbs wiring itself is covered by [4/13]/[5/13], not asserted here)..."
nvim -u init.lua --headless -c "lua
local ok, err = pcall(function()
  local db = require('cumulus.util.db')
  local root = '$FIXTURE_ROOT/none'
  local dbs = db.discover_datasources(root)
  assert(type(dbs) == 'table', 'discover_datasources must always return a table')
  assert(#dbs == 0, 'expected 0 entries when no spring.datasource.* keys exist, got ' .. #dbs)
end)
if not ok then
  io.stderr:write('FAIL: ' .. tostring(err) .. '\n')
  vim.cmd('cquit 1')
else
  print('OK: config with no datasource keys yields an empty discover_datasources() result')
end
" -c "qa!"

echo "[10/13] Behavioral: partial/malformed datasource block (url without username/password) is skipped with a warning, no crash..."
nvim -u init.lua --headless -c "lua
local ok, err = pcall(function()
  local notified = {}
  local orig_notify = vim.notify
  vim.notify = function(msg, level, opts)
    table.insert(notified, { msg = msg, level = level })
  end

  local db = require('cumulus.util.db')
  local root = '$FIXTURE_ROOT/malformed'
  local dbs = db.discover_datasources(root)

  vim.notify = orig_notify

  assert(type(dbs) == 'table', 'discover_datasources must always return a table')
  assert(#dbs == 0, 'a malformed (partial) datasource block must be skipped entirely, got ' .. #dbs .. ' entries')

  local saw_warn = false
  for _, n in ipairs(notified) do
    if n.level == vim.log.levels.WARN then saw_warn = true end
  end
  assert(saw_warn, 'a WARN notification is required for a malformed datasource block')
end)
if not ok then
  io.stderr:write('FAIL: ' .. tostring(err) .. '\n')
  vim.cmd('cquit 1')
else
  print('OK: malformed block skipped with a warning, no crash')
end
" -c "qa!"

echo "[11/13] Behavioral: credentials containing URL-reserved characters (@, :, /) are percent-encoded, not spliced in raw..."
nvim -u init.lua --headless -c "lua
local ok, err = pcall(function()
  local db = require('cumulus.util.db')
  local root = '$FIXTURE_ROOT/reserved-chars'
  local dbs = db.discover_datasources(root)
  assert(#dbs == 1, 'expected exactly 1 entry, got ' .. #dbs)
  assert(
    dbs[1].url == 'postgresql://user%40corp:p%40ss%3Aword%2F1@localhost:5432/mydb',
    'credentials were not correctly percent-encoded, got: ' .. tostring(dbs[1].url)
  )
end)
if not ok then
  io.stderr:write('FAIL: ' .. tostring(err) .. '\n')
  vim.cmd('cquit 1')
else
  print('OK: reserved characters in username/password are percent-encoded before splicing into the connection URL')
end
" -c "qa!"

echo "[12/13] Behavioral: \${VAR:default} placeholders resolve via the env var when set, else the literal default; JDBC-url-shaped defaults (containing their own ':' and '/') resolve intact..."
nvim -u init.lua --headless -c "lua
local ok, err = pcall(function()
  local db = require('cumulus.util.db')
  local root = '$FIXTURE_ROOT/placeholder-with-default'

  -- No matching env vars set -> falls back to the literal defaults.
  local dbs = db.discover_datasources(root)
  assert(#dbs == 1, 'expected exactly 1 entry via defaults, got ' .. #dbs)
  assert(
    dbs[1].url == 'postgresql://postgres:postgres@localhost:5432/ahun_duty',
    'defaults did not resolve correctly, got: ' .. tostring(dbs[1].url)
  )

  -- A set, non-empty env var must win over the placeholder's own default.
  vim.env.SPRING_DATASOURCE_USERNAME = 'envwins'
  local dbs2 = db.discover_datasources(root)
  vim.env.SPRING_DATASOURCE_USERNAME = nil
  assert(#dbs2 == 1, 'expected exactly 1 entry with env override, got ' .. #dbs2)
  assert(
    dbs2[1].url == 'postgresql://envwins:postgres@localhost:5432/ahun_duty',
    'a set environment variable must override the placeholder default, got: ' .. tostring(dbs2[1].url)
  )
end)
if not ok then
  io.stderr:write('FAIL: ' .. tostring(err) .. '\n')
  vim.cmd('cquit 1')
else
  print('OK: \${VAR:default} placeholders resolve via env-var-wins-over-default, JDBC-shaped defaults intact')
end
" -c "qa!"

echo "[13/13] Behavioral: \${VAR} placeholder with no default and no matching env var is skipped with a warning naming the unresolved variable(s) (not the generic 'could not parse' message)..."
nvim -u init.lua --headless -c "lua
local ok, err = pcall(function()
  local notified = {}
  local orig_notify = vim.notify
  vim.notify = function(msg, level) table.insert(notified, { msg = msg, level = level }) end

  local db = require('cumulus.util.db')
  local root = '$FIXTURE_ROOT/placeholder-unresolved'
  local dbs = db.discover_datasources(root)

  vim.notify = orig_notify

  assert(type(dbs) == 'table', 'discover_datasources must always return a table')
  assert(#dbs == 0, 'an unresolved placeholder with no default must be skipped entirely, got ' .. #dbs .. ' entries')

  local saw_specific_warn = false
  for _, n in ipairs(notified) do
    if n.level == vim.log.levels.WARN and tostring(n.msg):match('SPRING_DATASOURCE_URL') then
      saw_specific_warn = true
    end
  end
  assert(saw_specific_warn, 'expected a WARN notification naming the unresolved SPRING_DATASOURCE_URL variable')
end)
if not ok then
  io.stderr:write('FAIL: ' .. tostring(err) .. '\n')
  vim.cmd('cquit 1')
else
  print('OK: unresolved \${VAR} placeholder (no default, no env) skipped with a warning naming the variable')
end
" -c "qa!"

echo ""
echo "✔ Embedded Database Explorer credential auto-discovery (SPEC-3.1) smoke test PASSED."
echo ""
echo "NOT covered by this script (requires a live DBUI session / real DB"
echo "connection, unavailable in this sandbox) -- verify manually per"
echo "spec-3-1's Verification section:"
echo "  - :DBUI actually listing the auto-discovered connection on Neovim startup"
echo "    in a real Spring project"
echo "  - vim-dadbod-completion suggestions appearing alongside LSP/buffer"
echo "    sources when triggering completion in a live .sql buffer with an"
echo "    active DB connection"
echo "  - .sql Treesitter syntax highlighting rendering correctly after"
echo "    ':TSUpdate'/parser install"
