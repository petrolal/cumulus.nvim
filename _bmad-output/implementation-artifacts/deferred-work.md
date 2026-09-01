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
  summary: Scala/Metals integration added by this story covers only zero-config LSP attach + DAP registration, matching AC-1's literal scope. Full IDE-parity Metals UX is still missing: no `conform` formatter entry for `scala` (unlike its Java/Kotlin/Groovy siblings), no `:checkhealth cumulus` section for the Metals/sbt/coursier toolchain, no Metals-specific commands/keymaps (Import Build, Restart Build Server, compile/import status, worksheet eval), `metals.bare_config()` is used with no `settings` table (no status-bar/code-lens/decoration-provider feedback), and no custom `LspAttach` message for Metals in `lsp-core.lua` (unlike jdtls's).
  evidence: Flagged by the code-review blind-hunter layer. All of these are legitimate quality/completeness gaps but sit outside this story's explicit acceptance criteria (which only call for zero-config debugger setup, not full Metals workflow tooling) — worth a follow-up story rather than scope creep into this one.

- source_spec: `_bmad-output/implementation-artifacts/spec-2-1-project-wide-safe-rename-move.md`
  summary: Wire an oil.nvim move/rename action hook (`refactor.on_file_moved`) that asynchronously fixes a moved Java/Kotlin file's package declaration and cross-file importers.
  evidence: Split off to keep the spec within the 900-1600 token target; the project-wide rename (LSP + Spring-reference validation + quickfix preview) is the larger, more novel piece and stands alone as a shippable goal. The move-hook is independently shippable once oil.nvim's actual installed-version action/callback API is confirmed at implementation time.
- source_spec: `/home/petrolal/cumulus.nvim/_bmad-output/implementation-artifacts/spec-2-2-intelligent-extraction-methods-variables-interfaces.md`
  summary: Execute validate-*.sh scripts in the main validate.sh suite.
  evidence: scripts/validate-extract.sh and lua/cumulus/tests/extract_spec.lua are not currently run automatically by validate.sh, meaning regressions might ship undetected.

- source_spec: `_bmad-output/implementation-artifacts/spec-review-findings-fixes.md`
  summary: `refactor-treesitter.lua`'s `file_imports_symbol` cross-package scoping fix only recognizes a plain `import pkg.Symbol;`/`import pkg.*;` — it misses Kotlin `import pkg.Symbol as Alias` and Java `import static pkg.Class.Symbol;`, so a cross-package `@Autowired` consumer using either form is silently excluded from the project-wide rename preview.
  evidence: Flagged by the code-review edge-case-hunter and blind-hunter layers on the review-findings-fixes diff. The plain-import case (the common Spring idiom) is fixed and tested; extending the regex to cover static/aliased imports is a small but separate, open-ended widening of import-syntax coverage rather than part of the original cross-package bug being fixed.

- source_spec: `_bmad-output/implementation-artifacts/spec-review-findings-fixes.md`
  summary: `openapi.lua`'s OpenAPI `$ref` handling covers whole-path-item and operation-level refs but not a `$ref` inside an operation's `parameters` array, which can still silently produce an incomplete/garbage request block.
  evidence: Flagged by the code-review blind-hunter layer on the review-findings-fixes diff. Handling parameter-level `$ref`s is a meaningfully broader feature (resolving `#/components/parameters/...` against the spec's `components` section) than the two `$ref` shapes this bugfix pass targeted.

- source_spec: `_bmad-output/implementation-artifacts/spec-4-1-advanced-git-conflict-resolution.md`
  summary: `cumulus.util.git.in_worktree()` probes Neovim's process CWD via `git rev-parse`, not the current buffer's directory, so editing a file that lives in a git work tree while CWD is elsewhere (a common multi-project setup) is wrongly blocked by the guard, and the converse could open a view against the wrong repo.
  evidence: Flagged by the code-review blind-hunter layer. Left as-is for Story 4.1 because diffview.nvim itself operates on CWD's repo, so a CWD-scoped guard is at least self-consistent with what `:DiffviewOpen` would do anyway. Worth revisiting when Story 4.2 reuses this same guard for forge commands, where buffer-vs-CWD repo divergence matters more — likely wants a shared "repo root for buffer" resolver.
