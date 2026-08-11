# Specification: SPEC-036 - Maven & Gradle Build Command Consolidation

## Metadata
- **Spec ID**: SPEC-036
- **Title**: Maven & Gradle Build Command Consolidation (Business Logic → Rust)
- **Status**: ACTIVE
- **Author**: AI Systems Architect
- **Target Files/Paths**:
  - `crates/cumulus-core/src/build_cmd.rs` (new)
  - `crates/cumulus-core/src/main.rs` (extends)
  - `lua/cumulus/util/rust.lua` (extends)
  - `lua/cumulus/util/gradle.lua` (simplify to bridge only)
  - `lua/cumulus/util/maven.lua` (simplify to bridge only)

- **Implementation**: Rust (Lua bridge → UI only)
- **Prerequisite**: SPEC-031 (error standardization)

---

## Architecture

**Migrate Maven/Gradle wrapper discovery, offline mode detection, and network checks to Rust. Lua becomes pure UI bridge (toggle notification + command dispatch).**

```
Lua: M.toggle_offline_mode()  →  Rust: get-build-command  →  JSON { cmd, offline }  →  Lua: vim.notify() + execute
```

- **Rust Engine (`crates/cumulus-core`)**: `get-build-command --tool maven|gradle --dir <path> [--offline true|false]` performs:
  1. Find `mvnw` or `gradlew` wrapper in project root; fall back to system `mvn`/`gradle`.
  2. Check network connectivity (reuse `network.rs`; return early if offline).
  3. Return JSON:
     ```json
     {
       "success": true,
       "data": {
         "command": "./mvnw",
         "args": ["-o"],
         "offline": true,
         "reason": "Network unavailable"
       }
     }
     ```
  4. Also resolve wrapper executable permissions automatically (chmod +x).
- **Lua Bridge (`lua/cumulus/util/maven.lua` / `gradle.lua`)**: Calls `rust.get_build_command(tool, dir, offline)`. Returns command string. Replaces all file I/O + executable checks.
- **UI Integration**: `M.toggle_offline_mode()` notifies user and caches preference in Lua state (no persistence).

---

## Goal & Intent

Consolidate 299 lines of Lua file I/O, executable detection, and chmod logic into a single Rust command. Currently:
- `maven.lua:18–47` duplicates `findfile()` for `mvnw` + `pom.xml` location logic.
- `gradle.lua:18–47` identical but for `gradlew` + `build.gradle`.
- Both call `vim.fn.filereadable()`, `vim.fn.executable()`, `vim.fn.system("chmod")` synchronously on every build invocation.

Moving to Rust:
- Single source of truth for wrapper discovery + network checks.
- Async execution (non-blocking Neovim thread).
- Opportunity to add SHA-256 wrapper verification (future: SPEC-012 reuses this command).

---

## Scope Boundaries

**In scope:**
- Rust command `get-build-command` with Maven + Gradle tool detection.
- Wrapper discovery (`mvnw`/`gradlew` vs system `mvn`/`gradle`).
- Wrapper executable permission fixup (automatic chmod).
- Network connectivity check (reuse existing `network.rs` logic).
- Offline mode toggle + preference caching.
- JSON response envelope (tool, command, args, offline_reason).

**Out of scope:**
- Modifying frozen DevOps specs.
- Wrapper signature verification (SPEC-012 scope).
- Automatic dependency resolution (that belongs in SPEC-005, SPEC-009).
- Build execution itself (Lua orchestrates `vim.system("mvn ...")` as before).

---

## Prerequisite Analysis

- `lua/cumulus/util/gradle.lua` (147 LOC): Lines 18–47 = wrapper discovery + offline detection. Rest is Lua state management.
- `lua/cumulus/util/maven.lua` (152 LOC): Lines 18–47 = wrapper discovery + offline detection. Rest is Lua state management.
- `crates/cumulus-core/src/network.rs` (21 LOC): Already has non-blocking TCP check; reuse here.
- SPEC-031 (error standardization) must land first so error handling is uniform.

---

## Constraints & Guardrails

1. **Rust-First Directive**: All file system scanning and network checks MUST live in Rust. Zero Lua `findfile()` or `filereadable()` calls in Lua after migration.
2. **DevOps Guardrail**: Never touch frozen specs.
3. **Zero Free Files**: `build_cmd.rs` lives only in `crates/cumulus-core/src/`.
4. **Lua Bridge Pattern**: Lua only dispatches UI notifications (`toggle_offline_mode()`) and calls Rust to get the command. No business logic remains.
5. **Performance**: Wrapper discovery must complete in <50ms (Rust is fast here; I/O bound mostly).

---

## Execution Checklist

- [ ] **Task 1: Rust Implementation (`crates/cumulus-core/src/build_cmd.rs`)**
  - Create new module `pub mod build_cmd`.
  - Implement:
    ```rust
    pub struct BuildCommand {
      pub tool: String,        // "maven" or "gradle"
      pub command: String,     // "/path/to/mvnw" or "mvn"
      pub args: Vec<String>,   // ["-o"] if offline
      pub offline: bool,
      pub reason: Option<String>,
    }

    pub fn get_build_command(tool: &str, dir: &Path, offline_override: Option<bool>) -> Result<BuildCommand, CumulusError> {
      // 1. Detect wrapper
      // 2. Chmod if needed
      // 3. Check network (unless offline_override == true)
      // 4. Return BuildCommand
    }
    ```
  - Reuse `network::check_network()` for connectivity.
  - For Maven: look for `mvnw`, `pom.xml` in `dir`; add `-o` flag if offline.
  - For Gradle: look for `gradlew`, `build.gradle` or `build.gradle.kts` in `dir`; add `--offline` flag if offline.

- [ ] **Task 2: Add Subcommand to `main.rs`**
  - Route `get-build-command --tool maven|gradle --dir <path> [--offline true|false]` to `build_cmd::get_build_command()`.
  - Wrap response in `CumulusResponse<BuildCommand>`.

- [ ] **Task 3: Simplify Lua Bridge (`lua/cumulus/util/maven.lua`)**
  - Remove lines 18–47 (wrapper discovery + filereadable checks).
  - Keep `M.offline_mode` state variable.
  - Simplify `M.get_mvn_cmd()`:
    ```lua
    function M.get_mvn_cmd()
      local rust = require("cumulus.util.rust")
      local result = rust.get_build_command("maven", vim.fn.getcwd(), M.offline_mode)
      if not result then
        return "mvn"  -- fallback if Rust fails
      end
      return result.command .. " " .. table.concat(result.args, " ")
    end
    ```
  - Keep `M.run_maven_cmd()` logic unchanged (it's UI dispatch).

- [ ] **Task 4: Simplify Lua Bridge (`lua/cumulus/util/gradle.lua`)**
  - Parallel to maven.lua refactoring.
  - Remove wrapper discovery lines 18–47.
  - Simplify `M.get_gradle_cmd()` to call Rust `get_build_command()`.

- [ ] **Task 5: Add Rust Unit Tests**
  - Test `get_build_command()` with mock project directories.
  - Test wrapper detection (mvnw exists, pom.xml exists).
  - Test fallback to system commands.
  - Test offline flag propagation.
  - Test chmod edge case (wrapper not executable).

- [ ] **Task 6: Lua Integration Tests**
  - Call `rust.get_build_command("maven", cwd, false)` in headless Neovim.
  - Verify result contains valid command and args.
  - Verify offline flag respected.

---

## Verification Commands & Acceptance Criteria

```bash
cargo test --manifest-path crates/cumulus-core/Cargo.toml --lib build_cmd
cargo build --manifest-path crates/cumulus-core/Cargo.toml --release
luac -p lua/cumulus/util/maven.lua lua/cumulus/util/gradle.lua
nvim --headless "+checkhealth cumulus" +qa
bash scripts/validate.sh
```

### Acceptance Criteria
- [ ] `get-build-command --tool maven --dir $(pwd)` returns valid JSON with `command` and `args` fields.
- [ ] Offline flag is respected; `-o` (Maven) / `--offline` (Gradle) appended to args.
- [ ] `mvnw` wrapper found and made executable if present.
- [ ] `gradlew` wrapper found and made executable if present.
- [ ] Fallback to system `mvn`/`gradle` if no wrapper present.
- [ ] `M.toggle_offline_mode()` in `maven.lua` / `gradle.lua` works as before.
- [ ] `M.get_mvn_cmd()` / `M.get_gradle_cmd()` return complete command string (no file I/O).
- [ ] Lua lines 18–47 removed from both files (~60 lines deleted; boilerplate reduced).
- [ ] Network check non-blocking (async via vim.system).
- [ ] All existing build invocations (`:Maven install`, `:Gradle build`, etc.) still work.
