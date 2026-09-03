- source_spec: `_bmad-output/implementation-artifacts/spec-1-1-advanced-jvm-debugger-nvim-dap-integration.md`
  summary: `.github/workflows/ci.yml` only runs `sbt test` against the now-removed Scala `engine/` backend and will fail on every push/PR until it's replaced with a Lua-native check (e.g. `stylua --check` + `scripts/validate.sh`).
  evidence: Discovered when the story 1-1 implementation subagent silently deleted the file as an undisclosed side-effect; reverted since removing CI outright wasn't authorized by the spec. The underlying breakage predates story 1-1 — it stems from the Scala engine purge commit (76bae38) — and deserves its own scoped fix rather than being folded into this debugger story.

- source_spec: `_bmad-output/implementation-artifacts/spec-1-1-advanced-jvm-debugger-nvim-dap-integration.md`
  summary: `scripts/validate.sh`'s `nvim --headless "+lua ... assert(...)" +qa` pattern never propagates a non-zero exit code on Lua assertion failure, so its `if...then PASSED else FAILED` blocks report "PASSED" unconditionally regardless of what actually happened inside — a systemic, repo-wide test-harness gap affecting all 7 of its existing checks, not just the ones touched here.
  evidence: Confirmed directly: `nvim -u init.lua --headless "+lua assert(false, 'x')" +qa` exits 0. Worked around it for this story only by adding a separate `scripts/validate-dap-jvm.sh` using `vim.cmd('cquit 1')` on failure; the shared script's own harness bug is out of scope to fix here (it also has unrelated pre-existing failures further down — a missing `devops.resolve_search_dir` export and a `blink.cmp` dependency not installed in this sandbox — that a real fix would need to untangle first).

- source_spec: `_bmad-output/implementation-artifacts/spec-1-1-advanced-jvm-debugger-nvim-dap-integration.md`
  summary: The new `scripts/validate-dap-jvm.sh` (the one script with a trustworthy exit code) has no automated caller — `.github/workflows/ci.yml` only runs `sbt test` (itself deferred above) and `scripts/install-cn.sh`'s `HEALTH_SCRIPT` still points only at the broken `scripts/validate.sh`. It only catches regressions if someone remembers to run it by hand.
  evidence: Flagged independently by the code-review blind-hunter layer. Wiring it in properly depends on the CI rewrite already deferred above (replacing the dead `sbt test` job), so bundling that here would conflate two separate fixes.

- source_spec: `_bmad-output/implementation-artifacts/spec-1-1-advanced-jvm-debugger-nvim-dap-integration.md`
  summary: A `.scala`/`.sbt` buffer opened before mason-tool-installer's async `VimEnter` install of `metals` finishes will fail to attach (`initialize_or_attach` can't find the `metals` executable yet) on a fresh install.
  evidence: Flagged by the code-review edge-case-hunter layer. This is a pattern-level risk shared by every Mason-managed, ft-gated LSP in the repo (jdtls, kotlin-language-server, etc. have the same fresh-install race) — not unique to Scala or introduced by this story — so it deserves a single cross-cutting fix (e.g. a shared "wait for mason install" helper) rather than a one-off patch here.

- source_spec: `_bmad-output/implementation-artifacts/spec-1-1-advanced-jvm-debugger-nvim-dap-integration.md`
  summary: Scala/Metals integration added by this story covers only zero-config LSP attach + DAP registration, matching AC-1's literal scope. Full IDE-parity Metals UX is still missing: no `conform` formatter entry for `scala` (unlike its Java/Kotlin/Groovy siblings), no `:checkhealth tetravim` section for the Metals/sbt/coursier toolchain, no Metals-specific commands/keymaps (Import Build, Restart Build Server, compile/import status, worksheet eval), `metals.bare_config()` is used with no `settings` table (no status-bar/code-lens/decoration-provider feedback), and no custom `LspAttach` message for Metals in `lsp-core.lua` (unlike jdtls's).
  evidence: Flagged by the code-review blind-hunter layer. All of these are legitimate quality/completeness gaps but sit outside this story's explicit acceptance criteria (which only call for zero-config debugger setup, not full Metals workflow tooling) — worth a follow-up story rather than scope creep into this one.

- source_spec: `_bmad-output/implementation-artifacts/spec-2-1-project-wide-safe-rename-move.md`
  summary: Wire an oil.nvim move/rename action hook (`refactor.on_file_moved`) that asynchronously fixes a moved Java/Kotlin file's package declaration and cross-file importers.
  evidence: Split off to keep the spec within the 900-1600 token target; the project-wide rename (LSP + Spring-reference validation + quickfix preview) is the larger, more novel piece and stands alone as a shippable goal. The move-hook is independently shippable once oil.nvim's actual installed-version action/callback API is confirmed at implementation time.
- source_spec: `/home/petrolal/tetravim.nvim/_bmad-output/implementation-artifacts/spec-2-2-intelligent-extraction-methods-variables-interfaces.md`
  summary: Execute validate-*.sh scripts in the main validate.sh suite.
  evidence: scripts/validate-extract.sh and lua/tetravim/tests/extract_spec.lua are not currently run automatically by validate.sh, meaning regressions might ship undetected.

- source_spec: `_bmad-output/implementation-artifacts/spec-review-findings-fixes.md`
  summary: `refactor-treesitter.lua`'s `file_imports_symbol` cross-package scoping fix only recognizes a plain `import pkg.Symbol;`/`import pkg.*;` — it misses Kotlin `import pkg.Symbol as Alias` and Java `import static pkg.Class.Symbol;`, so a cross-package `@Autowired` consumer using either form is silently excluded from the project-wide rename preview.
  evidence: Flagged by the code-review edge-case-hunter and blind-hunter layers on the review-findings-fixes diff. The plain-import case (the common Spring idiom) is fixed and tested; extending the regex to cover static/aliased imports is a small but separate, open-ended widening of import-syntax coverage rather than part of the original cross-package bug being fixed.

- source_spec: `_bmad-output/implementation-artifacts/spec-review-findings-fixes.md`
  summary: `openapi.lua`'s OpenAPI `$ref` handling covers whole-path-item and operation-level refs but not a `$ref` inside an operation's `parameters` array, which can still silently produce an incomplete/garbage request block.
  evidence: Flagged by the code-review blind-hunter layer on the review-findings-fixes diff. Handling parameter-level `$ref`s is a meaningfully broader feature (resolving `#/components/parameters/...` against the spec's `components` section) than the two `$ref` shapes this bugfix pass targeted.

- source_spec: `_bmad-output/implementation-artifacts/spec-4-1-advanced-git-conflict-resolution.md`
  summary: `tetravim.util.git.in_worktree()` probes Neovim's process CWD via `git rev-parse`, not the current buffer's directory, so editing a file that lives in a git work tree while CWD is elsewhere (a common multi-project setup) is wrongly blocked by the guard, and the converse could open a view against the wrong repo.
  evidence: Flagged by the code-review blind-hunter layer. Left as-is for Story 4.1 because diffview.nvim itself operates on CWD's repo, so a CWD-scoped guard is at least self-consistent with what `:DiffviewOpen` would do anyway. Worth revisiting when Story 4.2 reuses this same guard for forge commands, where buffer-vs-CWD repo divergence matters more — likely wants a shared "repo root for buffer" resolver.

- source_spec: none
  summary: Fix `scripts/validate.sh`'s `nvim --headless "+lua assert(...)" +qa` pattern so a Lua assertion failure propagates a non-zero exit code, across all 7 of its existing checks (switch to `vim.cmd('cquit 1')` on failure, as the `validate-*.sh` scripts already do), so its PASSED/FAILED reporting is trustworthy.
  evidence: Split from the SPEC-4.1 bmad-review remediation (Goal S). The review's verification-gap and adversarial lenses flagged that validate-4-1.sh is not wired into validate.sh; wiring it in is part of the 4.1 remediation, but the underlying harness bug is a systemic, repo-wide gap affecting every check in validate.sh (already recorded against spec-1-1) and deserves its own scoped pass with before/after verification of all 7 checks rather than riding along with a single-story review fix.

- source_spec: `_bmad-output/implementation-artifacts/spec-4-1-review-remediation.md`
  summary: Apply the bmad-review structure/prose findings to `spec-4-1-advanced-git-conflict-resolution.md` (non-frozen sections only): reconcile the `diff3_mixed` vs `diff4_mixed` naming drift to `diff4_mixed` and state it once with rationale, add a Spec Change Log entry, condense Acceptance Criteria 1-5 into a pointer at the I/O matrix, fold the Verification "Commands" into the ACs, condense Suggested Review Order to a bare list + file:line links, and apply the 5 prose fixes (decode `BASE(over)`, split "`false` + `notify_err`", "rebound off" -> "remapped away from", the double-"and" errors bullet, the garden-path ", and use").
  evidence: Split from the SPEC-4.1 review-remediation spec to bring it under the 1600-token ceiling. Pure documentation edits to a `status: done` spec's non-frozen sections; touches no code, shares no review surface with the runtime hardening, and ships as its own trivial PR.

- source_spec: `_bmad-output/implementation-artifacts/spec-4-1-review-remediation.md`
  summary: Harden `scripts/validate-4-1.sh` and wire it into `scripts/validate.sh`: guard the lockfile restore with `[ -s "$LOCK_SNAPSHOT" ]` (0-byte lazy-lock.json corruption risk), wrap every `nvim --headless` call in `timeout`, assert each `mktemp -d` is not inside a work tree, add missing coverage stages (clean-tree/no-merge `<leader>gco`, scratch/modified/untracked/unborn-HEAD history rejects, no-view `<leader>gcq`/`<leader>gcf` no-op, buffer-scoped-repo open, health OK-branch line shape, modify/delete conflict), de-flake stage 7 (one deterministic signal + explicit SKIP, stop clearing the `diffview_nvim` autocmd group mid-run), and add a `[7/7]` stage in `validate.sh` invoking it with a propagated exit code. ALSO fix stage `[4/7]`'s now-stale white-box assertion: the runtime-hardening core made `M.in_worktree()` pure-Lua (`vim.fs.root`), moving the `git`-binary dependency into `M.guard()`, so `assert(git.in_worktree() == false)` under a monkey-patched-absent `git` must become `assert(git.guard() == false)` (keep the paired `health.lua` "git NOT found" check).
  evidence: Split from the SPEC-4.1 review-remediation spec to bring it under the 1600-token ceiling. Test-harness only -- no shipped behavior changes. As of the core's step-03 implementation, `bash scripts/validate-4-1.sh` exits 1 at stage 4 on the stale assertion above (documented in the core spec's Spec Change Log + Verification); the guard/health behavior it should test is correct, only the assertion is white-box-stale. Should land immediately after the runtime-hardening core (it tests that core's new behavior); `git_conflict_spec.lua` static-shape coverage stays with the core so nothing regresses in the interim. Note the `validate.sh` exit-code propagation bug (separate deferred entry) means the `[7/7]` wire-in relies on `validate-4-1.sh`'s own `cquit 1`, which is already trustworthy.
- source_spec: `/home/petrolal/tetravim.nvim/_bmad-output/implementation-artifacts/spec-2-2-intelligent-extraction-methods-variables-interfaces-2.md`
  summary: Kotlin Language Server might use different code action titles than JDTLS, causing extraction to fail.
  evidence: `extract.lua` strictly matches `"Extract to local variable"`, which is JDTLS-specific.
- source_spec: `/home/petrolal/tetravim.nvim/_bmad-output/implementation-artifacts/spec-2-2-intelligent-extraction-methods-variables-interfaces-2.md`
  summary: Java mapping tests use brittle string matching on `ftplugin/java.lua` source.
  evidence: `extract_spec.lua` reads the file text instead of testing the runtime AST/mapping behavior.
- source_spec: `/home/petrolal/tetravim.nvim/_bmad-output/implementation-artifacts/spec-2-2-intelligent-extraction-methods-variables-interfaces-2.md`
  summary: Missing behavioral test for code action title filtering.
  evidence: `validate-extract.sh` mocks responses with exact titles but never tests rejecting a code action with a matching kind but non-matching title.

- source_spec: `/home/petrolal/tetravim.nvim/_bmad-output/implementation-artifacts/spec-4-1-review-remediation.md`
  summary: `scripts/validate-4-1.sh` stage `[4/7]` runs `vim.cmd('cquit 1')` on failure and the script has `set -e`, so once the stale stage-4 assertion fails the script aborts and stages `[5/7]`-`[7/7]` (the only runtime coverage of `<leader>gco` opening the merge tabpage, `<leader>gcq` closing it, `<leader>gch`/`<leader>gcH` file history, and the `<leader>gx*`/`<leader>gX*` conflict-resolution keymaps writing to disk) never execute.
  evidence: Verified in-session -- running `bash scripts/validate-4-1.sh` after the SPEC-4.1 review-remediation implementation stops after the stage-4 `FAIL` line and never prints `[5/7]`+. The existing `validate-4-1.sh` hardening entry names the stage-4 assertion fix (`in_worktree() == false` -> `guard() == false`) but not that fixing it is also what re-enables all downstream behavioral stages; the `conflict_binds`/`scope_flag`/command-string rework this remediation ships is exactly what stages 5-7 exercise, so that coverage is currently dark. Fold into the same `validate-4-1.sh` follow-up: the assertion fix and a non-fatal `set -e` guard on stage 4 must land together so 5-7 run again.

- source_spec: `/home/petrolal/tetravim.nvim/_bmad-output/implementation-artifacts/spec-4-1-review-remediation.md`
  summary: The buffer-local `<leader>gx` / `<leader>gX` which-key groups in `tools-diffview.lua` `config()` register with `buffer = 0` from `User DiffviewViewOpened`/`DiffviewViewEnter` and `FileType DiffviewFiles`/`DiffviewFileHistory`, but the merge-tool diff panes (OURS/BASE/THEIRS/result) are separate buffers carrying the edited file's own filetype, so the custom group label is absent in most panes where the keys are actually pressed.
  evidence: diffview marks the tabpage (`vim.t[tabpage].diffview_view_initialized`) and names only its panel buffers `diffview://...`; the diff panes are plain file buffers. The `<leader>gx*`/`<leader>gX*` keymaps still work (diffview installs them buffer-locally via `keymaps.view`), and which-key still lists them by `desc`, but the `group` label degrades to a generic "+prefix" node in the panes the single `buffer = 0` autocmd did not land on. Follow-up: scope registration via a `BufWinEnter` check on `vim.wo.diff` + the tabpage's `diffview_view_initialized`, or on diffview buffer-name pattern, so every merge pane gets the labelled group.

## Deferred from: code review of spec-2-1-project-wide-safe-rename-move (2026-09-01)

- source_spec: `_bmad-output/implementation-artifacts/spec-2-1-project-wide-safe-rename-move.md`
  summary: Collision detection (matrix row 2 / AC3) depends entirely on JDTLS/Kotlin LS returning `resp.err` for a colliding rename; a `workspace/symbol` pre-check would abort before the quickfix independently of the server. Already disclosed in the frozen-era Spec Change Log.
  evidence: bmad-code-review acceptance-auditor + edge-case-hunter layers, 2026-09-01. `refactor.lua:362` — the only collision guard is `if not resp or resp.err`.

- source_spec: `_bmad-output/implementation-artifacts/spec-2-1-project-wide-safe-rename-move.md`
  summary: `classify_xml_line` requires `<bean` and `class=` on the same physical line; multi-line `<bean id="…"\n  class="…"/>` declarations (a common Spring XML style) are silently missed. Single-line-only is currently undocumented.
  evidence: bmad-code-review blind-hunter + edge-case-hunter + verification-gap layers, 2026-09-01. `refactor-treesitter.lua:130`.

- source_spec: `_bmad-output/implementation-artifacts/spec-2-1-project-wide-safe-rename-move.md`
  summary: `.kts` files are never scanned — `LANG_BY_EXT` maps `kts→kotlin` but the `rg`/`grep` globs only cover `*.kt`/`*.java`/`*.xml`, so Spring references in Kotlin build scripts are omitted from the rename preview.
  evidence: bmad-code-review edge-case-hunter + blind-hunter layers, 2026-09-01. `refactor-treesitter.lua:408` (rg_cmd) / `:452` (grep --include).

- source_spec: `_bmad-output/implementation-artifacts/spec-2-1-project-wide-safe-rename-move.md`
  summary: The `grep` fallback uses GNU-only flags (`-r`, `--include`, `--exclude-dir`); on busybox/Alpine `grep` it errors out and the scan returns `{}` (with a warn), giving zero Spring coverage whenever `rg` is not installed.
  evidence: bmad-code-review blind-hunter + edge-case-hunter layers, 2026-09-01. `refactor-treesitter.lua:447`.

- source_spec: `_bmad-output/implementation-artifacts/spec-2-1-project-wide-safe-rename-move.md`
  summary: `action-lock.lua` has no recovery path — no `reset()`, no user command, no expiry. A single missed `release()` on any of the ~10 terminal paths in `refactor.lua` (or `extract.lua`, which shares the lock) disables both project-wide rename and extract/inline for the rest of the session.
  evidence: bmad-code-review blind-hunter + edge-case-hunter + verification-gap layers, 2026-09-01. `action-lock.lua` exposes only `is_busy`/`acquire`/`release`.

- source_spec: `_bmad-output/implementation-artifacts/spec-2-1-project-wide-safe-rename-move.md`
  summary: Final success count can over-report — `total_applied = #lsp_items + spring_applied` assumes every LSP edit landed, but `vim.lsp.util.apply_workspace_edit` can partially fail (e.g. an unwritable URI) without throwing, so "Renamed N/N location(s)" is not always true.
  evidence: bmad-code-review blind-hunter + edge-case-hunter layers, 2026-09-01. `refactor.lua:469`.

- source_spec: `_bmad-output/implementation-artifacts/spec-2-1-project-wide-safe-rename-move.md`
  summary: No `undojoin` coordination between `apply_workspace_edit` and `apply_spring_edits` when both touch the same file, so one logical rename becomes several undo steps and a single `u` leaves the file half-renamed. Also: touched buffers are left modified/unsaved with no summary or `:wa` hint, which at multi-file scale makes the success toast misleading.
  evidence: bmad-code-review blind-hunter layer, 2026-09-01. `refactor.lua:452`–`:481`.

- source_spec: `_bmad-output/implementation-artifacts/spec-2-1-project-wide-safe-rename-move.md`
  summary: Apply wraps both edit phases in `vim.o.eventignore = "all"` (global, all buffers) rather than a targeted guard against the JVM `FileType`/`LspAttach` autostart it is actually trying to suppress. Restored correctly on every path, but the blast radius is the whole editor for the duration of the apply.
  evidence: bmad-code-review blind-hunter layer, 2026-09-01. `refactor.lua:451`, `refactor.lua:203`.

- source_spec: `_bmad-output/implementation-artifacts/spec-2-1-project-wide-safe-rename-move.md`
  summary: ~35 lines of buffer-local keymap wiring (`<leader>cr` + the five `<leader>c*` extract maps) are duplicated verbatim between `ftplugin/java.lua` on_attach and `lsp-kotlin.lua` on_attach, differing only by the `desc` language label; extract a shared `require`-able helper.
  evidence: bmad-code-review blind-hunter layer, 2026-09-01. `ftplugin/java.lua:65`–`:103` vs `lua/tetravim/plugins/lsp-kotlin.lua:61`–`:100`.

- source_spec: `_bmad-output/implementation-artifacts/spec-2-1-project-wide-safe-rename-move.md`
  summary: `old_name` comes from `vim.fn.expand("<cword>")` with no `textDocument/prepareRename`; a cursor on a non-identifier token or mid-punctuation can make the Spring text scan search a different string than the position-based LSP rename actually targets. Related: `win` is captured before the async `vim.ui.input` and used in `make_position_params(win, …)` with no `nvim_win_is_valid` guard, so a window/cursor move during the prompt shifts the rename position.
  evidence: bmad-code-review blind-hunter + edge-case-hunter layers, 2026-09-01. `refactor.lua:265`, `refactor.lua:257`/`:323`.

- source_spec: `_bmad-output/implementation-artifacts/spec-2-1-project-wide-safe-rename-move.md`
  summary: `refactor_spec.lua` hygiene — `loadfile("ftplugin/java.lua")` is cwd-relative, scratch buffers created by `enew`/`edit` are not cleaned between `it()` blocks, and a failed assertion before `action_lock.release()` leaks the shared singleton lock into later tests. Add an `after_each` that force-releases the lock and wipes scratch buffers, plus a multi-line-XML-comment case for `is_inside_xml_comment`.
  evidence: bmad-code-review blind-hunter + verification-gap layers, 2026-09-01. `refactor_spec.lua:562`, and the `action_lock` usage at `:47`–`:114`.

- source_spec: `_bmad-output/implementation-artifacts/spec-2-1-project-wide-safe-rename-move.md`
  summary: `classify_xml_line`'s `line:find("class%s*=%s*[\"'])` is unanchored, so a `<bean>` line carrying `superclass="com.x.FooService"` (or `data-class=`) but no `class=` is misclassified as a bean-class reference. Anchor with `%f[%a]class%s*=`.
  evidence: bmad-code-review edge-case-hunter layer, 2026-09-01. `refactor-treesitter.lua:134`.

## Deferred from: code review of spec-2-2-intelligent-extraction (…-2 finalization) (2026-09-01)

- source_spec: `_bmad-output/implementation-artifacts/spec-2-2-intelligent-extraction-methods-variables-interfaces-2.md`
  summary: The `codeAction/resolve` command-without-edit fallback in `extract.lua` `proceed_with_action` has zero execution coverage — every mocked codeAction response in `validate-extract.sh` carries `.edit`, and step 7 only inspects outgoing params. Add a deterministic mock-seam step: `buf_request_all` returns a command-only action for `textDocument/codeAction` and `{ result = { edit = … } }` for `codeAction/resolve`; assert the edit applies and `action_lock.is_busy()` is false. Add a sibling case with `codeActionProvider = true` (no `resolveProvider`) asserting a clean WARN + lock release.
  evidence: bmad-code-review verification-gap + blind-hunter layers, 2026-09-01. `lua/tetravim/util/extract.lua:28`–`:71`.

- source_spec: `_bmad-output/implementation-artifacts/spec-2-2-intelligent-extraction-methods-variables-interfaces-2.md`
  summary: `<leader>cm`/`<leader>cv`/`<leader>cc` (Extract Method/Variable/Constant) disambiguate purely on JDTLS English code-action titles (`"Extract to method"` / `"Extract to local variable"` / `"Extract to constant"`) via `string.find(…, 1, true)`. kotlin-language-server emits different titles and limited refactor kinds, so these three keymaps will report "No applicable code action" on every Kotlin buffer — so the finalization AC ("Given a Java/Kotlin buffer, invoking `<leader>cm/cv/cc` … a preview is correctly presented") is not satisfied for Kotlin. Also affects a JDTLS locale change or an appended ellipsis. Widen to kind-based matching with a title fallback, or discover the server's actual titles.
  evidence: bmad-code-review blind-hunter + acceptance-auditor + edge-case-hunter layers, 2026-09-01. Extends the two existing `deferred-work.md` entries on JDTLS-specific title matching. `lua/tetravim/util/extract.lua:74`–`:102`, `:278`–`:288`.

- source_spec: `_bmad-output/implementation-artifacts/spec-2-2-intelligent-extraction-methods-variables-interfaces-2.md`
  summary: `extract.lua` LSP-robustness gaps: `params.context.only = { "refactor.extract.interface" }` / `{ "refactor.inline" }` may be too specific for a server that tags the action with the shorter parent kind; actions with `kind == nil` are silently dropped (`action.kind and vim.startswith(...)`); normal-mode `<leader>cm/cv/cc` and visual `<leader>ce` pass a zero-width / meaningless range that JDTLS mostly rejects; `nvim_feedkeys('<Esc>','x')` to refresh `'<`/`'>` in a `<Cmd>` visual mapping is version-sensitive; 2-arg `make_range_params` (0.10+, deprecated 0.11) and `character_offset` are used with no min-version note / `pcall`; `diagnostics` context only samples the range's start line.
  evidence: bmad-code-review blind-hunter + edge-case-hunter layers, 2026-09-01. Cluster of unverified-without-a-live-LSP concerns. `lua/tetravim/util/extract.lua:97`, `:199`–`:240`.

- source_spec: `_bmad-output/implementation-artifacts/spec-2-2-intelligent-extraction-methods-variables-interfaces-2.md`
  summary: Shared `action-lock` recovery + preview-UX gaps: a dismissed/ignored `vim.ui.select` (confirm or disambiguation) strands the shared lock forever — the outer request timeout has already cleared, there is no stage-timeout, and `action-lock.lua` has no `force_release` / user command (disables extract AND project-rename until Neovim restarts); `_show_preview` calls `copen` unconditionally and never `cclose`s; the disambiguation menu shows identical `action.title` entries with nothing to choose between (append the target range/scope).
  evidence: bmad-code-review blind-hunter + edge-case-hunter layers, 2026-09-01. Same cross-cutting `action-lock` recovery item as the SPEC-2.1 defer. `lua/tetravim/util/extract.lua:115`–`:180`, `lua/tetravim/util/action-lock.lua`.

- source_spec: `_bmad-output/implementation-artifacts/spec-2-2-intelligent-extraction-methods-variables-interfaces-2.md`
  summary: `scripts/validate.sh` (frozen for this session's edits) — the finalization pass deleted `assert(ensure_set['metals'], 'Missing Mason package: metals')` with no replacement (violates its own frozen "Never silently delete failing tests in validate.sh"; metals is still gated by `validate-dap-jvm.sh` + `dap_jvm_spec.lua`), and swapped the `[5/7]` completion-plugin check from `require('blink.cmp')` to `require('cmp')` (follows the finalization CRITICAL-CONSTRAINTS line literally but conflicts with its Code Map "mock blink.cmp properly"; the assertion never gated anyway — non-exit-propagating `+lua` block). Restore the metals assertion and reconcile the completion-engine check in a dedicated validate.sh pass.
  evidence: bmad-code-review acceptance-auditor + verification-gap + blind-hunter layers, 2026-09-01. `scripts/validate.sh` (removed lines around the SPEC-1.1 Mason block and the `[5/7]` UI-spec block).

- source_spec: `_bmad-output/implementation-artifacts/spec-2-2-intelligent-extraction-methods-variables-interfaces-2.md`
  summary: `scripts/validate-extract.sh` has no end-to-end extract-interface or inline scenario (the original spec Execution task listed both) — the rewritten 7-stage script only exercises `extract_method` behaviorally. `lua/tetravim/core/devops.lua`'s `resolve_search_dir` forward-reference hoist was bundled into this extraction feature (legitimate fix, off-topic; `validate-devops.sh` now covers it). The `<leader>jx` which-key group ("refactor & jdtls") now labels only `jxo`/`jxH` after `jxm/jxv/jxc` were removed. Docs drift: `spec-2-1` and the original `spec-2-2` still describe `jvm.lua:436-486` `<leader>jx*` extract as an intact sibling feature; no help/README/which-key text reflects the `<leader>jx{m,v,c}` → `<leader>c{e,i,m,v,c}` move.
  evidence: bmad-code-review blind-hunter + acceptance-auditor layers, 2026-09-01.

- source_spec: none
  summary: A ~80MB / ~2.3M-line corrupted `scripts/validate-extract.sh` blob (from an earlier commit, since replaced by the clean 486-line version) remains in git history, bloating every clone. Needs a `git filter-repo` / history-rewrite + `gc` decision (destructive, coordination-sensitive) and a sweep for any other oversized committed blobs. Not fixable by a code patch.
  evidence: bmad-code-review blind-hunter layer, 2026-09-01 (review of spec-2-2 finalization). The current `scripts/validate-extract.sh` header itself notes it "replaces a previously-corrupted … version of itself".

## Deferred from: code review of spec-3-1-embedded-database-explorer (2026-09-01)

- source_spec: `_bmad-output/implementation-artifacts/spec-3-1-embedded-database-explorer.md`
  summary: `db.lua` only scans the exact filenames `application.properties` / `application.yml` / `application.yaml`. Spring profile-specific config files (`application-local.yml`, `application-dev.properties`, `application-docker.yml`, …), selected via `SPRING_PROFILES_ACTIVE` / `spring.profiles.active`, and multi-document YAML (`---` separators with `spring.config.activate.on-profile`) are never read — projects that keep datasource settings only in a profile file (very common for local dev) silently yield zero connections.
  evidence: bmad-code-review blind-hunter + edge-case-hunter layers, 2026-09-01. `lua/tetravim/util/db.lua` `find_files` / `parse_yaml_lines`. Outside the frozen scope (spec names exactly the three base filenames).

- source_spec: `_bmad-output/implementation-artifacts/spec-3-1-embedded-database-explorer.md`
  summary: `db.lua` recognizes only `spring.datasource.{url,username,password}` and JDBC URLs of the `scheme://authority` shape. Not handled: Hikari keys (`spring.datasource.hikari.jdbc-url` / `jdbcUrl`); non-`://` JDBC URLs (`jdbc:h2:mem:testdb`, `jdbc:oracle:thin:@host:1521:sid`, SQLite, `jdbc:tc:` Testcontainers) → misleading "Could not parse spring.datasource.url"; no driver-name mapping (SQL Server `;databaseName=` params left intact and unusable for dadbod). H2 in particular is ubiquitous in Spring dev/test.
  evidence: bmad-code-review blind-hunter layer, 2026-09-01. `lua/tetravim/util/db.lua` `parse_properties_lines` / `jdbc_to_dadbod_url`. Frozen scope is the three keys + JDBC-style URL.

- source_spec: `_bmad-output/implementation-artifacts/spec-3-1-embedded-database-explorer.md`
  summary: Discovery cost & UX: `init()` runs the recursive walk + file reads synchronously on the main thread during plugin init (startup latency ∝ repo size, even for sessions that never touch a DB); `DirChanged` re-runs the same on every global `:cd` (telescope/oil/project.nvim issue these on routine navigation) with no debounce/cache; malformed-block / unresolved-var warnings re-fire on every re-entry into a project (no warn-once-per-path guard); `MAX_DEPTH = 8` is shallow for deep monorepos and produces a scary truncation warning; symlinked directories (`kind == "link"`) are skipped and a directory-symlink cycle is re-walked to the depth cap; discovery + `.env` load assume `cwd` IS the project root (no upward `.git`/`pom.xml`/`build.gradle` search), so opening Neovim in a submodule misses the real root `.env`; cross-module precedence is global (`if #dbs > 0 then return`) not per-config-directory, so a module with only `application.yml` is dropped when any other module has a `.properties` entry.
  evidence: bmad-code-review blind-hunter + edge-case-hunter + acceptance-auditor layers, 2026-09-01. Cluster of scale/robustness enhancements to `lua/tetravim/util/db.lua` + `lua/tetravim/plugins/tools-dadbod.lua`; the "stateless, re-read every call" design is the spec's explicit choice, so these are additive (debounce, project-marker fast-path, warn-once, per-dir precedence, realpath cycle guard) rather than corrections.

- source_spec: `_bmad-output/implementation-artifacts/spec-3-1-embedded-database-explorer.md`
  summary: Parser robustness gaps in `db.lua`: `.properties` backslash line-continuation and `\uXXXX` escapes not handled (a continued value is truncated to its first physical line); CRLF files; YAML block scalars (`password: |` / `url: >`) store the literal `|`/`>` and drop the real multi-line value; a flat dotted `spring.datasource.url:` key written inside a YAML file is silently ignored by the indentation-stack parser (Spring allows it).
  evidence: bmad-code-review blind-hunter + edge-case-hunter layers, 2026-09-01. `lua/tetravim/util/db.lua` `parse_properties_lines` / `parse_yaml_lines`.

- source_spec: `_bmad-output/implementation-artifacts/spec-3-1-embedded-database-explorer.md`
  summary: No manual re-discovery escape hatch or observability — no `:TetraVimDbRediscover` command, no `BufWritePost application.{yml,properties}` refresh when the user edits config mid-session, and the `:checkhealth` section (health.lua ~line 202) reports only vim-dadbod-completion resolvability + the `sql` Tree-sitter parser, nothing about how many connections discovery produced for the current project or why it produced zero. A user hand-authored `vim.g.dbs` is also clobbered unconditionally on every `init()` / global `DirChanged` (folded into the code-review decision item on `vim.g.dbs` semantics — a "only overwrite our own discovered list" guard would need a sentinel).
  evidence: bmad-code-review blind-hunter + edge-case-hunter layers, 2026-09-01. `lua/tetravim/plugins/tools-dadbod.lua` / `lua/tetravim/health.lua`.

## Deferred from: spec-3-2-review-remediation (2026-09-01)

- source_spec: `_bmad-output/implementation-artifacts/spec-3-2-review-remediation.md`
  summary: SPEC-3.2 verification hardening (Goal B, split from the review-remediation spec on token budget). Add `lua/tetravim/tests/openapi_spec.lua` and `lua/tetravim/tests/http_spec.lua` pure-logic unit tests (fixture-driven `generate_http_from_spec` shape/edge coverage — relative & `"/"` server URLs, `.yaml`/missing paths, non-`/` path keys, newline `operationId`, BOM, required-param TODO lines, `{id}` TODO dedup — plus a `http.looks_like_json` truth table for single-value vs NDJSON vs prose vs whitespace). Add the missing functional rows to `scripts/validate-http.sh` (relative-URL warn, jq timeout ERROR + callback-skip, NDJSON no false warning, `<leader>Hj` on a `.http` buffer error path, exact generated-template shape incl. `# TODO: replace {id}` and its dedup) and bump its `[n/N]` counter. Wire `bash scripts/validate-http.sh` into `scripts/validate.sh` as a numbered step.
  evidence: bmad-review verification-gap lens (G1–G7), 2026-09-01. Split from `spec-3-2-review-remediation.md` at step-02: the code-hardening guards (Goal A) ship independently and are covered at baseline by `validate-http.sh`'s existing 14 steps; the new unit specs + validate rows + `validate.sh` wiring are test-only and independently shippable. Coupling risk accepted: Goal A lands verified only at baseline until this follows.

## Deferred from: spec-2-3-native-spring-boot-discovery-legacy-engine-deprecation (2026-09-03)

- source_spec: `_bmad-output/implementation-artifacts/spec-2-3-native-spring-boot-discovery-legacy-engine-deprecation.md`
  summary: Full decommission of the remaining legacy `tetravim-engine` surface. Story 2.3 removes only the six Spring functions from `lua/tetravim/util/engine.lua`; the other ~64 wrappers (`get_bin`/`install`/`ping`, `discover_jdk`/`discover_build_tool`/`discover_workspace`, `resolve_stacktrace_symbol`, `parse_coverage`, `parse_git_conflicts`, `optimize_imports`, the IaC validators for terraform/ansible/docker/helm/cfn, `discover_devops_roots`, etc.) and every caller of them across epics 1/3/4/5 still route through the downloaded native binary. Needs its own bmad story: audit each caller, decide native-Lua vs standard-LSP vs drop, sequence the migration, then delete `engine.lua` and the binary installer.
  evidence: User directive 2026-09-03 ("we need to decommission all the scala legacy engine") after confirming story 2.3 stays Spring-only. Scala source itself was already removed in `76bae38`; what remains is the Lua shim + prebuilt-binary dependency. Out of 2.3's frozen scope ("Spring discovery + springboot-debug").

- source_spec: `_bmad-output/implementation-artifacts/spec-2-3-native-spring-boot-discovery-legacy-engine-deprecation.md`
  summary: Multi-module / reactor Maven & Gradle layouts are not handled by native Spring discovery. `detect_root` stops at the nearest build-marker (a submodule dir, not the reactor root); `find_main_class` returns the first `@SpringBootApplication` match anywhere under the root; and the DAP/name de-dup keys on `project_name`, so two same-named sibling modules collide. A monorepo with several Spring services under one parent will point the debugger at the wrong module.
  evidence: bmad-review edge-case-hunter + blind-hunter, 2026-09-02 (review iteration 1). Deferred at classify: the frozen I/O matrix and Boundaries name a single project root, so multi-module support is additive, not a deviation.

- source_spec: `_bmad-output/implementation-artifacts/spec-2-3-native-spring-boot-discovery-legacy-engine-deprecation.md`
  summary: Multi-value endpoint mappings collapse to their first entry. `@RequestMapping(method = { RequestMethod.GET, RequestMethod.POST })` yields only `GET`; `@GetMapping({ "/a", "/b" })` keeps only `/a`. The endpoint picker under-reports routes for controllers that fan one handler across several verbs or paths.
  evidence: bmad-review edge-case-hunter, 2026-09-02 (review iteration 1). Deferred: the frozen acceptance list enumerates single-verb annotations; array-valued `method`/`value`/`path` is an enhancement to `endpoint_from_method` / `_endpoints_in_content`.

- source_spec: `_bmad-output/implementation-artifacts/spec-2-3-native-spring-boot-discovery-legacy-engine-deprecation.md`
  summary: Lombok-generated constructors are invisible to Tree-sitter bean parsing. A `@Service` annotated with `@RequiredArgsConstructor` / `@AllArgsConstructor` has no constructor node in the source AST (it is generated at compile time), so `injected_deps` comes back empty and the dependency graph shows the bean with no edges. Lombok constructor injection is extremely common in Spring codebases.
  evidence: bmad-review blind-hunter + edge-case-hunter, 2026-09-02 (review iteration 1). Deferred: recovering these deps means reading `final` fields + `@NonNull` fields when a Lombok constructor annotation is present — a distinct parsing feature beyond the frozen "constructor / @Autowired field / setter" contract.

- source_spec: `_bmad-output/implementation-artifacts/spec-2-3-native-spring-boot-discovery-legacy-engine-deprecation.md`
  summary: Explicit stereotype bean names are ignored. `@Service("customName")` / `@Component("x")` / `@Repository("y")` still key the picker rows and graph edges on the decapitalized class name, so beans wired by their explicit name and referenced that way elsewhere are mislabeled and their edges can fail to connect.
  evidence: bmad-review edge-case-hunter, 2026-09-02 (review iteration 1). Deferred: the frozen Design Notes fix the display name as "decapitalized type"; honoring the annotation's string argument is an additive change to `_beans_in_content`.
