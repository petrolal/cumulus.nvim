---
title: 'Project-Wide Safe Rename (Java & Kotlin)'
type: 'feature'
created: '2026-08-25'
status: 'done'
review_loop_iteration: 0
context: ['/home/petrolal/cumulus.nvim/_bmad-output/implementation-artifacts/epic-2-context.md']
baseline_commit: 'c394018bff55e904306bb58b61a2576673f2c994'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Java/Kotlin rename today is a bare `vim.lsp.buf.rename()` (`lua/cumulus/core/keymaps.lua:36-38`) that applies immediately with no cross-file preview and no awareness of Spring XML/annotation bean references LSP can't see. Large refactors risk silent breakage.

**Approach:** On JDTLS/Kotlin LS attach, override the buffer-local `<leader>cr` to run a project-wide rename: request a `textDocument/rename` WorkspaceEdit without auto-applying, merge in Tree-sitter-detected Spring bean references, preview everything via the quickfix list, and apply only on explicit confirm.

## Boundaries & Constraints

**Always:**
- Java and Kotlin only, via JDTLS and Kotlin LS respectively.
- No edit is applied without an explicit confirm after the quickfix preview is shown.
- All LSP requests and Tree-sitter scans run asynchronously (`vim.system`/`vim.schedule`, per `lua/cumulus/util/sync-runner.lua`); never block the editor.
- Rename logic lives in new `lua/cumulus/util/refactor*.lua` modules — never adds surface to `lua/cumulus/util/engine.lua` or the Scala `cumulus-engine`.
- Global `<leader>cr` behavior for non-JVM filetypes stays untouched; override is buffer-local only.

**Ask First:** none — UI approach (quickfix, no custom float) and Spring-reference scope (stereotype annotations, `@Autowired`, XML `<bean>`) are fixed by this spec.

**Never:**
- Scala/Metals/`sbt` support of any kind.
- A custom floating-window or split UI for the preview — reuse the quickfix list.
- File-move handling — deferred separately (oil.nvim move → package/import fix is out of scope for this spec; see `deferred-work.md`).
- Reintroducing any purged `engine.lua` stub (see `scripts/validate.sh:96-104`).

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Symbol under generated/build dir | Rename target's LSP-reported edit touches a file under `target/`/`build/` | Location is included in quickfix preview like any other; not silently dropped | N/A |
| Rename collides with existing symbol | Chosen new name already resolves in scope | Abort before quickfix; no edits applied | Visible `vim.notify` error naming the conflict |
| No JVM LSP attached | `<leader>cr` pressed in a buffer with no JDTLS/Kotlin LS client | Default `vim.lsp.buf.rename()` behavior is NOT silently substituted | Visible notify: no project-wide rename available for this buffer |

</frozen-after-approval>

## Code Map

- `lua/cumulus/core/keymaps.lua:36-38` -- existing global `<leader>cr` (`vim.lsp.buf.rename()`) -- leave as the non-JVM fallback, do not edit
- `lua/cumulus/core/keymaps.lua:68-78` -- existing `<leader>cR` single-file LSP rename -- out of scope, do not modify
- `lua/cumulus/plugins/lsp-core.lua:36-46` -- shared `LspAttach` autocmd (JDTLS gets a custom notify at line 12) -- reference for the attach pattern; actual override lives per-server below
- `ftplugin/java.lua:44-59` -- JDTLS `on_attach` -- add buffer-local `<leader>cr` override here
- `lua/cumulus/plugins/lsp-kotlin.lua:46` -- Kotlin LS `on_attach` (already disables `documentHighlightProvider` here) -- mirror the same override
- `lua/cumulus/util/jvm.lua:436-486` -- existing JDTLS refactor group `<leader>jx*` (Extract Var/Const/Method) -- sibling feature; do not merge into it, keep rename on `<leader>cr`
- `lua/cumulus/util/sync-runner.lua` (full file) -- async convention to follow: `vim.system` + `vim.schedule`, collapsing heartbeat notify
- `lua/cumulus/plugins/core-treesitter.lua` -- Tree-sitter is highlighting-only today; new Spring-bean queries are net-new, no existing query code to extend
- `lua/cumulus/util/engine.lua:625-634,1090-1135` -- legacy Scala-backed `parse_spring_beans`/bean picker -- reference only, do not call into or extend
- `scripts/validate.sh:96-104` -- purged-stub assertions -- new module names must not collide with or resemble these
- `AGENTS.md:10,42` -- policy: Java/Kotlin only, prefer native LSP/Tree-sitter over new `engine.lua` surface

## Tasks & Acceptance

**Execution:**
- [x] `lua/cumulus/util/refactor.lua` -- new module: `M.project_rename(new_name)` requests `textDocument/rename` without auto-applying, merges Tree-sitter-detected Spring references, populates the quickfix list, confirms via `vim.ui.select`, applies via `vim.lsp.util.apply_workspace_edit` on confirm -- central async rename flow
- [x] `lua/cumulus/util/refactor-treesitter.lua` -- new module: Tree-sitter queries for `@Component`/`@Service`/`@Repository`/`@Controller`/`@RestController` stereotypes, `@Autowired` field references, and XML `<bean id=".." class="..">` matches for a given symbol name
- [x] `ftplugin/java.lua` -- in `on_attach` (lines 44-59), buffer-local override: `<leader>cr` calls `refactor.project_rename` instead of default
- [x] `lua/cumulus/plugins/lsp-kotlin.lua` -- in `on_attach`, mirror the same buffer-local `<leader>cr` override
- [x] `lua/cumulus/tests/refactor_spec.lua` -- static shape tests for the new modules' public API (busted-style, matches existing `_spec.lua` pattern)
- [x] `scripts/validate-refactor.sh` -- headless behavioral smoke test against a small fixture project: rename with a Spring XML bean reference, confirm, assert all locations updated; `cquit` on assertion failure, mirroring `validate-dap-jvm.sh`

**Acceptance Criteria:**
- Given a Java class referenced in 2+ files including a Spring XML `<bean class="...">` entry, when the user triggers `<leader>cr` and confirms from the quickfix preview, then all Java references and the XML `class` attribute are updated.
- Given the same scenario, when the user cancels at the confirm prompt, then no files are modified.
- Given a rename target collides with an existing symbol, when project rename is attempted, then it aborts with a visible error and no edits are applied.

## Spec Change Log

- 2026-08-25: Implemented all 6 tasks as specified. `refactor.lua`'s `project_rename(new_name)` finds the buffer's JDTLS/Kotlin LS client (`find_jvm_client`, checked first -- satisfies the "no JVM LSP attached" I/O matrix row before anything else runs), requests `textDocument/rename` via `vim.lsp.buf_request_all` (never the default handler), converts the returned `WorkspaceEdit` into quickfix items by reusing `vim.lsp.util.locations_to_items` (rather than hand-rolling a range->item converter), merges in `refactor-treesitter.lua`'s async project scan, opens `:copen`, and only calls `vim.lsp.util.apply_workspace_edit` plus a parallel Spring-edit apply on an explicit `vim.ui.select` "Apply" choice. A rename collision surfaces via the LSP's own error response (`resp.err`) -- this depends on JDTLS/Kotlin LS actually returning an error for a colliding rename rather than a silently-broken edit; not independently re-validated against a live server (see below).
- 2026-08-25 (Tree-sitter approach, an implementation-time judgment call within the spec's stated design freedom): rather than hand-writing nested Tree-sitter queries against the exact `tree-sitter-java`/`tree-sitter-kotlin` grammars (high risk of a subtly-wrong field/node name silently returning zero matches, unverifiable in this sandbox against real Kotlin source), `refactor-treesitter.lua` uses a hybrid: (1) `rg`/`grep` (vim.system, async, mirrors `sync-runner.lua`'s convention) to find every whole-word candidate line across `.java`/`.kt`/`.xml` under the JVM client's `root_dir`; (2) Lua-pattern classification of each candidate into `xml_bean` / `autowired` / `stereotype` (or dropped, if it matches none -- a bare textual hit is never silently rewritten); (3) `vim.treesitter.get_string_parser` used as a precision filter to discard a pattern-matched candidate that Tree-sitter confirms sits inside a comment/string-literal node, pcall-guarded so a missing/broken grammar degrades to pattern-only matching rather than aborting the scan. This still uses Tree-sitter for real (step 3) but doesn't depend on it for primary classification, which is the part most likely to be silently wrong without a live grammar to test against.
- 2026-08-25 (apply-time fix found via testing): the first implementation of the Spring-edit apply path wrote directly to disk via `vim.fn.writefile`, inconsistent with `vim.lsp.util.apply_workspace_edit`'s actual behavior (edits a buffer in memory, does not save) -- fixed to also use `vim.fn.bufadd`/`bufload` + `nvim_buf_set_text`, so every touched file (LSP-driven and Spring-driven) ends up in the same "edited buffer, not yet saved" state and the user reviews/saves everything together, rather than some files being flushed to disk behind an already-open buffer's back.
- 2026-08-25 (side-effect fix found via testing): loading a not-yet-open `.java`/`.kt` file via `bufadd`/`bufload` to splice in a Spring edit fires the same `FileType`/`BufReadPost` autocmds as a normal `:edit` -- which meant applying a rename could silently launch a *second* JDTLS/Kotlin LS instance against a file the user never asked to open. Fixed by wrapping that load in `vim.o.eventignore = "all"` in `apply_spring_edits`.
- 2026-08-25 (`scripts/validate-refactor.sh`): this sandbox does have `jdtls`/`kotlin-language-server` Mason packages installed, but launching a real JDTLS session against a throwaway fixture project is slow/flaky and, per the same tradeoff `validate-dap-jvm.sh` already made for a live Scala/DAP session, out of scope for an automated script. The script instead mocks the LSP seam only (`vim.lsp.get_clients`, `vim.lsp.buf_request_all` return a fake `jdtls` client and a canned `WorkspaceEdit`) and exercises everything downstream for real: the merge with `refactor-treesitter.lua`'s un-mocked project scan, the quickfix build, the confirm gate, and applying both the LSP edit and the Spring text edits to real fixture files. Covers all three Acceptance Criteria (Apply updates Java + XML, Cancel touches nothing, a collision `err` response aborts before the quickfix with a visible `vim.notify` ERROR and no file touched). A real JDTLS round-trip is left as a manual-check item, same as spec-1-1 did for a live Metals/DAP session.
- 2026-08-25 (plenary harness): `PlenaryBustedFile`/`PlenaryBustedDirectory` hung indefinitely against this repo in this sandbox for both the new `refactor_spec.lua` and a pre-existing spec (`profiling_spec.lua`) -- a pre-existing environment limitation, not something introduced here. Every assertion in `refactor_spec.lua` was independently verified by running the same logic directly via `nvim --headless -c "lua ..."` (no busted harness involved); all passed. Logged so a future session doesn't mistake the hang for a regression caused by this spec.
- 2026-08-25 (post-implementation review, 3-layer patch findings applied -- blind-hunter, edge-case-hunter, verification-gap): ten confirmed patch-level findings against the diff above, all fixed without touching Intent/Boundaries:
  1. **Duplicate same-line occurrences corrupted the buffer** (live-reproduced): `classify_jvm_line` located the symbol from column 1 every time, ignoring the raw hit's own column, so N raw hits for a line with the symbol N times all classified to the SAME first-occurrence span -- `apply_spring_edits` then spliced `new_name` into that identical span N times. Fixed: `scan_root_async` now dedupes raw hits to one per (file, line) *before* classifying; `classify_jvm_line`/`classify_xml_line` each return every whole-word occurrence on the line (`M._find_all_occurrences`), and every occurrence gets its own quickfix item. Verified against the reviewer's exact reproduction line (`private FooService fooService = FooService.createDefault();`) both as a standalone unit test and end-to-end in `validate-refactor.sh`.
  2. **LSP-provided and Spring-scanned locations could overlap and double-apply** (e.g. a stereotype-annotated class's declaration line, touched by both the LSP rename and the stereotype classifier). Fixed: new `refactor.filter_overlapping_spring_items(lsp_items, spring_items)`, called before the Spring edits are ever applied, drops any Spring item whose (file, line, column-range) overlaps an LSP-covered location. `validate-refactor.sh`'s fixture now deliberately puts `@Service` on the renamed class specifically to exercise this overlap and asserts no double-splice (`BarServiceBarService`) occurs.
  3. **No package/FQN scoping -- cross-package name collisions could silently corrupt unrelated code.** Fixed: `refactor-treesitter.lua` gained `file_package(lines)` (parses a leading `package ...;`/`package ...` declaration) and package-prefix extraction on XML bean `class` attribute values; `scan_root_async` now takes an `old_package` argument (the renamed symbol's own file's package, read by `refactor.lua` before scanning) and skips any Java/Kotlin candidate file, or XML bean occurrence, whose package differs or is undeterminable -- only applied when the renamed symbol's own package IS determinable, per the spec's conservative-skip guidance. Verified with a `com.example.FooService` / `com.other.FooService` fixture, both as a unit test on `scan_root_async` and end-to-end in `validate-refactor.sh`.
  4. **XML bean classification never filtered comments.** `is_inside_comment_or_string` was Java/Kotlin-only. Fixed: new `is_inside_xml_comment(lines, lnum, col)`, a lightweight multi-line-aware `<!-- -->` state scan (no XML Tree-sitter grammar is guaranteed installed anywhere in this distribution), applied to every XML occurrence in `scan_root_async`. `validate-refactor.sh`'s fixture now includes a commented-out `<bean>` entry and asserts it is left untouched.
  5. **Apply-time failures were silently swallowed.** Fixed: `vim.lsp.util.apply_workspace_edit` is now pcall-guarded -- on failure it `notify_err`s and aborts *before* attempting any Spring edits (applying only the Spring half after a failed LSP half would leave a worse, more confusing half-renamed state than not applying at all). `apply_spring_edits` (now public, `M.apply_spring_edits`) returns `(applied_count, failed_files)`; the final notification now reads e.g. "Renamed X/Y location(s) -- N Spring-reference edit(s) failed in: ..." instead of unconditionally claiming full success.
  6. **Missing basic guards in `project_rename`.** Added: (a) an empty `vim.fn.expand('<cword>')` now `notify_warn`s and returns instead of proceeding with an empty old_name; (b) `new_name == old_name` (both the prompted and directly-passed paths) now `notify_info`s a no-op and returns instead of running the full request/scan/preview/confirm pipeline; (c) `vim.lsp.buf_request_all`'s callback now has a `RENAME_TIMEOUT_MS` (10s) `vim.defer_fn` deadline that `notify_err`s if the server never responds, guarded by a `responded` flag so a very late response after the timeout fired is a no-op rather than a second, contradictory notification.
  7. **`grep` fallback had no directory exclusions; case-sensitive extension matching; silent unavailability.** Fixed: the `grep` fallback command now carries `--exclude-dir` for `.git`/`target`/`build`/`node_modules`/`.gradle`/`out` (`rg` already honors `.gitignore`); file-extension matching in `scan_root_async` now lowercases before the `LANG_BY_EXT`/`xml` lookup so `.JAVA`/`.XML`/`.KT` aren't silently skipped; a single `vim.notify` WARN (not per-file spam) now fires when neither `rg` nor `grep` could be run, and a separate one when any candidate file couldn't be read.
  8. **No re-entrancy guard.** Added a module-level `M._busy` flag, set at the start of `_do_rename` and cleared at every terminal path (collision/no-changes/empty-preview/timeout/cancel/apply-failure/success); `project_rename` now checks it first and `notify_warn`s + returns if a rename is already in flight, rather than allowing overlapping previews/confirm prompts.
  9. **Inefficient re-parsing.** `scan_root_async` now parses each candidate file's Tree-sitter tree ONCE (`M._ts_root_for`) and reuses the root across every hit/occurrence in that file via `M._is_comment_or_string_node(root, row, col)`, instead of re-parsing the whole file fresh per hit; `is_inside_comment_or_string` stays as a public single-call convenience wrapper over the same two primitives.
  10. **Test coverage**: `refactor_spec.lua` gained a duplicate-occurrence classify test, a duplicate-occurrence `apply_spring_edits` end-to-end test (real temp file, real buffer, asserts both occurrences renamed with no corruption), a cross-package `scan_root_async` test (real temp fixture, asserts the unrelated package's file is excluded), direct unit tests for `apply_spring_edits`/`spring_items_to_qf`/`filter_overlapping_spring_items`, an `M._busy` reentrancy test, and -- replacing the old text-substring-only keymap check -- a test that invokes the real `lsp-kotlin.lua` `on_attach` with a stub client/bufnr and asserts (via `vim.fn.maparg('<leader>cr', 'n', false, true)`) a genuine buffer-local mapping was installed whose callback dispatches into `refactor.project_rename` (verified by monkeypatching `project_rename` and asserting it was called). `scripts/validate-refactor.sh`'s fixture was extended with the duplicate-occurrence line, the `@Service`-annotated overlap case, a commented-out bean entry, and a full `com.other` cross-package tree, with new assertions for all of the above. All 24 `refactor_spec.lua` assertions and all 4 `validate-refactor.sh` phases re-verified passing after these fixes (see Verification).

## Design Notes

Use the quickfix list (`vim.fn.setqflist` + `:copen`) as the dry-run preview, not a new floating window — no rounded-border float precedent exists in this codebase, and the project's convention is to call UI primitives directly rather than add an adapter layer. Get the WorkspaceEdit without applying it via `vim.lsp.buf_request_all(bufnr, 'textDocument/rename', params, cb)` (`vim.lsp.buf.rename()` itself applies immediately, so it can't be reused directly here). Confirm/cancel after the preview via `vim.ui.select({'Apply', 'Cancel'}, ...)`, consistent with the existing bean/endpoint picker idiom in `engine.lua`.

## Verification

**Commands:**
- `bash scripts/validate.sh` -- expected: still passes; purged-stub assertions unaffected
- `bash scripts/validate-refactor.sh` -- expected: exits 0 (rename fixture scenario applies correctly)
- `stylua lua/ ftplugin/ init.lua` -- expected: no diff

**Manual checks (if no CLI):**
- Open a Java file with a class referenced elsewhere and a Spring XML bean entry; trigger `<leader>cr`; confirm the quickfix lists both the code reference and the XML bean location before applying.

## Suggested Review Order

**Core rename flow**

- Entry point: finds the JVM client, guards empty/no-op names, checks re-entrancy before anything else runs.
  [`refactor.lua:248`](../../lua/cumulus/util/refactor.lua#L248)

- Requests the WorkspaceEdit without auto-applying; timeout guard so a silent server can't hang the flow.
  [`refactor.lua:304`](../../lua/cumulus/util/refactor.lua#L304)

- Kicks off the package-scoped Spring scan once the LSP response is in hand.
  [`refactor.lua:344`](../../lua/cumulus/util/refactor.lua#L344)

- Confirm gate, then pcall-guarded apply with partial-failure reporting instead of a blanket success claim.
  [`refactor.lua:380`](../../lua/cumulus/util/refactor.lua#L380)

**Corruption fixes: duplicate occurrences & LSP/Spring overlap**

- Root-cause fix: every whole-word occurrence on a line gets its own span, not just the first.
  [`refactor-treesitter.lua:65`](../../lua/cumulus/util/refactor-treesitter.lua#L65)

- Dedupes raw hits to one per (file, line) before classifying, so duplicate occurrences can't double-classify.
  [`refactor-treesitter.lua:491`](../../lua/cumulus/util/refactor-treesitter.lua#L491)

- Drops any Spring-scanned location that overlaps something the LSP edit already covers.
  [`refactor.lua:108`](../../lua/cumulus/util/refactor.lua#L108)

- Applies the Spring splices; now failure-tracked per file instead of silently swallowing errors.
  [`refactor.lua:176`](../../lua/cumulus/util/refactor.lua#L176)

**Package-scoping guard (cross-package name collisions)**

- Extracts a file's declared package so same-named classes in other packages can be excluded.
  [`refactor-treesitter.lua:84`](../../lua/cumulus/util/refactor-treesitter.lua#L84)

- XML bean classification also extracts the FQN package prefix off the `class` attribute.
  [`refactor-treesitter.lua:105`](../../lua/cumulus/util/refactor-treesitter.lua#L105)

**XML comment filtering**

- Lightweight multi-line `<!-- -->` state scan so a commented-out bean is never treated as live.
  [`refactor-treesitter.lua:221`](../../lua/cumulus/util/refactor-treesitter.lua#L221)

**Tree-sitter precision filter (parse-once-per-file)**

- Parses each file's tree once and reuses it across every hit, instead of re-parsing per occurrence.
  [`refactor-treesitter.lua:264`](../../lua/cumulus/util/refactor-treesitter.lua#L264)

**Buffer-local keymap wiring**

- Overrides `<leader>cr` for JDTLS-attached buffers only; global mapping for other filetypes untouched.
  [`java.lua:65`](../../ftplugin/java.lua#L65)

- Mirrors the same override for Kotlin LS.
  [`lsp-kotlin.lua:61`](../../lua/cumulus/plugins/lsp-kotlin.lua#L61)

**Tests & fixtures**

- Unit test proving the duplicate-occurrence corruption bug is fixed (the reviewer's exact repro line).
  [`refactor_spec.lua:267`](../../lua/cumulus/tests/refactor_spec.lua#L267)

- Unit test for the LSP/Spring overlap filter.
  [`refactor_spec.lua:110`](../../lua/cumulus/tests/refactor_spec.lua#L110)

- Unit test proving a same-named class in a different package is left untouched.
  [`refactor_spec.lua:316`](../../lua/cumulus/tests/refactor_spec.lua#L316)

- Real (non-substring-match) keymap-installation test: invokes `on_attach`, asserts a genuine mapping via `vim.fn.maparg`.
  [`refactor_spec.lua:383`](../../lua/cumulus/tests/refactor_spec.lua#L383)

- End-to-end fixture covering duplicate occurrences, LSP/Spring overlap, a commented bean, and a cross-package tree.
  [`validate-refactor.sh:119`](../../scripts/validate-refactor.sh#L119)
