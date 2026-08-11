# Specification: SPEC-021 - Spring Bean Dependency Graph Generator

## Metadata
- **Spec ID**: SPEC-021
- **Title**: Spring Bean Dependency Graph Generator
- **Status**: COMPLETED
- **Author**: Antigravity Assistant & AI Systems Architect
- **Target Files/Paths**:
  - `crates/cumulus-core/src/beans.rs` (new)
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
Scan Spring stereotypes (`@Component`, `@Service`, `@Repository`, `@Bean`, `@Autowired`) across project sources to extract bean dependency graphs.

---

## Execution Checklist
- [x] Implement `crates/cumulus-core/src/beans.rs`
- [x] Add `parse-spring-beans` subcommand in `main.rs`
- [x] Add unit tests in Rust
- [x] Add Lua binding in `lua/cumulus/util/rust.lua`
- [x] Add feature binding to `lua/cumulus/util/rust.lua` (single Lua dispatcher)
