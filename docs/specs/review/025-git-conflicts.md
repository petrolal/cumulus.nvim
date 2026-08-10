# Specification: SPEC-025 - Multi-Module Git Conflict Resolution & Marker Parser

## Metadata
- **Spec ID**: SPEC-025
- **Title**: Multi-Module Git Conflict Resolution & Marker Parser
- **Status**: ACTIVE
- **Author**: Antigravity Assistant & AI Systems Architect
- **Target Files/Paths**:
  - `crates/cumulus-core/src/conflicts.rs` (new)
  - `crates/cumulus-core/src/main.rs` (extends)
  - `lua/cumulus/util/rust.lua` (extends)
  - `lua/cumulus/util/conflicts.lua` (new)

---

## Goal & Intent
Scan files or buffers for Git conflict markers (`<<<<<<<`, `=======`, `>>>>>>>`) and extract structured conflict blocks for quick navigation.

---

## Execution Checklist
- [ ] Implement `crates/cumulus-core/src/conflicts.rs`
- [ ] Add `parse-git-conflicts` subcommand in `main.rs`
- [ ] Add unit tests in Rust
- [ ] Add Lua binding in `lua/cumulus/util/rust.lua`
- [ ] Create `lua/cumulus/util/conflicts.lua`
