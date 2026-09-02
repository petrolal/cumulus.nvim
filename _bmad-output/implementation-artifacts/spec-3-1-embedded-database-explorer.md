---
title: 'Embedded Database Explorer: Credential Auto-Discovery & SQL Completion'
type: 'feature'
created: '2026-08-26'
status: 'done'
review_loop_iteration: 1
context: ['/home/petrolal/tetravim.nvim/_bmad-output/implementation-artifacts/epic-3-context.md']
baseline_commit: 'c39dd536efc5af54b0880b0b718d5ff48a3ab626'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** `lua/tetravim/plugins/tools-dadbod.lua` already wires up `vim-dadbod`/`vim-dadbod-ui` with schema browsing and grid results (`<leader>Du/Df/Da`), but two Story 3.1 acceptance criteria are still unmet: DB connections must be added manually via `:DBUIAddConnection` (no auto-discovery from Spring config), and `.sql` buffers get no syntax highlighting or live-schema completion.

**Approach:** Add a pure-Lua Spring datasource-credential extractor that populates `vim.g.dbs` before DBUI loads, and extend the existing `tools-dadbod.lua` spec with the `sql` Treesitter parser plus a per-buffer `vim-dadbod-completion` cmp source — closing both gaps without touching the already-working DBUI wiring.

## Boundaries & Constraints

**Always:**
- Discover credentials with pure Lua file/string parsing only — never shell out to the compiled Scala `tetravim-engine` binary for this feature.
- Treat discovery as stateless: read config files on demand each time `tools-dadbod.lua` initializes; never cache or persist discovered credentials to disk.
- Keep all new config (treesitter parser, cmp source, credential wiring) inside `lua/tetravim/plugins/tools-dadbod.lua`, extending `opts`/`init` the same way `lsp-toml.lua` extends `nvim-treesitter`'s `ensure_installed` — do not edit `core-treesitter.lua` or `editor-completion.lua` bodies directly.
- Leave the existing `<leader>Du/Df/Da` keymaps in `keymaps.lua` untouched.

**Ask First:**
- If any discovered credential would need to be written to disk or persisted beyond the current session — conflicts with the stateless constraint above.

**Never:**
- Do not write a general-purpose YAML parser; extract only the `spring.datasource.{url,username,password}` keys (flat dotted in `.properties`, nested block in `.yml`).
- Do not add a new `tetravim-engine` subcommand for this.
- Do not migrate the `<leader>D` DB keymaps into `<leader>o` — that namespace is the engine-backed Terraform/CloudFormation/Ansible/Docker/Helm suite in `devops.lua` with a different discovery mechanism; dadbod-ui already owns its own command surface.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Properties config only | `application.properties` under `src/main/resources` has `spring.datasource.url/username/password` | One entry appended to `vim.g.dbs`: `{ name = "<project>", url = "<jdbc-style url with creds>" }` | N/A |
| YAML config only | `application.yml` has a nested `spring: datasource:` block | Same result, parsed from indentation-based nesting | N/A |
| Both properties and yml present | Both files exist under resources | `.properties` values take precedence (matches Spring Boot's own precedence); `.yml` ignored | N/A |
| No datasource keys found | Config files exist but no `spring.datasource.*` keys | `vim.g.dbs` left unset; DBUI falls back to manual `:DBUIAddConnection` | N/A (not an error) |
| Partial/malformed datasource block | `url` present but `username`/`password` missing | Skip that file's entry entirely, continue scanning others | Warn via `ui.notify_warn`, no crash |

</frozen-after-approval>

## Code Map

- `lua/tetravim/plugins/tools-dadbod.lua` -- existing dadbod/dadbod-ui spec (18 lines); `init()` currently only sets `db_ui_use_nerd_fonts`/`db_ui_show_help`. Extend `init()` to call the new discovery module and set `vim.g.dbs`; add `vim-dadbod-completion` dependency; append a second table entry extending `nvim-treesitter`'s `ensure_installed`; add a `FileType` autocmd for `sql`/`mysql`/`plsql` registering the cmp source.
- `lua/tetravim/plugins/lsp-toml.lua:3-11` -- reference pattern for extending `nvim-treesitter` `opts.ensure_installed` via `vim.list_extend` from a feature-isolated plugin file; copy this shape for adding `"sql"`.
- `lua/tetravim/plugins/editor-completion.lua` -- reference only, shows the project's `hrsh7th/nvim-cmp` base spec (do not edit; per-buffer sql source goes in `tools-dadbod.lua` instead).
- `lua/tetravim/util/ui.lua:13-42` -- `M.notify_warn`/`M.notify_err` wrappers, default title `"TetraVim"`; use for malformed-config warnings.
- `lua/tetravim/util/db.lua` (NEW) -- pure-Lua module: `M.discover_datasources(root_dir)` locates `application.properties`/`application.yml` under `root_dir` (e.g. via `vim.fs.find`), extracts `spring.datasource.*` keys per the precedence/edge-case rules above, and returns an array suitable for direct assignment to `vim.g.dbs`.
- `scripts/validate-extract.sh` -- structural model for the new smoke-test script: fixture files in a `mktemp -d`, headless `nvim --headless -u init.lua` static/functional assertions, `cquit 1` on failure (unlike the known-broken `+qa` pattern in `scripts/validate.sh`, noted in `deferred-work.md`).

## Tasks & Acceptance

**Execution:**
- [x] `lua/tetravim/util/db.lua` -- add `M.discover_datasources(root_dir)` parsing `spring.datasource.{url,username,password}` from `application.properties`/`application.yml` per the I/O matrix -- closes the credential auto-discovery AC.
- [x] `lua/tetravim/plugins/tools-dadbod.lua` -- in `init()`, call `require("tetravim.util.db").discover_datasources(vim.fn.getcwd())` and assign non-empty results to `vim.g.dbs` before DBUI loads; add `kristijanhusak/vim-dadbod-completion` to `dependencies` -- wires discovery into the existing plugin without touching keymaps.
- [x] `lua/tetravim/plugins/tools-dadbod.lua` -- append a second spec entry extending `nvim-treesitter`'s `opts.ensure_installed` with `"sql"`, matching `lsp-toml.lua`'s pattern -- gets syntax highlighting for `.sql` buffers.
- [x] `lua/tetravim/plugins/tools-dadbod.lua` -- add a `FileType` autocmd for `{"sql","mysql","plsql"}` that calls `require("cmp").setup.buffer({ sources = { { name = "vim-dadbod-completion" } } })` merged with existing buffer sources, guarded by `pcall` -- gets live-schema completion.
- [x] `scripts/validate-db.sh` (NEW) -- smoke test: static shape checks (`db.lua` exports `discover_datasources`; `tools-dadbod.lua` references `vim-dadbod-completion` and `tetravim.util.db`) plus functional fixture tests for the I/O matrix rows above; `cquit 1` on any failure.

**Acceptance Criteria:**
- Given a project with `application.properties` containing `spring.datasource.url/username/password`, when Neovim starts in that project and `:DBUI` opens, then the discovered connection appears without running `:DBUIAddConnection`.
- Given a project with no Spring datasource config, when Neovim starts, then `vim.g.dbs` is left unset and manual `:DBUIAddConnection` still works exactly as before.
- Given a `.sql` buffer with an active DB connection, when the user triggers completion mid-query, then `vim-dadbod-completion` schema-aware suggestions appear alongside existing LSP/buffer sources.
- Given `scripts/validate-db.sh` is run, then it exits 0 on success and exits non-zero (via `cquit 1`) on any assertion failure.

## Spec Change Log

- **Finding (post-ship, field report):** real-world `application.yaml` files (e.g. this project's own `ahun-duty-service`/`ahun-members-service`) wrap every `spring.datasource.*` value in Spring's standard `${ENV_VAR}` / `${ENV_VAR:default}` placeholder syntax. `discover_datasources` had no placeholder resolution, so `jdbc_to_dadbod_url` failed to match the scheme regex against the literal `${...}` text and every such project silently got "Could not parse spring.datasource.url" instead of a working connection — defeating the story's core credential-auto-discovery AC for what turned out to be the common case, not an edge case.
- **Amendment:** added `resolve_placeholders` to `lua/tetravim/util/db.lua`, applied to `url`/`username`/`password` in `build_entry` before URL construction. Resolution mirrors Spring's own `PropertySourcesPlaceholderConfigurer`: a set, non-empty environment variable wins; otherwise the literal text after the first `:` (itself possibly containing `:`/`/`, e.g. a full JDBC URL default) is used; a placeholder with no default and no matching environment variable is left unresolved and reported by name in a dedicated warning (distinct from the generic "could not parse" message, so the user knows exactly which env var to set).
- **Known-bad state avoided:** silently skipping (or worse, misreporting as a URL-syntax problem) the single most common real-world `application.yaml` shape.
- **KEEP:** everything else in `db.lua` (pure-Lua-only, no engine, stateless discovery, percent-encoding, `.properties`-over-YAML precedence, `.yml`/`.yaml` equivalence) is unchanged and still correct — this amendment only adds a resolution step ahead of the existing URL-building logic.

- **Finding (post-ship, field report, same session):** the first amendment above still left `ahun-members-service` unresolved — its `application.yaml` placeholders have no `:default` at all, and the project's own README documents the actual local-dev convention: create a `.env` file at the project root (the standard IntelliJ-EnvFile-plugin / `direnv` local-dev pattern), which is a project-root file, not a process environment variable, so `vim.env` lookups alone can never see it.
- **Amendment:** added `load_dotenv(root_dir)` + `parse_dotenv_lines` to `db.lua`, reading `root_dir/.env` (simple `KEY=VALUE`, `#` comments, optional `export ` prefix, optional quotes) once per `discover_datasources()` call and threading it through `resolve_placeholders` as a fallback source, checked after a real process env var but before the placeholder's own inline default (matching how a loaded `.env` behaves in practice: identical to a real env var once loaded, so it should not be overridden by a config-authored default). Stateless discovery is preserved -- the `.env` file is re-read fresh every call, nothing is cached.
- **Known-bad state avoided:** a second, extremely common local-dev setup (placeholder with no default, credentials supplied via project-root `.env`) silently producing zero connections despite the project author having done everything their own README asks.
- **KEEP:** the first amendment's env-var-wins-over-default resolution is unchanged; `.env` is inserted as an additional source, not a replacement for it.

## Design Notes

Connection URL format follows dadbod's own convention (`driver://user:password@host:port/database`); `db.lua` should build this string directly from the extracted `spring.datasource.url` (already JDBC-style, e.g. `jdbc:postgresql://localhost:5432/mydb`) plus username/password rather than inventing a new format — strip the `jdbc:` prefix and splice in credentials.

```lua
-- lua/tetravim/util/db.lua sketch
local M = {}
function M.discover_datasources(root_dir)
  local dbs = {}
  -- find application.properties first (precedence), then application.yml
  -- parse spring.datasource.{url,username,password}; skip incomplete entries
  return dbs
end
return M
```

## Verification

**Commands:**
- `bash scripts/validate-db.sh` -- expected: all checks pass, exit code 0.
- `nvim --headless -u init.lua -c "lua assert(require('tetravim.util.db').discover_datasources)" -c "qa"` -- expected: no error output, clean exit.

## Suggested Review Order

**Credential Discovery (Spring config parsing)**

- Entry point: pure-Lua discovery of `spring.datasource.*` from project config, with `.properties`-over-`.yml`/`.yaml` precedence.
  [`db.lua:245`](../../lua/tetravim/util/db.lua#L245)

- JDBC-to-dadbod URL conversion now percent-encodes credentials so `@`/`:`/`/` in a password can't corrupt the composed URL.
  [`db.lua:163`](../../lua/tetravim/util/db.lua#L163)

- RFC 3986 percent-encoder added specifically to close that URL-splicing gap found in review.
  [`db.lua:148`](../../lua/tetravim/util/db.lua#L148)

- Flat `.properties` key/value extraction, scoped to the three datasource keys only.
  [`db.lua:71`](../../lua/tetravim/util/db.lua#L71)

- Narrow indentation-stack `.yml`/`.yaml` parser -- deliberately not a general YAML parser, per spec boundary.
  [`db.lua:103`](../../lua/tetravim/util/db.lua#L103)

- Partial/malformed datasource blocks are warned and skipped rather than silently dropped or crashing.
  [`db.lua:197`](../../lua/tetravim/util/db.lua#L197)

**Plugin Wiring (tools-dadbod.lua integration)**

- `init()` populates `vim.g.dbs` from discovery before DBUI loads, and now warns (not swallows) on a real discovery failure.
  [`tools-dadbod.lua:77`](../../lua/tetravim/plugins/tools-dadbod.lua#L77)

- `register_dadbod_completion` -- shared helper covering fresh, re-fired, and already-open SQL buffers with duplicate-insertion and nil-config guards (all three were review findings).
  [`tools-dadbod.lua:13`](../../lua/tetravim/plugins/tools-dadbod.lua#L13)

- `config()` wires the `FileType` autocmd for future buffers and retroactively catches up buffers already open when the plugin loads.
  [`tools-dadbod.lua:92`](../../lua/tetravim/plugins/tools-dadbod.lua#L92)

- Second spec entry extends `nvim-treesitter`'s `ensure_installed` with `"sql"`, mirroring `lsp-toml.lua`'s established pattern.
  [`tools-dadbod.lua:118`](../../lua/tetravim/plugins/tools-dadbod.lua#L118)

**Tests**

- Smoke test rewritten to functionally exercise the plugin spec's own `init()`/`config()` (not just string-match its source), covering every I/O-matrix row plus the post-review wiring fixes.
  [`validate-db.sh:1`](../../scripts/validate-db.sh#L1)

## Review Findings — bmad-code-review 2026-09-01 (loop iteration 1)

Reviewed `c39dd53..HEAD` for `db.lua`, `tools-dadbod.lua`, `validate-db.sh` (4 adversarial layers). 1 decision-needed (→ patched), 7 patch, 16 defer, 5 dismissed. The `${VAR}` / `.env` additions are treated as already-adjudicated (documented Spec Change Log amendments with rationale), not open findings.

All applied 2026-09-01 (decision: option 1). `validate-db.sh` now **28/28** (4 new stages); `stylua --check` clean.

- [x] [Review][Patch] (was Decision) `vim.g.dbs` set to `{}` on an empty scan, not "left unset" [lua/tetravim/plugins/tools-dadbod.lua] — **Resolution (human, 2026-09-01): option 1.** `discover_and_assign_datasources` now does `vim.g.dbs = #dbs > 0 and dbs or nil` — an empty scan clears `vim.g.dbs` to `nil` (the frozen matrix's "left unset"), which still clears a stale list on project switch. `validate-db.sh` step `[24/28]` flipped to assert `vim.g.dbs == nil` for a zero-datasource project. The user-authored-`vim.g.dbs` clobber concern is left deferred (a sentinel-based "only overwrite our own" guard is a design choice — recorded in `deferred-work.md`).
- [x] [Review][Patch] `${VAR:}` (explicit empty default) reported as unresolved [lua/tetravim/util/db.lua] — **Fixed:** `resolve_placeholders` now computes `has_default = inner:find(":", 1, true) ~= nil` and uses `default` (even `""`) when `has_default` and no env/`.env` match, instead of falling to the unresolved branch. New `validate-db.sh` stage `[25/28]`.
- [x] [Review][Patch] `vim.fn.readfile` unguarded in `discover_datasources` [lua/tetravim/util/db.lua] — **Fixed:** both the properties and yaml read loops now `pcall(vim.fn.readfile, path)` per file and skip on failure, matching `load_dotenv`.
- [x] [Review][Patch] `src/test/resources` configs discovered as real datasources [lua/tetravim/util/db.lua] — **Fixed:** `find_files` skips any match whose path contains `/src/test/` or `/test/resources/`. New `validate-db.sh` stage `[26/28]` (prod `application.properties` wins, the `jdbc:h2:mem:` test config never appears).
- [x] [Review][Patch] `register_dadbod_completion` unguarded `cmp.setup` / `nvim_buf_call` [lua/tetravim/plugins/tools-dadbod.lua] — **Fixed:** the guard now also requires `type(cmp.setup) == "table"` and `type(cmp.setup.buffer) == "function"`; the `nvim_buf_call` is wrapped in `pcall` (`if call_ok and applied_ok then`).
- [x] [Review][Patch] `root_dir` nil/empty not guarded [lua/tetravim/util/db.lua] — **Fixed:** `load_dotenv` and `discover_datasources` both return an empty result immediately when `type(root_dir) ~= "string" or root_dir == ""`.
- [x] [Review][Patch] YAML inline comments captured into the value [lua/tetravim/util/db.lua] — **Fixed:** `parse_yaml_lines` strips a whitespace-preceded ` #...` from an unquoted scalar before deciding whether a value is present (so `password: secret # note` → `secret`, and `password:  # todo` → null).
- [x] [Review][Patch] `validate-db.sh` coverage gaps [scripts/validate-db.sh] — **Fixed:** stage `[27/28]` (multi-module → 2 entries with distinct path-qualified names — `entry_name`'s `total > 1` branch); stage `[28/28]` (keyless `.properties` falls through to a valid `application.yml`; **and** a global cmp sentinel source survives dadbod registration — the "merge, not replace" contract). Plus stages `[25/28]`/`[26/28]` above.

- [x] [Review][Defer] Spring profile-specific config files never scanned [lua/tetravim/util/db.lua:58] — deferred; `application-{profile}.yml/.properties` (selected via `SPRING_PROFILES_ACTIVE` / `spring.profiles.active`) and multi-document YAML (`---` + `spring.config.activate.on-profile`) are common in local dev but outside the frozen scope (spec names exactly `application.properties`/`.yml`/`.yaml`).
- [x] [Review][Defer] Hikari-style datasource keys not recognized [lua/tetravim/util/db.lua:105] — deferred; `spring.datasource.hikari.jdbc-url` / `jdbcUrl` is a standard alternative; frozen scope is `spring.datasource.{url,username,password}`.
- [x] [Review][Defer] Non-`scheme://` JDBC URLs misreported [lua/tetravim/util/db.lua:364] — deferred; `jdbc:h2:mem:`, `jdbc:oracle:thin:@host:port:sid`, SQLite, `jdbc:tc:` (Testcontainers) have no `scheme://authority` shape, so `jdbc_to_dadbod_url` returns nil → misleading "Could not parse spring.datasource.url". H2 especially common in dev/test. No driver-name mapping.
- [x] [Review][Defer] `DirChanged` / `init()` run a synchronous recursive walk + file reads on the main thread [lua/tetravim/plugins/tools-dadbod.lua:104] — deferred; no debounce, cache, or project-marker fast-path; `init()` adds startup latency proportional to repo size for sessions that never touch a DB; telescope/oil/project.nvim issue global `:cd` on routine navigation → re-walk on each. Also warn-notification spam re-fires per project re-entry (no warn-once-per-path guard). The "stateless, re-read every call" design is the spec's explicit choice — enhancements only.
- [x] [Review][Defer] `.env` / discovery root assumes `cwd` is the project root [lua/tetravim/util/db.lua:226] — deferred; opening Neovim in a submodule misses the real project-root `.env`; no upward search for `.git` / `pom.xml` / `build.gradle`.
- [x] [Review][Defer] `MAX_DEPTH = 8` shallow for deep monorepos; symlinked dirs skipped; no symlink-cycle guard [lua/tetravim/util/db.lua:40] — deferred; `packages/x/apps/y/services/z/src/main/resources/…` exceeds the cap (scary truncation warning, zero connections); `vim.fs.dir` reports symlinks as `kind == "link"` so symlinked module trees / a symlinked `application.properties` are never traversed; a directory-symlink cycle is re-walked to the depth cap.
- [x] [Review][Defer] Cross-module precedence is global, not per-directory [lua/tetravim/util/db.lua:496] — deferred; `if #dbs > 0 then return dbs` after the `.properties` tier drops a module that only has `application.yml` when *any other* module produced a `.properties` entry. Precedence should be resolved per config directory.
- [x] [Review][Defer] `.properties` backslash line-continuation / `\uXXXX` escapes / CRLF not handled [lua/tetravim/util/db.lua:105] — deferred; a value continued with a trailing `\` is silently truncated to its first physical line.
- [x] [Review][Defer] YAML block scalars not handled [lua/tetravim/util/db.lua:137] — deferred; `password: |` / `url: >` stores the literal `|` / `>` and drops the real multi-line value.
- [x] [Review][Defer] treesitter `opts` silently no-ops if `ensure_installed` isn't a table, and doesn't dedupe `sql` [lua/tetravim/plugins/tools-dadbod.lua:153] — deferred; matches the spec-sanctioned `lsp-toml.lua` pattern, but breaks silently if the base config ever uses `"all"`.
- [x] [Review][Defer] No manual re-discovery / mid-session refresh / diagnostic [lua/tetravim/plugins/tools-dadbod.lua] — deferred; no `:TetraVimDbRediscover` command, no `BufWritePost application.yml` refresh, and the `:checkhealth` section reports only dadbod-completion resolvability + the `sql` parser — nothing about *why* a project produced zero connections.
- [x] [Review][Defer] User-authored `vim.g.dbs` clobbered unconditionally [lua/tetravim/plugins/tools-dadbod.lua:67] — deferred; folded into the decision above — a "only overwrite our own discovered list" guard would need a sentinel.
- [x] [Review][Defer] `discover_and_assign_datasources`: `ok=true` but non-table `dbs` leaves `vim.g.dbs` stale [lua/tetravim/plugins/tools-dadbod.lua:71] — deferred; minor — `discover_datasources` always returns a table, so unreachable in practice.
- [x] [Review][Defer] `DirChanged` under `autochdir` re-runs the scan on buffer switches [lua/tetravim/plugins/tools-dadbod.lua:132] — deferred; same debounce cluster as the DirChanged-perf item.
- [x] [Review][Defer] `parse_properties_lines` key/value separator regex quirks [lua/tetravim/util/db.lua:110] — deferred; flat dotted `spring.datasource.url:` written *inside* a YAML file is silently ignored by `parse_yaml_lines` (Spring allows it); edge interaction only.
- [x] [Review][Defer] `ft = SQL_FILETYPES` on the `vim-dadbod` spec is an unspecified lazy-load trigger [lua/tetravim/plugins/tools-dadbod.lua:90] — deferred; consistent with AC #3 (completion available as soon as a SQL buffer opens), but now loads the whole dadbod stack for every `.sql` buffer even if no `:DB*` command is ever run. Intentional tradeoff — noted.
