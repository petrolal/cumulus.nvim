# Specification: SPEC-019 - JaCoCo & SonarQube Code Coverage Parser

## Metadata
- **Spec ID**: SPEC-019
- **Title**: JaCoCo & SonarQube Code Coverage Parser
- **Status**: ACTIVE
- **Author**: Antigravity Assistant & AI Systems Architect
- **Target Files/Paths**:
  - `crates/cumulus-core/src/coverage.rs` (new)
  - `crates/cumulus-core/src/main.rs` (extends)
  - `lua/cumulus/util/rust.lua` (extends)
  - `lua/cumulus/util/coverage.lua` (new)

---

## Goal & Intent
Parse JaCoCo XML reports (`jacoco.xml`) and map line coverage (covered vs. missed lines) to Neovim signs in the buffer gutter.

---

## Execution Checklist
- [ ] Implement `crates/cumulus-core/src/coverage.rs`
- [ ] Add `parse-coverage` subcommand in `main.rs`
- [ ] Add unit tests in Rust
- [ ] Add Lua binding in `lua/cumulus/util/rust.lua`
- [ ] Create `lua/cumulus/util/coverage.lua`
