# Specification: SPEC-025 - Multi-Module Git Conflict Resolution & Marker Parser

## Metadata
- **Spec ID**: SPEC-025
- **Title**: Multi-Module Git Conflict Resolution & Marker Parser
- **Status**: COMPLETED
- **Author**: Antigravity Assistant & AI Systems Architect
- **Target Files/Paths**:
  - `crates/cumulus-core/src/conflicts.rs` (new)
  - `crates/cumulus-core/src/main.rs` (extends)
  - `lua/cumulus/util/rust.lua` (extends)

- **Implementation**: Rust (Lua bridge only — minimal Lua)
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
Scan files or buffers for Git conflict markers (`<<<<<<<`, `=======`, `>>>>>>>`) and extract structured conflict blocks for quick navigation.

---

## Execution Checklist
- [x] Implement `crates/cumulus-core/src/conflicts.rs`
- [x] Add `parse-git-conflicts` subcommand in `main.rs`
- [x] Add unit tests in Rust
- [x] Add Lua binding in `lua/cumulus/util/rust.lua`
- [x] Add feature binding to `lua/cumulus/util/rust.lua` (single Lua dispatcher)
