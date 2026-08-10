# Specification: SPEC-021 - Spring Bean Dependency Graph Generator

## Metadata
- **Spec ID**: SPEC-021
- **Title**: Spring Bean Dependency Graph Generator
- **Status**: ACTIVE
- **Author**: Antigravity Assistant & AI Systems Architect
- **Target Files/Paths**:
  - `crates/cumulus-core/src/beans.rs` (new)
  - `crates/cumulus-core/src/main.rs` (extends)
  - `lua/cumulus/util/rust.lua` (extends)
  - `lua/cumulus/util/beans.lua` (new)

---

## Goal & Intent
Scan Spring stereotypes (`@Component`, `@Service`, `@Repository`, `@Bean`, `@Autowired`) across project sources to extract bean dependency graphs.

---

## Execution Checklist
- [ ] Implement `crates/cumulus-core/src/beans.rs`
- [ ] Add `parse-spring-beans` subcommand in `main.rs`
- [ ] Add unit tests in Rust
- [ ] Add Lua binding in `lua/cumulus/util/rust.lua`
- [ ] Create `lua/cumulus/util/beans.lua`
