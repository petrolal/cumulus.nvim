# Specification: SPEC-029 - Dependency CVE Vulnerability & Security Scanner

## Metadata
- **Spec ID**: SPEC-029
- **Title**: Dependency CVE Vulnerability & Security Scanner
- **Status**: BACKLOG
- **Author**: Antigravity Assistant & AI Systems Architect
- **Target Files/Paths**:
  - `crates/cumulus-core/src/cve_audit.rs` (new)
  - `crates/cumulus-core/src/main.rs` (extends)
  - `lua/cumulus/util/rust.lua` (extends)
  - `lua/cumulus/plugins/tools-linting.lua` (extends)

---

## Goal & Intent
Parse dependency coordinates (`groupId:artifactId:version`) in `pom.xml` or `build.gradle` (`cumulus-core audit-deps`) and check against an offline vulnerability database, emitting security diagnostics into `vim.diagnostic`.

---

## Scope Boundaries

**In scope:**
- Match dependency coordinates against CVE database.
- Emit inline warnings/errors for vulnerable libraries (e.g. Log4j CVEs).
- Display in gutter and Trouble.nvim.

---

## Execution Checklist
- [ ] Implement `crates/cumulus-core/src/cve_audit.rs`
- [ ] Add `audit-deps` subcommand in `main.rs`
- [ ] Add Rust unit tests
- [ ] Add Lua bridge binding in `lua/cumulus/util/rust.lua`
- [ ] Connect to `tools-linting.lua`
