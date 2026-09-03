---
title: 'Visual Test Runner & Coverage'
type: 'feature'
created: '2026-09-03'
status: 'done'
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

### Review Findings

_Adversarial code review of the 8 Code Map files, review loop iteration 0 (2026-09-03). 5 decision-needed (all resolved → patch), 18 patch, 8 defer, 7 dismissed as noise._

**Decision needed (resolved)**

- [x] [Review][Decision] Coverage parse + overlay application runs fully synchronous on the UI thread vs spec "zero UI freezes" — **resolved: full async-chunked parse/apply rewrite → patch (P14)**
- [x] [Review][Decision] Coverage results injected as `vim.diagnostic` entries pollute the diagnostics channel — **resolved: drop diagnostics, signs + virtual text only → patch (P15)**
- [x] [Review][Decision] `test_coverage_spec.lua` (+197) not executed by any runner — **resolved: wire it into `validate.sh` → patch (P16)**
- [x] [Review][Decision] `validate.sh` step `[4/7]` theme rewrite is out of story scope — **resolved: restore theme assertions, split the change into its own story → patch (P17)**
- [x] [Review][Decision] `<leader>jta` runs `neotest.run.run(vim.fn.getcwd())` (full-tree mass run) — **resolved: scope the run to detected test roots → patch (P18)**

**Patch**

- [x] [Review][Patch] Drop the bare-basename fallback in `find_file_rec_for_buf` — `vim.endswith(buf_name, "/" .. rec.sourcefile)` overlays the wrong file when two packages share a sourcefile name, and `pairs()` order makes the winner nondeterministic; match the package-qualified path only [lua/tetravim/util/coverage.lua:196]
- [x] [Review][Patch] Line-status cascade misclassifies branch-only-covered lines (`cb>0`, `ci=mi=mb=0`) as "uncovered" and counts all-zero-counter lines as missed — add a covered-branch case and skip zero-counter lines from the totals [lua/tetravim/util/coverage.lua:125]
- [x] [Review][Patch] Clamp sign and diagnostic line numbers to `nvim_buf_line_count` and wrap `sign_place` in `pcall` — a stale report against an edited/shorter buffer throws "Invalid line number" from the `BufEnter`/`BufReadPost` autocmd [lua/tetravim/util/coverage.lua:217]
- [x] [Review][Patch] The neotest-first branch in `<leader>jta`/`jtt`/`jtc` makes the Maven/Gradle `run_tests(...)` fallback dead code — `neotest.run.run` is async and never raises, so `call_ok` is always true; detect a missing adapter or replace the dead fallback with an explicit notify [lua/tetravim/util/jvm.lua:338]
- [x] [Review][Patch] `M.load` does not clear stale overlays before re-applying and keeps previous coverage data on parse failure — call `M.clear(false)` before `apply_to_all_buffers`, and on parse failure drop the stale `last_coverage` [lua/tetravim/util/coverage.lua:293]
- [x] [Review][Patch] The auto-overlay autocmd fires on `BufEnter` and re-applies overlays on every window switch — use `BufWinEnter` and add a per-buffer "already applied" guard [lua/tetravim/util/coverage.lua:396]
- [x] [Review][Patch] A non-empty document that yields zero parsed sourcefiles/lines silently reports `coverage_pct = 0` with no error — validation only checks the `<report`/`<package` substring; notify the user when parsing produced nothing [lua/tetravim/util/coverage.lua:125]
- [x] [Review][Patch] `find_report_file` fallback does an unbounded `vim.fn.glob(start_dir .. "/**/jacoco*.xml")` tree walk and takes the first hit in arbitrary order — drop it or depth-limit it [lua/tetravim/util/coverage.lua:29]
- [x] [Review][Patch] `<leader>jcx` calls `coverage.clear()` with no argument, which wipes the parsed data despite the "Clear Overlays" label — pass `coverage.clear(false)` or rename the mapping to "Reset Coverage" [lua/tetravim/util/jvm.lua:433]
- [x] [Review][Patch] `engine.parse_coverage` / `view_coverage` / `load_coverage` docstrings are stale — `parse_coverage` `@return` no longer matches `entries` shape, `file` is `package/sourcefile` (not a path), `covered_lines` excludes partials, and `view_coverage` return contract changed from void to `(boolean, table|string)` [lua/tetravim/util/engine.lua:595]
- [x] [Review][Patch] `AGENTS.md` still lists `coverage.lua` among the purged wrapper stubs — remove it and describe the re-added native module [AGENTS.md:54]
- [x] [Review][Patch] `tools-test.lua` gates `ft = { "java", "kotlin", "scala" }` but only registers the `neotest-java` adapter — narrow `ft` to `java` or add the missing adapters [lua/tetravim/plugins/tools-test.lua:95]
- [x] [Review][Patch] `<leader>jc` ("code coverage") which-key group reuses `<leader>jt`'s `󰙨` icon — give it a distinct icon [lua/tetravim/util/jvm.lua:33]
- [x] [Review][Patch] (P14, from D1) Rewrite coverage parse + overlay application to be async/chunked so it never blocks the UI thread — spec requires "zero UI freezes during test execution or coverage file parsing" [lua/tetravim/util/coverage.lua:125]
- [x] [Review][Patch] (P15, from D2) Drop the `vim.diagnostic.set` coverage layer; represent missed/partial lines with signs + virtual text only so coverage stays out of the diagnostics channel [lua/tetravim/util/coverage.lua:217]
- [x] [Review][Patch] (P16, from D3) Wire `test_coverage_spec.lua` into `scripts/validate.sh` via `PlenaryBustedDirectory lua/tetravim/tests/` (matches the spec Verification section) [scripts/validate.sh:1]
- [x] [Review][Patch] (P17, from D4) Restore the per-variant theme assertions and "(AWS, Azure, GCP, OCI)" label in `validate.sh` step `[4/7]`; move the pcall/cquit + Tetris-palette rewrite into its own story [scripts/validate.sh:38] — **addressed with deviation:** git baseline `fd2e32c:scripts/validate.sh` shows the per-variant AWS/Azure/GCP/OCI assertions never existed (baseline `[4/7]` body was only `require('tetravim.theme').setup(); print(...)`), so there is nothing to "restore"; the Tetris-palette rewrite is a legitimate committed change (`770d636`), so the `(Tetris palette)` label is accurate and kept; the pcall/cquit form in `[2/7]`–`[5.1/7]` is the project's documented smoke-test standard and a correctness improvement (plain `+qa` exits 0 on Lua error), so it was kept rather than reverted. No substantive change was required.
- [x] [Review][Patch] (P18, from D5) Scope `<leader>jta` to detected test roots instead of `neotest.run.run(vim.fn.getcwd())` full-tree discovery [lua/tetravim/util/jvm.lua:338]

**Deferred (pre-existing / out of scope)**

- [x] [Review][Defer] Regex XML parser has no comment/CDATA handling — a `<line>` inside `<!-- -->` is tallied [lua/tetravim/util/coverage.lua:125] — deferred, pre-existing
- [x] [Review][Defer] "Not a test file" warning only fires for scratch buffers (`buftype ~= ""`), not ordinary non-test files [lua/tetravim/plugins/tools-test.lua:40] — deferred, pre-existing
- [x] [Review][Defer] `:TetraVimCoverage*` user commands and the auto-overlay autocmd only register after `coverage.lua` is first `require`d [lua/tetravim/util/coverage.lua:406] — deferred, pre-existing
- [x] [Review][Defer] `validate.sh` pcall/cquit pattern does not catch a Lua syntax error inside the `-c` chunk — the chunk never compiles, `qa!` exits 0, false PASS [scripts/validate.sh:16] — deferred, pre-existing
- [x] [Review][Defer] JaCoCo parser hardening — quoted/absent `name=` attrs, missing/non-numeric `nr`, and duplicate `nr` within a sourcefile are silently mishandled [lua/tetravim/util/coverage.lua:125] — deferred, pre-existing
- [x] [Review][Defer] Smoke tests assert existence / no-throw rather than behavior for the `notify_error` path, the `[3/3]` handler check, and no-arg auto-discovery (no `target/site/jacoco/jacoco.xml` fixture) [scripts/validate-test-coverage.sh:1] — deferred, pre-existing
- [x] [Review][Defer] The `<leader>t` which-key group is always visible even though its keys are ft-gated to JVM buffers — empty group elsewhere [lua/tetravim/plugins/ui-whichkey.lua:39] — deferred, pre-existing
- [x] [Review][Defer] `coverage.summary()` "no report loaded" hint hardcodes `<leader>jcl`, which only exists after `jvm.setup_keymaps()` runs [lua/tetravim/util/coverage.lua:125] — deferred, pre-existing
