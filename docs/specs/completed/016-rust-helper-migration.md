# Specification: SPEC-016 - Rust Helper Migration for Build & Log Diagnostics Parsing

## Metadata
- **Spec ID**: SPEC-016
- **Title**: Rust Helper Migration for Build & Log Diagnostics Parsing
- **Status**: COMPLETED
- **Implementation**: Rust (Lua bridge only — minimal Lua)
- **Author**: AI Systems Architect & Antigravity Assistant
- **Target Files/Paths**:
  - `crates/cumulus-core/Cargo.toml` (new)
  - `crates/cumulus-core/src/main.rs` (new)
  - `crates/cumulus-core/src/maven.rs` (new)
  - `crates/cumulus-core/src/gradle.rs` (new)
  - `crates/cumulus-core/src/log_parser.rs` (new)
  - `lua/cumulus/util/rust.lua` (new)
  - `lua/cumulus/health.lua` (extends)
  - `scripts/validate.sh` (extends)

---

---

## Architecture

**Lua is a bridge to the Rust backend. That is it.**

```
Neovim  →  Lua (bridge)  →  cumulus-core (Rust binary)
```

- **Rust** (`crates/cumulus-core`): all logic — parsing, file I/O, network, validation, analysis
- **Lua**: one job only — call the Rust binary and pass results to Neovim APIs
- No Lua fallbacks. No Lua parsing. No Lua analysis. If the binary is missing, fail explicitly.
---

## Goal & Intent

Migrate performance-critical text parsing, POM XML analysis, Gradle task extraction, and Maven/Gradle build log diagnostic parsing from Lua string patterns to a compiled Rust helper binary (`cumulus-helper`).

The helper executes asynchronously via `vim.system` or fast Lua bridge, accelerating project analysis while maintaining 100% backward compatibility and fallback to Lua routines if the Rust binary is not compiled.

---

## Scope Boundaries

**In scope:**
- Create `crates/cumulus-core` Rust binary package with CLI subcommands:
  - `parse-pom`: Extract Maven goals, plugins, and dependencies from `pom.xml`.
  - `parse-gradle-tasks`: Parse `gradle tasks --all` output into structured task lists.
  - `parse-build-log`: High-performance log parser converting Maven/Gradle build errors into structured JSON diagnostics.
- Create `lua/cumulus/util/rust.lua` to manage binary detection and execution.
- Update `lua/cumulus/util/maven.lua` & `lua/cumulus/util/gradle.lua` to leverage `cumulus-helper` when present.
- Create `lua/cumulus/util/build-diagnostics.lua` to populate `vim.diagnostic` from parsed build logs.
- Integrate Rust binary check into `lua/cumulus/health.lua` and `scripts/validate.sh`.

**Out of scope:**
- Modifying frozen DevOps files (`cloud-*.lua`, `lsp-devops.lua`, `tools-dap-devops.lua`).

---

## Execution Checklist

- [x] Create active spec `docs/specs/active/016-rust-helper-migration.md`
- [x] Initialize Cargo workspace & `crates/cumulus-core` package
- [x] Implement Rust subcommands (`parse-pom`, `parse-gradle-tasks`, `parse-build-log`)
- [x] Build & test Rust binary via `cargo build --release` and `cargo test`
- [x] Implement `lua/cumulus/util/rust.lua` for binary discovery & execution
- [x] Integrate Rust parser into `lua/cumulus/util/maven.lua` & `lua/cumulus/util/gradle.lua`
- [x] Implement `lua/cumulus/util/build-diagnostics.lua`
- [x] Update `lua/cumulus/health.lua` and `scripts/validate.sh`
- [x] Run full project validation (`bash scripts/validate.sh`, `nvim --headless "+checkhealth cumulus"`)
