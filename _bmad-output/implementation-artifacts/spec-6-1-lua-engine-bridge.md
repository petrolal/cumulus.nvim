---
title: 'Epic 6 Story 6.1: Lua Integration Layer (engine.lua) & Util Refactoring'
type: 'feature'
created: '2026-08-15'
status: 'done'
baseline_commit: '0fcd11f'
review_loop_iteration: 0
context: ['_bmad-output/implementation-artifacts/epic-6-context.md']
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** The Neovim Lua integration layer currently calls the legacy `cumulus-core` binary via `lua/cumulus/util/rust.lua` and performs business logic (build tool detection, JDK discovery, test CLI assembly, theme state I/O, workspace finding) directly in Lua files.

**Approach:** Implement `lua/cumulus/util/engine.lua` replacing `rust.lua` to interface with `cumulus-engine`, providing backward-compatible wrapper functions for all 32+ subcommands. Refactor all Lua utility files and health checks to delegate business logic to the engine subcommands, reducing Lua code to pure UI glue.

## Boundaries & Constraints

**Always:**
- `engine.lua` must search for the engine binary in priority order:
  1. `cumulus-engine` on `$PATH`
  2. `engine/target/graalvm-native-image/cumulus-engine` (local build)
  3. `~/.local/share/nvim/cumulus/bin/cumulus-engine` (installed binary)
- `engine.lua` must safely decode JSON envelopes matching `{ "success": boolean, "data": any, "error": string|nil, "error_code": string|nil }`.
- `engine.lua` must provide all subcommand wrappers with compatible signatures previously provided by `rust.lua`.
- Replace all references to `cumulus.util.rust` with `cumulus.util.engine` across all `lua/cumulus/` files.
- `maven.lua` & `gradle.lua` delegate build tool discovery to `discover-build-tool`.
- `test-runner.lua` delegates command line assembly to `assemble-test-command`.
- `lsp-java.lua` & `lsp-kotlin.lua` (or LSP configs) delegate JDK discovery to `discover-jdk`.
- `theme/init.lua` delegates state read/write to `manage-theme`.
- `multimodule.lua` delegates root finding to `discover-workspace`.
- `autocmds.lua` delegates Java header generation to `generate-java-header`.
- `health.lua` verifies `cumulus-engine` via `ping` and removes references to `cargo`.

**Never:**
- Do not keep any leftover imports of `cumulus.util.rust`.
- Do not introduce business logic in Lua files; always delegate to engine subcommands.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Engine ping via health | `health.lua` calls `engine.ping()` | Reports engine active with version, Scala version, commit | Warns with build instructions if binary not found |
| Binary discovery on PATH | `cumulus-engine` in PATH | `engine.get_bin()` returns `"cumulus-engine"` | Falls back to local build or user local path |
| Engine execution failure | Invalid input / exit code != 0 | `success: false` in envelope | Notifies warning and returns nil |

</frozen-after-approval>

## Code Map

- `lua/cumulus/util/engine.lua` -- New bridge module replacing `rust.lua`, provides `get_bin()`, `is_available()`, `call_engine()`, `ping()`, and 32+ subcommand wrappers.
- `lua/cumulus/util/rust.lua` -- Legacy bridge module forwarded to `engine.lua`.
- `lua/cumulus/health.lua` -- Health check updated for `cumulus-engine` (`ping`), removing `cargo`.
- `lua/cumulus/util/maven.lua` -- Refactored to delegate build tool check to `discover-build-tool` and use `engine.lua`.
- `lua/cumulus/util/gradle.lua` -- Refactored to delegate build tool check to `discover-build-tool` and use `engine.lua`.
- `lua/cumulus/util/test-runner.lua` -- Refactored to delegate command assembly to `assemble-test-command` and use `engine.lua`.
- `lua/cumulus/util/multimodule.lua` -- Refactored to delegate root discovery to `discover-workspace` and use `engine.lua`.
- `lua/cumulus/theme/init.lua` -- Refactored to delegate state I/O to `manage-theme` and use `engine.lua`.
- `lua/cumulus/core/autocmds.lua` -- Refactored to use `engine.lua` and `generate-java-header`.
- `lua/cumulus/core/keymaps.lua` -- Refactored to require `cumulus.util.engine`.
- `lua/cumulus/util/beans.lua`, `build-diagnostics.lua`, `conflicts.lua`, `coverage.lua`, `dap-stacktrace.lua`, `endpoints.lua`, `import-optimizer.lua`, `k8s-validator.lua`, `log-indexer.lua`, `migrations.lua`, `session.lua`, `springboot-debug.lua` -- Updated require from `rust` to `engine`.

## Tasks & Acceptance

**Execution:**
- [x] `lua/cumulus/util/engine.lua` -- Create engine bridge with binary discovery, process invocation via `vim.system`, JSON envelope parsing, and all 32+ subcommand wrapper methods -- Replaces `rust.lua`
- [x] `lua/cumulus/health.lua` -- Update healthcheck to verify `cumulus-engine` via `ping` and remove `cargo` checks -- Meets Story 6.1 AC
- [x] `lua/cumulus/util/maven.lua` -- Update to use `engine.lua` and delegate to `discover-build-tool` -- Removes Lua business logic
- [x] `lua/cumulus/util/gradle.lua` -- Update to use `engine.lua` and delegate to `discover-build-tool` -- Removes Lua business logic
- [x] `lua/cumulus/util/test-runner.lua` -- Update to use `engine.lua` and delegate to `assemble-test-command` -- Removes Lua business logic
- [x] `lua/cumulus/util/multimodule.lua` -- Update to use `engine.lua` and delegate to `discover-workspace` -- Removes Lua business logic
- [x] `lua/cumulus/theme/init.lua` -- Update to use `engine.lua` and delegate to `manage-theme` -- Removes Lua file I/O
- [x] `lua/cumulus/core/autocmds.lua` & `lua/cumulus/core/keymaps.lua` -- Update requires to `cumulus.util.engine` -- Integrates new engine bridge
- [x] `lua/cumulus/util/*.lua` -- Update all remaining utility modules to require `cumulus.util.engine` -- Ensures zero stale references

**Acceptance Criteria:**
- Given Neovim with `cumulus-engine`, when `:checkhealth cumulus` is run, then `cumulus-engine` is reported active with version metadata from `ping`.
- Given any Lua util module calling native functionality, when invoked, then it delegates to `cumulus.util.engine` and receives parsed envelope data.

## Spec Change Log

## Verification

**Commands:**
- `nvim --headless -c "checkhealth cumulus" -c "qa"` -- expected: health check runs cleanly without Lua errors
- `nvim --headless -c "lua assert(require('cumulus.util.engine').is_available() ~= nil)" -c "qa"` -- expected: engine module loads and executes

## Suggested Review Order

**Core Engine Bridge & Health**

- Primary entry point implementing the Scala engine interface and 32+ subcommand wrappers
  [`engine.lua:1`](../../lua/cumulus/util/engine.lua#L1)

- Updated health check to query `cumulus-engine ping` and remove cargo dependency
  [`health.lua:26`](../../lua/cumulus/health.lua#L26)

**Lua Business Logic Absorption**

- Delegated build tool detection to `discover-build-tool`
  [`maven.lua:18`](../../lua/cumulus/util/maven.lua#L18)

- Delegated build tool detection to `discover-build-tool`
  [`gradle.lua:18`](../../lua/cumulus/util/gradle.lua#L18)

- Delegated CLI command construction to `assemble-test-command`
  [`test-runner.lua:60`](../../lua/cumulus/util/test-runner.lua#L60)

- Delegated theme state persistence to `manage-theme`
  [`theme/init.lua:14`](../../lua/cumulus/theme/init.lua#L14)

- Delegated sub-module parsing to `parse-modules`
  [`multimodule.lua:12`](../../lua/cumulus/util/multimodule.lua#L12)

**Engine Updates**

- Extended `ping` subcommand to return metadata matching `PingData`
  [`Main.scala:270`](../../engine/src/main/scala/cumulus/Main.scala#L270)
