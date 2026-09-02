---
title: 'Bmad-Review Findings Fixes (SPEC-2.1/2.2/3.1/3.2 + devops.lua)'
type: 'bugfix'
created: '2026-08-31'
status: 'done'
review_loop_iteration: 0
context: []
baseline_commit: '13f9835702a51d857f6ce8ec956970396bbabfae'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** A full bmad-review of the last 9 commits (SPEC-2.1 rename, SPEC-2.2 extraction, SPEC-3.1 DB explorer, SPEC-3.2 HTTP client, plus an incidental devops.lua fix) surfaced 32 findings: real correctness bugs, missing edge-case handling, and untested code paths. Left unfixed, the worst is a cross-package Spring rename bug that defeats SPEC-2.1's core safety promise.

**Approach:** Fix each finding in place, file by file, per the Code Map below. Three findings were verified as false positives during planning and are explicitly excluded (see Never). One finding (devops.lua ordering) already has its code fix shipped; only its missing regression test remains.

## Boundaries & Constraints

**Always:** Preserve every existing passing test/assertion. New notify_warn/notify_err text follows each file's existing tone (see surrounding calls). New tests use the `cquit`-based pattern (`vim.cmd('cquit 1')` on failure), never `validate.sh`'s `+lua assert(...)` pattern, for anything that must reliably fail CI/manual runs.

**Ask First:** None anticipated — every fix is additive/corrective within existing function boundaries.

**Never:** Do not add `--no-ignore` to the `rg` scan in refactor-treesitter.lua (gitignore-honoring is intentional, matches the grep fallback's explicit `--exclude-dir`s). Do not add a "zero datasources found" warning to tools-dadbod.lua's `init()` (would spam non-Spring SQL buffers). Do not touch `http.lua`'s jq timeout detection (`code==124`/`signal==15` verified correct empirically). Do not attempt full nested-`${...}` placeholder resolution in db.lua — report as unresolved instead.

</frozen-after-approval>

## Code Map

- `lua/tetravim/util/refactor-treesitter.lua:548-551` -- `scan_root_async`'s JVM `package_ok` check compares candidate file's own package to `old_package` only; misses cross-package `@Autowired` usage.
- `lua/tetravim/util/refactor.lua` -- `M._show_preview` (apply_workspace_edit, ~line 416), `M._on_rename_response` (root fallback, ~line 367), `project_rename` (busy-flag on ui.input, ~line 269-284).
- `lua/tetravim/util/extract.lua` -- `handle_action_response` (ambiguous first-match selection), `do_action` (visual-mode byte-vs-char offsets), both share the new lock module.
- New `lua/tetravim/util/action-lock.lua` -- shared busy lock consumed by refactor.lua and extract.lua (replaces each module's own `M._busy`).
- `lua/tetravim/util/db.lua` -- `parse_properties_lines` (`:`/whitespace separators), `parse_yaml_lines` (explicit empty-string scalar), `resolve_placeholders` (empty-string-vs-unset, nested placeholder), `jdbc_to_dadbod_url` (already-has-credentials), `find_files`/`discover_datasources` (MAX_DEPTH truncation warning).
- `lua/tetravim/plugins/tools-dadbod.lua` -- extract discovery+assign into a named local fn; add `DirChanged` autocmd re-invoking it.
- `lua/tetravim/util/openapi.lua` -- `method_entries` loop (operation-level `$ref` skip+warn), `base_url` resolution (unresolved `{var}` template warning), `build_request_block` (BODY_METHODS empty-body placeholder, path-param TODO comments).
- `lua/tetravim/core/keymaps.lua:191-205` -- `<leader>Hj` add a soft JSON-shape check before jq_filter.
- `lua/tetravim/health.lua` -- add SPEC-2.1 (`rg`/`grep`) and SPEC-3.1 (`vim-dadbod-completion`, `sql` treesitter parser) sections, mirroring the existing SPEC-3.2 section (~line 187).
- `lua/tetravim/tests/extract_spec.lua:36-51` -- replace the regex-only java.lua test with a dynamic one, mirroring `refactor_spec.lua:374-405`'s `loadfile` + mocked `jdtls.start_or_attach` pattern, covering all 5 keymaps (ce/ci/cm/cv/cc).
- `scripts/validate-extract.sh` -- currently corrupted (2.3M lines, ~184 unique); replace wholesale with a real behavioral smoke test mirroring `validate-refactor.sh`'s mocked-LSP-seam pattern, exercising `do_action`/`handle_action_response` for at least `extract_method`.
- `scripts/validate-refactor.sh` -- extend fixture with a cross-package consumer that imports the renamed class (proves the package_ok fix); add a case forcing the grep fallback (stub `vim.system` to fail the `rg` call) to cover `M._grep_fallback`.
- `scripts/validate-http.sh` -- add fixture paths for a whole-path-item `$ref` (already-implemented, untested) and an operation-level `$ref` (new fix), asserting both are skipped with a warning and other operations still generate.
- New `scripts/validate-devops.sh` -- cquit-based regression guard: real (unmocked) calls to `find_tf_root`/`find_cfn_root`/`find_ansible_root`/`find_docker_root`/`find_helm_root` must not error, guarding the `resolve_search_dir`/`create_root_finder` declaration order.

## Tasks & Acceptance

**Execution:**
- [x] `refactor-treesitter.lua` -- also accept a candidate whose file imports `old_package.<symbol>` (exact or `.*` wildcard) -- fixes cross-package @Autowired scoping.
- [x] `refactor.lua` -- wrap `apply_workspace_edit` call in `eventignore="all"`; on nil `root_dir`, warn and skip the Spring scan instead of falling back to cwd; `pcall` the `vim.ui.input` call in `project_rename`, resetting `M._busy` on throw.
- [x] New `action-lock.lua` -- `is_busy()`/`acquire()`/`release()`; wire into `refactor.lua` and `extract.lua`, removing their private `M._busy`.
- [x] `extract.lua` -- collect ALL matching actions in `handle_action_response`; if >1, `vim.ui.select` the titles before preview; convert visual-mode byte columns to LSP character offsets via `vim.lsp.util.character_offset`.
- [x] `db.lua` -- properties parser accepts `:`/whitespace separators; YAML parser accepts an explicit `""`/`''` empty scalar; `resolve_placeholders` treats an explicitly-empty env/dotenv value as resolved (not unset) and reports (not corrupts) a nested `${...}` default; `jdbc_to_dadbod_url` returns nil when the URL authority already has an `@`; `find_files`/`discover_datasources` warn once when MAX_DEPTH truncated the scan.
- [x] `tools-dadbod.lua` -- factor discovery+assign into a local fn, called from both `init()` and a new `DirChanged` autocmd in `config()`.
- [x] `openapi.lua` -- skip+warn an operation whose value is itself `{"$ref": ...}`; warn when `base_url` contains an unresolved `{var}`; emit a blank-line + `{}` body for BODY_METHODS blocks; emit a `# TODO` comment per unresolved `{param}` path segment.
- [x] `keymaps.lua` -- `<leader>Hj` warns (does not abort) when the buffer doesn't `vim.json.decode`.
- [x] `health.lua` -- two new `vim.health.start()` sections per Code Map.
- [x] `extract_spec.lua` -- dynamic java.lua keymap test per Code Map.
- [x] `validate-extract.sh` -- rewritten from scratch, cquit-based, real behavioral coverage.
- [x] `validate-refactor.sh` -- cross-package-import fixture case + grep-fallback case.
- [x] `validate-http.sh` -- two `$ref` fixture cases.
- [x] New `validate-devops.sh` -- regression guard per Code Map.

**Additional fixes made during implementation (not in the original Code Map, but load-bearing for it):**
- `refactor-treesitter.lua`'s `scan_root_async` had a pre-existing missing `end` for its `for i = file_idx, chunk_end do` chunking loop, a genuine Lua syntax error that made the ENTIRE file fail to `require` (confirmed: `scripts/validate-refactor.sh` failed at step 1 against the original committed file, before any Code Map fix could even run). Fixed by closing the loop; also fixed the resulting mis-indentation via `stylua`.
- `db.lua`'s new `find_files` now returns `(results, truncated)`; one existing call site (`vim.list_extend(yml_files, find_files(...))`) needed parenthesizing to avoid passing `truncated` as `vim.list_extend`'s `start` argument.
- `resolve_placeholders` reads env vars via `vim.uv.os_getenv` instead of `vim.env[name]` -- `vim.env` collapses an explicitly-empty variable (`FOO=`) to Lua `nil`, which would have silently defeated the empty-string-vs-unset fix.
- Updated the two pre-existing `M._busy`-based tests (`refactor_spec.lua`, `extract_spec.lua`) to drive the shared `action-lock.lua` instead, since `M._busy` no longer exists; added a new cross-module lock integration test.
- `stylua` incidentally reformatted two pre-existing trailing-whitespace violations in `ftplugin/java.lua` and `lua/tetravim/plugins/lsp-kotlin.lua` (unrelated to this bugfix's scope, whitespace-only).

**Acceptance Criteria:**
- Given a class renamed via `<leader>cr`, when a different-package file `@Autowired`-injects it by imported simple name, then that file's reference is included in the rename preview and applied.
- Given an extract/inline action with multiple ambiguous LSP-provided candidates, when triggered, then the user is prompted to pick one before any preview/apply.
- Given a Spring YAML config with `spring.datasource.password: ""`, when discovery runs, then the connection is NOT skipped as incomplete.
- Given an OpenAPI operation expressed as `{"$ref": ...}`, when generating .http text, then it is skipped with a warning, not rendered as a garbage block.
- Given `scripts/validate.sh` and the three new/rewritten `validate-*.sh` scripts, when run, then all exit 0 and no script relies solely on `validate.sh`'s non-propagating assert pattern for these regressions.

## Spec Change Log

**Round 2 (post-implementation adversarial review of the diff against baseline_commit):** 12 additional findings triaged and verified by the coordinator, all applied:
1. `tools-dadbod.lua`: `discover_and_assign_datasources()` now always assigns `vim.g.dbs = dbs` on a successful scan (even empty), so switching into a zero-datasource project correctly clears the list.
2. `tools-dadbod.lua`: the `DirChanged` autocmd now only re-runs discovery when `vim.v.event.scope == "global"`, never for window/tab-local `:lcd`/`:tcd`.
3. `db.lua`: `resolve_placeholders` no longer indexes `vim.uv.os_getenv`/`dotenv` with a nil/empty key for a nameless placeholder (`${}`, `${:default}`) -- treated as unresolved instead of throwing and aborting the whole scan.
4. `db.lua`: an unterminated placeholder (`${VAR` with no closing `}`) is now also reported in `unresolved`, matching every other failure path.
5. `extract.lua` / `refactor.lua`: the three remaining `vim.ui.select(...)` confirm/disambiguation calls are now `pcall`-wrapped, releasing `action_lock` on a synchronous throw (matching the `vim.ui.input` pattern already in `project_rename`).
6. `openapi.lua`: `build_request_block`'s path-parameter TODO loop now dedupes by name (`/a/{id}/b/{id}` emits one TODO, not two).
7. `keymaps.lua`: `<leader>Hj`'s JSON-shape check now falls back to per-line decode (JSON Lines) before warning, avoiding a false positive on legitimate jq input `vim.json.decode` rejects only because it isn't a single top-level value.
8. `refactor_spec.lua`: the cross-module lock test's risky assertions are now `pcall`-wrapped with an unconditional `action_lock.release()` before re-raising, so a failed assertion there can no longer strand the lock for the rest of the busted run.
9. `refactor.lua`: rewrapped a doc-comment line that broke the file's ~80-column convention.
10. `validate-db.sh`: added DirChanged re-discovery + zero-datasources-clears coverage, an explicitly-empty `.env`-wins-over-default case, a genuine bare-whitespace `.properties` separator line in the existing fixture, an unterminated-placeholder case, and a nameless-placeholder (`${}`/`${:default}`) case -- 24 steps total (was 20).
11. `validate-http.sh`: added a case for the unresolved server-URL `{variable}` template warning -- 14 steps total (was 13).
12. `refactor_spec.lua`: added a unit test for the no-`root_dir` skip path in `M._on_rename_response` (warns naming the client, never invokes `scan_root_async`, releases the lock on both Apply and Cancel).

## Design Notes

Shared lock module (`action-lock.lua`) is a ~15-line table with three functions; both `refactor.lua` and `extract.lua` `require` it instead of keeping a private `M._busy`, closing the cross-module race without changing either module's public API.

## Verification

**Commands:**
- `bash scripts/validate.sh` -- expected: all 7 sections PASS.
- `bash scripts/validate-refactor.sh` -- expected: PASS, including new cross-package and grep-fallback cases.
- `bash scripts/validate-extract.sh` -- expected: PASS (rewritten).
- `bash scripts/validate-http.sh` -- expected: PASS, including new `$ref` cases.
- `bash scripts/validate-db.sh` -- expected: PASS.
- `bash scripts/validate-devops.sh` -- expected: PASS (new).
- `stylua lua/ ftplugin/ init.lua --check` -- expected: no diff.

## Suggested Review Order

**Cross-package Spring rename correctness (the highest-severity fix)**

- Entry point: a same-file-package-only check missed the common cross-package `@Autowired` case.
  [`refactor-treesitter.lua:582`](../../lua/tetravim/util/refactor-treesitter.lua#L582)

- New helper admitting a cross-package candidate that imports the renamed symbol.
  [`refactor-treesitter.lua:110`](../../lua/tetravim/util/refactor-treesitter.lua#L110)

- Root-dir cwd-fallback removed -- warns and skips the Spring scan rather than guessing the wrong tree.
  [`refactor.lua:383`](../../lua/tetravim/util/refactor.lua#L383)

**Shared action-lock (cross-module race prevention)**

- New ~15-line shared lock replacing each module's private `M._busy`.
  [`action-lock.lua:18`](../../lua/tetravim/util/action-lock.lua#L18)

- `refactor.lua` now requires the shared lock instead of its own flag.
  [`refactor.lua:37`](../../lua/tetravim/util/refactor.lua#L37)

- Every `vim.ui.select`/`vim.ui.input` call is `pcall`-wrapped so a throwing UI provider can't strand the lock.
  [`refactor.lua:436`](../../lua/tetravim/util/refactor.lua#L436)

- Same `pcall`-around-`vim.ui.select` guard applied to extract.lua's two confirm/disambiguation prompts.
  [`extract.lua:122`](../../lua/tetravim/util/extract.lua#L122)

**Extract: disambiguation and multi-byte correctness**

- Collects every matching code action instead of picking the first; prompts when ambiguous.
  [`extract.lua:122`](../../lua/tetravim/util/extract.lua#L122)

- Visual-mode selection converted from byte columns to LSP character offsets.
  [`extract.lua:215`](../../lua/tetravim/util/extract.lua#L215)

**DB credential discovery edge cases**

- `resolve_placeholders`: nested/unterminated/nameless placeholders now reported unresolved instead of corrupting the value or throwing.
  [`db.lua:270`](../../lua/tetravim/util/db.lua#L270)

- JDBC URL whose authority already carries credentials is now skipped rather than double-spliced.
  [`db.lua:364`](../../lua/tetravim/util/db.lua#L364)

- `.properties`/`.yml` parsers: `:`/whitespace separators and explicit empty-string scalars.
  [`db.lua:105`](../../lua/tetravim/util/db.lua#L105)

- `DirChanged` re-runs discovery on project switch, scoped to global-only `:cd` (not `:lcd`/`:tcd`).
  [`tools-dadbod.lua:67`](../../lua/tetravim/plugins/tools-dadbod.lua#L67)

**OpenAPI `$ref` and template handling**

- Operation-level `$ref` (as opposed to whole-path-item `$ref`, already handled) now skipped with a warning.
  [`openapi.lua:193`](../../lua/tetravim/util/openapi.lua#L193)

- Unresolved `{variable}` template in a server URL now warns instead of silently baking in the literal.
  [`openapi.lua:149`](../../lua/tetravim/util/openapi.lua#L149)

- Path-parameter TODO comments deduped by name.
  [`openapi.lua:61`](../../lua/tetravim/util/openapi.lua#L61)

**DevOps regression guard**

- New standalone script proving `find_tf_root`/etc. are real-callable, guarding the `resolve_search_dir` declaration-order bug `validate.sh`'s assert pattern can't reliably catch.
  [`validate-devops.sh:1`](../../scripts/validate-devops.sh#L1)

**Peripherals: health checks, keymaps, tests**

- New `:checkhealth` sections for SPEC-2.1 (`rg`/`grep`) and SPEC-3.1 (dadbod-completion, sql parser).
  [`health.lua:116`](../../lua/tetravim/health.lua#L116)

- `<leader>Hj` now soft-warns on non-JSON input without false-positiving on JSON Lines.
  [`keymaps.lua:217`](../../lua/tetravim/core/keymaps.lua#L217)

- Rewritten from a corrupted 2.3M-line file into a real behavioral smoke test for the extract dispatch pipeline.
  [`validate-extract.sh:1`](../../scripts/validate-extract.sh#L1)
