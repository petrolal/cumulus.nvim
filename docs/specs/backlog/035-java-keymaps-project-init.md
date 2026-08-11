# Specification: SPEC-035 - Java & JVM Build Keymaps Workspace Initialization & Instant Availability

## Metadata
- **Spec ID**: SPEC-035
- **Title**: Java & JVM Build Keymaps Workspace Initialization & Instant Availability (IntelliJ Ultimate Enterprise Parity)
- **Status**: BACKLOG
- **Author**: AI Systems Architect
- **Target Files/Paths**:
  - `crates/cumulus-core/src/project_detect.rs` (new)
  - `crates/cumulus-core/src/main.rs` (extends)
  - `lua/cumulus/util/rust.lua` (extends)
  - `lua/cumulus/core/lang-keymaps.lua` (extends)
  - `lua/cumulus/core/keymaps.lua` (extends)
  - `lua/cumulus/core/autocmds.lua` (extends)

- **Implementation**: Rust (Lua bridge only — zero pure-Lua file finding loops)

---

## Architecture

**Lua is a bridge to the Rust backend. All project type detection, build tool root discovery, and workspace scope resolution live in Rust.**

```
VimEnter / BufEnter  →  Lua (rust.detect_project_type)  →  cumulus-core detect-project-type  →  JSON Payload  →  Instant Keymap Binding
```

- **Enterprise Parity Target**: Replicate IntelliJ IDEA Ultimate's instant action availability — when a developer opens a Maven or Gradle enterprise project, JVM build, test, and refactoring keymaps (`<leader>cj*`) are immediately active across all Java, Kotlin, Groovy, XML, and build buffers without requiring manual navigation to `pom.xml` or `build.gradle` first.
- **Rust Engine (`crates/cumulus-core`)**: `detect-project-type --dir <path>` scans the workspace root and parent directories for Maven (`pom.xml`), Gradle (`build.gradle`, `build.gradle.kts`, `settings.gradle`), or multi-module configurations. Returns JSON payload:
  ```json
  {
    "is_jvm_project": true,
    "build_tool": "maven",
    "root_dir": "/home/user/project",
    "has_pom": true,
    "has_gradle": false
  }
  ```
- **Lua Bridge (`lua/cumulus/util/rust.lua`)**: `rust.detect_project_type(dir_path)` invokes `cumulus-core` and returns decoded project metadata.
- **Keymap & Autocmd Integration**: Updates `lua/cumulus/core/lang-keymaps.lua` to attach `<leader>cj*` keymaps immediately upon opening any JVM or build file in a detected Maven/Gradle workspace, eliminating `ready_gate` blocking or `findfile` buffer scope locks.

---

## Goal & Intent
Fix the Java build keymap bug where `<leader>cj` keymaps only appear when opening `pom.xml` or `build.gradle` buffers. Ensure JVM build, test, and sync keymaps are immediately bound across all Java/Kotlin/Groovy/XML buffers upon opening a Maven or Gradle project for the first time.

---

## Scope Boundaries

**In scope:**
- High-speed Rust project detector (`detect-project-type`) in `crates/cumulus-core/src/project_detect.rs`.
- IPC binding in `lua/cumulus/util/rust.lua`.
- Refactoring `lang_keymaps.lua` and `keymaps.lua` to bind `<leader>cj*` instantly on project load.
- Workspace-wide keymap activation for Java, Kotlin, Groovy, XML, and build buffers.

**Out of scope:**
- Modifying frozen DevOps specs (`cloud-*.lua`, `lsp-devops.lua`, `tools-dap-devops.lua`).

---

## Prerequisite Analysis

- `lua/cumulus/core/lang-keymaps.lua` currently uses `vim.fn.findfile("pom.xml", ...)` in a Lua condition function, which fails when opening Java files from deeply nested subdirectories or before opening build buffers.
- `ready_gate = true` in `keymaps.lua` blocks keymap registration until asynchronous sync completes.

---

## Constraints & Guardrails

1. **Rust-First Directive**: All project root and build tool detection MUST be in Rust (`crates/cumulus-core/src/project_detect.rs`). No Lua `findfile` loops.
2. **DevOps Guardrail**: Never touch frozen DevOps specs.
3. **Zero Free Files**: All bridge logic resides in `lua/cumulus/util/rust.lua`.
4. **Performance Budget**: Project detection completes under 2ms.

---

## Execution Checklist

- [ ] **Task 1: Rust Engine Implementation (`crates/cumulus-core`)**
  - Create `crates/cumulus-core/src/project_detect.rs`.
  - Add `DetectProjectType { dir: PathBuf }` subcommand to `main.rs`.
  - Implement workspace root scanner for `pom.xml`, `build.gradle`, `build.gradle.kts`, `settings.gradle`.
  - Add Rust unit tests in `project_detect.rs`.

- [ ] **Task 2: Lua Bridge Binding (`lua/cumulus/util/rust.lua`)**
  - Add `M.detect_project_type(dir_path)` function calling `cumulus-core detect-project-type`.

- [ ] **Task 3: Keymap & Autocmd Refactoring**
  - Update `lua/cumulus/core/keymaps.lua` to use `rust.detect_project_type()` in the `<leader>cj` stack condition.
  - Refactor `lua/cumulus/core/lang-keymaps.lua` so keymaps register instantly on `VimEnter` / `FileType` for Java/Kotlin/Groovy/XML buffers in JVM projects.

---

## Verification Commands & Acceptance Criteria

```bash
cargo test --manifest-path crates/cumulus-core/Cargo.toml
bash scripts/validate.sh
luac -p lua/cumulus/util/rust.lua lua/cumulus/core/keymaps.lua lua/cumulus/core/lang-keymaps.lua
nvim --headless "+checkhealth cumulus" +qa
```

### Acceptance Criteria
- [ ] `cargo test` passes with new `project_detect` unit tests.
- [ ] `detect-project-type` subcommand identifies Maven/Gradle projects from any subdirectory.
- [ ] Opening any `.java` or `.kt` file in a Maven/Gradle project immediately activates `<leader>cj` keymaps without opening `pom.xml`/`build.gradle`.
- [ ] Zero pure-Lua `findfile` loops in `lang-keymaps.lua`.
