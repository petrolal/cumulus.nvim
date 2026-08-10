# Specification: SPEC-020 - Flyway & Liquibase Migration Validator

## Metadata
- **Spec ID**: SPEC-020
- **Title**: Flyway & Liquibase Migration Validator
- **Status**: ACTIVE
- **Author**: Antigravity Assistant & AI Systems Architect
- **Target Files/Paths**:
  - `crates/cumulus-core/src/migrations.rs` (new)
  - `crates/cumulus-core/src/main.rs` (extends)
  - `lua/cumulus/util/rust.lua` (extends)
  - `lua/cumulus/util/migrations.lua` (new)

---

## Goal & Intent
Validate Flyway (`V1__...sql`) and Liquibase migration scripts for version ordering, naming conventions, and duplicate version conflicts.

---

## Execution Checklist
- [ ] Implement `crates/cumulus-core/src/migrations.rs`
- [ ] Add `validate-migrations` subcommand in `main.rs`
- [ ] Add unit tests in Rust
- [ ] Add Lua binding in `lua/cumulus/util/rust.lua`
- [ ] Create `lua/cumulus/util/migrations.lua`
