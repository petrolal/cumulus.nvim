# Epic 6 Context: Neovim Lua Bridge Migration & Complete Rust Removal

<!-- Compiled from planning artifacts. Edit freely. Regenerate with compile-epic-context if planning docs change. -->

## Goal

Migrate Neovim Lua integration layer to connect to the new `cumulus-engine` Scala binary via `lua/cumulus/util/engine.lua` (replacing `rust.lua`), refactor all Lua `util/` modules to delegate business logic to the engine subcommands (reducing Lua to UI glue), update health checks, and completely purge the legacy Rust engine codebase (`crates/cumulus-core/`).

## Stories

- Story 6.1: Lua Integration Layer (`engine.lua`) & Util File Refactoring
- Story 6.2: Purge Legacy Rust Engine (`crates/cumulus-core`)

## Requirements & Constraints

- **Engine Discovery (NFR4)**: `engine.lua` must search for the engine executable in priority order:
  1. `cumulus-engine` on `$PATH`
  2. `engine/target/graalvm-native-image/cumulus-engine` (local build)
  3. `~/.local/share/nvim/cumulus/bin/cumulus-engine` (installed binary)
- **Protocol Envelope Backward Compatibility (`SPEC-031`)**: Lua bridge must parse the standard envelope `{ "success": boolean, "data": any, "error": string|nil, "error_code": string|nil }`.
- **Lua Business Logic Absorption (NFR5)**: Lua files must contain zero business logic and act purely as Neovim UI glue:
  - `maven.lua` & `gradle.lua`: Delegate build tool detection to `discover-build-tool` (removing `find_maven()` and `find_gradle()`).
  - `test-runner.lua`: Delegate CLI command assembly to `assemble-test-command`.
  - `lsp-java.lua` & `lsp-kotlin.lua`: Delegate JDK discovery to `discover-jdk` (removing `find_java21_home()`).
  - `theme/init.lua`: Delegate state I/O to `manage-theme` (removing Lua `KEY=VALUE` parsing).
  - `multimodule.lua`: Delegate workspace root discovery to `discover-workspace`.
  - `autocmds.lua`: Delegate Java header fallback to `generate-java-header`.
  - `health.lua`: Remove `cargo` binary check, query `cumulus-engine ping` for version/commit/Scala info, and suggest `sbt` / `:CumulusInstallEngine` on failure.
- **Rust Engine Purge**: Delete `crates/cumulus-core/`, Cargo configs, and remove Rust build references from `.gitignore` and docs.

## Technical Decisions

- **Bridge Module**: `lua/cumulus/util/engine.lua` replaces `lua/cumulus/util/rust.lua`. Maintain exact or backward-compatible API functions for callers.
- **Process Execution**: Use `vim.system` or asynchronous job wrappers where appropriate, preserving JSON stdout decoding.

## Cross-Story Dependencies

- Replaces all usages of `rust.lua` across `lua/cumulus/` before purging `crates/cumulus-core/` in Story 6.2.
- Prepares ground for automated binary installation in Epic 7.
