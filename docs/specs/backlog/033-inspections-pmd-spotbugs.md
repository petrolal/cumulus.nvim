# Specification: SPEC-033 - Unified Code Inspection Engine for PMD, SpotBugs & Checkstyle

## Metadata
- **Spec ID**: SPEC-033
- **Title**: Unified Code Inspection Engine for PMD, SpotBugs & Checkstyle (IntelliJ Ultimate Enterprise Parity)
- **Status**: BACKLOG
- **Author**: AI Systems Architect
- **Target Files/Paths**:
  - `crates/cumulus-core/src/inspection_parser.rs` (extends SPEC-013)
  - `crates/cumulus-core/src/main.rs` (extends)
  - `lua/cumulus/util/rust.lua` (extends)
  - `lua/cumulus/plugins/tools-linting.lua` (extends)

- **Implementation**: Rust (Lua bridge only — zero pure-Lua XML inspection parsing)

---

## Architecture

**Lua is a bridge to the Rust backend. All static analysis XML report parsing (Checkstyle, PMD, SpotBugs) lives in Rust.**

```
Build Report XML  →  Lua (rust.parse_code_inspections)  →  cumulus-core parse-code-inspections  →  JSON Diagnostics  →  vim.diagnostic / Trouble.nvim
```

- **Enterprise Parity Target**: Replicate IntelliJ IDEA Ultimate's Inspection Results tool window by aggregating Checkstyle, PMD, and SpotBugs static analysis reports into Neovim's unified diagnostic engine.
- **Rust Engine (`crates/cumulus-core`)**: `parse-code-inspections --file <xml_path> --engine checkstyle|pmd|spotbugs` parses:
  - Checkstyle XML (`<checkstyle>`, `<file>`, `<error>`)
  - PMD XML (`<pmd>`, `<file>`, `<violation rule="..." priority="...">`)
  - SpotBugs / FindBugs XML (`<BugCollection>`, `<file>`, `<BugInstance type="..." priority="...">`)
  And outputs unified JSON diagnostics:
  ```json
  [
    {
      "file": "/home/user/project/src/main/java/com/example/User.java",
      "line": 85,
      "col": 12,
      "message": "[PMD:UnusedPrivateField] Avoid unused private fields such as 'secretKey'.",
      "severity": "WARN",
      "rule": "UnusedPrivateField",
      "engine": "pmd"
    }
  ]
  ```
- **Lua Bridge (`lua/cumulus/util/rust.lua`)**: `rust.parse_code_inspections(xml_path, engine)` invokes `cumulus-core` and returns decoded diagnostics.
- **UI Integration**: Feeds `vim.diagnostic` namespace `"cumulus_inspections"`, making PMD and SpotBugs issues visible in line gutters, statusline badges, and Trouble.nvim panels.

---

## Goal & Intent
Upgrade the Checkstyle inspection parser (from SPEC-013) to a full enterprise Static Analysis engine supporting PMD and SpotBugs/FindBugs reports, matching IntelliJ Ultimate's enterprise Code Inspection suite.

---

## Scope Boundaries

**In scope:**
- Extending `crates/cumulus-core/src/inspection_parser.rs` with PMD and SpotBugs XML parsers.
- Adding `--engine` flag to `parse-code-inspections` subcommand.
- IPC binding in `lua/cumulus/util/rust.lua`.
- Diagnostic namespace integration in `lua/cumulus/plugins/tools-linting.lua`.

**Out of scope:**
- Modifying frozen DevOps specs (`cloud-*.lua`, `lsp-devops.lua`, `tools-dap-devops.lua`).

---

## Prerequisite Analysis

- `crates/cumulus-core/src/inspection_parser.rs` already parses Checkstyle XML. `SPEC-033` extends it to handle PMD and SpotBugs XML tags.

---

## Constraints & Guardrails

1. **Rust-First Directive**: All XML parsing for static analysis reports MUST be in Rust (`crates/cumulus-core/src/inspection_parser.rs`). No Lua XML regex matching.
2. **DevOps Guardrail**: Never touch frozen DevOps specs.
3. **Zero Free Files**: All bridge logic resides in `lua/cumulus/util/rust.lua`.
4. **Performance Budget**: XML report parsing completes under 4ms.

---

## Execution Checklist

- [ ] **Task 1: Rust Engine Expansion (`crates/cumulus-core`)**
  - Extend `crates/cumulus-core/src/inspection_parser.rs`.
  - Replace `parse-checkstyle` with `parse-code-inspections --file <xml_path> --engine checkstyle|pmd|spotbugs` in `main.rs`.
  - Add PMD XML parser (`<violation rule="..." priority="...">`).
  - Add SpotBugs XML parser (`<BugInstance type="..." priority="...">`).
  - Add Rust unit tests in `inspection_parser.rs`.

- [ ] **Task 2: Lua Bridge Binding (`lua/cumulus/util/rust.lua`)**
  - Add `M.parse_code_inspections(xml_path, engine)` function calling `cumulus-core parse-code-inspections`.

- [ ] **Task 3: Diagnostic Integration (`lua/cumulus/plugins/tools-linting.lua`)**
  - Register `cumulus-inspections` as a diagnostic provider for Java buffers in `tools-linting.lua`.

---

## Verification Commands & Acceptance Criteria

```bash
cargo test --manifest-path crates/cumulus-core/Cargo.toml
bash scripts/validate.sh
luac -p lua/cumulus/util/rust.lua lua/cumulus/plugins/tools-linting.lua
nvim --headless "+checkhealth cumulus" +qa
```

### Acceptance Criteria
- [ ] `cargo test` passes with new PMD and SpotBugs unit tests in `inspection_parser.rs`.
- [ ] `parse-code-inspections` parses PMD and SpotBugs XML files into valid JSON diagnostics.
- [ ] Diagnostics appear in gutters and Trouble.nvim for PMD/SpotBugs errors.
- [ ] Zero pure-Lua XML parsing code.
