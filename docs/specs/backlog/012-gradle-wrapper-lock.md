# Specification: SPEC-012 - Gradle Wrapper Version Lock & SHA-256 Verification

## Metadata
- **Spec ID**: SPEC-012
- **Title**: Gradle Wrapper Version Lock & SHA-256 Verification
- **Status**: BACKLOG
- **Author**: AI Systems Architect
- **Target Files/Paths**:
  - `crates/cumulus-core/src/gradle_wrapper.rs` (new)
  - `crates/cumulus-core/src/main.rs` (extends)
  - `lua/cumulus/util/rust.lua` (extends)
  - `lua/cumulus/health.lua` (extends)

- **Implementation**: Rust (Lua bridge only — zero pure-Lua properties/YAML regex parsing)

---

## Architecture

**Lua is a bridge to the Rust backend. All wrapper property parsing, SHA-256 checksum verification, and CI workflow analysis live in Rust.**

```
:checkhealth cumulus  →  Lua (rust.verify_gradle_wrapper)  →  cumulus-core verify-gradle-wrapper  →  JSON Response  →  Health Report
```

- **Rust Engine (`crates/cumulus-core`)**: `verify-gradle-wrapper --dir <path>` parses `gradle/wrapper/gradle-wrapper.properties`, extracts `distributionUrl` and `distributionSha256Sum`, compares local Gradle version against CI configuration files (`.github/workflows/*.yml`, `.gitlab-ci.yml`, `Jenkinsfile`), verifies wrapper binary SHA-256 integrity if present, and outputs JSON payload:
  ```json
  {
    "local_version": "7.6.1",
    "ci_version": "8.5",
    "sha256_configured": true,
    "sha256_valid": true,
    "issues": [
      "Gradle version mismatch: local=7.6.1, CI (.github/workflows/build.yml)=8.5"
    ]
  }
  ```
- **Lua Bridge (`lua/cumulus/util/rust.lua`)**: `rust.verify_gradle_wrapper(dir_path)` invokes `cumulus-core` and returns the decoded verification result.
- **UI Integration**: Hooks into `:checkhealth cumulus` in `lua/cumulus/health.lua` under a dedicated "Gradle Wrapper & Build Lock" section.

---

## Goal & Intent
Eliminate "works on my machine but fails in CI" build discrepancies by detecting mismatches between local Gradle wrapper settings and CI/CD workflow configurations, while verifying wrapper binary SHA-256 checksums to guard against compromised binaries.

---

## Scope Boundaries

**In scope:**
- High-speed Rust parser (`verify-gradle-wrapper`) for `gradle-wrapper.properties` and CI YAML/Groovy workflows.
- SHA-256 checksum validation.
- IPC binding in `lua/cumulus/util/rust.lua`.
- Integration into `lua/cumulus/health.lua`.

**Out of scope:**
- Automatically modifying CI workflows or wrapper property files.
- Modifying frozen DevOps specs.

---

## Prerequisite Analysis

- `lua/cumulus/health.lua` provides Neovim standard healthcheck reports.
- `crates/cumulus-core/src/gradle.rs` already handles Gradle task parsing.

---

## Constraints & Guardrails

1. **Rust-First Directive**: All parsing of `.properties`, `.yml`, and `Jenkinsfile` MUST be in Rust (`crates/cumulus-core/src/gradle_wrapper.rs`). No Lua regex parsing in `health.lua`.
2. **DevOps Guardrail**: Never touch frozen DevOps specs.
3. **Zero Free Files**: All bridge logic resides in `lua/cumulus/util/rust.lua`.
4. **Performance Budget**: Verification completes under 3ms.

---

## Execution Checklist

- [ ] **Task 1: Rust Engine Implementation (`crates/cumulus-core`)**
  - Create `crates/cumulus-core/src/gradle_wrapper.rs`.
  - Add `VerifyGradleWrapper { dir: PathBuf }` subcommand to `main.rs`.
  - Implement `gradle-wrapper.properties` parser.
  - Implement CI configuration scanner (`.github/workflows/*.yml`, `.gitlab-ci.yml`, `Jenkinsfile`).
  - Implement SHA-256 checksum validator.
  - Add Rust unit tests in `gradle_wrapper.rs`.

- [ ] **Task 2: Lua Bridge Binding (`lua/cumulus/util/rust.lua`)**
  - Add `M.verify_gradle_wrapper(dir_path)` function calling `cumulus-core verify-gradle-wrapper`.

- [ ] **Task 3: Healthcheck Wiring**
  - Extend `lua/cumulus/health.lua` to call `rust.verify_gradle_wrapper()` and render warnings/OK status items.

---

## Verification Commands & Acceptance Criteria

```bash
cargo test --manifest-path crates/cumulus-core/Cargo.toml
bash scripts/validate.sh
luac -p lua/cumulus/util/rust.lua lua/cumulus/health.lua
nvim --headless "+checkhealth cumulus" +qa
```

### Acceptance Criteria
- [ ] `cargo test` passes with new `gradle_wrapper` unit tests.
- [ ] `verify-gradle-wrapper` subcommand identifies version mismatches between local wrapper and CI workflows.
- [ ] `:checkhealth cumulus` displays Gradle wrapper status and SHA-256 verification results.
- [ ] Zero pure-Lua properties or YAML regex parsing.
