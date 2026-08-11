# Specification: SPEC-009 - Dependency Lens & Version Checker

## Metadata
- **Spec ID**: SPEC-009
- **Title**: Dependency Lens & Version Checker (IntelliJ Ultimate Enterprise Parity)
- **Status**: BACKLOG
- **Author**: AI Systems Architect
- **Target Files/Paths**:
  - `crates/cumulus-core/src/dep_lens.rs` (new)
  - `crates/cumulus-core/src/main.rs` (extends)
  - `lua/cumulus/util/rust.lua` (extends)
  - `lua/cumulus/core/keymaps.lua` (extends)
  - `lua/cumulus/core/autocmds.lua` (extends)

- **Implementation**: Rust (Lua bridge only — zero pure-Lua dependency parsing or network version checking)

---

## Architecture

**Lua is a bridge to the Rust backend. All dependency manifest parsing, version catalog resolution, and version age classification live in Rust.**

```
Neovim Buffer  →  Lua (rust.check_dep_versions)  →  cumulus-core check-dep-versions  →  JSON Response  →  Virtual Text / CodeLens
```

- **Enterprise Parity Target**: Replicate IntelliJ IDEA Ultimate's Package Search and Dependency Analyzer inline version hints and upgrade quick-fixes for enterprise Maven and Gradle builds.
- **Rust Engine (`crates/cumulus-core`)**: `check-dep-versions --file <path>` parses `pom.xml`, `build.gradle`, `build.gradle.kts`, or `gradle/libs.versions.toml`, queries cached Maven Central / Gradle Plugin Portal metadata (stored in `~/.cache/nvim/dependency-versions.json`), and outputs JSON array:
  ```json
  [
    {
      "group": "org.springframework.boot",
      "artifact": "spring-boot-starter-web",
      "current_version": "3.1.0",
      "latest_version": "3.2.5",
      "line": 42,
      "age_status": "MAJOR_OUTDATED"
    }
  ]
  ```
- **Lua Bridge (`lua/cumulus/util/rust.lua`)**: `rust.check_dep_versions(file_path)` invokes `cumulus-core` and returns the decoded list.
- **UI Integration**: Renders virtual text on dependency lines (`Current: 3.1.0 → Latest: 3.2.5 [MAJOR_OUTDATED]`) and registers code actions to bump version inline.

---

## Goal & Intent
Expose real-time dependency freshness and outdated version warnings inline within `pom.xml` and `build.gradle` files without blocking Neovim's main UI thread, delivering IntelliJ Ultimate's Package Search developer experience.

---

## Scope Boundaries

**In scope:**
- High-speed Rust parser (`check-dep-versions`) for Maven POM XML and Gradle version catalogs.
- Disk-cached version lookups for offline operation.
- IPC binding in `lua/cumulus/util/rust.lua`.
- Virtual text and CodeLens rendering.

**Out of scope:**
- Auto-updating dependencies without user confirmation.
- Modifying frozen DevOps specs.

---

## Prerequisite Analysis

- `crates/cumulus-core/src/dep_resolver.rs` (SPEC-026) already parses direct dependencies from `pom.xml` and `libs.versions.toml`. `dep_lens.rs` extends this parser with line-number mapping and version classification.

---

## Constraints & Guardrails

1. **Rust-First Directive**: All XML/TOML parsing and HTTP version lookup cache management MUST be implemented in Rust (`crates/cumulus-core/src/dep_lens.rs`).
2. **DevOps Guardrail**: Never touch frozen paths (`cloud-*.lua`, `lsp-devops.lua`, `tools-dap-devops.lua`).
3. **Zero Free Files**: All bridge functions live in `lua/cumulus/util/rust.lua`.
4. **Offline Resilience**: Must return cached results when offline.

---

## Execution Checklist

- [ ] **Task 1: Rust Engine Implementation (`crates/cumulus-core`)**
  - Create `crates/cumulus-core/src/dep_lens.rs`.
  - Add `CheckDepVersions { file: PathBuf }` subcommand to `main.rs`.
  - Extend POM/TOML parser to record 1-indexed line numbers for dependency declarations.
  - Add version age classifier (`CURRENT`, `MINOR_OUTDATED`, `MAJOR_OUTDATED`).
  - Add Rust unit tests in `dep_lens.rs`.

- [ ] **Task 2: Lua Bridge Binding (`lua/cumulus/util/rust.lua`)**
  - Add `M.check_dep_versions(file_path)` function calling `cumulus-core check-dep-versions`.

- [ ] **Task 3: Editor UI & Autocmd Wiring**
  - Wire `BufReadPost`/`BufWritePost` autocmd in `lua/cumulus/core/autocmds.lua` for build files to display extmark virtual text.
  - Add `<leader>cdu` (Dependency Update) keymap in `lua/cumulus/core/keymaps.lua`.

---

## Verification Commands & Acceptance Criteria

```bash
cargo test --manifest-path crates/cumulus-core/Cargo.toml
bash scripts/validate.sh
luac -p lua/cumulus/util/rust.lua lua/cumulus/core/keymaps.lua lua/cumulus/core/autocmds.lua
nvim --headless "+checkhealth cumulus" +qa
```

### Acceptance Criteria
- [ ] `cargo test` passes with new `dep_lens` unit tests.
- [ ] `check-dep-versions` returns JSON containing line numbers and version statuses.
- [ ] Dependency virtual text appears in `pom.xml` and `libs.versions.toml` buffers.
- [ ] Works in offline mode using disk-cached metadata.
- [ ] Zero pure-Lua XML/TOML parsing code.
