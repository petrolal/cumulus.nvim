---
title: 'HTTP Client & REST API Explorer'
type: 'feature'
created: '2026-08-26'
status: 'done'
review_loop_iteration: 0
context: ['/home/petrolal/cumulus.nvim/_bmad-output/implementation-artifacts/epic-3-context.md']
baseline_commit: '61633a32aec732c87972512e6a9a8ba2070d6531'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** There is no way to test REST endpoints from inside Neovim today — no `.http` file execution, no OpenAPI-to-request-template generation, no JSON response filtering — so developers still reach for Postman, undercutting the "complete IntelliJ IDEA replacement" goal this epic targets.

**Approach:** Wire up `kulala.nvim` (an actively-maintained, IntelliJ-`.http`-syntax-compatible REST client) as the core request-execution/response-display engine — the same "established plugin does the heavy lifting" shape Story 3.1 used for `vim-dadbod`/`vim-dadbod-ui` — and add two narrow custom Lua pieces the plugin doesn't provide: JSON-OpenAPI-spec-to-`.http`-template generation, and `jq`-based response filtering.

## Boundaries & Constraints

**Always:**
- Use `kulala.nvim` as the `.http` execution/response engine; do not hand-write a custom HTTP request executor.
- `jq` filtering shells out to a real `jq` binary via `vim.system` (async, per `profiling.lua`'s established pattern) — never reimplement jq logic in Lua.
- OpenAPI template generation supports JSON specs only, parsed via `vim.json.decode`; never write a YAML parser for this.
- Response/filtered output renders in a persistent split, never a floating window, per this epic's established UX pattern.
- Follow `tools-dadbod.lua`'s plugin-isolation shape: new feature lives in its own `lua/cumulus/plugins/tools-http.lua` file with `init`/`config` split.
- New keymaps live under a dedicated `<leader>H` group — never under `<leader>o` (reserved for the engine-backed Terraform/CFN/Ansible/Docker/Helm suite, per the precedent set in Story 3.1).

**Ask First:**
- If `kulala.nvim`'s response-display config has no documented option to force a persistent split (never floating), stop and ask before writing custom display code to work around it — that could expand scope well beyond this story.

**Never:**
- Do not add OpenAPI YAML support in this story (JSON only; warn and skip on `.yaml`/`.yml`).
- Do not register any new keymap under `<leader>o`.
- Do not implement a bespoke HTTP client instead of using `kulala.nvim`.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Valid JSON OpenAPI spec | `spec.json` with 2 paths, 3 operations (GET/POST) | Generates `.http` text with one request block per operation (method, url, headers) | N/A |
| OpenAPI spec is YAML | `spec.yaml` | Nothing generated; explicit "JSON only, v1 scope" warning | Warn via `ui.notify_warn` |
| OpenAPI spec missing/unreadable | Bad path passed | Returns nil/empty, no crash | Warn via `ui.notify_warn` |
| `jq` installed, valid filter | `{"a":1}` piped through `.a` | Filtered output (`1`) shown in a persistent split | N/A |
| `jq` not installed | Any filter attempt | No crash; clear error with install hint | `ui.notify_err` |
| `jq` filter syntax error | Malformed filter expression | No crash; jq's own stderr surfaced to the user | `ui.notify_err` with jq's stderr text |

</frozen-after-approval>

## Code Map

- `lua/cumulus/plugins/tools-dadbod.lua` -- Story 3.1's plugin-isolation reference: `init()`/`config()` split, `ft`-triggered lazy load, dependency wiring. Copy this shape for `tools-http.lua`.
- `lua/cumulus/util/ui.lua:13-42` -- `notify_info/notify_warn/notify_err` wrappers; use these, never raw `vim.notify`.
- `lua/cumulus/util/profiling.lua:47-131` -- canonical `vim.system(cmd, {text=true}, function(out) vim.schedule(function() ... end) end)` async pattern; model `jq` invocation and OpenAPI generation's file I/O callbacks on this.
- `lua/cumulus/core/keymaps.lua:137-140` -- where Story 3.1 added the `<leader>D` group; add a new `<leader>H` block the same way (leave `<leader>D` and `<leader>o` untouched).
- `lua/cumulus/plugins/ui-whichkey.lua:31` -- where the `"database"` group label was registered for `<leader>D`; register an `"http"` group label for `<leader>H` the same way.
- `ftplugin/sql.lua` -- minimal buffer-local-settings shape (indent, commentstring) to model a new `ftplugin/http.lua` on.
- `scripts/validate-db.sh` -- current best-practice smoke-test model (fixture-based, `cquit 1` on failure) for the new `scripts/validate-http.sh`.

## Tasks & Acceptance

**Execution:**
- [x] `lua/cumulus/plugins/tools-http.lua` (NEW) -- add the `kulala.nvim` plugin spec (`ft = {"http"}`), configure its response display for a persistent split (consult its own docs for the exact option once installed; if none exists, halt per the "Ask First" boundary), register `<leader>H` keymaps for: run current request, generate `.http` from an OpenAPI spec, and jq-filter the last response -- delivers `.http` execution.
- [x] `lua/cumulus/util/openapi.lua` (NEW) -- `M.generate_http_from_spec(spec_path)`: read a JSON OpenAPI file via `vim.json.decode`, walk `paths`, emit one `.http`-formatted request block per operation (method + url + headers); warn and return nil for `.yaml`/`.yml` or unreadable input -- delivers OpenAPI template generation.
- [x] `lua/cumulus/util/http.lua` (NEW) -- `M.jq_filter(json_text, filter_expr, callback)`: guard `vim.fn.executable("jq") == 1` (else `notify_err` with an install hint), shell out via `vim.system` per the `profiling.lua` pattern, surface jq's stderr through `notify_err` on nonzero exit -- delivers jq filtering.
- [x] `lua/cumulus/core/keymaps.lua` -- add the `<leader>H` keymap group wiring the three actions above together.
- [x] `ftplugin/http.lua` (NEW) -- buffer-local settings for `.http` files, mirroring `ftplugin/sql.lua`.
- [x] `scripts/validate-http.sh` (NEW) -- smoke test: static shape checks (`openapi.lua`/`http.lua` export the functions above; `tools-http.lua` references `kulala`) plus functional fixture tests for every I/O-matrix row above (skip/warn gracefully, not fail, if `jq` isn't installed in the test environment); `cquit 1` on any real failure.

**Acceptance Criteria:**
- Given a `.http` file, when the user triggers the run-request keymap, then `kulala.nvim` executes it and the response renders in a persistent split, never a floating window.
- Given a valid JSON OpenAPI spec, when the user triggers the generate-from-OpenAPI keymap, then a `.http` file with one request block per operation is produced.
- Given a YAML OpenAPI spec, when the same keymap runs, then nothing is generated and a clear "JSON only" warning appears.
- Given a JSON response and `jq` installed, when the user applies a valid filter, then the filtered result appears in a persistent split.
- Given `jq` is not installed, when the user attempts a filter, then a clear error with an install hint appears and nothing crashes.
- Given `scripts/validate-http.sh` runs, then it exits 0 on success and non-zero on a deliberately broken assertion.

## Design Notes

`openapi.lua` sketch:

```lua
local M = {}
function M.generate_http_from_spec(spec_path)
  -- warn+return nil for .yaml/.yml (out of v1 scope) or unreadable files
  -- vim.json.decode the file, walk `paths`, emit "METHOD url\nHeader: v\n\n" blocks
  return http_text_or_nil
end
return M
```

`http.lua`'s `jq_filter` mirrors `profiling.lua`'s `vim.system` + `vim.schedule` shape exactly, just with `jq` as the command and the filter expression as an argument.

## Verification

**Commands:**
- `bash scripts/validate-http.sh` -- expected: all checks pass, exit code 0 (jq-dependent checks skip gracefully with a clear note if `jq` isn't installed, rather than failing the whole run).
- `nvim --headless -u init.lua -c "lua assert(require('cumulus.util.openapi').generate_http_from_spec); assert(require('cumulus.util.http').jq_filter)" -c "qa"` -- expected: no error output, clean exit.

## Suggested Review Order

**Plugin Wiring (kulala.nvim as the execution engine)**

- Entry point: `kulala.nvim` plugin spec, lazy-loaded on `.http` buffers, forcing persistent-split display per the epic's UX pattern.
  [`tools-http.lua:15`](../../lua/cumulus/plugins/tools-http.lua#L15)

- `<leader>Hr` now guards against running outside a `.http` buffer instead of surfacing kulala's own raw error (review fix).
  [`keymaps.lua:162`](../../lua/cumulus/core/keymaps.lua#L162)

- Shared split-opening helper, switched to `vsplit` to match kulala's own vertical response-window orientation (review fix).
  [`keymaps.lua:148`](../../lua/cumulus/core/keymaps.lua#L148)

**OpenAPI-to-.http Template Generation**

- Core parser: JSON-only by design (no YAML parser), walks `paths`, sorts for deterministic output.
  [`openapi.lua:72`](../../lua/cumulus/util/openapi.lua#L72)

- Per-operation block builder -- now type-guards `operationId`/`summary` so a malformed spec degrades gracefully instead of crashing (review fix).
  [`openapi.lua:40`](../../lua/cumulus/util/openapi.lua#L40)

- `<leader>Ho` keymap: prompts for a spec path, generates the template, opens it in a persistent split.
  [`keymaps.lua:177`](../../lua/cumulus/core/keymaps.lua#L177)

**jq Response Filtering**

- Shells out to a real `jq` binary async, with a timeout guard added in review (was previously unbounded).
  [`http.lua:25`](../../lua/cumulus/util/http.lua#L25)

- `<leader>Hj` keymap: filters the current buffer's JSON through a user-supplied jq expression.
  [`keymaps.lua:191`](../../lua/cumulus/core/keymaps.lua#L191)

**Peripherals**

- `:checkhealth` entry for `jq`/`kulala.nvim`, added in review to match the established convention for other DevOps CLI dependencies.
  [`health.lua:187`](../../lua/cumulus/health.lua#L187)

- Smoke test, strengthened in review to invoke the actual `<leader>Ho`/`<leader>Hj` callbacks (not just assert the keymaps exist) and check real split/filetype behavior.
  [`validate-http.sh:1`](../../scripts/validate-http.sh#L1)

- Buffer-local `.http` conventions, including the `###` comment-leader fix added in review.
  [`ftplugin/http.lua:1`](../../ftplugin/http.lua#L1)
