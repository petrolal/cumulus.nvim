# Specification: SPEC-013 - Inline IntelliJ Code Inspections

## Metadata
- **Spec ID**: SPEC-013
- **Title**: Inline IntelliJ Code Inspections
- **Status**: BACKLOG
- **Author**: AI Systems Architect
- **Target Files/Paths**:
  - `lua/cumulus/plugins/tools-linting.lua` (extends)
  - `lua/cumulus/plugins/tools-mason.lua` (extends)

---

## Goal & Intent

JDTLS diagnostics only show language-level errors (syntax, type mismatches). IntelliJ shows style warnings, potential bugs (null pointer dereference, unused imports), and optimization hints (unoptimized collections, inefficient loops). Teams lose lightweight feedback that would catch bugs early.

This spec integrates **Checkstyle** (code style) and **SpotBugs** (potential bugs) into the linting pipeline:
- `checkstyle`: Google style (or configurable)
- `spotbugs`: Scan for likely bugs (null dereferences, redundant comparisons, etc.)
- Show inline as diagnostic markers (same as LSP errors)
- Integrate with Trouble.nvim for full error list

---

## Scope Boundaries

**In scope:**
- Add Checkstyle to Mason ensure_installed
- Add SpotBugs to Mason ensure_installed
- Wire Checkstyle + SpotBugs into nvim-lint `linters_by_ft.java`
- Parse Checkstyle/SpotBugs output → diagnostics
- Show in gutter + Trouble

**Out of scope:**
- Custom Checkstyle rules (use Google defaults)
- PMD or other static analyzers (add in separate spec)
- Automatic code fixes via code actions (defer to future spec)

---

## Prerequisite Analysis

- nvim-lint already configured and running
- Mason can install Checkstyle and SpotBugs
- Output parsers for both tools need regex implementation

---

## Execution Checklist

- [ ] Extend `lua/cumulus/plugins/tools-mason.lua`:
  - [ ] Add `"checkstyle"` to `ensure_installed`
  - [ ] Add `"spotbugs"` to `ensure_installed`
- [ ] Extend `lua/cumulus/plugins/tools-linting.lua`:
  - [ ] Add to `linters_by_ft.java`: `{ "checkstyle", "spotbugs" }`
  - [ ] Implement parser for Checkstyle output format
  - [ ] Implement parser for SpotBugs output format
  - [ ] Map severity levels to diagnostic severity

---

## Verification Commands

```bash
bash scripts/validate.sh
luac -p lua/cumulus/plugins/tools-linting.lua lua/cumulus/plugins/tools-mason.lua
nvim --headless "+Lazy! sync" +qa
nvim --headless --startuptime /tmp/nvim-startuptime.log +qa && grep "NVIM STARTED" /tmp/nvim-startuptime.log
```

### Acceptance Criteria
- [ ] Checkstyle warnings appear on Java files (style issues)
- [ ] SpotBugs warnings appear (potential bugs)
- [ ] Warnings show in gutter and Trouble
- [ ] Startup time < 50ms (lint is on-demand only)

---

## Summary

Early bug detection and code quality consistency via inline code inspections equivalent to IntelliJ.
