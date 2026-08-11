# Specification: SPEC-008 - Multi-Module Project Navigation

## Metadata
- **Spec ID**: SPEC-008
- **Title**: Multi-Module Project Navigation
- **Status**: COMPLETED
- **Author**: AI Systems Architect
- **Target Files/Paths**:
  - `lua/cumulus/plugins/editor-telescope.lua` (extends)

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

Enterprise Maven/Gradle projects use multi-module structures with dozens of sub-modules. Jumping between modules requires manually finding `pom.xml` or `build.gradle` files scattered across nested directories. This spec adds Telescope pickers for instant module navigation:

- `:Telescope maven_modules` → list Maven modules, open pom.xml
- `:Telescope gradle_modules` → list Gradle modules, open build.gradle
- Preview pane shows module dependencies and description

---

## Scope Boundaries

**In scope:**
- Parse root `pom.xml` for `<module>` entries
- Parse `settings.gradle` for `include` directives
- Create Telescope pickers
- Show module dependencies in preview
- Jump to module pom.xml/build.gradle

**Out of scope:**
- Gradle composite builds (advanced feature)
- BOM (Bill of Materials) visualization
- Dependency graph visualization (defer to future spec)

---

## Prerequisite Analysis

- Telescope already integrated and configured
- No existing multi-module navigation

---

## Execution Checklist
- [x] Add feature binding to `lua/cumulus/util/rust.lua` (single Lua dispatcher)
  - [x] Implement `parse_maven_modules()` → read root pom.xml, extract `<module>` list
  - [x] Implement `parse_gradle_modules()` → read settings.gradle, extract `include` list
  - [x] Implement `get_module_info()` → read module pom.xml/build.gradle and extract name, description
- [x] Extend `lua/cumulus/plugins/editor-telescope.lua`:
  - [x] Add `:Telescope maven_modules` command
  - [x] Add `:Telescope gradle_modules` command

---

## Verification Commands

```bash
bash scripts/validate.sh
luac -p lua/cumulus/util/multimodule.lua lua/cumulus/plugins/editor-telescope.lua
nvim --headless --startuptime /tmp/nvim-startuptime.log +qa && grep "NVIM STARTED" /tmp/nvim-startuptime.log
```

### Acceptance Criteria
- [x] `:Telescope maven_modules` shows list of modules
- [x] `:Telescope gradle_modules` works for Gradle projects
- [x] Selecting module opens its pom.xml/build.gradle
- [x] Preview shows module info

---

## Summary

Speeds up multi-module project navigation and reduces mental load in large enterprise projects.
