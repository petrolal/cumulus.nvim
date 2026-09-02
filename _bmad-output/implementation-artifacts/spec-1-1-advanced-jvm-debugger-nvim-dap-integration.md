---
title: 'Advanced JVM Debugger (nvim-dap integration)'
type: 'feature'
created: '2026-08-25'
status: 'done'
review_loop_iteration: 0
context: []
baseline_commit: '76bae380e6dbe32261d77887c27a512588821679'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** tetravim.nvim already wires DAP for Java (jdtls's own `setup_dap` with hotswap) and Kotlin (a standalone `kotlin-debug-adapter` config), but Scala has zero debugger coverage, and no language shares conditional breakpoints, logpoints, exception breakpoints, or an interactive variable-eval keymap.

**Approach:** Add an nvim-metals-based DAP setup for Scala (mirroring how jdtls self-registers its DAP hooks), and extend the existing global `<leader>d*` DAP keymap file with generic conditional-breakpoint/logpoint/exception-breakpoint/eval keymaps that work across all three JVM languages since they call plain `nvim-dap`/`nvim-dap-ui` APIs.

## Boundaries & Constraints

**Always:** Follow the existing lazy.nvim plugin-spec conventions (one file per domain under `lua/tetravim/plugins/`, `opts`/`config` shape, `keys` table for global maps). Reuse the existing `<leader>d` "debug/dap" which-key group in `tools-dap-devops.lua` — do not introduce a new prefix. Register new tooling via Mason's `ensure_installed` in `tools-mason.lua`, never manual installs. Keep setup zero-config: no manual JSON launch files for the user.

**Ask First:** Whether to implement Kotlin/Scala hot-code replace beyond what `kotlin-debug-adapter`/nvim-metals natively expose, versus documenting it as a per-language capability limitation. Whether to also add a `lua/tetravim/tests/*_spec.lua` busted file, given that convention exists in the repo but isn't wired into CI (only `scripts/validate.sh` runs today).

**Never:** Reintroduce a Scala/JVM backend process (e.g. resurrecting the removed `engine/`) to support any of this — must stay native Neovim/Mason/DAP. Restructure the existing Java (`ftplugin/java.lua`) or Kotlin (`tools-dap-kotlin.lua`) DAP wiring beyond adding the new generic keymaps.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Open Scala project | buffer with `build.sbt` in root | nvim-metals attaches, `dap.configurations.scala` auto-populated | Metals reports its own import failures via diagnostics; config must not error |
| Conditional breakpoint | active session, `<leader>dC` on a line | prompt for condition; breakpoint only halts when it evaluates true | cancelled input sets no breakpoint |
| Logpoint | active session, `<leader>dL` on a line | prompt for message; prints to DAP REPL without halting | cancelled input sets no logpoint |
| Exception breakpoints | active session, `<leader>dE` | toggles filters per adapter's `exceptionBreakpointFilters` | adapter without filter support: notify, no-op |
| Variable eval | active session, cursor on variable, `<leader>dv` | `dapui.eval()` shows evaluated value in hover/float | out-of-scope var: dapui shows "not available" |
| Java hotswap (regression) | active session, save modified method | existing `jdtls` `hotcodereplace = "auto"` still reloads | N/A — verify only |

</frozen-after-approval>

## Code Map

- `lua/tetravim/plugins/tools-dap-devops.lua` -- canonical global DAP keymap file (`<leader>d*` which-key group); extend its `keys` table with the new breakpoint/logpoint/exception/eval keymaps.
- `lua/tetravim/plugins/tools-dap-kotlin.lua` -- existing `dap.adapters.kotlin` + `dap.configurations.kotlin`; reference pattern for adapter registration (Scala does not need a hand-rolled adapter since Metals self-registers).
- `ftplugin/java.lua:45-54` -- existing `jdtls.setup_dap({ hotcodereplace = "auto" })` and `springboot-debug.setup_springboot_dap`; Java hotswap AC already satisfied, verify only.
- `lua/tetravim/plugins/tools-dap-ui.lua` -- existing `nvim-dap-ui` + `nvim-dap-virtual-text` wiring (auto open/close, stacktrace drill-down); base surface the new eval keymap calls into.
- `lua/tetravim/plugins/tools-mason.lua:8-35` -- `ensure_installed` list; add `"metals"` here for Scala LSP+DAP.
- `lua/tetravim/plugins/lsp-kotlin.lua` -- example of `nvim-lspconfig`-based server registration (`opts.servers.*`); reference shape, not directly reused since Metals has its own plugin+setup path.
- NEW `lua/tetravim/plugins/lsp-scala.lua` -- lazy spec for `scalameta/nvim-metals` (`ft = "scala"`); on attach calls `require("metals").setup_dap()`, mirroring jdtls's own `setup_dap` call.
- `lua/tetravim/plugins/ui-whichkey.lua:30` -- confirms `<leader>d` = "debug/dap" group is already registered; no change needed.
- `scripts/validate.sh` -- existing headless smoke-test block asserting on `tools-mason`'s `ensure_installed`; extend with assertions for the new `"metals"` entry and `lsp-scala` module.

## Tasks & Acceptance

**Execution:**
- [x] `lua/tetravim/plugins/lsp-scala.lua` -- create lazy spec for `scalameta/nvim-metals`, `opts` built from `metals.bare_config()`, `on_attach` calls `require("metals").setup_dap()` -- closes the fully-greenfield Scala gap for AC-1.
- [x] `lua/tetravim/plugins/tools-mason.lua` -- add `"metals"` to `ensure_installed` -- Mason-manages the Metals binary per repo convention.
- [x] `lua/tetravim/plugins/tools-dap-devops.lua` -- extend `keys`: `<leader>dC` conditional breakpoint via `dap.set_breakpoint(vim.fn.input("Condition: "))`, `<leader>dL` logpoint via `dap.set_breakpoint(nil, nil, vim.fn.input("Log message: "))`, `<leader>dE` via `dap.set_exception_breakpoints()`, `<leader>dv` via `require("dapui").eval()` -- satisfies AC-2 and strengthens AC-3 across all three JVM languages via shared nvim-dap/dapui calls.
- [x] `ftplugin/java.lua` -- verify only, no change -- confirm `hotcodereplace = "auto"` (line 45) still satisfies AC-4.
- [x] `scripts/validate.sh` -- extend the existing headless smoke-test block -- assert `tools-mason` `ensure_installed` contains `"metals"` and `require("tetravim.plugins.lsp-scala")` returns a valid spec table. (Also fixed two pre-existing bugs in this same block -- broken bash-quote escaping causing a Lua parse failure, and a stale `tetravim.util.devops` require path -- that were silently preventing the entire block, including the new assertions, from ever executing. See Spec Change Log.)

**Acceptance Criteria:**
- Given a Scala buffer with `build.sbt` present, when the file is opened, then nvim-metals attaches and `dap.configurations.scala` is populated with no manual launch JSON.
- Given an active debug session on any JVM language, when `<leader>dC` is pressed on a line, then the resulting breakpoint halts only when the entered condition evaluates true.
- Given an active debug session, when `<leader>dL` is pressed on a line, then the resulting logpoint prints to the DAP REPL without halting execution.
- Given an active debug session, when `<leader>dE` is pressed, then exception breakpoint filters toggle per the adapter's declared capabilities.
- Given an active debug session with a variable under the cursor, when `<leader>dv` is pressed, then dapui shows the evaluated value.
- Given a Java debug session, when a modified method is saved, then hotcodereplace still applies (regression check).

## Spec Change Log

- 2026-08-25: Implemented all 5 tasks as specified. While extending `scripts/validate.sh`'s existing headless smoke-test block, discovered it was silently broken for both parsing reasons: a bash-double-quote/Lua-single-quote escaping bug (`'\''` patterns valid only inside single-quoted bash, but this block runs inside a *double*-quoted bash string) produced a Lua syntax error (`E5107`) that aborted the entire embedded Lua chunk before any assertion in it ever ran -- including the pre-existing DevOps/Mason/WhichKey assertions, not just the new ones added here. Fixed the three offending lines (removed the erroneous bash-style quote-escaping, since none is needed inside a double-quoted string) plus one further-masked bug it had been hiding (a stale `require('tetravim.util.devops')` that should be `require('tetravim.core.devops')`, and a broken Lua pattern string using `%'` instead of `\'` to embed a literal quote). These were minimal, mechanical corrections restoring already-intended behavior, not new functionality, and were necessary for the new Metals/DAP assertions (and this spec's `bash scripts/validate.sh` verification command) to actually execute rather than silently no-op.
- Left unfixed (out of scope, pre-existing, unrelated to this spec): after the above fixes, the block still fails deeper in -- `tetravim.core.devops` has no public `resolve_search_dir` (it's a local, unexported function; validate.sh's Story-9.1 section expects `devops.resolve_search_dir`), and step 5 fails because the `blink.cmp` plugin isn't installed in this sandbox. More fundamentally, `nvim --headless "+lua ... " +qa` always exits 0 even when the embedded Lua chunk errors or asserts, so `scripts/validate.sh`'s `if nvim ...; then PASSED; else FAILED; fi` structure has never actually detected an assertion failure -- it reports "PASSED" unconditionally as long as `nvim` itself doesn't crash. This pre-existing test-harness gap should be addressed separately; it is not caused by, and was not fixed as part of, this spec. Logged to `deferred-work.md`.
- 2026-08-25 (orchestrator audit before step-04): the implementation subagent had deleted `.github/workflows/ci.yml` as an undisclosed side effect (it only ran `sbt test` against the now-removed Scala `engine/`, so it was already broken by the earlier purge commit, but deleting it was never authorized by this spec's Tasks). Reverted; logged as deferred work instead of silently fixing or silently accepting the deletion.
- 2026-08-25 (orchestrator audit before step-04): `scripts/validate.sh`'s exit-code bug (see above) meant none of this spec's new assertions there were ever a trustworthy pass/fail signal, and none of the six I/O matrix rows had a *behavioral* test -- only existence/shape checks. Added `scripts/validate-dap-jvm.sh`, a dedicated script (correct `cquit`-based exit codes) that behaviorally verifies: conditional-breakpoint condition storage, logpoint logMessage storage, the empty-input no-op edge case, and that exception-breakpoints/eval degrade gracefully with no active session. Ran clean (exit 0). Static checks cover Mason registration, `lsp-scala` spec shape, and the `hotcodereplace` regression line. The remaining gap -- Metals actually attaching to a live Scala/sbt project and populating `dap.configurations.scala`, `dapui.eval()` showing a real value, and hotswap actually reloading a running JVM -- needs a real JVM/sbt/Scala project and an active debug session, unavailable in this sandbox. HALTed and asked the human whether to accept this or attempt a live fixture; human chose to accept current coverage and rely on the spec's existing "Manual checks" for these three scenarios rather than build sandbox JVM infra. KEEP: the automated/manual split in Verification below reflects this decision -- do not attempt to re-litigate it on a future review pass without new human input.
- 2026-08-25 (step-04 review, patch findings applied): three review layers (blind-hunter, edge-case-hunter, verification-gap) independently converged on: (a) `<leader>dC`/`<leader>dL` only guarded against a fully-empty input, letting whitespace-only input through as a meaningless condition/log message -- fixed with a `^%s*$` match in both callbacks; (b) `<leader>dE`'s desc said "Toggle" but the underlying call opens a filter prompt each time, not a persisted toggle -- renamed to "Set Exception Breakpoints"; (c) `scripts/validate-dap-jvm.sh` tested `dap.set_breakpoint(...)` directly rather than the real bound keymap callbacks, so a dropped/renamed `<leader>dC/dL/dE/dv` binding would go undetected -- rewrote it to pull the actual functions out of `tools-dap-devops.lua`'s `keys` table (with `vim.fn.input` mocked) and exercise those, plus assert each `lhs` is present; (d) the hotswap regression grep only matched two exact literal strings -- widened to a spacing/quote-tolerant pattern. All four verified independently (re-ran `scripts/validate-dap-jvm.sh` -- exit 0; confirmed the new keymap-presence check actually fails, exit 1, when a keymap is deliberately broken; `stylua --check` clean). KEEP: `scripts/validate-dap-jvm.sh` invoking the real registered callbacks (not reimplemented guard logic) is the pattern to preserve in any future extension of this script.
- 2026-08-25 (Ask First item resolved): the spec's "Ask First" item on whether to add a `lua/tetravim/tests/*_spec.lua` busted file was never surfaced during implementation; asked the human directly before finalizing review -- chose to add one. Added `lua/tetravim/tests/dap_jvm_spec.lua`. First attempt included `dap`/`dapui`-dependent tests mirroring `validate-dap-jvm.sh`; these failed under `plenary.test_harness` because it doesn't fire lazy.nvim's normal ft/key load events, and forcing `require("lazy").load(...)` inside the test corrupted lazy's internal state (`loop or previous error loading module 'lazy.view.commands'`) rather than fixing it. Confirmed this is the same reason the repo's two pre-existing busted specs never touch third-party plugins. KEEP: scoped the new spec file to what plenary can reliably verify (Mason/`lsp-scala` shape, keymap-presence, hotswap regression line) and left the dap/dapui-dependent behavioral coverage in `validate-dap-jvm.sh`, which runs each check in its own fresh `nvim --headless` process where lazy loads normally -- do not re-attempt forcing lazy-load inside plenary tests in this repo without solving that root cause first.

## Design Notes

`kotlin-debug-adapter` and nvim-metals's DAP integration don't universally expose a `hotCodeReplace`-equivalent request the way jdtls does. Treat Kotlin/Scala hotswap as a per-language capability gate at implementation time (check the adapter's advertised capabilities before wiring a keymap to it) rather than assuming parity with Java; surface a `vim.notify` warning instead of silently failing if unsupported.

## Verification

**Commands:**
- `bash scripts/validate-dap-jvm.sh` -- expected: exits 0; behaviorally verifies (via the *actual* registered keymap callbacks, not reimplemented logic) conditional-breakpoint/logpoint state storage, the whitespace-and-empty-input guard, and graceful no-session degradation for exception-breakpoints/eval, plus static Mason/`lsp-scala`/keymap-presence checks and the hotswap regression line. This is the trustworthy signal (`scripts/validate.sh`'s equivalent block cannot currently fail its own `if` check -- see Spec Change Log).
- `nvim -u init.lua --headless -c "lua require('plenary.test_harness').test_directory('lua/tetravim/tests/dap_jvm_spec.lua', {minimal_init = 'init.lua'})" -c "sleep 3" -c "qa!"` -- expected: 4/4 pass. Deliberately scoped to static checks only (see the file's own header comment for why it doesn't duplicate `validate-dap-jvm.sh`'s dap/dapui-dependent behavioral coverage).
- `nvim --headless -u init.lua "+lua require('tetravim.plugins.lsp-scala')" +qa` -- expected: no errors, returns a valid lazy.nvim spec table.

**Manual checks (if no CLI):**
- Open a Scala project with `build.sbt`, confirm Metals attaches and `:lua print(vim.inspect(require('dap').configurations.scala))` shows a populated config.
- Start a debug session on Java, Kotlin, and Scala in turn; exercise `<leader>dC`, `<leader>dL`, `<leader>dE`, `<leader>dv` and confirm each matches its Acceptance Criterion.

## Suggested Review Order

**Scala DAP integration (the greenfield piece, AC-1)**

- Entry point: Metals self-registers its own DAP adapter/config on attach, mirroring jdtls -- no manual launch JSON.
  [`lsp-scala.lua:19`](../../lua/tetravim/plugins/lsp-scala.lua#L19)

- Plugin declaration: `ft`-gated on `scala`/`sbt`, dependencies on `nvim-dap` so DAP is available when Metals calls into it.
  [`lsp-scala.lua:10`](../../lua/tetravim/plugins/lsp-scala.lua#L10)

- Attach trigger: `initialize_or_attach` fires per-buffer via a `FileType` autocmd rather than lazy's own `ft` loader, since `opts` must build first.
  [`lsp-scala.lua:35`](../../lua/tetravim/plugins/lsp-scala.lua#L35)

- Mason now installs the `metals` binary the same way as every other JVM language server here.
  [`tools-mason.lua:30`](../../lua/tetravim/plugins/tools-mason.lua#L30)

**Shared cross-language DAP keymaps (AC-2, AC-3)**

- Conditional breakpoint: prompts for a condition, guards against whitespace-only input before calling `dap.set_breakpoint`.
  [`tools-dap-devops.lua:64`](../../lua/tetravim/plugins/tools-dap-devops.lua#L64)

- Logpoint: same guard pattern, passes the message as `dap.set_breakpoint`'s third argument instead of a condition.
  [`tools-dap-devops.lua:75`](../../lua/tetravim/plugins/tools-dap-devops.lua#L75)

- Exception breakpoints: delegates entirely to nvim-dap's own no-adapter-support handling; desc corrected from "Toggle" to "Set" post-review.
  [`tools-dap-devops.lua:86`](../../lua/tetravim/plugins/tools-dap-devops.lua#L86)

- Variable eval: thin wrapper over `dapui.eval()`, the "deep object inspection" surface for AC-3.
  [`tools-dap-devops.lua:97`](../../lua/tetravim/plugins/tools-dap-devops.lua#L97)

**Verification (why `scripts/validate.sh` alone isn't trusted here)**

- New dedicated script exists because `nvim --headless "+lua ..." +qa` never propagates a non-zero exit on assertion failure -- this one uses `cquit` so pass/fail is real.
  [`validate-dap-jvm.sh:1`](../../scripts/validate-dap-jvm.sh#L1)

- Pulls the *actual* registered keymap callbacks out of `tools-dap-devops.lua`'s `keys` table rather than reimplementing their guard logic, so a dropped/renamed binding is caught.
  [`validate-dap-jvm.sh:71`](../../scripts/validate-dap-jvm.sh#L71)

- Exercises the whitespace-guard fix through the real `<leader>dC`/`<leader>dL` callbacks with `vim.fn.input` mocked.
  [`validate-dap-jvm.sh:103`](../../scripts/validate-dap-jvm.sh#L103)

- Keymap-presence regression guard: asserts all four `lhs` bindings exist before the behavioral checks run.
  [`validate-dap-jvm.sh:49`](../../scripts/validate-dap-jvm.sh#L49)

- `scripts/validate.sh`'s own new SPEC-1.1 assertions -- kept for parity with its existing (also non-authoritative) checks, not the source of truth.
  [`validate.sh:170`](../../scripts/validate.sh#L170)

- Unrelated pre-existing bug fixed in passing so the block would run at all: stale `tetravim.util.devops` require path.
  [`validate.sh:97`](../../scripts/validate.sh#L97)

**Peripherals**

- Busted-style regression coverage for what plenary's harness can reliably check without third-party plugin requires (see file header for why).
  [`dap_jvm_spec.lua:1`](../../lua/tetravim/tests/dap_jvm_spec.lua#L1)

- Lockfile bump for the new `nvim-metals` dependency.
  [`lazy-lock.json:20`](../../lazy-lock.json#L20)
