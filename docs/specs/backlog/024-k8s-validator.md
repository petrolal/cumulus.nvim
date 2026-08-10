# Specification: SPEC-024 - Helm Chart Values & Kubernetes Schema Validator

## Metadata
- **Spec ID**: SPEC-024
- **Title**: Helm Chart Values & Kubernetes Schema Validator
- **Status**: BACKLOG
- **Author**: Antigravity Assistant & AI Systems Architect
- **Target Files/Paths**:
  - `crates/cumulus-core/src/k8s_validator.rs` (new)
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
Validate Helm `values.yaml` and Kubernetes manifest YAML syntax & field structure natively in Rust.

---

## Execution Checklist
- [ ] Implement `crates/cumulus-core/src/k8s_validator.rs`
- [ ] Add `validate-k8s-manifest` subcommand in `main.rs`
- [ ] Add unit tests in Rust
- [ ] Add Lua binding in `lua/cumulus/util/rust.lua`
- [ ] Add feature binding to `lua/cumulus/util/rust.lua` (single Lua dispatcher)
