# Specification: SPEC-038 - Test Runner & Sync Orchestration Integration

## Metadata
- **Spec ID**: SPEC-038
- **Title**: Test Runner & JDTLS Sync Orchestration (Lua Orchestration, Rust Backends)
- **Status**: ACTIVE
- **Author**: AI Systems Architect
- **Target Files/Paths**:
  - `crates/cumulus-core/src/test_runner.rs` (extends)
  - `crates/cumulus-core/src/sync_runner.rs` (new)
  - `crates/cumulus-core/src/main.rs` (extends)
  - `lua/cumulus/util/rust.lua` (extends)
  - `lua/cumulus/util/test-runner.lua` (refactor to orchestration only)
  - `lua/cumulus/util/sync-runner.lua` (refactor to orchestration only)

- **Implementation**: Rust backend + Lua orchestration bridge
- **Prerequisite**: SPEC-031 (error standardization), SPEC-005 (JDTLS sync health)

---

## Architecture

**Split test/sync runners into stateless Rust backends (parsing, execution) + Lua orchestration (terminal UI, keymaps). Establish pattern for all async workflows.**

```
Lua Keymap (e.g., <leader>tt)  →  Lua: spawn terminal + run test via Rust backend  →  Real-time test output  →  Lua: parse results + show UI
```

Current Problems:
- `test-runner.lua` (140 LOC) orchestrates terminal spawning + output capture + UI updates.
- `sync-runner.lua` (152 LOC) orchestrates Maven/Gradle dependency resolve + JDTLS restart.
- Both duplicate the same pattern: run command → parse output → update diagnostics/statusline.
- No clear separation: business logic (test detection, sync detection) is Lua; would benefit from Rust speed + testability.

Solution:
- Rust `test-runner` command: execute tests, return structured JSON with pass/fail counts + per-test results.
- Rust `sync-runner` command: run dependency resolution, validate classpath, return sync status + errors.
- Lua keeps UI orchestration, statusline updates, keymaps. No parsing or business logic.

---

## Goal & Intent

Consolidate overlapping async workflow patterns across the codebase:

**Test Runner** (SPEC-007, SPEC-028):
- Currently: Lua spawns terminal, captures output, calls Rust `parse-test-result` to extract counts.
- Opportunity: Move entire test invocation to Rust; Lua only shows terminal UI.
- Benefit: Faster test result extraction, better error handling, easier to add test filtering / retry logic.

**Sync Runner** (SPEC-005 enabler):
- Currently: Lua runs `mvn dependency:resolve` or `./gradlew dependencies`, waits for output.
- Opportunity: Rust command encapsulates dependency sync + classpath validation; returns sync status + error details.
- Benefit: Unifies sync detection (SPEC-005 check-jdtls-sync) with sync execution; single source of truth.

Both use case:
- Spawn long-running subprocess.
- Stream output to terminal (or capture for parsing).
- Parse results into structured data.
- Update UI (statusline, diagnostics, notifications).

This spec formalizes a reusable pattern for async workflows in Cumulus.

---

## Scope Boundaries

**In scope:**
- Extend `test_parser.rs` → new `test_runner.rs`: orchestrate test execution + parsing in Rust.
- Create `sync_runner.rs`: orchestrate Maven/Gradle sync + classpath validation in Rust.
- Refactor `lua/cumulus/util/test-runner.lua` to pure Lua orchestration (terminal UI only).
- Refactor `lua/cumulus/util/sync-runner.lua` to pure Lua orchestration (keymap + notification UI only).
- Add IPC bridges in `lua/cumulus/util/rust.lua` for both commands.
- Document async workflow pattern for future specs (e.g., SPEC-033 PMD/SpotBugs linter runner).

**Out of scope:**
- Modifying frozen DevOps specs.
- Test filtering / retry logic (future enhancement; basic execution only).
- Auto-restart JDTLS (Lua decides whether to restart after sync; Rust only detects sync completion).

---

## Prerequisite Analysis

- `crates/cumulus-core/src/test_parser.rs` (107 LOC) already extracts JUnit counts from output. `test_runner.rs` reuses this.
- `crates/cumulus-core/src/test_context.rs` (88 LOC) detects test environment. Already in use.
- `crates/cumulus-core/src/dep_resolver.rs` (275 LOC) resolves dependencies. `sync_runner.rs` leverages this.
- `crates/cumulus-core/src/gradle.rs` / `maven.rs` detect build tools. Reused here.
- SPEC-005 (JDTLS sync health) will depend on sync detection; this spec provides the sync execution backend.
- SPEC-031 (error standardization) ensures consistent JSON response envelopes.

---

## Constraints & Guardrails

1. **Rust-First Directive**: All business logic (test execution, dependency sync, classpath validation) MUST be in Rust. Lua is pure orchestration.
2. **DevOps Guardrail**: Never touch frozen specs.
3. **Async Pattern**: Both `run-tests` and `run-sync` must execute asynchronously (non-blocking Neovim via vim.system). No blocking waits.
4. **Terminal UI**: Lua handles terminal buffers, statusline updates, keymap dispatch. Rust is headless.
5. **Performance Budget**: Test result extraction must complete in <500ms for standard projects. Sync detection <1s.

---

## Execution Checklist

- [ ] **Task 1: Extend Rust Test Backend (`crates/cumulus-core/src/test_runner.rs`)**
  - Create new module: `pub mod test_runner`.
  - Implement `run_tests(tool: &str, dir: &Path, test_filter: Option<&str>) -> Result<TestRunResult, CumulusError>`:
    - Detects Maven or Gradle (reuse `maven.rs` / `gradle.rs`).
    - Constructs test command (`mvn test`, `mvn test -Dtest=ClassName`, `./gradlew test`, etc.).
    - Executes via `std::process::Command::new()` (captures stdout/stderr).
    - Parses output using `test_parser.rs` logic.
    - Returns structured result:
      ```rust
      pub struct TestRunResult {
        pub tool: String,
        pub tests_run: usize,
        pub tests_passed: usize,
        pub tests_failed: usize,
        pub tests_skipped: usize,
        pub duration_ms: u64,
        pub failures: Vec<TestFailure>,  // Line number + error message
      }
      ```
  - Add unit tests for Maven + Gradle test parsing.

- [ ] **Task 2: Create Rust Sync Backend (`crates/cumulus-core/src/sync_runner.rs`)**
  - Create new module: `pub mod sync_runner`.
  - Implement `run_sync(tool: &str, dir: &Path) -> Result<SyncRunResult, CumulusError>`:
    - Detects Maven or Gradle.
    - Constructs dependency sync command (`mvn dependency:resolve`, `./gradlew dependencies`).
    - Executes and captures output.
    - Validates classpath by checking for `.m2/repository` or `~/.gradle/caches` (basic heuristic).
    - Returns:
      ```rust
      pub struct SyncRunResult {
        pub tool: String,
        pub success: bool,
        pub duration_ms: u64,
        pub dependency_count: Option<usize>,
        pub errors: Vec<String>,
      }
      ```
  - Reuse `dep_resolver.rs` for dependency count validation if available.

- [ ] **Task 3: Add Subcommands to `main.rs`**
  - Route `run-tests --tool maven|gradle --dir <path> [--filter PATTERN]` → `test_runner::run_tests()`.
  - Route `run-sync --tool maven|gradle --dir <path>` → `sync_runner::run_sync()`.
  - Both return JSON-wrapped results via `CumulusResponse<T>`.

- [ ] **Task 4: Add Lua Bridge (`lua/cumulus/util/rust.lua`)**
  - Add `M.run_tests(tool, dir, filter)` calling `run-tests` subcommand.
  - Add `M.run_sync(tool, dir)` calling `run-sync` subcommand.

- [ ] **Task 5: Refactor `lua/cumulus/util/test-runner.lua`**
  - Remove all Rust output parsing logic (it's now in Rust).
  - Keep terminal UI spawning:
    ```lua
    function M.run_tests(opts)
      opts = opts or {}
      local tool = opts.tool or "maven"
      local filter = opts.filter or nil

      -- Spawn terminal buffer for live output
      local term_cmd = M.build_test_command(tool, filter)
      vim.cmd("new | terminal " .. term_cmd)

      -- Call Rust async; when done, parse results
      local rust = require("cumulus.util.rust")
      vim.schedule(function()
        local result = rust.run_tests(tool, vim.fn.getcwd(), filter)
        if result then
          M.show_test_results(result)  -- Lua UI update
        end
      end)
    end
    ```
  - Reduce ~140 lines to ~60 (orchestration + UI only).

- [ ] **Task 6: Refactor `lua/cumulus/util/sync-runner.lua`**
  - Remove dependency resolution + validation logic (now in Rust).
  - Keep Lua dispatch:
    ```lua
    function M.run_sync()
      local rust = require("cumulus.util.rust")
      local tool = M.detect_tool()
      vim.notify("Syncing dependencies...", vim.log.levels.INFO)

      vim.schedule(function()
        local result = rust.run_sync(tool, vim.fn.getcwd())
        if result and result.success then
          vim.notify("Dependencies synced", vim.log.levels.INFO)
          M.restart_jdtls()  -- Lua decides JDTLS restart
        else
          vim.notify("Sync failed: " .. (result.errors[1] or "unknown"), vim.log.levels.ERROR)
        end
      end)
    end
    ```
  - Reduce ~152 lines to ~50.

- [ ] **Task 7: Add Rust Unit Tests**
  - Test `run_tests()` with mock Maven/Gradle projects (or in CI with real projects).
  - Test `run_sync()` with local .m2 / .gradle cache checks.
  - Verify JSON response structure.

- [ ] **Task 8: Integration Test**
  - Lua: Call `rust.run_tests("maven", cwd)` in headless Neovim; verify result contains test counts.
  - Verify terminal output (optional; may skip in CI).

---

## Verification Commands & Acceptance Criteria

```bash
cargo test --manifest-path crates/cumulus-core/Cargo.toml --lib test_runner sync_runner
cargo build --manifest-path crates/cumulus-core/Cargo.toml --release
luac -p lua/cumulus/util/test-runner.lua lua/cumulus/util/sync-runner.lua
nvim --headless "+checkhealth cumulus" +qa
bash scripts/validate.sh
```

### Acceptance Criteria
- [ ] `run-tests --tool maven --dir /path/to/project` returns JSON with test counts + failures.
- [ ] `run-sync --tool maven --dir /path/to/project` returns success status + dependency count.
- [ ] Test terminal shows live output (via vim.system streaming).
- [ ] Rust test parsing correctly extracts pass/fail/skip counts from real test output.
- [ ] Lua `M.run_tests()` and `M.run_sync()` work end-to-end (terminal shows output, results parsed).
- [ ] ~90 lines of Lua business logic removed from test-runner.lua + sync-runner.lua.
- [ ] Error handling graceful: if Rust binary missing, Lua falls back to system execution.
- [ ] JDTLS restart only triggered if sync succeeds (Lua logic preserved).
- [ ] No regression in existing test execution or dependency sync behavior.
- [ ] Pattern documented so future specs (e.g., linter runners) reuse it.
