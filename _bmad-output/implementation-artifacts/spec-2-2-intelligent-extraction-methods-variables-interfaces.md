---
title: 'Intelligent Extraction (Methods, Variables, Interfaces)'
type: 'feature'
created: '2026-08-25'
status: 'done'
review_loop_iteration: 0
context: ['/home/petrolal/tetravim.nvim/_bmad-output/implementation-artifacts/epic-2-context.md']
baseline_commit: 'f4b7c7481f6ef0e8e70485b9f5a3b165a7e23ad1'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** `<leader>jxv/jxc/jxm` (`lua/tetravim/util/jvm.lua:436-462`) already give Java-only, immediate-apply Extract Variable/Constant/Method via `nvim-jdtls`, satisfying that part of the AC as-is. But "Extract Interface" and "Inline Variable/Method" don't exist anywhere in the codebase, and every existing extraction path applies edits with zero preview — inconsistent with the dry-run-before-apply standard Story 2.1 established for refactors.

**Approach:** Add a new `lua/tetravim/util/extract.lua` module providing `extract_interface()` and `inline()`, both for Java and Kotlin, driven by generic `textDocument/codeAction` requests (filtered by `kind` prefix `refactor.extract.interface` / `refactor.inline`) rather than jdtls-specific commands (none exist for these). Route results through Story 2.1's dry-run pattern: quickfix preview + `vim.ui.select` confirm before applying, reusing `refactor.lua`'s `find_jvm_client`/`workspace_edit_to_locations`/busy-guard primitives. Leave `<leader>jxv/jxc/jxm` untouched.

## Boundaries & Constraints

**Always:**
- Reuse `refactor.lua`'s `find_jvm_client`, `workspace_edit_to_locations`, and its busy-guard/quickfix+`vim.ui.select` dry-run convention — no new preview mechanism.
- Never auto-apply: every codeAction result is previewed and requires explicit "Apply" confirm.
- New keymaps are buffer-local, added in `ftplugin/java.lua` and `lsp-kotlin.lua` on_attach, mirroring the existing `<leader>cr` override wiring (comment block citing SPEC-2.2).
- All LSP interaction stays async (`vim.lsp.buf_request_all`/`vim.system`), never blocking the editor.
- Attempt both Java and Kotlin for `extract_interface`/`inline` (both are generic-codeAction-driven, unlike jdtls's extract-variable/constant/method wrappers).

**Ask First:**
- If a server's codeAction for extract-interface/inline carries only a `command` with no independently-previewable `edit` (edit only materializes server-side on command execution), HALT and ask how to proceed before deciding whether to preview post-hoc or accept an unpreviewed apply for that path.

**Never:**
- Do not modify, wrap, or merge into the existing `<leader>jx*` group (`jvm.lua:436-486`) — it already satisfies Extract Method/Variable/Constant for Java and stays untouched.
- Do not add Kotlin support for Extract Method/Variable/Constant — no equivalent tooling exists here (`nvim-jdtls` is Java-specific); that gap is pre-existing and out of scope.
- No new `engine.lua`/Scala surface; no centralized LSP-monolith module.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Extract interface happy path | Concrete Java/Kotlin class, `<leader>ce`, confirm | New interface file + updated class previewed in quickfix, applied on confirm | N/A |
| Inline happy path | Cursor on local var/method, `<leader>ci`, confirm | All usages replaced, declaration removed, applied on confirm | N/A |
| Cancel at confirm | Either action, "Cancel" chosen | No files modified | N/A |
| No JVM LSP attached | Non-Java/Kotlin buffer or LSP not attached | No-op | Visible `notify_warn`, no request sent |
| No applicable code action | Cursor not on an inlineable/extractable symbol | No-op | Visible `notify_warn`, no quickfix opened |
| Concurrent invocation | Action triggered while one is already in flight | Second call rejected | Visible `notify_warn`, first call unaffected |

</frozen-after-approval>

## Code Map

- `lua/tetravim/util/jvm.lua:436-486` -- existing `<leader>jx*` (Extract Var/Const/Method, jdtls-specific, immediate-apply, Java-only) -- sibling feature, do not modify
- `lua/tetravim/util/refactor.lua:62-70` -- `M.find_jvm_client(bufnr)` -- reuse to locate JDTLS/Kotlin LS client
- `lua/tetravim/util/refactor.lua:78-97` -- `M.workspace_edit_to_locations(workspace_edit)` -- reuse to build quickfix items from a WorkspaceEdit
- `lua/tetravim/util/refactor.lua:248-292,304-444` -- `M.project_rename`/`_do_rename`/`_show_preview` -- the busy-guard + async-request + quickfix + `vim.ui.select` confirm pattern to replicate structurally in the new module
- `lua/tetravim/util/refactor-treesitter.lua:264-302` -- `M._ts_root_for`/`M._is_comment_or_string_node` -- reusable parse-once Tree-sitter helpers if AST inspection is needed to scope a codeAction request (e.g. class range for extract-interface)
- `ftplugin/java.lua:44-69` (override added at 65-67 for `<leader>cr`) -- add new buffer-local `<leader>ce`/`<leader>ci` here, same pattern, comment cites SPEC-2.2
- `lua/tetravim/plugins/lsp-kotlin.lua:46-64` (override added at 61-63 for `<leader>cr`) -- mirror the same two buffer-local keymaps
- `lua/tetravim/core/keymaps.lua:33-86` -- global `<leader>c*` group; `<leader>ce`/`<leader>ci` confirmed free, no collision
- `lua/tetravim/tests/refactor_spec.lua` (full file) -- structure/style to match for the new `extract_spec.lua` (static shape tests, buffer-local wiring check via `io.open`/`maparg`)
- `scripts/validate-refactor.sh` (full file) -- 4-phase structure/mocking approach (`vim.lsp.get_clients`, `vim.lsp.buf_request_all`, `vim.ui.select` mocks, `cquit` on assertion failure) to mirror in `validate-extract.sh`
- `AGENTS.md:34` -- test-split policy: static shape in `_spec.lua`, real-plugin-runtime behavior in `validate-*.sh`

## Tasks & Acceptance

**Execution:**
- [x] `lua/tetravim/util/extract.lua` -- new module: `M.extract_interface()` requests `textDocument/codeAction` scoped to the class under cursor, filters for `kind` prefix `refactor.extract.interface`, resolves/executes to get a `WorkspaceEdit`, previews via quickfix (`refactor.workspace_edit_to_locations`), confirms via `vim.ui.select`, applies via `vim.lsp.util.apply_workspace_edit` -- new capability, no existing wrapper
- [x] `lua/tetravim/util/extract.lua` -- `M.inline()` requests `textDocument/codeAction` at cursor, filters for `kind` prefix `refactor.inline`, same preview/confirm/apply flow -- new capability, no existing wrapper
- [x] `lua/tetravim/util/extract.lua` -- module-level `M._busy` reentrancy guard, mirroring `refactor.lua`'s pattern -- prevents overlapping previews/confirms
- [x] `ftplugin/java.lua` -- in `on_attach`, buffer-local `<leader>ce` -> `extract.extract_interface()`, `<leader>ci` -> `extract.inline()`
- [x] `lua/tetravim/plugins/lsp-kotlin.lua` -- in `on_attach`, mirror the same two buffer-local overrides
- [x] `lua/tetravim/tests/extract_spec.lua` -- static shape tests for the new module's public API, matching `refactor_spec.lua`'s pattern
- [x] `scripts/validate-extract.sh` -- headless behavioral smoke test: happy-path extract-interface, happy-path inline, cancel path, no-code-action path; `cquit` on assertion failure

**Acceptance Criteria:**
- Given a concrete Java or Kotlin class with no existing interface, when the user triggers `<leader>ce` and confirms from the preview, then a new interface file is created and the class is updated to implement it; nothing is written until confirm.
- Given a local variable or method under the cursor with an available inline codeAction, when the user triggers `<leader>ci` and confirms, then all usages are replaced with the inlined value/body and the declaration is removed.
- Given either action, when the user cancels at the confirm prompt, then no files are modified.
- Given no applicable extract-interface or inline codeAction at the current position, when the corresponding keymap is triggered, then a visible warning is shown and no request/preview occurs.

## Design Notes

Unlike Story 2.1's rename (LSP has a dedicated `textDocument/rename` request), extract-interface and inline have no dedicated LSP request or `nvim-jdtls` command — they only surface via generic `textDocument/codeAction` results filtered by `kind`. If a matched action lacks a resolvable `edit` and only carries a `command`, use `codeAction/resolve` first if the server supports it (check `resolveProvider` in the codeAction registration) to obtain a previewable edit before falling back to the Ask-First path above.

## Spec Change Log

## Verification

**Commands:**
- `bash scripts/validate.sh` -- expected: still passes, unaffected
- `bash scripts/validate-extract.sh` -- expected: exits 0 (interface/inline fixture scenarios apply correctly)
- `stylua lua/ ftplugin/ init.lua` -- expected: no diff

**Manual checks (if no CLI):**
- Open a concrete Java class; trigger `<leader>ce`; confirm the quickfix shows the new interface file and the updated `implements` clause before applying.
- Place cursor on a local variable with 2+ usages; trigger `<leader>ci`; confirm all usages are inlined and the declaration is gone after applying.

## Suggested Review Order

**Core Extraction Logic**

- Orchestrates async codeAction retrieval, filtering, dry-run preview, and application.
  [`extract.lua:1`](../../lua/tetravim/util/extract.lua#L1)

- Manages busy guard state and constructs the specific LSP request with diagnostics.
  [`extract.lua:231`](../../lua/tetravim/util/extract.lua#L231)

- Handles LSP response, correctly unwraps commands via codeAction/resolve with a timeout.
  [`extract.lua:129`](../../lua/tetravim/util/extract.lua#L129)

- Formats WorkspaceEdits for the quickfix list and drives the vim.ui.select confirmation.
  [`extract.lua:194`](../../lua/tetravim/util/extract.lua#L194)

**Keymap Integration**

- Injects buffer-local `<leader>ce` and `<leader>ci` extraction keymaps for Java files.
  [`java.lua:68`](../../ftplugin/java.lua#L68)

- Mirrors identical extraction keymap overrides for Kotlin.
  [`lsp-kotlin.lua:32`](../../lua/tetravim/plugins/lsp-kotlin.lua#L32)

**Testing and Verification**

- Static testing ensuring module shape, busy-guard logic, and map wiring.
  [`extract_spec.lua:1`](../../lua/tetravim/tests/extract_spec.lua#L1)

- Headless smoke testing that mocks complex async LSP response flows to ensure reliability.
  [`validate-extract.sh:1`](../../scripts/validate-extract.sh#L1)
