# Specification: SPEC-017 - Rust Helper Expansion: Multi-Module, Stack Trace & Java Header Generators

## Metadata
- **Spec ID**: SPEC-017
- **Title**: Rust Helper Expansion: Multi-Module, Stack Trace & Java Header Generators
- **Status**: REVIEW
- **Author**: AI Systems Architect & Antigravity Assistant
- **Target Files/Paths**:
  - `crates/cumulus-core/src/multimodule.rs` (new)
  - `crates/cumulus-core/src/log_parser.rs` (extends)
  - `crates/cumulus-core/src/java_gen.rs` (new)
  - `crates/cumulus-core/src/main.rs` (extends)
  - `lua/cumulus/util/rust.lua` (extends)
  - `lua/cumulus/util/multimodule.lua` (new)
  - `lua/cumulus/core/autocmds.lua` (extends)

---

## Goal & Intent

Expand `cumulus-core` with native Rust routines for:
1. Multi-Module Project Navigation: Extracting Maven sub-modules (`pom.xml`) and Gradle sub-projects (`settings.gradle`) for instant pickers.
2. Runtime Exception Stack Trace Parsing: Extracting clickable `file:line` locations from Java/Kotlin stack traces.
3. Java Package & Header Generator: Computing dot-notation package names from source paths and outputting template class headers.

---

## Execution Checklist

- [x] Create active spec `docs/specs/active/017-rust-multimodule-stacktrace.md`
- [x] Implement `crates/cumulus-core/src/multimodule.rs` (Maven & Gradle module extractor)
- [x] Extend `crates/cumulus-core/src/log_parser.rs` with `parse_stacktrace`
- [x] Implement `crates/cumulus-core/src/java_gen.rs` (Java package & class header resolver)
- [x] Update `crates/cumulus-core/src/main.rs` CLI subcommands
- [x] Add unit tests in Rust and run `cargo test`
- [x] Extend `lua/cumulus/util/rust.lua` with new Lua bindings
- [x] Implement `lua/cumulus/util/multimodule.lua`
- [x] Update `lua/cumulus/core/autocmds.lua` to leverage Rust Java header generator
- [x] Run full project validation (`bash scripts/validate.sh`)
