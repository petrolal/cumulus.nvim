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
  - `lua/cumulus/util/log-indexer.lua` (new)

---

## Goal & Intent
Parse large log files (`.log`) for `ERROR` and `WARN` severity messages and return line index positions for quick split jumping.

---

## Execution Checklist
- [ ] Implement `crates/cumulus-core/src/log_indexer.rs`
- [ ] Add `index-log` subcommand in `main.rs`
- [ ] Add unit tests in Rust
- [ ] Add Lua binding in `lua/cumulus/util/rust.lua`
- [ ] Create `lua/cumulus/util/log-indexer.lua`
