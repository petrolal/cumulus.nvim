---
title: 'gRPC & Protobufs Integration'
type: 'feature'
created: '2026-09-03'
status: 'in-review'
baseline_commit: 'bc1875f2379f9c499bdcd7b3a905c103b4e4707e'
review_loop_iteration: 0
context: ['/home/petrolal/tetravim.nvim/_bmad-output/implementation-artifacts/epic-3-context.md']
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** There is no protobuf/gRPC support in the editor — no `.proto` highlighting, formatting, or LSP navigation, no way to inspect a running gRPC server's services/methods, and no way to craft and send a test RPC. Microservice developers still leave Neovim for Postman/grpcurl, undercutting this epic's "complete IntelliJ replacement" goal.

**Approach:** Delegate editing intelligence to standard tooling — `protols` (LSP) + the `proto` Tree-sitter parser, `buf` for formatting — wired through the existing `lsp-core.lua` / `core-treesitter.lua` / `conform` fragment patterns. Add one narrow module `lua/tetravim/util/grpc.lua`: an async `grpcurl` wrapper (list / describe / invoke) modeled on `util/http.lua`'s `jq_filter`, plus a pure descriptor-to-JSON request-skeleton generator modeled on `util/openapi.lua`. Surface it under a new `<leader>G` group, rendering into the shared persistent split.

## Boundaries & Constraints

**Always:**
- `.proto` intelligence comes from `protols` (bare `{ servers = { protols = {} } }` fragment consumed by `lsp-core.lua`) and the `proto` parser; formatting goes through `buf` as a `conform` `formatters_by_ft` entry. No hand-written proto parsing.
- gRPC list / describe / invoke shell out to a real `grpcurl` via `vim.system` async, following `util/http.lua:25-67` verbatim in shape: `executable` guard with install hint, explicit `timeout`, timeout-code branch, `vim.schedule`d callback, `ui.notify_err` with captured stderr on nonzero exit.
- Request payloads are generated as JSON skeletons with per-field `TODO` placeholders (model on `util/openapi.lua`'s `build_request_block`), rendered editable via the existing global `tetravim_http_open_in_split(text, "json", hint)`.
- Before `invoke`, validate the edited payload with `util/http.lua`'s `looks_like_json` — refuse + `notify_err` on malformed JSON, never pass it to `grpcurl`.
- Service / method lists use a Telescope or Snacks picker (both already available). Notify only via `require("tetravim.util.ui")` wrappers.
- New keymaps live only under `<leader>G`; register the group label in `ui-whichkey.lua`'s `list_extend` block. `buf` / `protols` / `grpcurl` are provisioned by adding them to `tools-mason.lua`'s `ensure_installed` (the established path; `bootstrap.sh` is not touched).
- Guard `.proto` → filetype `proto` with `vim.filetype.add(...)` (harmless if the installed Neovim already maps it).

**Ask First:**
- If `protols` is not a recognized `nvim-lspconfig` server (no default config resolves), HALT — do not write a custom `vim.lsp.config` block.
- If server reflection is unavailable and the feature would need proto descriptor sets / import-path resolution to work, HALT — that is a scope expansion beyond "reflection-driven".

**Never:**
- No external backend, engine, bridge, daemon, or helper Bash/Python script (AD-07). New runtime code = Lua plugin fragments + one `util/grpc.lua` + one `ftplugin/proto.lua`.
- Do not reimplement descriptor parsing in Lua beyond walking `grpcurl describe` / `-msg-template` JSON.
- Do not register keymaps outside `<leader>G`. Do not put RPC output in a floating window.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| List services, server up | reachable `host:port` with reflection | service names shown in a picker | N/A |
| Describe method | service + method selected | JSON request skeleton (fields → `TODO`) opened editable in persistent `json` split | N/A |
| Invoke RPC, valid payload | edited JSON + `host:port` + method | `grpcurl` invoked async; response rendered in persistent `json` split | N/A |
| Invoke RPC, malformed payload | payload buffer fails `looks_like_json` | no invocation | `ui.notify_err`, abort before `vim.system` |
| `grpcurl` not installed | any gRPC action | no crash; error names `grpcurl` + install hint | `ui.notify_err` via `executable` guard |
| Server unreachable / reflection off | bad address or reflection disabled | no crash; `grpcurl` stderr surfaced | `ui.notify_err` with stderr |
| `grpcurl` call exceeds timeout | hung server | no hang; explicit "timed out" message | timeout-code branch → `ui.notify_err` |
| Format `.proto`, `buf` present | `conform.format()` on a `proto` buffer | buffer reformatted by `buf` | conform no-ops if `buf` absent |

</frozen-after-approval>

## Code Map

- `lua/tetravim/plugins/lsp-toml.lua:1-21` / `lsp-html.lua:1-26` -- per-language convention: one file with a `{ "neovim/nvim-lspconfig", opts = { servers = { <name> = {} } } }` fragment **and** a `{ "nvim-treesitter/nvim-treesitter", opts = function(_, opts) vim.list_extend(opts.ensure_installed, { "<lang>" }) end }` fragment. Model `lua/tetravim/plugins/lsp-proto.lua` on this: `servers = { protols = {} }`, parser `proto`.
- `lua/tetravim/plugins/lsp-core.lua:26-60` -- consumes `opts.servers`: `vim.lsp.config` + `vim.lsp.enable` on 0.11+, else `require("lspconfig")[server].setup`. No shared `on_attach`/`capabilities` exists — do not invent one.
- `lua/tetravim/plugins/core-treesitter.lua:27-49` -- rewritten-branch API; do NOT edit the `ensure_installed` seed list, extend via the opts-function fragment (as `tools-dadbod.lua:162-169` does for `sql`).
- `lua/tetravim/plugins/tools-formatting.lua:8-28` -- `conform` `formatters_by_ft`; add `proto = { "buf" }`. `format_on_save` gate at `:29-39` is already generic.
- `lua/tetravim/plugins/tools-mason.lua:8-35` -- `local ensure_installed` list consumed by the `mason-tool-installer` fragment at `:43-64`; add `"buf"`, `"protols"`, `"grpcurl"`.
- `lua/tetravim/util/http.lua:14`, `:25-67` -- `JQ_TIMEOUT_MS` + `M.jq_filter`: the exact `executable` guard + `vim.system(..., { text = true, stdin = ..., timeout = ... }, cb)` + timeout-code branch + `ui.notify_err` + success-callback shape to copy for the `grpcurl` wrapper.
- `lua/tetravim/util/http.lua:78-93` -- `M.looks_like_json(text)`; call before any `invoke`.
- `lua/tetravim/util/openapi.lua:40-112` -- local `build_request_block`: emits `TODO` param placeholders + `"{}"` body placeholder. `:123-312` -- `M.generate_http_from_spec`: `pcall(vim.json.decode)`, deterministic sorted iteration, returns text or `nil` after `ui.notify_warn`, no disk writes. Model the descriptor→skeleton generator on both.
- `lua/tetravim/core/keymaps.lua:143-178` -- global `tetravim_http_open_in_split(text, filetype, name_hint)`: reuses a window whose buffer basename matches `^<hint>-`, else `botright vsplit`, scratch `nofile`/`wipe` buffer, sets `filetype`. Call with `"grpc-request"` / `"grpc-response"`. Reuse verbatim — no second split helper.
- `lua/tetravim/core/keymaps.lua:180-251` -- the `<leader>H` block (`pcall(require, ...)`, `vim.ui.input` prompts, `ui.notify_err` on failure, result → split helper). Add the `<leader>G` block in the same style after `:251`.
- `lua/tetravim/plugins/ui-whichkey.lua:21-42` -- top-level `vim.list_extend(opts.spec, { ... })` with `{ "<leader>D", group = "database", ... }` etc.; add `{ "<leader>G", group = "grpc/proto", icon = "<glyph>" }`.
- `lua/tetravim/util/ui.lua:13-42` -- `notify` / `notify_info` / `notify_warn` / `notify_err`.
- `lua/tetravim/health.lua:228-256` -- Story 3.2's checkhealth section: `vim.health.start(...)`, a `{ name, desc, install }` tools table looped with `vim.fn.executable`, `pcall(require, ...)`, `pcall(vim.treesitter.get_string_parser, "", "<lang>")`. Add an analogous "gRPC & Protobufs (Story 3.4)" section ~`:257`.
- `ftplugin/http.lua:1-17` / `ftplugin/sql.lua:1-13` -- 2-space indent block + `commentstring` + `comments`; model `ftplugin/proto.lua` with `commentstring = "// %s"`, `comments = "s1:/*,mb:*,ex:*/,://"`.
- `scripts/validate-http.sh` -- smoke-test model: `mktemp -d` fixture root + `trap ... EXIT`, heredoc fixtures, numbered `nvim -u init.lua --headless -c "lua ... pcall ... cquit 1"` steps, monkeypatch/restore of `vim.notify`/`vim.fn.executable`/`vim.ui.input`, `vim.wait` for async, real-binary steps gated behind `executable` + SKIP. Copy into `scripts/validate-3-4.sh`.
- `lua/tetravim/tests/notify_spec.lua:3-51` -- plenary-busted shape (`describe` / `before_each` monkeypatch / `after_each` restore / `it` + `assert.*`). Model `lua/tetravim/tests/grpc_spec.lua` on it.
- Clean slate: no existing `ftplugin/proto.lua`, `lsp-proto.lua`, `proto` parser entry, or `buf`/`protols`/`grpcurl` reference in runtime Lua. `core/autocmds.lua:15` "keyboard protocol" comment is unrelated.

## Tasks & Acceptance

**Execution:**
- [x] `lua/tetravim/plugins/lsp-proto.lua` (NEW) -- `nvim-lspconfig` fragment `servers = { protols = {} }` + `nvim-treesitter` opts-fn fragment adding `"proto"`; one fragment's `init` calls `vim.filetype.add({ extension = { proto = "proto" } })`. HALT per "Ask First" if `protols` is unknown to lspconfig. Delivers AC-1 editing/navigation.
- [x] `lua/tetravim/plugins/tools-formatting.lua` -- add `proto = { "buf" }` to `formatters_by_ft`. Delivers AC-1 formatting.
- [x] `lua/tetravim/plugins/tools-mason.lua` -- add `"buf"`, `"protols"`, `"grpcurl"` to `ensure_installed`.
- [x] `lua/tetravim/util/grpc.lua` (NEW) -- `M.list_services(addr, cb)`, `M.describe(addr, symbol, cb)`, `M.invoke(addr, method, json_payload, cb)`: each guards `vim.fn.executable("grpcurl") == 1` (else `ui.notify_err` + install hint), runs `vim.system({ "grpcurl", ... }, { text = true, timeout = GRPCURL_TIMEOUT_MS, stdin = json_payload or nil }, ...)` with a `vim.schedule`d callback, timeout-code branch, stderr via `ui.notify_err` on nonzero exit. `invoke` refuses (`ui.notify_err`) when `require("tetravim.util.http").looks_like_json(json_payload)` is false. `M.request_skeleton(describe_json)` -- pure: walk the type's fields, emit a deterministic JSON object string with `TODO`-annotated placeholders; return string or `nil` after `ui.notify_warn`. Delivers AC-2, AC-3.
- [x] `lua/tetravim/core/keymaps.lua` -- `<leader>G` block after line 251: `<leader>Gl` list services → picker → on pick, `describe` → method picker; `<leader>Gm` describe symbol under cursor / prompt; `<leader>Gi` generate skeleton into a `grpc-request` split, then on `<CR>` (buffer-local) read it, `looks_like_json`-guard, `invoke`, render response in a `grpc-response` split; `<leader>Gf` `require("conform").format()` the current `proto` buffer. Address via `vim.ui.input`; failures via `ui.notify_err`.
- [x] `lua/tetravim/plugins/ui-whichkey.lua` -- add `{ "<leader>G", group = "grpc/proto", icon = "<glyph>" }` to the `:21-42` `list_extend` block.
- [x] `ftplugin/proto.lua` (NEW) -- 2-space indent block mirroring `ftplugin/http.lua`, `commentstring = "// %s"`, `comments = "s1:/*,mb:*,ex:*/,://"`.
- [x] `lua/tetravim/health.lua` -- new `vim.health.start("TetraVim gRPC & Protobufs (Story 3.4)")` section: `{ buf, protols, grpcurl }` executable checks with install hints + `pcall(vim.treesitter.get_string_parser, "", "proto")` ok/warn.
- [x] `lua/tetravim/tests/grpc_spec.lua` (NEW) -- unit-test every I/O-matrix row reachable without a live server: `request_skeleton` happy + malformed (→ nil + warn); `invoke` aborts on non-JSON payload; `executable` guard fires when `grpcurl` absent (monkeypatch `vim.fn.executable`); command-array construction correct. Monkeypatch `vim.system` / `vim.notify`; no real binary.
- [x] `scripts/validate-3-4.sh` (NEW) -- copy `validate-http.sh` structure: assert `util/grpc.lua` exports the four functions and `lsp-proto.lua` references `protols` + `proto`; run `grpc_spec.lua`; exercise `request_skeleton` and the `looks_like_json` guard functionally; gate any `grpcurl`/`buf`/`protols` step behind `vim.fn.executable(...) == 1` + SKIP note; `cquit 1` on real failure.

**Acceptance Criteria:**
- Given a `.proto` file is opened, then `proto` Tree-sitter highlighting is active and `protols` attaches (hover / go-to-definition work).
- Given a `.proto` buffer, when the format keymap runs, then `buf` reformats it (clean no-op if `buf` is absent).
- Given a reachable gRPC server with reflection, when the user runs `<leader>Gl`, then its services and then a chosen service's methods are browsable in a picker.
- Given a selected method, when the user generates a request, then a JSON skeleton with `TODO` placeholders opens editable in a persistent split.
- Given an edited valid JSON payload, when the user invokes the RPC, then `grpcurl` runs async and the response renders in a persistent `json` split; given malformed JSON, then the invoke is refused with a clear error and nothing crashes.
- Given `grpcurl` is not installed, when any gRPC action runs, then a clear error names `grpcurl` with an install hint and the editor does not block.
- Given `scripts/validate-3-4.sh` runs, then it exits 0 on success, non-zero on a deliberately broken assertion, and skips real-binary steps gracefully.

## Spec Change Log

## Design Notes

`util/grpc.lua`'s three wrappers are structurally identical to `util/http.lua:jq_filter` — only the command array and `stdin` differ. Canonical body for `invoke`:

```lua
if vim.fn.executable("grpcurl") ~= 1 then ui.notify_err("grpcurl not found on $PATH ...") return end
if not require("tetravim.util.http").looks_like_json(json_payload) then ui.notify_err("...not valid JSON") return end
vim.system({ "grpcurl", "-d", "@", "-plaintext", addr, method },
  { text = true, stdin = json_payload, timeout = 15000 }, function(out) vim.schedule(function()
    if out.code == 124 then ui.notify_err("grpcurl timed out")
    elseif out.code == 0 then cb(out.stdout or "") else ui.notify_err("grpcurl failed: "..(out.stderr or "")) end
  end) end)
```

`request_skeleton` walks `grpcurl describe <type>` / `-msg-template` JSON and emits `{ "fieldName": "TODO: <type>" }` with stable key ordering — same discipline as `openapi.lua`'s sorted `paths` walk. Default `-plaintext` for v1; a TLS toggle is out of scope unless raised.

## Verification

**Commands:**
- `bash scripts/validate-3-4.sh` -- expected: all checks pass, exit 0; `grpcurl`/`buf`/`protols` steps SKIP with a clear note if absent.
- `nvim --headless -u init.lua -c "lua local g = require('tetravim.util.grpc'); assert(g.list_services and g.describe and g.invoke and g.request_skeleton)" -c "qa"` -- expected: no error output, clean exit.
- `nvim --headless -u init.lua -c "PlenaryBustedDirectory lua/tetravim/tests/grpc_spec.lua" -c "qa"` -- expected: all specs pass.
- `nvim --headless -u init.lua -c "checkhealth tetravim" -c "qa"` -- expected: a "gRPC & Protobufs (Story 3.4)" section is present.
