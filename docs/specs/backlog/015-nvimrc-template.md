# Specification: SPEC-015 - Project `.nvimrc` Template & Local Overrides

## Metadata
- **Spec ID**: SPEC-015
- **Title**: Project `.nvimrc` Template & Local Overrides (IntelliJ Ultimate Enterprise Parity)
- **Status**: BACKLOG
- **Author**: AI Systems Architect
- **Target Files/Paths**:
  - `crates/cumulus-core/src/nvimrc_validator.rs` (new)
  - `crates/cumulus-core/src/main.rs` (extends)
  - `lua/cumulus/util/rust.lua` (extends)
  - `.nvimrc.lua.example` (new)
  - `lua/cumulus/core/options.lua` (extends)

- **Implementation**: Rust (Lua bridge only — zero pure-Lua file validation logic)

---

## Architecture

**Lua is a bridge to the Rust backend. All `.nvimrc` schema validation, option deprecation checking, and template verification live in Rust.**

```
Neovim Startup  →  exrc (.nvimrc.lua)  →  Lua (rust.validate_nvimrc)  →  cumulus-core validate-nvimrc  →  JSON Response
```

- **Enterprise Parity Target**: Replicate IntelliJ IDEA Ultimate's `.idea/` project settings sharing (profiles, build parameters, workspace overrides) for enterprise software teams via Git-managed `.nvimrc.lua`.
- **Rust Engine (`crates/cumulus-core`)**: `validate-nvimrc --file <path>` parses `.nvimrc.lua`, checks structure against `.nvimrc.lua.example` schema definitions (Maven active profiles, Gradle default tasks, JVM debug ports, formatter overrides), verifies key types, and outputs JSON payload:
  ```json
  {
    "valid": true,
    "warnings": [
      "Deprecated key 'maven_opts' found on line 12; use 'jvm_args' instead"
    ]
  }
  ```
- **Lua Bridge (`lua/cumulus/util/rust.lua`)**: `rust.validate_nvimrc(file_path)` invokes `cumulus-core` and returns the decoded validation result.
- **UI Integration**: Displays a warning notification on project load if `.nvimrc.lua` contains invalid or deprecated keys.

---

## Goal & Intent
Enable secure, standardized per-project configuration via `.nvimrc.lua` checked into enterprise project repositories, ensuring project-specific Maven profiles, Gradle tasks, and JVM settings are validated by Rust upon opening the project.

---

## Scope Boundaries

**In scope:**
- High-speed Rust schema validator (`validate-nvimrc`) for `.nvimrc.lua`.
- Creating `.nvimrc.lua.example` template at repository root.
- Enabling `vim.opt.exrc = true` in `lua/cumulus/core/options.lua`.
- IPC binding in `lua/cumulus/util/rust.lua`.

**Out of scope:**
- Implementing un-sandboxed code execution in `.nvimrc.lua` (rely on Neovim's native `exrc` security sandbox).
- Modifying frozen DevOps specs.

---

## Prerequisite Analysis

- Neovim native `exrc` option supports auto-loading `.nvimrc.lua` from working directory when enabled.

---

## Constraints & Guardrails

1. **Rust-First Directive**: All parsing and schema validation of `.nvimrc.lua` MUST be in Rust (`crates/cumulus-core/src/nvimrc_validator.rs`).
2. **DevOps Guardrail**: Never touch frozen DevOps specs.
3. **Zero Free Files**: All bridge logic resides in `lua/cumulus/util/rust.lua`.
4. **Performance Budget**: Validation completes under 2ms.

---

## Execution Checklist

- [ ] **Task 1: Rust Engine Implementation (`crates/cumulus-core`)**
  - Create `crates/cumulus-core/src/nvimrc_validator.rs`.
  - Add `ValidateNvimrc { file: PathBuf }` subcommand to `main.rs`.
  - Implement AST/schema parser for `.nvimrc.lua` options.
  - Add Rust unit tests in `nvimrc_validator.rs`.

- [ ] **Task 2: Template Creation & Option Wiring**
  - Create `.nvimrc.lua.example` template in repository root.
  - Set `vim.opt.exrc = true` in `lua/cumulus/core/options.lua`.

- [ ] **Task 3: Lua Bridge Binding (`lua/cumulus/util/rust.lua`)**
  - Add `M.validate_nvimrc(file_path)` function calling `cumulus-core validate-nvimrc`.

---

## Verification Commands & Acceptance Criteria

```bash
cargo test --manifest-path crates/cumulus-core/Cargo.toml
bash scripts/validate.sh
luac -p lua/cumulus/util/rust.lua lua/cumulus/core/options.lua
nvim --headless "+checkhealth cumulus" +qa
```

### Acceptance Criteria
- [ ] `cargo test` passes with new `nvimrc_validator` unit tests.
- [ ] `.nvimrc.lua.example` exists at repository root.
- [ ] `validate-nvimrc` subcommand detects invalid keys in `.nvimrc.lua`.
- [ ] `vim.opt.exrc` is enabled in `options.lua`.
- [ ] Zero pure-Lua file parsing code.
