# Specification: SPEC-009 - Dependency Lens & Version Checker

## Metadata
- **Spec ID**: SPEC-009
- **Title**: Dependency Lens & Version Checker
- **Status**: BACKLOG
- **Author**: AI Systems Architect
- **Target Files/Paths**:
  - `lua/cumulus/plugins/lsp-deps.lua` (new)
  - `lua/cumulus/plugins/tools-mason.lua` (extends)

---

## Goal & Intent

When editing `pom.xml` / `build.gradle`, developers can't see if dependencies are outdated or if security updates are available. Many projects run weeks behind latest versions, creating accumulating security and stability risks.

This spec adds inline code lens for each dependency showing:
- `Current: 1.2.3 → Latest: 1.5.0`
- Color-coded by age (green = current, yellow = 1-2 versions old, red = 3+ versions old)
- Code action to auto-update version on keypress

---

## Scope Boundaries

**In scope:**
- Show available versions from Maven Central / Gradle Plugin Portal
- Inline code lens on each dependency line
- Code action to auto-update
- Cache version info for offline work

**Out of scope:**
- Automatic version updates (require explicit user action)
- Beta/snapshot version suggestions
- Transitive dependency tree (defer to future spec)

---

## Prerequisite Analysis

- No existing dependency checking
- Requires Mason package for version lookup or cached data
- Code lens needs LSP-like provider

---

## Execution Checklist

- [ ] Create `lua/cumulus/plugins/lsp-deps.lua`:
  - [ ] Implement version lookup (cache file in `~/.cache/nvim/dependency-versions.json`)
  - [ ] Implement code lens provider for pom.xml/build.gradle
  - [ ] Implement code action for version update
- [ ] Add setup in keymaps or autocmds to refresh on buffer enter
- [ ] Cache management: refresh cache periodically

---

## Verification Commands

```bash
bash scripts/validate.sh
luac -p lua/cumulus/plugins/lsp-deps.lua lua/cumulus/plugins/tools-mason.lua
nvim --headless --startuptime /tmp/nvim-startuptime.log +qa && grep "NVIM STARTED" /tmp/nvim-startuptime.log
```

### Acceptance Criteria
- [ ] Code lens appears for each dependency showing latest version
- [ ] Color-coded by version age
- [ ] Code action updates dependency version
- [ ] Works offline with cached data

---

## Summary

Compliance win; improves security posture and dependency health visibility in real-time.
