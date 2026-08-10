# Specification: SPEC-028 - Multi-Module Topological Build Order & DAG Solver

## Metadata
- **Spec ID**: SPEC-028
- **Title**: Multi-Module Topological Build Order & DAG Solver
- **Status**: BACKLOG
- **Author**: Antigravity Assistant & AI Systems Architect
- **Target Files/Paths**:
  - `crates/cumulus-core/src/dag.rs` (new)
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
Parse all sub-module dependencies in multi-module Maven/Gradle projects, construct a Directed Acyclic Graph (DAG), and solve topological build order (`cumulus-core compute-build-order`) for targeted sub-project compilation.

---

## Scope Boundaries

**In scope:**
- Build graph of inter-module `<dependency>` references.
- Topological sort algorithm (Kahn's or Tarjan's algorithm in Rust).
- Output JSON array of module build sequence for `mvn -pl ... -am` or `./gradlew :module:build`.

---

## Execution Checklist
- [ ] Implement `crates/cumulus-core/src/dag.rs`
- [ ] Add `compute-build-order` subcommand in `main.rs`
- [ ] Add Rust unit tests
- [ ] Add Lua bridge binding in `lua/cumulus/util/rust.lua`
- [ ] Connect to `multimodule.lua`
