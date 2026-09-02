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

# -- ${VAR} placeholders with NO default, resolved via a project-root .env
# file instead -- the documented local-dev convention this repo's own
# real-world field-testing project (ahun-members-service) actually uses.
mkdir -p "$FIXTURE_ROOT/dotenv-resolved/src/main/resources"
cat > "$FIXTURE_ROOT/dotenv-resolved/src/main/resources/application.yaml" <<'YAML'
spring:
  datasource:
    url: ${SPRING_DATASOURCE_URL}
    username: ${SPRING_DATASOURCE_USERNAME}
    password: ${SPRING_DATASOURCE_PASSWORD}
YAML
cat > "$FIXTURE_ROOT/dotenv-resolved/.env" <<'ENV'
# comment and blank line should be ignored

export SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/ahun_members_service
SPRING_DATASOURCE_USERNAME=ahun
SPRING_DATASOURCE_PASSWORD="ahun"
ENV

# -- bmad-review fix coverage: explicit empty-string YAML scalar ---------
mkdir -p "$FIXTURE_ROOT/yaml-empty-password/src/main/resources"
cat > "$FIXTURE_ROOT/yaml-empty-password/src/main/resources/application.yml" <<'YAML'
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/db
    username: postgres
    password: ""
YAML

# -- bmad-review fix coverage: .properties ':' separator AND bare
# whitespace (no ':'/'=' at all) in the SAME fixture, since Java's
# .properties format allows either.
mkdir -p "$FIXTURE_ROOT/properties-colon-sep/src/main/resources"
cat > "$FIXTURE_ROOT/properties-colon-sep/src/main/resources/application.properties" <<'PROPS'
spring.datasource.url: jdbc:postgresql://localhost:5432/db
spring.datasource.username postgres
spring.datasource.password: secret
PROPS

# -- bmad-review fix coverage: an explicitly-empty env var must resolve --
# to "", not be treated as unset (which would otherwise report it
# unresolved and skip the connection).
mkdir -p "$FIXTURE_ROOT/empty-env-var/src/main/resources"
cat > "$FIXTURE_ROOT/empty-env-var/src/main/resources/application.properties" <<'PROPS'
spring.datasource.url=jdbc:postgresql://localhost:5432/db
spring.datasource.username=postgres
spring.datasource.password=${BMAD_REVIEW_EMPTY_PW}
PROPS

# -- bmad-review fix coverage: JDBC URL whose authority already carries --
# credentials must be skipped, not double-spliced into a corrupted URL.
mkdir -p "$FIXTURE_ROOT/jdbc-has-credentials/src/main/resources"
cat > "$FIXTURE_ROOT/jdbc-has-credentials/src/main/resources/application.properties" <<'PROPS'
spring.datasource.url=jdbc:postgresql://admin:adminpw@localhost:5432/db
spring.datasource.username=postgres
spring.datasource.password=secret
PROPS

# -- bmad-review fix coverage: a nested ${OUTER:${INNER}} default must ---
# be reported unresolved, never silently corrupted.
mkdir -p "$FIXTURE_ROOT/nested-placeholder/src/main/resources"
cat > "$FIXTURE_ROOT/nested-placeholder/src/main/resources/application.properties" <<'PROPS'
spring.datasource.url=jdbc:postgresql://localhost:5432/db
spring.datasource.username=postgres
spring.datasource.password=${OUTER:${INNER}}
PROPS

# -- bmad-review fix coverage: MAX_DEPTH (8) truncation must warn --------
DEEP_PATH="$FIXTURE_ROOT/max-depth"
for i in 1 2 3 4 5 6 7 8 9 10; do
  DEEP_PATH="$DEEP_PATH/d$i"
done
mkdir -p "$DEEP_PATH"
cat > "$DEEP_PATH/application.properties" <<'PROPS'
spring.datasource.url=jdbc:postgresql://localhost:5432/db
spring.datasource.username=postgres
spring.datasource.password=secret
PROPS

# -- adversarial-review fix coverage: an UNTERMINATED placeholder (no
# closing '}') must be reported unresolved, never silently corrupted or
# thrown from.
mkdir -p "$FIXTURE_ROOT/unterminated-placeholder/src/main/resources"
cat > "$FIXTURE_ROOT/unterminated-placeholder/src/main/resources/application.properties" <<'PROPS'
spring.datasource.url=jdbc:postgresql://localhost:5432/db
spring.datasource.username=postgres
spring.datasource.password=${TRUNCATED
PROPS

# -- adversarial-review fix coverage: a NAMELESS placeholder ('${}' /
# '${:default}') must not throw indexing the environment/dotenv with a
# nil/empty key -- treated as unresolved instead.
mkdir -p "$FIXTURE_ROOT/nameless-placeholder/src/main/resources"
cat > "$FIXTURE_ROOT/nameless-placeholder/src/main/resources/application.properties" <<'PROPS'
spring.datasource.url=jdbc:postgresql://localhost:5432/db
spring.datasource.username=postgres
spring.datasource.password=${}
PROPS

mkdir -p "$FIXTURE_ROOT/nameless-placeholder-with-default/src/main/resources"
cat > "$FIXTURE_ROOT/nameless-placeholder-with-default/src/main/resources/application.properties" <<'PROPS'
spring.datasource.url=jdbc:postgresql://localhost:5432/db
spring.datasource.username=postgres
spring.datasource.password=${:default}
PROPS

# -- adversarial-review fix coverage: an explicitly-empty .env value must
# win over a placeholder's own default (not be treated as "value not
# present, fall back to default").
mkdir -p "$FIXTURE_ROOT/dotenv-empty-wins/src/main/resources"
cat > "$FIXTURE_ROOT/dotenv-empty-wins/src/main/resources/application.yaml" <<'YAML'
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/db
    username: postgres
    password: ${DOTENV_EMPTY_PW:somedefault}
YAML
cat > "$FIXTURE_ROOT/dotenv-empty-wins/.env" <<'ENV'
DOTENV_EMPTY_PW=
ENV

# -- adversarial-review fix coverage: DirChanged re-discovery -- two
# fixture roots with DIFFERENT datasources, plus a third with none at all,
# to exercise re-scanning (and clearing) vim.g.dbs on project switch.
mkdir -p "$FIXTURE_ROOT/dirchanged-a/src/main/resources"
cat > "$FIXTURE_ROOT/dirchanged-a/src/main/resources/application.properties" <<'PROPS'
spring.datasource.url=jdbc:postgresql://localhost:5432/dirchanged_a
spring.datasource.username=usera
spring.datasource.password=passa
PROPS

mkdir -p "$FIXTURE_ROOT/dirchanged-b/src/main/resources"
cat > "$FIXTURE_ROOT/dirchanged-b/src/main/resources/application.properties" <<'PROPS'
spring.datasource.url=jdbc:postgresql://localhost:5432/dirchanged_b
spring.datasource.username=userb
spring.datasource.password=passb
PROPS

mkdir -p "$FIXTURE_ROOT/dirchanged-none/src/main/resources"
cat > "$FIXTURE_ROOT/dirchanged-none/src/main/resources/application.properties" <<'PROPS'
server.port=8080
PROPS

# -- bmad-code-review 2026-09-01 fix coverage -------------------------------

# (P1) ${VAR:} -- an EXPLICIT empty default. Spring resolves this to "",
# so the connection must build (empty password), not be reported unresolved.
mkdir -p "$FIXTURE_ROOT/empty-default-placeholder/src/main/resources"
cat > "$FIXTURE_ROOT/empty-default-placeholder/src/main/resources/application.yaml" <<'YAML'
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/emptydef
    username: postgres
    password: ${CR_20260901_UNSET_PW:}
YAML

# (P3) src/test/resources config (typically an in-memory H2 test DB) must
# NOT be discovered as a real datasource; only the src/main one counts.
mkdir -p "$FIXTURE_ROOT/test-resources-excluded/src/main/resources"
mkdir -p "$FIXTURE_ROOT/test-resources-excluded/src/test/resources"
cat > "$FIXTURE_ROOT/test-resources-excluded/src/main/resources/application.properties" <<'PROPS'
spring.datasource.url=jdbc:postgresql://localhost:5432/prod_db
spring.datasource.username=produser
spring.datasource.password=prodpass
PROPS
cat > "$FIXTURE_ROOT/test-resources-excluded/src/test/resources/application.properties" <<'PROPS'
spring.datasource.url=jdbc:h2:mem:testdb
spring.datasource.username=sa
spring.datasource.password=
PROPS

# (P7a) Multi-module: two modules each with their own application.properties
# under one root -> two vim.g.dbs entries with DISTINCT, path-qualified names.
mkdir -p "$FIXTURE_ROOT/multi-module/mod-a/src/main/resources"
mkdir -p "$FIXTURE_ROOT/multi-module/mod-b/src/main/resources"
cat > "$FIXTURE_ROOT/multi-module/mod-a/src/main/resources/application.properties" <<'PROPS'
spring.datasource.url=jdbc:postgresql://localhost:5432/mod_a
spring.datasource.username=usera
spring.datasource.password=passa
PROPS
cat > "$FIXTURE_ROOT/multi-module/mod-b/src/main/resources/application.properties" <<'PROPS'
spring.datasource.url=jdbc:postgresql://localhost:5432/mod_b
spring.datasource.username=userb
spring.datasource.password=passb
PROPS

# (P7c) .properties present but with NO datasource keys, alongside a valid
# application.yml in the same root -> the YAML datasource is still found
# (the properties tier only wins when it produced a USABLE entry).
mkdir -p "$FIXTURE_ROOT/props-keyless-yml-valid/src/main/resources"
cat > "$FIXTURE_ROOT/props-keyless-yml-valid/src/main/resources/application.properties" <<'PROPS'
server.port=9090
PROPS
cat > "$FIXTURE_ROOT/props-keyless-yml-valid/src/main/resources/application.yml" <<'YML'
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/fellthrough
    username: ymluser
    password: ymlpass
YML

echo "[1/28] Static: db.lua module shape + tools-dadbod.lua wiring (supplementary only -- the real wiring behavior is exercised functionally below)..."
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

echo "[2/28] Functional: vim-dadbod-completion cmp source registration -- fresh buffer, no duplication on re-fire, and a buffer whose filetype was already sql BEFORE config() ran..."
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

echo "[3/28] Functional: nvim-treesitter ensure_installed (resolved via the plugin spec's own opts function) contains \"sql\"..."
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

echo "[4/28] Functional: init() end-to-end sets vim.g.dbs to exactly what discover_datasources() returns for the project cwd..."
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

echo "[5/28] Functional: init() surfaces a WARN notification (and leaves vim.g.dbs unset) instead of silently swallowing a discover_datasources() error..."
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

echo "[6/28] Behavioral: application.properties only -> one dadbod-style connection..."
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

echo "[7/28] Behavioral: application.yml AND application.yaml (both nested-indentation extensions Spring Boot recognizes) each produce one connection..."
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

echo "[8/28] Behavioral: both application.properties and application.yml present -> properties wins, yml ignored..."
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

echo "[9/28] Behavioral: no spring.datasource.* keys anywhere -> discover_datasources() returns an empty table, not an error (vim.g.dbs wiring itself is covered by [4/28]/[5/28], not asserted here)..."
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

echo "[10/28] Behavioral: partial/malformed datasource block (url without username/password) is skipped with a warning, no crash..."
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

echo "[11/28] Behavioral: credentials containing URL-reserved characters (@, :, /) are percent-encoded, not spliced in raw..."
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

echo "[12/28] Behavioral: \${VAR:default} placeholders resolve via the env var when set, else the literal default; JDBC-url-shaped defaults (containing their own ':' and '/') resolve intact..."
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

echo "[13/28] Behavioral: \${VAR} placeholder with no default and no matching env var is skipped with a warning naming the unresolved variable(s) (not the generic 'could not parse' message)..."
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

echo "[14/28] Behavioral: \${VAR} placeholders with no default resolve via a project-root .env file (export prefix, comments, quoted values all handled); a real env var still wins over .env..."
nvim -u init.lua --headless -c "lua
local ok, err = pcall(function()
  local db = require('cumulus.util.db')
  local root = '$FIXTURE_ROOT/dotenv-resolved'

  local dbs = db.discover_datasources(root)
  assert(#dbs == 1, 'expected exactly 1 entry via .env resolution, got ' .. #dbs)
  assert(
    dbs[1].url == 'postgresql://ahun:ahun@localhost:5432/ahun_members_service',
    '.env values did not resolve correctly, got: ' .. tostring(dbs[1].url)
  )

  -- A real process env var must still take precedence over the .env file.
  vim.env.SPRING_DATASOURCE_USERNAME = 'realenvwins'
  local dbs2 = db.discover_datasources(root)
  vim.env.SPRING_DATASOURCE_USERNAME = nil
  assert(#dbs2 == 1, 'expected exactly 1 entry, got ' .. #dbs2)
  assert(
    dbs2[1].url == 'postgresql://realenvwins:ahun@localhost:5432/ahun_members_service',
    'a real environment variable must win over the .env file, got: ' .. tostring(dbs2[1].url)
  )
end)
if not ok then
  io.stderr:write('FAIL: ' .. tostring(err) .. '\n')
  vim.cmd('cquit 1')
else
  print('OK: .env file resolves unresolved placeholders; a real env var still takes precedence over it')
end
" -c "qa!"

echo "[15/28] bmad-review fix: explicit YAML empty-string scalar (password: \"\") is NOT treated as missing..."
nvim -u init.lua --headless -c "lua
local ok, err = pcall(function()
  local db = require('cumulus.util.db')
  local dbs = db.discover_datasources('$FIXTURE_ROOT/yaml-empty-password')
  assert(#dbs == 1, 'expected exactly 1 entry, got ' .. #dbs)
  assert(
    dbs[1].url == 'postgresql://postgres:@localhost:5432/db',
    'explicit empty-string password was not preserved, got: ' .. tostring(dbs[1].url)
  )
end)
if not ok then
  io.stderr:write('FAIL: ' .. tostring(err) .. '\n')
  vim.cmd('cquit 1')
else
  print('OK: explicit YAML empty-string scalar resolved to an empty password, not skipped as incomplete')
end
" -c "qa!"

echo "[16/28] bmad-review fix: .properties accepts ':' (and bare whitespace) as the key/value separator, not just '='..."
nvim -u init.lua --headless -c "lua
local ok, err = pcall(function()
  local db = require('cumulus.util.db')
  local dbs = db.discover_datasources('$FIXTURE_ROOT/properties-colon-sep')
  assert(#dbs == 1, 'expected exactly 1 entry, got ' .. #dbs)
  assert(
    dbs[1].url == 'postgresql://postgres:secret@localhost:5432/db',
    'colon-separated .properties keys were not parsed, got: ' .. tostring(dbs[1].url)
  )
end)
if not ok then
  io.stderr:write('FAIL: ' .. tostring(err) .. '\n')
  vim.cmd('cquit 1')
else
  print('OK: .properties \":\"-separated keys parsed correctly')
end
" -c "qa!"

echo "[17/28] bmad-review fix: an env var explicitly set to the empty string resolves as empty, not as unset..."
nvim -u init.lua --headless -c "lua
local ok, err = pcall(function()
  local db = require('cumulus.util.db')
  vim.fn.setenv('BMAD_REVIEW_EMPTY_PW', '')
  local dbs = db.discover_datasources('$FIXTURE_ROOT/empty-env-var')
  vim.fn.setenv('BMAD_REVIEW_EMPTY_PW', vim.NIL)
  assert(#dbs == 1, 'expected exactly 1 entry (empty-but-set env var must resolve, not be reported unresolved), got ' .. #dbs)
  assert(
    dbs[1].url == 'postgresql://postgres:@localhost:5432/db',
    'an explicitly-empty env var was not resolved to an empty string, got: ' .. tostring(dbs[1].url)
  )
end)
if not ok then
  io.stderr:write('FAIL: ' .. tostring(err) .. '\n')
  vim.cmd('cquit 1')
else
  print('OK: an explicitly-empty env var resolved to an empty value, not treated as unset')
end
" -c "qa!"

echo "[18/28] bmad-review fix: a JDBC URL whose authority already carries credentials is skipped, never double-spliced..."
nvim -u init.lua --headless -c "lua
local ok, err = pcall(function()
  local db = require('cumulus.util.db')
  local notified = {}
  local orig = vim.notify
  vim.notify = function(msg, level) table.insert(notified, { msg = msg, level = level }) end
  local dbs = db.discover_datasources('$FIXTURE_ROOT/jdbc-has-credentials')
  vim.notify = orig
  assert(#dbs == 0, 'a JDBC URL that already carries credentials must be skipped, got ' .. #dbs .. ' entries')
  local warned = false
  for _, n in ipairs(notified) do
    if n.level == vim.log.levels.WARN then warned = true end
  end
  assert(warned, 'expected a WARN notification, got none')
end)
if not ok then
  io.stderr:write('FAIL: ' .. tostring(err) .. '\n')
  vim.cmd('cquit 1')
else
  print('OK: JDBC URL with existing credentials skipped with a warning, no double-splice')
end
" -c "qa!"

echo "[19/28] bmad-review fix: a nested \${OUTER:\${INNER}} default is reported unresolved, never silently corrupted..."
nvim -u init.lua --headless -c "lua
local ok, err = pcall(function()
  local db = require('cumulus.util.db')
  local notified = {}
  local orig = vim.notify
  vim.notify = function(msg, level) table.insert(notified, { msg = msg, level = level }) end
  local dbs = db.discover_datasources('$FIXTURE_ROOT/nested-placeholder')
  vim.notify = orig
  assert(#dbs == 0, 'a nested placeholder must be reported unresolved, not silently resolved, got ' .. #dbs .. ' entries')
  local mentions_outer = false
  for _, n in ipairs(notified) do
    if tostring(n.msg):match('OUTER') then mentions_outer = true end
  end
  assert(mentions_outer, 'expected the unresolved warning to name OUTER')
end)
if not ok then
  io.stderr:write('FAIL: ' .. tostring(err) .. '\n')
  vim.cmd('cquit 1')
else
  print('OK: nested \${OUTER:\${INNER}} placeholder reported unresolved, not corrupted')
end
" -c "qa!"

echo "[20/28] bmad-review fix: hitting the MAX_DEPTH (8) scan limit warns that coverage may be incomplete..."
nvim -u init.lua --headless -c "lua
local ok, err = pcall(function()
  local db = require('cumulus.util.db')
  local notified = {}
  local orig = vim.notify
  vim.notify = function(msg, level) table.insert(notified, { msg = msg, level = level }) end
  local dbs = db.discover_datasources('$FIXTURE_ROOT/max-depth')
  vim.notify = orig
  assert(#dbs == 0, 'the deeply-nested config file is beyond MAX_DEPTH and must not be found, got ' .. #dbs)
  local warned_depth = false
  for _, n in ipairs(notified) do
    if tostring(n.msg):match('depth limit') then warned_depth = true end
  end
  assert(warned_depth, 'expected a depth-limit-truncation WARN notification')
end)
if not ok then
  io.stderr:write('FAIL: ' .. tostring(err) .. '\n')
  vim.cmd('cquit 1')
else
  print('OK: MAX_DEPTH truncation surfaced a coverage-may-be-incomplete warning')
end
" -c "qa!"

echo "[21/28] adversarial-review fix: an unterminated \${VAR placeholder (no closing '}') is reported unresolved, never silently corrupted or thrown from..."
nvim -u init.lua --headless -c "lua
local ok, err = pcall(function()
  local db = require('cumulus.util.db')
  local notified = {}
  local orig = vim.notify
  vim.notify = function(msg, level) table.insert(notified, { msg = msg, level = level }) end
  local call_ok, dbs = pcall(db.discover_datasources, '$FIXTURE_ROOT/unterminated-placeholder')
  vim.notify = orig
  assert(call_ok, 'discover_datasources must never throw on an unterminated placeholder, got: ' .. tostring(dbs))
  assert(#dbs == 0, 'an unterminated placeholder must be reported unresolved (connection skipped), got ' .. #dbs .. ' entries')
  local saw_warn = false
  for _, n in ipairs(notified) do
    if n.level == vim.log.levels.WARN then saw_warn = true end
  end
  assert(saw_warn, 'expected a WARN notification for the unterminated placeholder')
end)
if not ok then
  io.stderr:write('FAIL: ' .. tostring(err) .. '\n')
  vim.cmd('cquit 1')
else
  print('OK: unterminated \${VAR placeholder reported unresolved, no crash')
end
" -c "qa!"

echo "[22/28] adversarial-review fix: a nameless placeholder ('\${}' / '\${:default}') does not throw indexing the environment/dotenv with a nil/empty key -- treated as unresolved instead..."
nvim -u init.lua --headless -c "lua
local ok, err = pcall(function()
  local db = require('cumulus.util.db')

  for _, fixture in ipairs({ 'nameless-placeholder', 'nameless-placeholder-with-default' }) do
    local notified = {}
    local orig = vim.notify
    vim.notify = function(msg, level) table.insert(notified, { msg = msg, level = level }) end
    local call_ok, dbs = pcall(db.discover_datasources, '$FIXTURE_ROOT/' .. fixture)
    vim.notify = orig

    assert(call_ok, fixture .. ': discover_datasources must never throw on a nameless placeholder, got: ' .. tostring(dbs))
    assert(#dbs == 0, fixture .. ': a nameless placeholder must be reported unresolved, got ' .. #dbs .. ' entries')
    local saw_warn = false
    for _, n in ipairs(notified) do
      if n.level == vim.log.levels.WARN then saw_warn = true end
    end
    assert(saw_warn, fixture .. ': expected a WARN notification for the nameless placeholder')
  end
end)
if not ok then
  io.stderr:write('FAIL: ' .. tostring(err) .. '\n')
  vim.cmd('cquit 1')
else
  print('OK: nameless \${} / \${:default} placeholders reported unresolved, no crash')
end
" -c "qa!"

echo "[23/28] adversarial-review fix: an explicitly-empty .env value wins over a placeholder's own default (not treated as \"absent, use default\")..."
nvim -u init.lua --headless -c "lua
local ok, err = pcall(function()
  local db = require('cumulus.util.db')
  local dbs = db.discover_datasources('$FIXTURE_ROOT/dotenv-empty-wins')
  assert(#dbs == 1, 'expected exactly 1 entry, got ' .. #dbs)
  assert(
    dbs[1].url == 'postgresql://postgres:@localhost:5432/db',
    'an explicitly-empty .env value must win over the placeholder default (\"somedefault\"), got: ' .. tostring(dbs[1].url)
  )
end)
if not ok then
  io.stderr:write('FAIL: ' .. tostring(err) .. '\n')
  vim.cmd('cquit 1')
else
  print('OK: explicitly-empty .env value resolved as empty, not the placeholder default')
end
" -c "qa!"

echo "[24/28] adversarial-review fix: DirChanged re-runs discovery on project switch (global-scope cd only, never window/tab-local lcd/tcd), and correctly CLEARS vim.g.dbs to nil (frozen I/O-matrix 'left unset') when switching into a project with zero datasources..."
nvim -u init.lua --headless -c "lua
local ok, err = pcall(function()
  local spec = require('cumulus.plugins.tools-dadbod')
  spec[1].config()

  local db = require('cumulus.util.db')

  -- vim.fn.chdir() is the GLOBAL-scope 'cd' -- it fires a real DirChanged
  -- with v:event.scope == 'global' (verified empirically), unlike manually
  -- firing nvim_exec_autocmds('DirChanged', ...), which does NOT populate
  -- v:event.scope and would silently no-op through the scope guard.
  vim.g.dbs = nil
  vim.fn.chdir('$FIXTURE_ROOT/dirchanged-a')
  local expected_a = db.discover_datasources(vim.fn.getcwd())
  assert(#expected_a == 1, 'fixture setup problem: dirchanged-a should yield 1 entry')
  assert(vim.deep_equal(vim.g.dbs, expected_a), 'vim.g.dbs must reflect dirchanged-a after DirChanged, got: ' .. vim.inspect(vim.g.dbs))

  vim.fn.chdir('$FIXTURE_ROOT/dirchanged-b')
  local expected_b = db.discover_datasources(vim.fn.getcwd())
  assert(#expected_b == 1, 'fixture setup problem: dirchanged-b should yield 1 entry')
  assert(vim.deep_equal(vim.g.dbs, expected_b), 'vim.g.dbs must reflect dirchanged-b after switching, got: ' .. vim.inspect(vim.g.dbs))
  assert(not vim.deep_equal(vim.g.dbs, expected_a), 'vim.g.dbs must NOT still point at the previous project (dirchanged-a)')

  vim.fn.chdir('$FIXTURE_ROOT/dirchanged-none')
  assert(
    vim.g.dbs == nil,
    'switching into a project with ZERO datasources must CLEAR vim.g.dbs to nil (frozen matrix: left unset), got: ' .. vim.inspect(vim.g.dbs)
  )

  -- A window-local ':lcd' fires DirChanged with v:event.scope == 'window'
  -- -- must NOT re-run discovery (vim.g.dbs is a single global shared
  -- across every tab/window; a local cd in one window must not overwrite
  -- what every other window sees). Deliberately the LAST step in this test
  -- -- once a window has a local directory, Neovim keeps that window's
  -- local-directory flag \"sticky\" (verified empirically: even a
  -- subsequent GLOBAL vim.fn.chdir() in that same window then fires
  -- DirChanged with scope==\"window\", not \"global\"), so this must not
  -- run before any assertion that still needs a real global-scope
  -- DirChanged in this window.
  local before_lcd = vim.g.dbs
  vim.cmd('lcd ' .. vim.fn.fnameescape('$FIXTURE_ROOT/dirchanged-a'))
  assert(
    vim.deep_equal(vim.g.dbs, before_lcd),
    'a window-local :lcd must NOT trigger re-discovery, vim.g.dbs must be unchanged, got: ' .. vim.inspect(vim.g.dbs)
  )
end)
if not ok then
  io.stderr:write('FAIL: ' .. tostring(err) .. '\n')
  vim.cmd('cquit 1')
else
  print('OK: DirChanged re-ran discovery on GLOBAL cd only (never window-local lcd), and correctly cleared vim.g.dbs for a zero-datasource project')
end
" -c "qa!"

echo "[25/28] bmad-code-review fix: \${VAR:} (explicit empty default) resolves to an empty string (Spring semantics), not 'unresolved -> skip connection'..."
nvim -u init.lua --headless -c "lua
local ok, err = pcall(function()
  local db = require('cumulus.util.db')
  local dbs = db.discover_datasources('$FIXTURE_ROOT/empty-default-placeholder')
  assert(#dbs == 1, 'expected exactly 1 connection from an empty-default placeholder, got ' .. #dbs)
  -- password resolved to '' -> percent-encoded empty -> 'user:@host'
  assert(dbs[1].url:match('://postgres:@localhost:5432/emptydef'), 'empty-default password must resolve to \"\", got url: ' .. dbs[1].url)
end)
if not ok then
  io.stderr:write('FAIL: ' .. tostring(err) .. '\n')
  vim.cmd('cquit 1')
else
  print('OK: \${VAR:} resolved to an empty string and built a connection')
end
" -c "qa!"

echo "[26/28] bmad-code-review fix: an application.properties under src/test/resources is NOT discovered as a real datasource..."
nvim -u init.lua --headless -c "lua
local ok, err = pcall(function()
  local db = require('cumulus.util.db')
  local dbs = db.discover_datasources('$FIXTURE_ROOT/test-resources-excluded')
  assert(#dbs == 1, 'expected exactly 1 connection (src/main only), got ' .. #dbs)
  assert(dbs[1].url:match('/prod_db'), 'the src/main datasource must win, got: ' .. dbs[1].url)
  assert(not dbs[1].url:match('h2:mem'), 'the src/test H2 config must never appear in vim.g.dbs')
end)
if not ok then
  io.stderr:write('FAIL: ' .. tostring(err) .. '\n')
  vim.cmd('cquit 1')
else
  print('OK: src/test/resources config excluded from discovery')
end
" -c "qa!"

echo "[27/28] bmad-code-review fix: a multi-module root yields one entry per module with DISTINCT, path-qualified names..."
nvim -u init.lua --headless -c "lua
local ok, err = pcall(function()
  local db = require('cumulus.util.db')
  local dbs = db.discover_datasources('$FIXTURE_ROOT/multi-module')
  assert(#dbs == 2, 'expected 2 connections for a 2-module project, got ' .. #dbs)
  assert(dbs[1].name ~= dbs[2].name, 'multi-module entries must have distinct names, both were: ' .. dbs[1].name)
  assert(dbs[1].name:match('%(') and dbs[2].name:match('%('), 'multi-module names must carry the relative-path suffix, got: ' .. dbs[1].name .. ' / ' .. dbs[2].name)
end)
if not ok then
  io.stderr:write('FAIL: ' .. tostring(err) .. '\n')
  vim.cmd('cquit 1')
else
  print('OK: multi-module discovery produced distinct, path-qualified connection names')
end
" -c "qa!"

echo "[28/28] bmad-code-review fix: an application.properties with NO datasource keys does NOT mask a valid application.yml in the same root (properties tier wins only on a USABLE entry); and cmp source registration MERGES rather than replaces existing sources..."
nvim -u init.lua --headless -c "lua
local ok, err = pcall(function()
  local db = require('cumulus.util.db')
  local dbs = db.discover_datasources('$FIXTURE_ROOT/props-keyless-yml-valid')
  assert(#dbs == 1, 'a keyless .properties must fall through to the valid .yml, got ' .. #dbs .. ' entries')
  assert(dbs[1].url:match('@localhost:3306/fellthrough$'), 'the YAML datasource must be the one discovered, got: ' .. dbs[1].url)

  -- cmp merge, not replace: seed a sentinel source into the GLOBAL cmp
  -- config (where the project's real LSP/buffer/snippet sources live), run
  -- the plugin's FileType wiring against a fresh sql buffer, and assert the
  -- sentinel SURVIVES alongside the newly-inserted dadbod source.
  local cmp_ok, cmp = pcall(require, 'cmp')
  if cmp_ok and type(cmp.setup) == 'table' and type(cmp.setup.global) == 'function' then
    cmp.setup.global({ sources = { { name = 'cr_sentinel_src' } } })
    vim.cmd('enew')
    local buf = vim.api.nvim_get_current_buf()
    vim.bo[buf].filetype = 'sql'
    require('cumulus.plugins.tools-dadbod')[1].config()
    local srcs = vim.api.nvim_buf_call(buf, function()
      return cmp.get_config().sources
    end)
    local names = {}
    for _, s in ipairs(srcs or {}) do
      names[s.name] = true
    end
    assert(names['vim-dadbod-completion'], 'dadbod cmp source must be registered on the sql buffer, got: ' .. vim.inspect(srcs))
    assert(names['cr_sentinel_src'], 'registration must MERGE -- the pre-existing (global) cmp source must survive, got: ' .. vim.inspect(srcs))
  end
end)
if not ok then
  io.stderr:write('FAIL: ' .. tostring(err) .. '\n')
  vim.cmd('cquit 1')
else
  print('OK: keyless .properties falls through to .yml; cmp registration merges (pre-existing source preserved)')
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
