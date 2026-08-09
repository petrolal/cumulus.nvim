# Specification: SPEC-008 - Multi-Module Project Navigation

## Metadata
- **Spec ID**: SPEC-008
- **Title**: Multi-Module Project Navigation
- **Status**: BACKLOG
- **Author**: AI Systems Architect
- **Target Files/Paths**:
  - `lua/cumulus/util/multimodule.lua` (new)
  - `lua/cumulus/plugins/editor-telescope.lua` (extends)

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

- [ ] Create `lua/cumulus/util/multimodule.lua`:
  - [ ] Implement `parse_maven_modules()` → read root pom.xml, extract `<module>` list
  - [ ] Implement `parse_gradle_modules()` → read settings.gradle, extract `include` list
  - [ ] Implement `get_module_info()` → read module pom.xml/build.gradle and extract name, description
- [ ] Extend `lua/cumulus/plugins/editor-telescope.lua`:
  - [ ] Add `:Telescope maven_modules` command
  - [ ] Add `:Telescope gradle_modules` command

---

## Verification Commands

```bash
bash scripts/validate.sh
luac -p lua/cumulus/util/multimodule.lua lua/cumulus/plugins/editor-telescope.lua
nvim --headless --startuptime /tmp/nvim-startuptime.log +qa && grep "NVIM STARTED" /tmp/nvim-startuptime.log
```

### Acceptance Criteria
- [ ] `:Telescope maven_modules` shows list of modules
- [ ] `:Telescope gradle_modules` works for Gradle projects
- [ ] Selecting module opens its pom.xml/build.gradle
- [ ] Preview shows module info

---

## Summary

Speeds up multi-module project navigation and reduces mental load in large enterprise projects.
