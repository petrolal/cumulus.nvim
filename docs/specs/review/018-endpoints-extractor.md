# Specification: SPEC-018 - Spring Boot & Microservice Endpoint Extractor

## Metadata
- **Spec ID**: SPEC-018
- **Title**: Spring Boot & Microservice Endpoint Extractor
- **Status**: ACTIVE
- **Author**: Antigravity Assistant & AI Systems Architect
- **Target Files/Paths**:
  - `crates/cumulus-core/src/endpoints.rs` (new)
  - `crates/cumulus-core/src/main.rs` (extends)
  - `lua/cumulus/util/rust.lua` (extends)
  - `lua/cumulus/util/endpoints.lua` (new)
  - `lua/cumulus/core/keymaps.lua` (extends)

---

## Goal & Intent
Extract Spring Boot (`@RestController`, `@GetMapping`, `@PostMapping`, etc.) and Jakarta/JAX-RS endpoints across Java/Kotlin source trees into a structured JSON schema to populate Neovim Telescope/Snacks pickers for instant endpoint navigation.

---

## Execution Checklist
- [ ] Implement `crates/cumulus-core/src/endpoints.rs`
- [ ] Add `extract-endpoints` subcommand in `main.rs`
- [ ] Add unit tests in Rust
- [ ] Add Lua binding in `lua/cumulus/util/rust.lua`
- [ ] Create `lua/cumulus/util/endpoints.lua`
- [ ] Register `<leader>cje` keymap in `lua/cumulus/core/keymaps.lua`
