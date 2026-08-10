# Specification: SPEC-022 - High-Speed Log File Indexer & Stream Parser

## Metadata
- **Spec ID**: SPEC-022
- **Title**: High-Speed Log File Indexer & Stream Parser
- **Status**: ACTIVE
- **Author**: Antigravity Assistant & AI Systems Architect
- **Target Files/Paths**:
  - `crates/cumulus-core/src/log_indexer.rs` (new)
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
Parse large log files (`.log`) for `ERROR` and `WARN` severity messages and return line index positions for quick split jumping.

---

## Execution Checklist
- [ ] Implement `crates/cumulus-core/src/log_indexer.rs`
- [ ] Add `index-log` subcommand in `main.rs`
- [ ] Add unit tests in Rust
- [ ] Add Lua binding in `lua/cumulus/util/rust.lua`
- [ ] Add feature binding to `lua/cumulus/util/rust.lua` (single Lua dispatcher)
