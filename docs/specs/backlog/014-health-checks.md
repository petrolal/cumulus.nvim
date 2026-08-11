# Specification: SPEC-014 - Standardized Setup & Health Check Automation

## Metadata
- **Spec ID**: SPEC-014
- **Title**: Standardized Setup & Health Check Automation (IntelliJ Ultimate Enterprise Parity)
- **Status**: BACKLOG
- **Author**: AI Systems Architect
- **Target Files/Paths**:
  - `crates/cumulus-core/src/system_health.rs` (new)
  - `crates/cumulus-core/src/main.rs` (extends)
  - `lua/cumulus/util/rust.lua` (extends)
  - `lua/cumulus/health.lua` (extends)
  - `lua/cumulus/core/init.lua` (extends)

- **Implementation**: Rust (Lua bridge only — zero pure-Lua shell execution loops)

---

## Architecture

**Lua is a bridge to the Rust backend. All binary discovery, environment variable validation, and Mason package directory verification live in Rust.**

```
:checkhealth cumulus  →  Lua (rust.check_system_health)  →  cumulus-core check-system-health  →  JSON Response  →  Formatted Report
```

- **Enterprise Parity Target**: Match IntelliJ IDEA Ultimate's Environment & SDK Health Inspection for production enterprise SDKs (JDK 17+, Maven, Gradle, Rust, Node, Python, Mason binaries).
- **Rust Engine (`crates/cumulus-core`)**: `check-system-health` inspects system PATH binaries (`rg`, `fd`, `git`, `cargo`, `npm`, `node`, `python3`, `java`, `mvn`, `gradle`), verifies `JAVA_HOME` environment directory validity, parses Java JDK version (`>= 17`), verifies Mason tool installation directories (`~/.local/share/nvim/mason/bin/`), and returns JSON payload:
  ```json
  {
    "overall_ok": true,
    "checks": [
      {
        "category": "JVM Environment",
        "name": "Java JDK",
        "status": "OK",
        "message": "JDK 21 installed at /usr/lib/jvm/java-21-openjdk",
        "fix_suggestion": null
      }
    ]
  }
  ```
- **Lua Bridge (`lua/cumulus/util/rust.lua`)**: `rust.check_system_health()` invokes `cumulus-core` and returns the decoded health table.
- **UI Integration**: `lua/cumulus/health.lua` iterates over Rust output to populate Neovim `:checkhealth` sections. On `VimEnter`, a lightweight notification summarizes any system health warnings.

---

## Goal & Intent
Automate platform verification and developer environment diagnostics, ensuring missing binaries, invalid `JAVA_HOME` paths, or uninstalled Mason tools are caught immediately upon editor startup in enterprise development environments.

---

## Scope Boundaries

**In scope:**
- High-speed Rust platform health checker (`check-system-health`).
- Environment variable and binary version parser in Rust.
- IPC binding in `lua/cumulus/util/rust.lua`.
- Integration into `lua/cumulus/health.lua` and `lua/cumulus/core/init.lua`.

**Out of scope:**
- Auto-installing OS package manager dependencies.
- Modifying frozen DevOps specs.

---

## Prerequisite Analysis

- `lua/cumulus/health.lua` currently contains hardcoded Lua loops calling `vim.fn.executable()`. `SPEC-014` replaces these loops with a single call to `rust.check_system_health()`.

---

## Constraints & Guardrails

1. **Rust-First Directive**: All binary version checking, PATH scanning, and env var validation MUST reside in Rust (`crates/cumulus-core/src/system_health.rs`).
2. **DevOps Guardrail**: Never touch frozen DevOps specs.
3. **Zero Free Files**: All bridge logic resides in `lua/cumulus/util/rust.lua`.
4. **Performance Budget**: Health check completes under 4ms.

---

## Execution Checklist

- [ ] **Task 1: Rust Engine Implementation (`crates/cumulus-core`)**
  - Create `crates/cumulus-core/src/system_health.rs`.
  - Add `CheckSystemHealth` subcommand to `main.rs`.
  - Implement PATH executable scanner, JDK version parser, `JAVA_HOME` validator, and Mason directory inspector.
  - Add Rust unit tests in `system_health.rs`.

- [ ] **Task 2: Lua Bridge Binding (`lua/cumulus/util/rust.lua`)**
  - Add `M.check_system_health()` function calling `cumulus-core check-system-health`.

- [ ] **Task 3: Healthcheck & Startup Wiring**
  - Refactor `lua/cumulus/health.lua` to render results from `rust.check_system_health()`.
  - Add lightweight startup health summary in `lua/cumulus/core/init.lua`.

---

## Verification Commands & Acceptance Criteria

```bash
cargo test --manifest-path crates/cumulus-core/Cargo.toml
bash scripts/validate.sh
luac -p lua/cumulus/util/rust.lua lua/cumulus/health.lua lua/cumulus/core/init.lua
nvim --headless "+checkhealth cumulus" +qa
```

### Acceptance Criteria
- [ ] `cargo test` passes with new `system_health` unit tests.
- [ ] `check-system-health` subcommand returns JSON array of system checks.
- [ ] `:checkhealth cumulus` displays comprehensive report driven by Rust output.
- [ ] Zero pure-Lua binary checking loops in `health.lua`.
