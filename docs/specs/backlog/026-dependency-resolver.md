# Specification: SPEC-026 - Direct Maven & Gradle Dependency Resolver

## Metadata
- **Spec ID**: SPEC-026
- **Title**: Direct Maven & Gradle Dependency Resolver
- **Status**: BACKLOG
- **Author**: Antigravity Assistant & AI Systems Architect
- **Target Files/Paths**:
  - `crates/cumulus-core/src/dep_resolver.rs` (new)
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
Parse `pom.xml` dependency management and Gradle version catalogs (`libs.versions.toml`) in Rust (`cumulus-core resolve-deps`) to resolve direct project dependencies in < 10ms without spawning heavy JVM shell processes.

---

## Scope Boundaries

**In scope:**
- Parse POM `<dependencyManagement>`, property placeholders `${version}`, and parent POM inheritance.
- Parse `libs.versions.toml` TOML version catalogs for Gradle.
- Output JSON array of `{ group, artifact, version, scope }`.

**Out of scope:**
- Remote Maven artifact downloading (handled by Maven/Gradle CLI).

---

## Execution Checklist
- [ ] Implement `crates/cumulus-core/src/dep_resolver.rs`
- [ ] Add `resolve-deps` subcommand in `main.rs`
- [ ] Add Rust unit tests
- [ ] Add Lua bridge binding in `lua/cumulus/util/rust.lua`
- [ ] Connect to `maven.lua` and `gradle.lua`
