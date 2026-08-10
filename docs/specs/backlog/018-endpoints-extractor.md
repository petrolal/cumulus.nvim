# Specification: SPEC-018 - Spring Boot & Microservice Endpoint Extractor

## Metadata
- **Spec ID**: SPEC-018
- **Title**: Spring Boot & Microservice Endpoint Extractor
- **Status**: BACKLOG
- **Author**: Antigravity Assistant & AI Systems Architect
- **Target Files/Paths**:
  - `crates/cumulus-core/src/endpoints.rs` (new)
  - `crates/cumulus-core/src/main.rs` (extends)
  - `lua/cumulus/util/rust.lua` (extends)
  - `lua/cumulus/core/keymaps.lua` (extends)

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
Extract Spring Boot (`@RestController`, `@GetMapping`, `@PostMapping`, etc.) and Jakarta/JAX-RS endpoints across Java/Kotlin source trees into a structured JSON schema to populate Neovim Telescope/Snacks pickers for instant endpoint navigation.

---

## Execution Checklist
- [ ] Implement `crates/cumulus-core/src/endpoints.rs`
- [ ] Add `extract-endpoints` subcommand in `main.rs`
- [ ] Add unit tests in Rust
- [ ] Add Lua binding in `lua/cumulus/util/rust.lua`
- [ ] Add feature binding to `lua/cumulus/util/rust.lua` (single Lua dispatcher)
- [ ] Register `<leader>cje` keymap in `lua/cumulus/core/keymaps.lua`
