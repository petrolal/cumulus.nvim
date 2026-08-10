# Specification: SPEC-030 - Session State & Layout Sanitizer

## Metadata
- **Spec ID**: SPEC-030
- **Title**: Session State & Layout Sanitizer
- **Status**: BACKLOG
- **Author**: Antigravity Assistant & AI Systems Architect
- **Target Files/Paths**:
  - `crates/cumulus-core/src/session_cleaner.rs` (new)
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
Inspect Neovim session files (`cumulus-core session-sanitize`), strip unneeded scratch buffers (`buftype=nofile`), and format session state files atomically for `persistence.nvim`.

---

## Scope Boundaries

**In scope:**
- Fast session file parser in Rust.
- Strip floating layout window entries and blank scratch buffers.

---

## Execution Checklist
- [ ] Implement `crates/cumulus-core/src/session_cleaner.rs`
- [ ] Add `session-sanitize` subcommand in `main.rs`
- [ ] Add Rust unit tests
- [ ] Add Lua bridge binding in `lua/cumulus/util/rust.lua`
- [ ] Connect to `session.lua`
