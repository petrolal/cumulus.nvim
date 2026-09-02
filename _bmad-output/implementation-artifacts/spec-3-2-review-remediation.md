---
title: 'SPEC-3.2 HTTP Client Review Remediation (Code Hardening)'
type: 'bugfix'
created: '2026-09-01'
status: 'in-review'
review_loop_iteration: 0
baseline_commit: '70e20519671bd1286694a81c9179c5b8d81a9a46'
context: ['{project-root}/_bmad-output/implementation-artifacts/spec-3-2-http-client-rest-api-explorer.md']
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** A multi-lens review of the shipped SPEC-3.2 HTTP Client found defensive-guard gaps: relative or `"/"` OpenAPI `servers` URLs generate hostless request lines with no warning; `vim.fn.expand` mangles spec paths containing `%`/`#`; `kulala.run()` and the `<leader>Hj` filter target are unguarded (raw stack trace / filters the wrong buffer); `cumulus_http_open_in_split` makes file-backed listed buffers that `:w` dumps into the repo; a few malformed-spec shapes crash or emit garbage blocks; and `:checkhealth` omits `curl` (kulala's request backend).

**Approach:** Apply targeted guards to the five implementation files. No happy-path behavior changes — only added warnings, fallbacks, `pcall`s, and buffer hygiene. Verification-side work (new `_spec.lua` files, extra `validate-http.sh` rows, wiring into `validate.sh`) is split to a follow-up tracked in `deferred-work.md`; the existing 14-step `validate-http.sh` must keep passing.

## Boundaries & Constraints

**Always:**
- Keep `kulala.nvim` as the sole `.http` engine and `jq` (via `vim.system`) as the sole filter engine — no new HTTP or JSON logic in Lua.
- Route every message through `cumulus.util.ui` `notify_info/notify_warn/notify_err`, never raw `vim.notify`.
- Preserve the persistent-split / never-floating response UX, the `<leader>H` group, and deterministic (sorted) generator output. Do not touch `<leader>o` or `<leader>D`.
- The existing `scripts/validate-http.sh` must still pass unchanged after these fixes.

**Ask First:**
- If moving `looks_like_json` from `core/keymaps.lua` into `cumulus.util.http` reveals any other caller, stop and confirm.
- If a fix can't be made without changing the original SPEC-3.2 frozen I/O contract, stop and ask.

**Never:**
- Do not add OpenAPI YAML support, `$ref` resolution, or `servers[].variables` substitution (still out of scope, warn-only).
- Do not change any `kulala.nvim` config option — `split_direction = "right"` is valid in the installed version; leave it.
- Do not add the new unit tests or `validate.sh` wiring here (deferred).
- Do not re-architect the OpenAPI generator — guard existing branches, don't restructure.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Behavior |
|----------|--------------|-------------------|
| Relative server URL | `servers: [{ "url": "/api/v3" }]` | Blocks still emitted with the literal prefix; `notify_warn` the URL is relative |
| Bare-slash server URL | `servers: [{ "url": "/" }]` | `base_url` → `{{baseUrl}}`; no hostless `GET /users` line; `notify_warn` |
| >1 server entry | `servers` has 2+ items | First used; `notify_info` names it |
| Path key without leading slash | `paths: { "users": {...} }` | Key skipped + `notify_warn`; siblings still generate |
| Newline in `operationId`/`summary` | `"operationId": "list\nUsers"` | `### list Users` on one line; block intact |
| UTF-8 BOM on the JSON file | BOM + valid OpenAPI JSON | Parses and generates normally |
| Required query/header param | `parameters: [{ "in":"query","name":"page","required":true }]` | `# TODO: set required query parameter 'page'` line in that block |
| jq filter starting with `-` | filter `-C .` | Treated as a filter, not a flag (argv `{ "jq", "--", expr }`) |
| `<leader>Hj` on a `.http` source buffer | cursor in a `.http` buffer | `notify_err` to move to the response / a JSON buffer; no jq call |
| `<leader>Hj` on kulala's response window | `filetype=kulala_ui` | That buffer's body text is filtered |
| `kulala.run()` throws | malformed `.http` | `pcall` + `notify_err`; no raw stack trace |
| `<leader>Ho` / `<leader>Hj` output split | any success | Scratch buffer (`buftype=nofile`, `bufhidden=wipe`); reuses an open result window instead of stacking |

</frozen-after-approval>

## Code Map

- `lua/cumulus/util/openapi.lua` -- `generate_http_from_spec` at `:83`. `vim.fn.expand` at `:101` → `vim.fs.normalize`. `base_url` derivation `:130-142` (relative / `""`-after-strip guard + multi-server `notify_info`). Path-key loop `:171-214` (skip non-`/` keys, collect + warn). `build_request_block` `:40-81` (sanitize `name` `:42-49`; required-param TODO lines before the request line near `:66`). BOM strip before `vim.json.decode` at `:119`.
- `lua/cumulus/util/http.lua` -- `jq_filter` at `:25`, argv `{ "jq", filter_expr }` at `:44` → `{ "jq", "--", filter_expr }`. Timeout branch `:50` is already correct on the installed Neovim 0.12 (`vim.system{timeout=…}` → `code=124, signal=15`, verified empirically) — no change. Add `M.looks_like_json` here, moved verbatim from `keymaps.lua:200-214`.
- `lua/cumulus/core/keymaps.lua` -- `cumulus_http_open_in_split` `:148-160` (scratch buffer + window reuse by `name_hint` prefix). `<leader>Hr` `:162` (`pcall` around `kulala.run` at `:174`). `looks_like_json` `:200-214` (delete; re-`require` from `util.http`). `<leader>Hj` `:217-240` (target selection + `desc` → `"jq-Filter JSON Response/Buffer"`).
- `lua/cumulus/plugins/tools-http.lua` -- `:15-33` kulala spec; add one comment line noting kulala shells out to `curl`. No option change.
- `lua/cumulus/health.lua` -- Story 3.2 section from `:220`; `jq` tool table `:222-228`. Add a `curl` `vim.fn.executable` check in the same shape (install hint `apt install curl / brew install curl`).
- `_bmad-output/implementation-artifacts/deferred-work.md` -- remove the single `<leader>H*` keymap-block entry ("Deferred from: code review of spec-2-1", cites `core/keymaps.lua:64-214`); this spec resolves all of it. Leave every other entry byte-for-byte.

## Tasks & Acceptance

**Execution:**
- [ ] `lua/cumulus/util/openapi.lua` -- (1) `vim.fn.expand` → `vim.fs.normalize`; (2) after the trailing-slash strip, `if base_url == "" then base_url = "{{baseUrl}}"` and `elseif base_url ~= "{{baseUrl}}" and not base_url:match("^%a[%w+.-]*://") then notify_warn` it is relative; (3) `notify_info` naming the chosen server when `#spec.servers > 1`; (4) skip any `path_key` not matching `^/`, collect and `notify_warn` them; (5) `name = name:gsub("[\r\n]", " ")` in `build_request_block`; (6) strip a leading `\239\187\191` BOM before `vim.json.decode`; (7) for each `operation.parameters[]` with `required == true` and `["in"]` in `{query,header}`, emit a deduped `# TODO: set required <in> parameter '<name>'` line before the request line.
- [ ] `lua/cumulus/util/http.lua` -- argv → `{ "jq", "--", filter_expr }`; add `M.looks_like_json(text)` moved verbatim from `keymaps.lua`.
- [ ] `lua/cumulus/core/keymaps.lua` -- `cumulus_http_open_in_split`: `nvim_create_buf(false, true)`, set `buftype=nofile` / `bufhidden=wipe` / `swapfile=false`, and reuse an existing window whose buffer name starts with `name_hint .. "-"` instead of always `botright vsplit`. `<leader>Hr`: `pcall(kulala.run)`, `notify_err` on failure. Delete local `looks_like_json`; call `require("cumulus.util.http").looks_like_json`. `<leader>Hj`: if `&ft == "http"` `notify_err` (move to response/JSON buffer) and return; if `&ft == "kulala_ui"` filter that buffer's lines; else keep the current-buffer path with the existing soft `looks_like_json` warning; `desc` → `"jq-Filter JSON Response/Buffer"`.
- [ ] `lua/cumulus/plugins/tools-http.lua` -- add a comment noting kulala uses `curl` as its request backend.
- [ ] `lua/cumulus/health.lua` -- add a `curl` `vim.fn.executable` check next to `jq` in the Story 3.2 section.
- [ ] `_bmad-output/implementation-artifacts/deferred-work.md` -- delete the `<leader>H*` keymap-block entry only; other entries untouched.

**Acceptance Criteria:**
- Given `bash scripts/validate-http.sh`, when it runs after these changes, then every step still passes and it exits 0.
- Given `stylua lua/ ftplugin/ init.lua`, when it runs, then it reports no diff.
- Given a `.http` buffer with malformed syntax, when `<leader>Hr` runs, then a `notify_err` appears and no raw Lua stack trace reaches the user.
- Given a successful `<leader>Ho` or `<leader>Hj`, when the split opens, then its buffer is unlisted with `buftype=nofile`, and a second invocation reuses the same window rather than stacking.
- Given `<leader>Hj` invoked from a `.http` buffer, when triggered, then it errors clearly and never shells out to `jq`.
- Given `deferred-work.md` after this change, then the `<leader>H*` keymap-block entry is gone and all other entries are unchanged.

## Design Notes

Relative-URL guard, right after the trailing-slash strip in `openapi.lua`:

```lua
base_url = spec.servers[1].url:gsub("/+$", "")
if base_url == "" then
  base_url = "{{baseUrl}}"
elseif base_url ~= "{{baseUrl}}" and not base_url:match("^%a[%w+.-]*://") then
  ui.notify_warn("OpenAPI server URL is relative (" .. base_url .. "); generated requests need a host prefix")
end
```

The `http.lua` timeout branch already fires correctly on the installed Neovim (`code == 124`, verified) — do not "fix" it.

## Verification

**Commands:**
- `stylua lua/ ftplugin/ init.lua` -- expected: no diff.
- `bash scripts/validate-http.sh` -- expected: all steps pass (jq-dependent ones skip gracefully if `jq` is absent), exit 0.
- `nvim --headless -u init.lua -c "lua assert(require('cumulus.util.openapi').generate_http_from_spec); assert(require('cumulus.util.http').jq_filter); assert(require('cumulus.util.http').looks_like_json)" -c "qa"` -- expected: clean exit, no output.

**Manual checks:**
- With a real endpoint: `<leader>Hr` renders the response in a right split (never floating); `<leader>Hj` on that response window filters it; re-running `<leader>Ho` reuses the generated-template window instead of opening a second one.
