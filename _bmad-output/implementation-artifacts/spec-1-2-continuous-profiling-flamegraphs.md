---
title: 'Continuous Profiling & Flamegraphs'
type: 'feature'
created: '2026-08-25'
status: 'done'
review_loop_iteration: 1
context: ['/home/petrolal/cumulus.nvim/_bmad-output/implementation-artifacts/epic-1-context.md']
baseline_commit: 'fef4fd7042e0c885cba97ed997ca081c8a98c61a'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** JVM backend developers need to identify CPU and memory bottlenecks locally, but switching to external profilers like VisualVM or YourKit breaks the editor flow and increases friction.

**Approach:** Integrate `async-profiler` natively via Lua to start/stop profiling JVM processes directly from within Neovim, using `Snacks` to render terminals or UI. The flamegraphs generated will be viewable without leaving the terminal/editor workflow.

## Boundaries & Constraints

**Always:** Follow the existing lazy.nvim plugin-spec conventions. Use `Snacks.terminal` or standard UI components for terminal interfaces. Ensure the capability serves Java, Kotlin, and Scala seamlessly.

**Never:** Reintroduce a custom Scala/JVM backend to drive this functionality. Do not block the Neovim UI thread while profiling operations are ongoing. Ensure architectural integrity by avoiding global mappings for language-specific commands.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Start Profiling | `<leader>cp` (or similar) pressed | Prompts user with `vim.ui.select` over `jps -l` to pick JVM process | Shows error if `async-profiler` binary is missing |
| Stop Profiling | Profiling active | Stops profiler, generates a flamegraph file | Warns if no active profiling session is found |
| View Flamegraph | Generated flamegraph exists | Opens a flamegraph (e.g., HTML) for the user | Shows error if flamegraph generation failed |
| Default browser missing | View triggered without browser | Handle gracefully | Specify explicit fallback or notify user |
| Concurrent starts | Profiling already active | Reject new start requests | State machine tracks active PID |
| Missing async-profiler | Command `<leader>cps` used | Fail fast | Clear 'async-profiler not found' notification |
| Flamegraph collision | Concurrent instances running | Safe temporary path | Use `vim.fn.tempname()` or append PID+timestamp |
| Insufficient OS permissions | OS blocks perf events (e.g., `perf_event_paranoid`) | Surfaced permission error | Notify user with remediation command |
| Target is not a Java process| PID exists but isn't Java | Invalid target error | Parse `not a Java process` output and notify |
| Target terminates early | JVM stops before profiler stops | Clear stale tracking state | `vim.uv.kill(pid, 0)` checks clear active state |
| Missing jps | `<leader>cps` used but `jps` missing | Error notification | Notify `jps not found` before prompting |
| No running Java processes | `jps -l` returns empty | Error notification | Notify `No Java processes found` before prompting |
| User cancels prompt | Escapes `vim.ui.select` | Abort gracefully | No Lua error triggered for nil PID |

</frozen-after-approval>

## Tasks & Code Map

**Execution:**
- [x] `lua/cumulus/core/devops.lua` or standard util location — Create a module providing start/stop/view APIs for profiling. Use `vim.uv.kill(pid, 0)` for liveness checks and `vim.fn.tempname()` for generating isolated flamegraph paths. Ensure robust checking for existing PIDs, parse exit codes (including permission drops), and wrap `jps` using `vim.ui.select`.
- [x] `lua/cumulus/core/lang-keymaps.lua` — Register buffer-local keybindings under `<leader>c` to trigger profiling commands, aligning with the `AGENTS.md` language-stack commands policy.
- [x] `lua/cumulus/tests/profiling_spec.lua` — Create a unit test to verify the module API shape.
- [x] `scripts/validate.sh` — Extend headless validation to ensure `<leader>c` mappings execute without Lua errors.
- [x] `scripts/validate-profiling.sh` (New File) — Add a dedicated behavioral validation script to robustly test edge cases (missing binary, missing jps, invalid PIDs).

## Design Notes

- Since `async-profiler` might not be in standard Mason registries, the implementation relies on a fallback logic (looking for `async-profiler` in `$PATH`).
- View flamegraph logic uses system browser fallback mechanisms if standard Neovim UI viewing fails.

## Verification

**Commands:**
- `bash scripts/validate-profiling.sh` — expected: Headless behavioral validation passes tests for edge cases and fallback logic.
- `bash scripts/validate.sh` — expected: Headless validation passes `<leader>cp` mappings check.
- `nvim -u init.lua --headless -c "lua require('plenary.test_harness').test_directory('lua/cumulus/tests/profiling_spec.lua', {minimal_init = 'init.lua'})" -c "qa!"` — expected: Busted test passes.
