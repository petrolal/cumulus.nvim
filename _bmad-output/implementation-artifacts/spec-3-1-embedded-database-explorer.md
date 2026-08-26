---
title: 'Embedded Database Explorer: Credential Auto-Discovery & SQL Completion'
type: 'feature'
created: '2026-08-26'
status: 'done'
review_loop_iteration: 0
context: ['/home/petrolal/cumulus.nvim/_bmad-output/implementation-artifacts/epic-3-context.md']
baseline_commit: 'c39dd536efc5af54b0880b0b718d5ff48a3ab626'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** `lua/cumulus/plugins/tools-dadbod.lua` already wires up `vim-dadbod`/`vim-dadbod-ui` with schema browsing and grid results (`<leader>Du/Df/Da`), but two Story 3.1 acceptance criteria are still unmet: DB connections must be added manually via `:DBUIAddConnection` (no auto-discovery from Spring config), and `.sql` buffers get no syntax highlighting or live-schema completion.

**Approach:** Add a pure-Lua Spring datasource-credential extractor that populates `vim.g.dbs` before DBUI loads, and extend the existing `tools-dadbod.lua` spec with the `sql` Treesitter parser plus a per-buffer `vim-dadbod-completion` cmp source — closing both gaps without touching the already-working DBUI wiring.

## Boundaries & Constraints

**Always:**
- Discover credentials with pure Lua file/string parsing only — never shell out to the compiled Scala `cumulus-engine` binary for this feature.
- Treat discovery as stateless: read config files on demand each time `tools-dadbod.lua` initializes; never cache or persist discovered credentials to disk.
- Keep all new config (treesitter parser, cmp source, credential wiring) inside `lua/cumulus/plugins/tools-dadbod.lua`, extending `opts`/`init` the same way `lsp-toml.lua` extends `nvim-treesitter`'s `ensure_installed` — do not edit `core-treesitter.lua` or `editor-completion.lua` bodies directly.
- Leave the existing `<leader>Du/Df/Da` keymaps in `keymaps.lua` untouched.

**Ask First:**
- If any discovered credential would need to be written to disk or persisted beyond the current session — conflicts with the stateless constraint above.

**Never:**
- Do not write a general-purpose YAML parser; extract only the `spring.datasource.{url,username,password}` keys (flat dotted in `.properties`, nested block in `.yml`).
- Do not add a new `cumulus-engine` subcommand for this.
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

- `lua/cumulus/plugins/tools-dadbod.lua` -- existing dadbod/dadbod-ui spec (18 lines); `init()` currently only sets `db_ui_use_nerd_fonts`/`db_ui_show_help`. Extend `init()` to call the new discovery module and set `vim.g.dbs`; add `vim-dadbod-completion` dependency; append a second table entry extending `nvim-treesitter`'s `ensure_installed`; add a `FileType` autocmd for `sql`/`mysql`/`plsql` registering the cmp source.
- `lua/cumulus/plugins/lsp-toml.lua:3-11` -- reference pattern for extending `nvim-treesitter` `opts.ensure_installed` via `vim.list_extend` from a feature-isolated plugin file; copy this shape for adding `"sql"`.
- `lua/cumulus/plugins/editor-completion.lua` -- reference only, shows the project's `hrsh7th/nvim-cmp` base spec (do not edit; per-buffer sql source goes in `tools-dadbod.lua` instead).
- `lua/cumulus/util/ui.lua:13-42` -- `M.notify_warn`/`M.notify_err` wrappers, default title `"Cumulus"`; use for malformed-config warnings.
- `lua/cumulus/util/db.lua` (NEW) -- pure-Lua module: `M.discover_datasources(root_dir)` locates `application.properties`/`application.yml` under `root_dir` (e.g. via `vim.fs.find`), extracts `spring.datasource.*` keys per the precedence/edge-case rules above, and returns an array suitable for direct assignment to `vim.g.dbs`.
- `scripts/validate-extract.sh` -- structural model for the new smoke-test script: fixture files in a `mktemp -d`, headless `nvim --headless -u init.lua` static/functional assertions, `cquit 1` on failure (unlike the known-broken `+qa` pattern in `scripts/validate.sh`, noted in `deferred-work.md`).

## Tasks & Acceptance

**Execution:**
- [x] `lua/cumulus/util/db.lua` -- add `M.discover_datasources(root_dir)` parsing `spring.datasource.{url,username,password}` from `application.properties`/`application.yml` per the I/O matrix -- closes the credential auto-discovery AC.
- [x] `lua/cumulus/plugins/tools-dadbod.lua` -- in `init()`, call `require("cumulus.util.db").discover_datasources(vim.fn.getcwd())` and assign non-empty results to `vim.g.dbs` before DBUI loads; add `kristijanhusak/vim-dadbod-completion` to `dependencies` -- wires discovery into the existing plugin without touching keymaps.
- [x] `lua/cumulus/plugins/tools-dadbod.lua` -- append a second spec entry extending `nvim-treesitter`'s `opts.ensure_installed` with `"sql"`, matching `lsp-toml.lua`'s pattern -- gets syntax highlighting for `.sql` buffers.
- [x] `lua/cumulus/plugins/tools-dadbod.lua` -- add a `FileType` autocmd for `{"sql","mysql","plsql"}` that calls `require("cmp").setup.buffer({ sources = { { name = "vim-dadbod-completion" } } })` merged with existing buffer sources, guarded by `pcall` -- gets live-schema completion.
- [x] `scripts/validate-db.sh` (NEW) -- smoke test: static shape checks (`db.lua` exports `discover_datasources`; `tools-dadbod.lua` references `vim-dadbod-completion` and `cumulus.util.db`) plus functional fixture tests for the I/O matrix rows above; `cquit 1` on any failure.

**Acceptance Criteria:**
- Given a project with `application.properties` containing `spring.datasource.url/username/password`, when Neovim starts in that project and `:DBUI` opens, then the discovered connection appears without running `:DBUIAddConnection`.
- Given a project with no Spring datasource config, when Neovim starts, then `vim.g.dbs` is left unset and manual `:DBUIAddConnection` still works exactly as before.
- Given a `.sql` buffer with an active DB connection, when the user triggers completion mid-query, then `vim-dadbod-completion` schema-aware suggestions appear alongside existing LSP/buffer sources.
- Given `scripts/validate-db.sh` is run, then it exits 0 on success and exits non-zero (via `cquit 1`) on any assertion failure.

## Design Notes

Connection URL format follows dadbod's own convention (`driver://user:password@host:port/database`); `db.lua` should build this string directly from the extracted `spring.datasource.url` (already JDBC-style, e.g. `jdbc:postgresql://localhost:5432/mydb`) plus username/password rather than inventing a new format — strip the `jdbc:` prefix and splice in credentials.

```lua
-- lua/cumulus/util/db.lua sketch
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
- `nvim --headless -u init.lua -c "lua assert(require('cumulus.util.db').discover_datasources)" -c "qa"` -- expected: no error output, clean exit.

## Suggested Review Order

**Credential Discovery (Spring config parsing)**

- Entry point: pure-Lua discovery of `spring.datasource.*` from project config, with `.properties`-over-`.yml`/`.yaml` precedence.
  [`db.lua:245`](../../lua/cumulus/util/db.lua#L245)

- JDBC-to-dadbod URL conversion now percent-encodes credentials so `@`/`:`/`/` in a password can't corrupt the composed URL.
  [`db.lua:163`](../../lua/cumulus/util/db.lua#L163)

- RFC 3986 percent-encoder added specifically to close that URL-splicing gap found in review.
  [`db.lua:148`](../../lua/cumulus/util/db.lua#L148)

- Flat `.properties` key/value extraction, scoped to the three datasource keys only.
  [`db.lua:71`](../../lua/cumulus/util/db.lua#L71)

- Narrow indentation-stack `.yml`/`.yaml` parser -- deliberately not a general YAML parser, per spec boundary.
  [`db.lua:103`](../../lua/cumulus/util/db.lua#L103)

- Partial/malformed datasource blocks are warned and skipped rather than silently dropped or crashing.
  [`db.lua:197`](../../lua/cumulus/util/db.lua#L197)

**Plugin Wiring (tools-dadbod.lua integration)**

- `init()` populates `vim.g.dbs` from discovery before DBUI loads, and now warns (not swallows) on a real discovery failure.
  [`tools-dadbod.lua:77`](../../lua/cumulus/plugins/tools-dadbod.lua#L77)

- `register_dadbod_completion` -- shared helper covering fresh, re-fired, and already-open SQL buffers with duplicate-insertion and nil-config guards (all three were review findings).
  [`tools-dadbod.lua:13`](../../lua/cumulus/plugins/tools-dadbod.lua#L13)

- `config()` wires the `FileType` autocmd for future buffers and retroactively catches up buffers already open when the plugin loads.
  [`tools-dadbod.lua:92`](../../lua/cumulus/plugins/tools-dadbod.lua#L92)

- Second spec entry extends `nvim-treesitter`'s `ensure_installed` with `"sql"`, mirroring `lsp-toml.lua`'s established pattern.
  [`tools-dadbod.lua:118`](../../lua/cumulus/plugins/tools-dadbod.lua#L118)

**Tests**

- Smoke test rewritten to functionally exercise the plugin spec's own `init()`/`config()` (not just string-match its source), covering every I/O-matrix row plus the post-review wiring fixes.
  [`validate-db.sh:1`](../../scripts/validate-db.sh#L1)
