# Specification: SPEC-031 - Pure-Lua Fallback Elimination & Strict Rust IPC Enforcer

## Metadata
- **Spec ID**: SPEC-031
- **Title**: Pure-Lua Fallback Elimination & Strict Rust IPC Enforcer (IntelliJ Ultimate Enterprise Parity)
- **Status**: BACKLOG
- **Author**: AI Systems Architect
- **Target Files/Paths**:
  - `lua/cumulus/util/build-diagnostics.lua` (remediates SPEC-004 / SPEC-016)
  - `lua/cumulus/util/session.lua` (remediates SPEC-030 prep)
  - `lua/cumulus/health.lua` (remediates SPEC-014 prep)
  - `lua/cumulus/util/rust.lua` (extends)

- **Implementation**: Rust-First Enforcement (Zero pure-Lua fallback parsing or regex matching)

---

## Architecture

**Lua is a bridge to the Rust backend. Pure-Lua fallback parsers are completely removed.**

```
Build Log / Input  →  Lua (rust.parse_build_log)  →  cumulus-core parse-build-log  →  JSON Output  →  vim.diagnostic
```

- **Enterprise Parity Target**: Guarantee IntelliJ IDEA Ultimate execution speeds and zero UI-thread blocking by enforcing strict compiled Rust binary IPC across all build diagnostics, log indexing, and workspace operations.
- **Strict IPC Directive**: If `cumulus-core` is absent or uncompiled, Lua modules MUST NOT execute fallback Lua regex loops (`parse_maven_lua`, `parse_gradle_lua`). Instead, surface an explicit, high-visibility notification:
  `"cumulus-core binary not found. Please run 'cargo build --release' inside crates/cumulus-core."`

---

## Goal & Intent
Eliminate legacy pure-Lua fallback parsers in [`lua/cumulus/util/build-diagnostics.lua`](file:///home/petrolal/cumulus.nvim/lua/cumulus/util/build-diagnostics.lua) and related modules created during early specifications (`SPEC-004`), ensuring the codebase strictly complies with the **Rust-First Directive**.

---

## Scope Boundaries

**In scope:**
- Removing `parse_maven_lua` and `parse_gradle_lua` fallback functions from `build-diagnostics.lua`.
- Enforcing explicit error notifications when `cumulus-core` binary is missing across all `lua/cumulus/util/*.lua` modules.
- Reconciling `SPEC-004` and `SPEC-016` references in `docs/specs/completed/`.

**Out of scope:**
- Modifying frozen DevOps specs (`cloud-*.lua`, `lsp-devops.lua`, `tools-dap-devops.lua`).

---

## Prerequisite Analysis

- `lua/cumulus/util/build-diagnostics.lua:18-70` currently retains legacy `parse_maven_lua` and `parse_gradle_lua` functions.
- `crates/cumulus-core/src/log_parser.rs` already handles Maven and Gradle log parsing in Rust with 100% test coverage.

---

## Constraints & Guardrails

1. **Rust-First Directive**: Zero Lua fallback parsing functions allowed in `lua/cumulus/util/`.
2. **DevOps Guardrail**: Never touch frozen DevOps specs.
3. **Zero Free Files**: All changes reside in existing sanctioned `lua/cumulus/util/` modules.
4. **Performance Budget**: Diagnostics population stays under 2ms.

---

## Execution Checklist

- [ ] **Task 1: Refactor `lua/cumulus/util/build-diagnostics.lua`**
  - Delete `parse_maven_lua()` (lines 18–46) and `parse_gradle_lua()` (lines 48–70).
  - Update `populate_from_log()` to require `rust.parse_build_log()`.
  - If `rust.is_available()` is false, notify: `"cumulus-core binary missing. Run 'cargo build --release' in crates/cumulus-core"`.

- [ ] **Task 2: Audit `lua/cumulus/util/*.lua` for Residual Fallbacks**
  - Search all `lua/cumulus/util/` files for non-Rust parsing logic and remove fallback branches.

- [ ] **Task 3: Post-Execution Verification**
  - Run `scripts/validate.sh` and assert zero fallback code remains.

---

## Verification Commands & Acceptance Criteria

```bash
cargo test --manifest-path crates/cumulus-core/Cargo.toml
bash scripts/validate.sh
luac -p lua/cumulus/util/build-diagnostics.lua lua/cumulus/util/rust.lua
nvim --headless "+checkhealth cumulus" +qa
```

### Acceptance Criteria
- [ ] `parse_maven_lua` and `parse_gradle_lua` functions are removed from `build-diagnostics.lua`.
- [ ] `scripts/validate.sh` passes 100%.
- [ ] Attempting to parse build logs without `cumulus-core` shows an explicit build instruction notification.
- [ ] Zero pure-Lua log parsing functions remain in `lua/cumulus/util/`.
