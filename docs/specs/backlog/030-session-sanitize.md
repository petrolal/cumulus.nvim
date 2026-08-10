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
  - `lua/cumulus/util/session.lua` (extends)

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
