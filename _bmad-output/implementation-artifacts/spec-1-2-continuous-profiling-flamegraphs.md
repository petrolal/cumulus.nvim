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

**Ask First:** `async-profiler` is not typically available directly in the default `mason-registry` — how should it be managed/installed? Should we generate a standalone HTML flamegraph and open it in the default browser, or use a terminal-rendered/webview-based component inside Neovim?

**Never:** Reintroduce a custom Scala/JVM backend to drive this functionality. Do not block the Neovim UI thread while profiling operations are ongoing.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Start Profiling | `<leader>jp` (or similar) pressed | Prompts for JVM PID, starts `async-profiler` in background | Shows error if `async-profiler` binary is missing or PID is invalid |
| Stop Profiling | Profiling active | Stops profiler, generates flamegraph file | Warns if no active profiling session is found |
| View Flamegraph | Generated flamegraph exists | Opens flamegraph (e.g., HTML) for the user | Shows error if flamegraph generation failed |

</frozen-after-approval>

## Code Map

- `lua/cumulus/util/profiling.lua` -- New module to encapsulate the profiling logic (commands to run `async-profiler`, track state, generate output).
- `lua/cumulus/util/jvm.lua` -- Register keybindings to trigger the profiling commands.
- `lua/cumulus/tests/profiling_spec.lua` -- Add a busted test to verify the public API shape.
- `scripts/validate.sh` -- Add headless validation to assert the presence and loadability of `<leader>jps`, `<leader>jpx`, and `<leader>jpv` keymaps.

## Tasks & Acceptance

**Execution:**
- [x] `lua/cumulus/util/profiling.lua` -- Create module providing start/stop/view APIs. Ensure robustness by checking if active PID already exists, parsing exit codes when using system fallback, verifying directory writability, and preserving previous flamegraph states on error.
- [x] `lua/cumulus/util/jvm.lua` -- Register keybindings to trigger the profiling commands.
- [x] `lua/cumulus/tests/profiling_spec.lua` -- Create unit test to verify module API shape.
- [x] `scripts/validate.sh` -- Extend headless validation to ensure `<leader>jp` mappings execute without Lua errors.

**Acceptance Criteria:**
- Given a running JVM process, when the user triggers the start profile command and provides the PID, then `async-profiler` begins profiling in the background without blocking the editor.
- Given an active profiling session, when the user triggers the stop command, then the profiler halts and a flamegraph file (e.g., HTML) is successfully generated.
- Given a generated flamegraph, when the view command is triggered, then the user can inspect the flamegraph directly (e.g. via browser or webview).

## Spec Change Log

- **2026-08-25**: (Loop 1) Review found that `async-profiler` is not a Neovim plugin, so placing it in `lua/cumulus/plugins/tools-profiling.lua` caused an invalid lazy.nvim spec. Also, test coverage for the keymaps and module shape was missing.
  - **Amended**: Removed `tools-profiling.lua` from Code Map/Tasks. Pointed to `lua/cumulus/util/profiling.lua` instead. Added tasks for `profiling_spec.lua` and `scripts/validate.sh` checks. Added edge-case handling instructions to the execution task for `profiling.lua`.
  - **Avoided**: Deploying an empty/invalid lazy spec and shipping code without validation coverage.
  - **KEEP**: The Lua implementation wrapping `async-profiler` with `Snacks.terminal.run` and the `jvm.lua` keymap registrations (`<leader>jps`, `<leader>jpx`, `<leader>jpv`) were good and should be preserved.

## Design Notes

Since `async-profiler` might not be in standard Mason registries, the implementation relies on a fallback logic (looking for `async-profiler` in `$PATH`).

## Verification

**Manual checks (if no CLI):**
- Verify `async-profiler` can be launched via the configured keymap.
- Verify the editor does not freeze during the profiling window.
- Verify a flamegraph HTML file is correctly produced upon stopping.

## Suggested Review Order

**Profiling Engine Logic**

- Core API structure handling robust background execution and CWD fallbacks
  [`profiling.lua:16`](../../lua/cumulus/util/profiling.lua#L16)

- Trims and validates PID, then initiates async-profiler using terminal or system fallback
  [`profiling.lua:29`](../../lua/cumulus/util/profiling.lua#L29)

- Gracefully closes profiling session and generates safe temporary flamegraph paths
  [`profiling.lua:55`](../../lua/cumulus/util/profiling.lua#L55)

**Editor Keybindings**

- Registers `<leader>jp` mapping group for async-profiler commands
  [`jvm.lua:31`](../../lua/cumulus/util/jvm.lua#L31)

- Assigns `start`, `stop`, and `view` to explicit `<leader>jps`, `x`, `v` mappings
  [`jvm.lua:488`](../../lua/cumulus/util/jvm.lua#L488)

**Test Coverage & Validation**

- Asserts keymap presence and validates the underlying lua module via headless checks
  [`validate.sh:394`](../../scripts/validate.sh#L394)

- Basic Busted suite enforcing module interface stability
  [`profiling_spec.lua:3`](../../lua/cumulus/tests/profiling_spec.lua#L3)
