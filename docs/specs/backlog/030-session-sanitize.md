# Specification: SPEC-030 - Session State & Layout Sanitizer

## Metadata
- **Spec ID**: SPEC-030
- **Title**: Session State & Layout Sanitizer (IntelliJ Ultimate Enterprise Parity)
- **Status**: BACKLOG
- **Author**: AI Systems Architect
- **Target Files/Paths**:
  - `crates/cumulus-core/src/session_cleaner.rs` (new)
  - `crates/cumulus-core/src/main.rs` (extends)
  - `lua/cumulus/util/rust.lua` (extends)
  - `lua/cumulus/util/session.lua` (extends)

- **Implementation**: Rust (Lua bridge only — zero pure-Lua session script parsing or buffer cleanup loops)

---

## Architecture

**Lua is a bridge to the Rust backend. All session `.vim` script parsing, scratch buffer stripping, and atomic layout rewriting live in Rust.**

```
PersistenceSavePre Autocmd  →  Lua (rust.sanitize_session)  →  cumulus-core session-sanitize  →  Cleaned .vim Session File
```

- **Enterprise Parity Target**: Replicate IntelliJ IDEA Ultimate's Workspace State & Frame Preservation engine for multi-tab enterprise workspace persistence.
- **Rust Engine (`crates/cumulus-core`)**: `session-sanitize --file <path>` opens Neovim `persistence.nvim` generated `.vim` session files, identifies un-named scratch buffers (`badd +0 [No Name]`), Snacks dashboard/explorer floating windows (`snacks_dashboard`, `snacks_picker`), and empty buffer references, strips invalid window creation commands, and writes back a sanitized `.vim` session file atomically.
- **Lua Bridge (`lua/cumulus/util/rust.lua`)**: `rust.sanitize_session(session_file_path)` invokes `cumulus-core` and returns boolean success.
- **UI Integration**: `lua/cumulus/util/session.lua` invokes `rust.sanitize_session()` during `PersistenceSavePre` autocmd hooks.

---

## Goal & Intent
Eliminate persistent `[No Name]` scratch window pollution and floating layout window corruption during `persistence.nvim` session save and restore cycles by processing session state files in Rust before saving.

---

## Scope Boundaries

**In scope:**
- High-speed Rust session file cleaner (`session-sanitize`).
- Stripping ephemeral picker windows, dashboard buffers, and un-named blank pages from `.vim` session scripts.
- IPC binding in `lua/cumulus/util/rust.lua`.
- Integration into `lua/cumulus/util/session.lua`.

**Out of scope:**
- Modifying `persistence.nvim` plugin core code.
- Modifying frozen DevOps specs.

---

## Prerequisite Analysis

- `lua/cumulus/util/session.lua` currently contains pure-Lua window inspection loops (`close_dashboard_windows`, `close_blank_windows`). `SPEC-030` replaces these manual loops with Rust session script sanitization.

---

## Constraints & Guardrails

1. **Rust-First Directive**: All parsing and regex filtering of `.vim` session files MUST be in Rust (`crates/cumulus-core/src/session_cleaner.rs`).
2. **DevOps Guardrail**: Never touch frozen DevOps specs.
3. **Zero Free Files**: All bridge logic resides in `lua/cumulus/util/rust.lua`.
4. **Performance Budget**: Sanitization completes under 3ms.

---

## Execution Checklist

- [ ] **Task 1: Rust Engine Implementation (`crates/cumulus-core`)**
  - Create `crates/cumulus-core/src/session_cleaner.rs`.
  - Add `SessionSanitize { file: PathBuf }` subcommand to `main.rs`.
  - Implement `.vim` session file parser and line filtering engine.
  - Add Rust unit tests in `session_cleaner.rs`.

- [ ] **Task 2: Lua Bridge Binding (`lua/cumulus/util/rust.lua`)**
  - Add `M.sanitize_session(file_path)` function calling `cumulus-core session-sanitize`.

- [ ] **Task 3: Session Module Refactoring**
  - Update `lua/cumulus/util/session.lua` to call `rust.sanitize_session()` during `PersistenceSavePre`.

---

## Verification Commands & Acceptance Criteria

```bash
cargo test --manifest-path crates/cumulus-core/Cargo.toml
bash scripts/validate.sh
luac -p lua/cumulus/util/rust.lua lua/cumulus/util/session.lua
nvim --headless "+checkhealth cumulus" +qa
```

### Acceptance Criteria
- [ ] `cargo test` passes with new `session_cleaner` unit tests.
- [ ] `session-sanitize` subcommand strips blank buffers and floating picker windows from `.vim` session scripts.
- [ ] Session restore opens without stray `[No Name]` split windows.
- [ ] Zero pure-Lua window inspection loops in `session.lua`.
