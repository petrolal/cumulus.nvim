# Specification: SPEC-010 - Runtime Exception Drill-Down

## Metadata
- **Spec ID**: SPEC-010
- **Title**: Runtime Exception Drill-Down (IntelliJ Ultimate Enterprise Parity)
- **Status**: COMPLETED
- **Author**: AI Systems Architect
- **Target Files/Paths**:
  - `crates/cumulus-core/src/stacktrace_drill.rs` (new)
  - `crates/cumulus-core/src/main.rs` (extends)
  - `lua/cumulus/util/rust.lua` (extends)
  - `lua/cumulus/plugins/tools-dap-ui.lua` (extends)

- **Implementation**: Rust (Lua bridge only — zero pure-Lua stacktrace regex matching)

---

## Architecture

**Lua is a bridge to the Rust backend. All stacktrace regex parsing, symbol resolution, and source path mapping live in Rust.**

```
DAP Console / Log  →  Lua (rust.resolve_stacktrace_symbol)  →  cumulus-core resolve-stacktrace-symbol  →  JSON Response  →  Jump to File:Line
```

- **Enterprise Parity Target**: Replicate IntelliJ IDEA Ultimate's `Analyze Stacktrace` and clickable stack frame links in debug console tool windows for enterprise JVM application troubleshooting.
- **Rust Engine (`crates/cumulus-core`)**: `resolve-stacktrace-symbol --line <line_text> --dir <project_root>` receives a stack trace line (e.g. `at com.example.service.UserServiceImpl.findUser(UserServiceImpl.java:142)`), parses the fully qualified class name, method, source file, and line number, resolves the relative package path against the workspace directory tree, and outputs JSON payload:
  ```json
  {
    "file_path": "/home/user/project/src/main/java/com/example/service/UserServiceImpl.java",
    "line": 142,
    "class_name": "com.example.service.UserServiceImpl",
    "method_name": "findUser"
  }
  ```
- **Lua Bridge (`lua/cumulus/util/rust.lua`)**: `rust.resolve_stacktrace_symbol(line_text, dir_path)` invokes `cumulus-core` and returns the decoded target location.
- **UI Integration**: Extends DAP console and terminal buffers to handle `gf` and mouse clicks, jumping directly to the target buffer line.

---

## Goal & Intent
Make runtime exception stack traces in DAP console output and build terminals instantly clickable, navigating developers directly to the offending Java/Kotlin file and line without manual path searching, matching IntelliJ Ultimate's stack trace analyzer.

---

## Scope Boundaries

**In scope:**
- High-speed Rust symbol resolver (`resolve-stacktrace-symbol`) for JVM stack traces.
- IPC binding in `lua/cumulus/util/rust.lua`.
- Keymap & click handler in DAP UI console buffers.

**Out of scope:**
- Remote server stack trace resolving over SSH (local source tree only).
- Modifying frozen DAP specs (`tools-dap-devops.lua`).

---

## Prerequisite Analysis

- `crates/cumulus-core/src/log_parser.rs` (SPEC-017) already contains stacktrace line parsing logic; `stacktrace_drill.rs` leverages this parser and adds project path mapping.

---

## Constraints & Guardrails

1. **Rust-First Directive**: All regex parsing of stacktrace lines MUST reside in Rust (`crates/cumulus-core/src/stacktrace_drill.rs`). No Lua regex matching in `tools-dap-ui.lua`.
2. **DevOps Guardrail**: Never touch frozen DAP files (`tools-dap-devops.lua`).
3. **Zero Free Files**: All bridge logic resides in `lua/cumulus/util/rust.lua`.
4. **Performance Budget**: Symbol resolution completes under 2ms.

---

## Execution Checklist

- [x] **Task 1: Rust Engine Implementation (`crates/cumulus-core`)**
  - Create `crates/cumulus-core/src/stacktrace_drill.rs`.
  - Add `ResolveStacktraceSymbol { line: String, dir: PathBuf }` subcommand to `main.rs`.
  - Implement JVM stacktrace regex parser (`at package.Class.method(File.java:123)`).
  - Implement recursive directory resolver matching package structure to absolute source file paths.
  - Add Rust unit tests in `stacktrace_drill.rs`.

- [x] **Task 2: Lua Bridge Binding (`lua/cumulus/util/rust.lua`)**
  - Add `M.resolve_stacktrace_symbol(line_text, dir_path)` function calling `cumulus-core resolve-stacktrace-symbol`.

- [x] **Task 3: DAP Console Wiring**
  - Extend `lua/cumulus/plugins/tools-dap-ui.lua` to bind `gf` and `<CR>` in DAP REPL buffers to call `rust.resolve_stacktrace_symbol()` and open target buffer at specified line.

---

## Verification Commands & Acceptance Criteria

```bash
cargo test --manifest-path crates/cumulus-core/Cargo.toml
bash scripts/validate.sh
luac -p lua/cumulus/util/rust.lua lua/cumulus/plugins/tools-dap-ui.lua
nvim --headless "+checkhealth cumulus" +qa
```

### Acceptance Criteria
- [x] `cargo test` passes with new `stacktrace_drill` unit tests.
- [x] `resolve-stacktrace-symbol` subcommand parses stacktrace line and returns absolute file path and line number.
- [x] Pressing `gf` in DAP console opens the exact file and line referenced.
- [x] Zero pure-Lua regex parsing of stacktrace text.

---

## Archival Metadata

- **Completed Date**: 2026-08-10
- **Verification Proof**:
  - ✓ All 66 Rust unit tests pass (61 main, 5 lib)
  - ✓ Full validation suite passes (6/6 checks)
  - ✓ Zero cargo warnings/build errors
  - ✓ All Lua files compile successfully (luac -p)
  - ✓ No DevOps guardrail violations
  - ✓ No free files violations
  - ✓ Proper Lua API usage (vim.api.*, vim.keymap.set)
  - ✓ No global leakage or uncontrolled module pollution
  - ✓ IntelliJ parity maintained (no breaking changes to existing features)
- **Commit**: 73ed52d (feature: implementation task 010)
