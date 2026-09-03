---
title: 'Visual Test Runner & Coverage'
type: 'feature'
created: '2026-09-03'
status: 'in-review'
review_loop_iteration: 0
context: ['/home/petrolal/tetravim.nvim/_bmad-output/implementation-artifacts/epic-1-context.md']
baseline_commit: 'fd2e32cf1c521f7bad7b121b35e3c9cce3db8682'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** JVM developers currently lack an interactive visual test discovery/runner and inline code coverage overlay, having to rely on raw terminal commands or external IDEs to inspect test results and verify JaCoCo coverage.

**Approach:** Integrate `neotest` with `neotest-java` for visual test tree discovery, nearest test execution, and DAP debugging; build a native Lua JaCoCo XML coverage parser and buffer overlay module that highlights covered/uncovered lines without relying on the deprecated Scala engine.

## Boundaries & Constraints

**Always:** Follow the existing lazy.nvim plugin-spec conventions. Provide native Lua parsing and buffer overlay rendering. Ensure zero UI freezes during test execution or coverage file parsing. Fall back gracefully when external tools or files are missing.

**Ask First:** If any additional heavy external test framework plugins are required beyond `neotest` and `neotest-java`.

**Never:** Reintroduce Scala or `sbt` code. Never delegate test discovery or coverage parsing to the deprecated `tetravim-engine` binary.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Run Nearest Test | Cursor on test method, `<leader>jtt` or `<leader>tr` | `neotest.run.run()` executes nearest JUnit test with visual progress | Warns if no test context found or buffer is not a test file |
| Run Test Class/File | Active test file, `<leader>jtc` or `<leader>tf` | `neotest.run.run(vim.fn.expand('%'))` executes all tests in file | Warns if file is not runnable |
| Toggle Test Summary / Output | `<leader>jts` or `<leader>ts`, `<leader>jto` or `<leader>to` | Opens/closes Neotest summary tree or output panel | No-op / safe toggle |
| Debug Nearest Test | Cursor on test method, `<leader>jtd` or `<leader>td` | Runs nearest test with `{ strategy = "dap" }` | Warns if DAP / debugger is not configured |
| Load JaCoCo Coverage | `<leader>jcl` or `:TetraVimCoverageLoad [path]` | Discovers `jacoco.xml`, parses line data, overlays signs/diagnostics in buffers | Warns if no `jacoco.xml` found in standard target/build dirs |
| Toggle / Clear Coverage | `<leader>jct` (toggle), `<leader>jcx` (clear) | Clears signs/extmarks from buffers | Cleans up namespace safely |
| Corrupt / Empty XML | Malformed `jacoco.xml` file | Gracefully notifies user of parse failure without crashing | Catches error with pcall and displays error notification |

</frozen-after-approval>

## Code Map

- `lua/tetravim/plugins/tools-test.lua` -- Lazy plugin specification for `nvim-neotest/neotest`, `rcasia/neotest-java`, and keybindings.
- `lua/tetravim/util/coverage.lua` -- Native Lua module for finding, parsing JaCoCo XML reports, and applying signs/extmarks/diagnostics buffer overlays.
- `lua/tetravim/util/jvm.lua` -- JVM platform keymap integration under `<leader>jt` (test runner) and `<leader>jc` (code coverage).
- `lua/tetravim/plugins/ui-whichkey.lua` -- Global `<leader>t` and JVM which-key group spec definitions.
- `lua/tetravim/util/engine.lua` -- Migrate `M.view_coverage` and `M.load_coverage` to delegate to `tetravim.util.coverage.load` instead of the Scala engine.
- `lua/tetravim/tests/test_coverage_spec.lua` -- Busted unit tests validating JaCoCo XML parsing logic, coverage calculations, and module API.
- `scripts/validate-test-coverage.sh` -- Dedicated behavioral smoke test verifying Neotest specs, keymaps, and coverage overlay.
- `scripts/validate.sh` -- Integration into the main smoke test suite.

## Tasks & Acceptance

**Execution:**
- [x] `lua/tetravim/util/coverage.lua` -- Create native Lua JaCoCo coverage module: discover standard report paths (`target/site/jacoco/jacoco.xml`, `build/reports/jacoco/test/jacocoTestReport.xml`, `target/site/jacoco-aggregate/jacoco.xml`), parse XML line coverage (covered, uncovered, partial), manage signs (`TetraVimCoverageCovered`, `TetraVimCoverageUncovered`, `TetraVimCoveragePartial`) and diagnostics overlay, provide `load`, `clear`, `toggle`, and `summary` functions.
- [x] `lua/tetravim/plugins/tools-test.lua` -- Create lazy spec for `neotest` and `neotest-java` with proper dependencies (`nvim-nio`, `plenary.nvim`, `FixCursorHold.nvim`, `nvim-treesitter`), lazy loading on JVM filetypes and keymaps.
- [x] `lua/tetravim/util/jvm.lua` -- Update test runner (`<leader>jt`) keymaps to invoke `neotest` runners with fallback, and add `<leader>jc` group keymaps for coverage (`<leader>jcl` load, `<leader>jcx` clear, `<leader>jct` toggle, `<leader>jcs` summary).
- [x] `lua/tetravim/plugins/ui-whichkey.lua` -- Register `<leader>t` (test runner) and `<leader>jc` (code coverage) which-key groups.
- [x] `lua/tetravim/util/engine.lua` -- Update `view_coverage` / `load_coverage` to delegate to native Lua `coverage.lua` instead of calling deprecated engine commands.
- [x] `lua/tetravim/tests/test_coverage_spec.lua` -- Implement unit tests verifying JaCoCo XML parsing, line coverage mapping, edge cases (missing file, empty XML, partial coverage), and clear/toggle functionality.
- [x] `scripts/validate-test-coverage.sh` & `scripts/validate.sh` -- Create dedicated smoke test script verifying neotest and coverage module, and reference it in `validate.sh` and `AGENTS.md`.

**Acceptance Criteria:**
- Given a Java/JVM project with JUnit tests, when the developer triggers `<leader>jtt` (run nearest) or `<leader>jtc` (run file), then `neotest` discovers and executes the test context with visual feedback.
- Given an active test session, when the developer toggles `<leader>jts` (summary) or `<leader>jto` (output), then the Neotest test tree and output panels open and close smoothly.
- Given a Maven or Gradle build producing a JaCoCo XML report, when the developer runs `<leader>jcl` (or `:TetraVimCoverageLoad`), then the report is parsed natively in Lua and buffer signs/extmarks highlight covered, uncovered, and partial lines.
- Given active coverage signs in a buffer, when the developer triggers `<leader>jcx` or `<leader>jct`, then the signs and overlays are cleared or toggled without errors.
- Given missing or malformed JaCoCo XML, then the system notifies the developer gracefully without raising unhandled Lua errors or freezing the editor.

## Design Notes

The JaCoCo XML format contains `<package name="..."> <sourcefile name="..."> <line nr="N" mi="M" ci="C" mb="MB" cb="CB"/>`. A fast, streaming Lua pattern parser extracts line numbers and instruction/branch coverage flags without requiring heavy external C libraries or the deprecated Scala engine.
Signs are defined using standard Tetris/TetraVim highlight groups:
- Covered lines: `DiagnosticSignOk` / `DiffAdd` / green sign `▎`
- Uncovered lines: `DiagnosticSignError` / `DiffDelete` / red sign `▎`
- Partially covered lines: `DiagnosticSignWarn` / `DiffChange` / yellow sign `▎`

## Verification

**Commands:**
- `bash scripts/validate-test-coverage.sh` -- expected: all static, behavioral, and coverage parsing checks exit 0.
- `bash scripts/validate.sh` -- expected: full smoke suite passes.
- `nvim --headless -u init.lua -c "Lazy! load plenary.nvim" -c "PlenaryBustedDirectory lua/tetravim/tests/"` -- expected: all busted specs pass.
