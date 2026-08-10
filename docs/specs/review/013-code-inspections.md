# Specification: SPEC-013 - Inline IntelliJ Code Inspections

## Metadata
- **Spec ID**: SPEC-013
- **Title**: Inline IntelliJ Code Inspections
- **Status**: REVIEW
- **Implementation**: Rust (Lua bridge only — minimal Lua)
- **Author**: AI Systems Architect
- **Target Files/Paths**:
  - `crates/cumulus-core/src/inspection_parser.rs` (new)
  - `crates/cumulus-core/src/main.rs` (extends)
  - `lua/cumulus/util/rust.lua` (extends)
  - `lua/cumulus/plugins/tools-linting.lua` (extends)
  - `lua/cumulus/plugins/tools-mason.lua` (extends)

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
- Parse Checkstyle/SpotBugs output via Rust native helper (`parse-checkstyle`) into diagnostics
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

- [x] Create `crates/cumulus-core/src/inspection_parser.rs` (Checkstyle XML report parser in Rust)
- [x] Extend `crates/cumulus-core/src/main.rs` with `parse-checkstyle` subcommand
- [x] Extend `lua/cumulus/util/rust.lua` with `rust.parse_checkstyle()`
- [x] Extend `lua/cumulus/plugins/tools-mason.lua`: Add `"checkstyle"` to `ensure_installed`
- [x] Extend `lua/cumulus/plugins/tools-linting.lua`: Add to `linters_by_ft.java`: `{ "checkstyle" }`

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
