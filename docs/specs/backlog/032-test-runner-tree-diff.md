# Specification: SPEC-032 - JUnit 5 XML Test Suite Tree, Duration & Assertion Diff Viewer

## Metadata
- **Spec ID**: SPEC-032
- **Title**: JUnit 5 XML Test Suite Tree, Duration & Assertion Diff Viewer (IntelliJ Ultimate Enterprise Parity)
- **Status**: BACKLOG
- **Author**: AI Systems Architect
- **Target Files/Paths**:
  - `crates/cumulus-core/src/test_report.rs` (new)
  - `crates/cumulus-core/src/main.rs` (extends)
  - `lua/cumulus/util/rust.lua` (extends)
  - `lua/cumulus/util/test-runner.lua` (extends)

- **Implementation**: Rust (Lua bridge only — zero pure-Lua XML parsing)

---

## Architecture

**Lua is a bridge to the Rust backend. All JUnit XML report parsing, duration calculations, and assertion diff extractions live in Rust.**

```
Test Execution  →  Surefire/Gradle XML  →  Lua (rust.parse_test_report)  →  cumulus-core parse-test-report  →  JSON Response  →  Test Tree & Diff UI
```

- **Enterprise Parity Target**: Replicate IntelliJ IDEA Ultimate's Test Runner tool window with hierarchical test trees, per-test execution duration in milliseconds, and expected-vs-actual assertion diff viewer (`assertEquals` / `assertThat`).
- **Rust Engine (`crates/cumulus-core`)**: `parse-test-report --file <xml_path>` parses Maven Surefire (`target/surefire-reports/TEST-*.xml`) and Gradle (`build/test-results/test/TEST-*.xml`) JUnit XML files, returning JSON payload:
  ```json
  [
    {
      "classname": "com.example.service.UserServiceTest",
      "name": "testFindUser_Success",
      "time_ms": 145,
      "status": "FAILED",
      "failure_type": "org.opentest4j.AssertionFailedError",
      "failure_message": "expected: <User(id=1)> but was: <null>",
      "expected": "User(id=1)",
      "actual": "null",
      "file": "/home/user/project/src/test/java/com/example/service/UserServiceTest.java",
      "line": 48
    }
  ]
  ```
- **Lua Bridge (`lua/cumulus/util/rust.lua`)**: `rust.parse_test_report(xml_path)` invokes `cumulus-core` and returns the decoded test results table.
- **UI Integration**: Displays a structured test result tree in Snacks picker / Telescope, showing test status icons, millisecond execution benchmarks, and a side-by-side assertion diff buffer for failures.

---

## Goal & Intent
Upgrade the basic test runner output (from SPEC-007 / SPEC-016) to full IntelliJ Ultimate Test Runner parity, giving enterprise Java/Kotlin developers structured test tree navigation, timing analysis, and assertion diffs.

---

## Scope Boundaries

**In scope:**
- High-speed Rust XML parser (`parse-test-report`) for JUnit 5 Surefire/Gradle XML reports.
- Extracting execution timing (`time_ms`) and assertion expected/actual values.
- IPC binding in `lua/cumulus/util/rust.lua`.
- Test tree & diff viewer integration in `lua/cumulus/util/test-runner.lua`.

**Out of scope:**
- Modifying frozen DevOps specs (`cloud-*.lua`, `lsp-devops.lua`, `tools-dap-devops.lua`).

---

## Prerequisite Analysis

- `crates/cumulus-core/src/test_parser.rs` currently parses console stdout text. `test_report.rs` adds XML report parsing for Surefire/Failsafe/Gradle test results.
- `lua/cumulus/util/test-runner.lua` manages test invocation.

---

## Constraints & Guardrails

1. **Rust-First Directive**: All XML parsing of test reports MUST be in Rust (`crates/cumulus-core/src/test_report.rs`). No Lua XML parsing.
2. **DevOps Guardrail**: Never touch frozen DevOps specs.
3. **Zero Free Files**: All bridge logic resides in `lua/cumulus/util/rust.lua`.
4. **Performance Budget**: XML report parsing completes under 3ms.

---

## Execution Checklist

- [ ] **Task 1: Rust Engine Implementation (`crates/cumulus-core`)**
  - Create `crates/cumulus-core/src/test_report.rs`.
  - Add `ParseTestReport { file: PathBuf }` subcommand to `main.rs`.
  - Implement JUnit XML report parser using `quick-xml` / `serde-xml-rs`.
  - Extract classname, test name, duration in ms, failure stack, expected & actual values.
  - Add Rust unit tests in `test_report.rs`.

- [ ] **Task 2: Lua Bridge Binding (`lua/cumulus/util/rust.lua`)**
  - Add `M.parse_test_report(xml_path)` function calling `cumulus-core parse-test-report`.

- [ ] **Task 3: Test Runner UI Upgrade (`lua/cumulus/util/test-runner.lua`)**
  - Update `test-runner.lua` to locate generated XML reports post-execution and call `rust.parse_test_report()`.
  - Render test tree picker with pass/fail icons, duration benchmarks, and diff buffer for failures.

---

## Verification Commands & Acceptance Criteria

```bash
cargo test --manifest-path crates/cumulus-core/Cargo.toml
bash scripts/validate.sh
luac -p lua/cumulus/util/rust.lua lua/cumulus/util/test-runner.lua
nvim --headless "+checkhealth cumulus" +qa
```

### Acceptance Criteria
- [ ] `cargo test` passes with new `test_report` unit tests.
- [ ] `parse-test-report` subcommand returns JSON array with class names, durations in ms, and assertion diffs.
- [ ] Test failure notifications display expected vs actual diffs.
- [ ] Zero pure-Lua XML parsing code.
