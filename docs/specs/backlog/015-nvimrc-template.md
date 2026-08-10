# Specification: SPEC-015 - Project `.nvimrc` Template

## Metadata
- **Spec ID**: SPEC-015
- **Title**: Project `.nvimrc` Template
- **Status**: BACKLOG
- **Author**: AI Systems Architect
- **Target Files/Paths**:
  - `.nvimrc.lua.example` (new, template)
  - `lua/cumulus/core/init.lua` (extends)

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

Teams often have project-specific settings (Maven profiles for integration tests, Gradle tasks for Docker builds, JVM args for profiling, IDE formatter rules). These live in scattered docs or team memory, not in the editor. Developers manually apply them, creating inconsistency and lost knowledge.

This spec enables per-project configuration via `.nvimrc.lua` at the project root:
- Auto-loaded by Neovim's `exrc` option when opening project
- Contains project-specific settings: Maven profiles, Gradle tasks, test commands, formatter prefs
- Example: `MAVEN_PROFILES="integration,docker"`, `GRADLE_TASKS="bootRun,testIntegration"`
- Checked into git repo; shared across team

---

## Scope Boundaries

**In scope:**
- Enable Neovim's exrc for `.nvimrc.lua` auto-load
- Create `.nvimrc.lua.example` template
- Document configuration options in template comments
- Show warnings if `.nvimrc.lua` exists but `.nvimrc.lua.example` is outdated

**Out of scope:**
- Sandbox/security restrictions for `.nvimrc.lua` (use Neovim's native exrc sandboxing)
- Complex scripting in `.nvimrc.lua`; keep it data-driven

---

## Prerequisite Analysis

- Neovim's `exrc` option (set in `lua/cumulus/core/options.lua` if not already)
- `.nvimrc.lua` is auto-loaded from project root if exrc is enabled

---

## Execution Checklist

- [ ] Extend `lua/cumulus/core/options.lua`:
  - [ ] Ensure `vim.opt.exrc = true` is set (enable per-project .nvimrc.lua)
- [ ] Create `.nvimrc.lua.example` template at project root:
  - [ ] Document structure with comments
  - [ ] Example configuration for Maven (profiles, goals)
  - [ ] Example configuration for Gradle (tasks, plugins)
  - [ ] Example JVM args for debugging/profiling
  - [ ] Example formatter preferences (Google style, etc.)
- [ ] Document in README: "Copy `.nvimrc.lua.example` to `.nvimrc.lua` to customize your setup"
- [ ] Add `.nvimrc.lua` to `.gitignore` (user-local overrides) or include in repo (team-wide settings)

---

## Verification Commands

```bash
bash scripts/validate.sh
luac -p lua/cumulus/core/options.lua
# Manual test: create .nvimrc.lua at project root with custom settings
# Open Neovim and verify settings are loaded
```

### Acceptance Criteria
- [ ] `.nvimrc.lua.example` exists with clear documentation
- [ ] `exrc` is enabled in options
- [ ] Team can copy template to `.nvimrc.lua` and customize
- [ ] Settings are auto-loaded when opening project
- [ ] README documents the workflow

---

## Summary

Share project-specific configs via git; ensure consistency across developer machines and reduce onboarding friction.
