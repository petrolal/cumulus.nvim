# Specification: SPEC-007 - Test Runner Integration (JUnit 5, Gradle, Maven)

## Metadata
- **Spec ID**: SPEC-007
- **Title**: Test Runner Integration (JUnit 5, Gradle, Maven)
- **Status**: REVIEW
- **Implementation**: Rust (minimal Lua)
- **Implementation**: Rust (minimal Lua)
- **Implementation**: Rust
- **Author**: AI Systems Architect
- **Target Files/Paths**:
  - `crates/cumulus-core/src/test_parser.rs` (new)
  - `crates/cumulus-core/src/main.rs` (extends)
  - `lua/cumulus/util/test-runner.lua` (new)
  - `lua/cumulus/util/rust.lua` (extends)
  - `lua/cumulus/core/lang-keymaps.lua` (extends)

---

## Goal & Intent

Developers currently must switch to terminal to run individual tests, then switch back to Neovim to edit. This breaks flow and eliminates the IDE experience of inline test results. 

This spec adds test execution directly from the editor:
- `<leader>cjtt` → run nearest test method
- `<leader>cjtc` → run test class
- `<leader>cjta` → run all tests
- Show PASS/FAIL inline and in Trouble diagnostics

---

## Scope Boundaries

**In scope:**
- Detect test class/method from current buffer + cursor position
- Generate Maven: `mvn test -Dtest=ClassName#methodName`
- Generate Gradle: `./gradlew test --tests ClassName.methodName`
- Parse JUnit 5 output for PASS/FAIL via Rust native helper (`parse-test-output`) with Lua fallback
- Populate diagnostics for failed tests

**Out of scope:**
- Older JUnit versions (only 5.x)
- TestNG or other frameworks
- Code coverage reporting

---

## Prerequisite Analysis

- No existing test runner in the codebase
- Maven/Gradle commands already exist and can be extended
- JUnit 5 output parseable via regex (`FAILURE:`, `Tests run:`, stack traces)

---

## Execution Checklist

- [x] Create `crates/cumulus-core/src/test_parser.rs` (JUnit 5 Maven/Gradle test log parser)
- [x] Extend `crates/cumulus-core/src/main.rs` with `parse-test-output` subcommand
- [x] Extend `lua/cumulus/util/rust.lua` with `rust.parse_test_output()`
- [x] Create `lua/cumulus/util/test-runner.lua`:
  - [x] Implement `detect_test_class_and_method()` → parses current Java buffer for test class/method name
  - [x] Implement `run_test_maven()` and `run_test_gradle()` → generate commands and run via terminal
  - [x] Implement `parse_test_output()` → extract PASS/FAIL from output
  - [x] Implement `populate_test_diagnostics()` → show test failures as diagnostics
- [x] Add to Java `lang_keymaps` stack in `lua/cumulus/core/lang-keymaps.lua`:
  - `<leader>cjtt` → run nearest test
  - `<leader>cjtc` → run test class
  - `<leader>cjta` → run all tests

---

## Verification Commands

```bash
bash scripts/validate.sh
luac -p lua/cumulus/util/test-runner.lua lua/cumulus/core/keymaps.lua
nvim --headless --startuptime /tmp/nvim-startuptime.log +qa && grep "NVIM STARTED" /tmp/nvim-startuptime.log
```

### Acceptance Criteria
- [ ] `<leader>cjtt` runs nearest test and shows result
- [ ] `<leader>cjtc` runs test class
- [ ] Failed tests show in diagnostics
- [ ] PASS/FAIL status visible in statusline

---

## Summary

Eliminates test/edit context switch by enabling test execution and result display directly in Neovim. Unlocks test-driven development workflow.
