# Specification: SPEC-029 - Dependency CVE Vulnerability & Security Scanner

## Metadata
- **Spec ID**: SPEC-029
- **Title**: Dependency CVE Vulnerability & Security Scanner
- **Status**: BACKLOG
- **Author**: AI Systems Architect
- **Target Files/Paths**:
  - `crates/cumulus-core/src/cve_audit.rs` (new)
  - `crates/cumulus-core/src/main.rs` (extends)
  - `lua/cumulus/util/rust.lua` (extends)
  - `lua/cumulus/plugins/tools-linting.lua` (extends)

- **Implementation**: Rust (Lua bridge only — zero pure-Lua vulnerability database matching)

---

## Architecture

**Lua is a bridge to the Rust backend. All dependency coordinate extraction, CVE database lookup, and vulnerability severity scoring live in Rust.**

```
Neovim Linting  →  Lua (rust.audit_deps)  →  cumulus-core audit-deps  →  JSON Diagnostics  →  vim.diagnostic / Trouble.nvim
```

- **Rust Engine (`crates/cumulus-core`)**: `audit-deps --file <path>` parses `pom.xml`, `build.gradle`, `build.gradle.kts`, or `gradle/libs.versions.toml`, matches dependency coordinates (`groupId:artifactId:version`) against an embedded/offline vulnerability database, and outputs JSON array:
  ```json
  [
    {
      "file": "/home/user/project/pom.xml",
      "line": 34,
      "severity": "ERROR",
      "cve_id": "CVE-2021-44228",
      "summary": "Log4j2 Remote Code Execution (Log4Shell)",
      "affected_version": "2.14.1",
      "fixed_version": "2.17.1"
    }
  ]
  ```
- **Lua Bridge (`lua/cumulus/util/rust.lua`)**: `rust.audit_deps(file_path)` invokes `cumulus-core` and returns the decoded diagnostic list.
- **UI Integration**: Hooks into `nvim-lint` / `vim.diagnostic` in `lua/cumulus/plugins/tools-linting.lua` to render gutter signs and Trouble.nvim list items.

---

## Goal & Intent
Expose known CVE security vulnerabilities (such as Log4Shell or Spring4Shell) inline in project dependency files immediately upon opening `pom.xml` or `build.gradle`.

---

## Scope Boundaries

**In scope:**
- High-speed Rust vulnerability scanner (`audit-deps`) operating on offline CVE databases.
- IPC binding in `lua/cumulus/util/rust.lua`.
- Integration into `vim.diagnostic` namespace.

**Out of scope:**
- Auto-updating project dependencies without developer confirmation.
- Modifying frozen DevOps specs.

---

## Prerequisite Analysis

- `crates/cumulus-core/src/dep_resolver.rs` (SPEC-026) already parses project dependencies. `cve_audit.rs` reuses this parser.
- `tools-linting.lua` configures `nvim-lint` diagnostics.

---

## Constraints & Guardrails

1. **Rust-First Directive**: All CVE matching and severity calculation MUST be in Rust (`crates/cumulus-core/src/cve_audit.rs`). No Lua vulnerability tables.
2. **DevOps Guardrail**: Never touch frozen DevOps specs.
3. **Zero Free Files**: All bridge logic resides in `lua/cumulus/util/rust.lua`.
4. **Performance Budget**: Scan completes under 5ms.

---

## Execution Checklist

- [ ] **Task 1: Rust Engine Implementation (`crates/cumulus-core`)**
  - Create `crates/cumulus-core/src/cve_audit.rs`.
  - Add `AuditDeps { file: PathBuf }` subcommand to `main.rs`.
  - Integrate offline CVE database lookup for common JVM dependencies.
  - Add Rust unit tests in `cve_audit.rs`.

- [ ] **Task 2: Lua Bridge Binding (`lua/cumulus/util/rust.lua`)**
  - Add `M.audit_deps(file_path)` function calling `cumulus-core audit-deps`.

- [ ] **Task 3: Diagnostics Integration**
  - Extend `lua/cumulus/plugins/tools-linting.lua` to register `cumulus-cve` as a `nvim-lint` linter calling `rust.audit_deps()`.

---

## Verification Commands & Acceptance Criteria

```bash
cargo test --manifest-path crates/cumulus-core/Cargo.toml
bash scripts/validate.sh
luac -p lua/cumulus/util/rust.lua lua/cumulus/plugins/tools-linting.lua
nvim --headless "+checkhealth cumulus" +qa
```

### Acceptance Criteria
- [ ] `cargo test` passes with new `cve_audit` unit tests.
- [ ] `audit-deps` subcommand identifies vulnerable dependencies in `pom.xml`.
- [ ] Diagnostics appear in `vim.diagnostic` and Trouble.nvim.
- [ ] Operates offline without network latency.
- [ ] Zero pure-Lua CVE parsing code.
