# Specification: SPEC-014 - Standardized Setup & Health Check Automation

## Metadata
- **Spec ID**: SPEC-014
- **Title**: Standardized Setup & Health Check Automation
- **Status**: BACKLOG
- **Author**: AI Systems Architect
- **Target Files/Paths**:
  - `lua/cumulus/health.lua` (extends)
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

New team members (or after system updates) often have missing/outdated tooling. JDTLS doesn't load because Java isn't installed, Maven is outdated, or Mason didn't auto-install packages. They don't realize there's a problem until trying to code and getting cryptic LSP errors.

This spec enhances the health check system to catch setup issues immediately:
- Check Java version (report installed versions, warn if < 11)
- Check Maven/Gradle installed & executable
- Check JAVA_HOME environment variable is set
- Check Mason packages: `jdtls`, `kotlin-language-server`, `google-java-format`, etc.
- Check Git hooks are configured (pre-commit validation)
- Display warnings or errors on VimEnter (quiet; full report via `:checkhealth cumulus`)

---

## Scope Boundaries

**In scope:**
- Comprehensive tool/dependency checks
- Visual warning badges on startup
- Detailed report via `:checkhealth cumulus`
- Quick-fix suggestions (e.g., "Run `:Mason` to install missing tools")

**Out of scope:**
- Auto-install tools (require explicit user action via Mason)
- System package installation (use existing package managers)

---

## Prerequisite Analysis

- `lua/cumulus/health.lua` already exists
- Checks can use `vim.fn.system()`, `vim.fn.executable()`, environment variables

---

## Execution Checklist

- [ ] Extend `lua/cumulus/health.lua`:
  - [ ] Implement `check_java_version()` → reports installed versions, warns if < 11
  - [ ] Implement `check_maven_gradle()` → verifies both are executable
  - [ ] Implement `check_java_home()` → verifies JAVA_HOME env var is set
  - [ ] Implement `check_mason_packages()` → lists missing packages from ensure_installed
  - [ ] Implement `check_git_hooks()` → verifies pre-commit hooks exist
  - [ ] Add all to health report, color-coded (✓ OK, ⚠ WARN, ✗ ERROR)
- [ ] Extend `lua/cumulus/core/init.lua`:
  - [ ] On VimEnter, run health checks
  - [ ] Show summary notification with any WARN/ERROR counts
  - [ ] Suggest: "Run `:checkhealth cumulus` for details"

---

## Verification Commands

```bash
bash scripts/validate.sh
luac -p lua/cumulus/health.lua lua/cumulus/core/init.lua
nvim --headless "+checkhealth cumulus" +qa
```

### Acceptance Criteria
- [ ] `:checkhealth cumulus` shows comprehensive tool/dependency status
- [ ] VimEnter shows quiet notification with issues found
- [ ] All checks pass with properly configured system
- [ ] Helpful error messages guide user to fixes

---

## Summary

Catch setup issues immediately; reduce onboarding time by 50% by automating verification of all required tools and dependencies.
