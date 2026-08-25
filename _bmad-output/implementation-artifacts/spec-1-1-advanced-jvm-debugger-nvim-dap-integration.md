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

**Problem:** cumulus.nvim already wires DAP for Java (jdtls's own `setup_dap` with hotswap) and Kotlin (a standalone `kotlin-debug-adapter` config), but Scala has zero debugger coverage, and no language shares conditional breakpoints, logpoints, exception breakpoints, or an interactive variable-eval keymap.

**Approach:** Add an nvim-metals-based DAP setup for Scala (mirroring how jdtls self-registers its DAP hooks), and extend the existing global `<leader>d*` DAP keymap file with generic conditional-breakpoint/logpoint/exception-breakpoint/eval keymaps that work across all three JVM languages since they call plain `nvim-dap`/`nvim-dap-ui` APIs.

## Boundaries & Constraints

**Always:** Follow the existing lazy.nvim plugin-spec conventions (one file per domain under `lua/cumulus/plugins/`, `opts`/`config` shape, `keys` table for global maps). Reuse the existing `<leader>d` "debug/dap" which-key group in `tools-dap-devops.lua` — do not introduce a new prefix. Register new tooling via Mason's `ensure_installed` in `tools-mason.lua`, never manual installs. Keep setup zero-config: no manual JSON launch files required (though standard `.vscode/launch.json` overrides are still respected if present).

**Never:** Reintroduce a Scala/JVM backend process (e.g. resurrecting the removed `engine/`) to support any of this — must stay native Neovim/Mason/DAP. Restructure the existing Java (`ftplugin/java.lua`) or Kotlin (`tools-dap-kotlin.lua`) DAP wiring beyond adding the new generic keymaps.

**Out of Scope:** Kotlin/Scala hot-code replace is explicitly out of scope for this feature.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Open Scala project | buffer with `build.sbt`, `build.sc`, or `project.scala` | nvim-metals attaches, `dap.configurations.scala` auto-populated | Metals reports its own import failures via diagnostics; config must not error |
| Conditional breakpoint | active session, `<leader>dC` | Async `vim.ui.input` prompts for condition; halts only when evaluated true | `nil` input sets no breakpoint; `""` clears existing condition |
| Logpoint | active session, `<leader>dL` | Async `vim.ui.input` prompts for message; prints to DAP REPL without halting | `nil` input sets no logpoint; `""` clears existing message |
| Exception breakpoints | active session, `<leader>dE` | toggles filters per adapter's `exceptionBreakpointFilters` | adapter without filter support: notify, no-op |
| Variable eval | active session, cursor on var, `<leader>dv` | `dapui.eval()` shows evaluated value in hover/float | out-of-scope var: dapui shows "not available" |
| Java hotswap (regression) | active session, save modified method | existing `jdtls` `hotcodereplace = "auto"` still reloads | N/A — verify only |
| Metals binary unavailable | Open Scala project, metals not installed | Error notification expected | Gracefully report missing binary, no crash |
| dap-ui missing | `<leader>dv` | Error notification | Notify user if `pcall(require, 'dapui')` fails, fail gracefully |
| Plenary test runs | Running async tests | Tests execute reliably | Rely on plenary's built-in async runner without arbitrary sleeps |
| No active debug session | `<leader>dC`, `<leader>dL`, `<leader>dv` invoked | Warn user | Graceful warning notification |

</frozen-after-approval>

## Tasks & Code Map

**Execution:**
- [x] `lua/cumulus/plugins/lsp-scala.lua` (New File) — create lazy spec for `scalameta/nvim-metals`, `opts` built from `metals.bare_config()`, ensure `root_pattern` supports sbt, Mill, and scala-cli. `on_attach` calls `require("metals").setup_dap()`.
- [x] `lua/cumulus/plugins/tools-mason.lua` — add `"metals"` to `ensure_installed`.
- [x] `lua/cumulus/plugins/tools-dap-devops.lua` — extend `keys`: 
  - `<leader>dC` via `vim.ui.input` passing result to `dap.set_breakpoint`.
  - `<leader>dL` via `vim.ui.input` passing result as message to `dap.set_breakpoint`.
  - `<leader>dE` via `dap.set_exception_breakpoints()`.
  - `<leader>dv` checks active session, `pcall`s `dapui`, and calls `dapui.eval()`, notifying on failure.
- [x] `ftplugin/java.lua` — verify only, confirm `hotcodereplace = "auto"` applies.
- [x] `scripts/validate.sh` — assert `tools-mason` contains `"metals"` and `lsp-scala` returns a valid spec table.

## Design Notes

- `kotlin-debug-adapter` and nvim-metals's DAP integration don't universally expose a `hotCodeReplace`-equivalent request the way jdtls does. Kotlin/Scala hotswap is explicitly out of scope. 
- Busted spec test added as per Ask First resolution, scoped to static checks.

## Verification

**Commands:**
- `bash scripts/validate-dap-jvm.sh` — expected: exits 0; behaviorally verifies conditional-breakpoint/logpoint state storage, the whitespace-and-empty-input guard, and graceful no-session degradation.
- `nvim -u init.lua --headless -c "lua require('plenary.test_harness').test_directory('lua/cumulus/tests/dap_jvm_spec.lua', {minimal_init = 'init.lua'})" -c "qa!"` — expected: 4/4 pass.
- `nvim --headless -u init.lua "+lua require('cumulus.plugins.lsp-scala')" +qa` — expected: no errors, returns a valid lazy.nvim spec table.
