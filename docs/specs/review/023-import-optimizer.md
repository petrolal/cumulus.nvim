# Specification: SPEC-023 - Java/Kotlin Import Optimizer

## Metadata
- **Spec ID**: SPEC-023
- **Title**: Java/Kotlin Import Optimizer
- **Status**: ACTIVE
- **Author**: Antigravity Assistant & AI Systems Architect
- **Target Files/Paths**:
  - `crates/cumulus-core/src/imports.rs` (new)
  - `crates/cumulus-core/src/main.rs` (extends)
  - `lua/cumulus/util/rust.lua` (extends)
  - `lua/cumulus/util/import-optimizer.lua` (new)

---

## Goal & Intent
Parse Java/Kotlin file imports, sort according to IntelliJ ordering conventions, remove duplicate imports, and return formatted import lines.

---

## Execution Checklist
- [ ] Implement `crates/cumulus-core/src/imports.rs`
- [ ] Add `optimize-imports` subcommand in `main.rs`
- [ ] Add unit tests in Rust
- [ ] Add Lua binding in `lua/cumulus/util/rust.lua`
- [ ] Create `lua/cumulus/util/import-optimizer.lua`
