# Specification: SPEC-006 - SpringBoot Debug Configuration & Hotswap Engine

## Metadata
- **Spec ID**: SPEC-006
- **Title**: SpringBoot Debug Configuration & Hotswap Engine (IntelliJ Ultimate Enterprise Parity)
- **Status**: BACKLOG
- **Author**: AI Systems Architect
- **Target Files/Paths**:
  - `crates/cumulus-core/src/springboot_debug.rs` (new)
  - `crates/cumulus-core/src/main.rs` (extends)
  - `lua/cumulus/util/rust.lua` (extends)
  - `lua/cumulus/core/keymaps.lua` (extends)
  - `ftplugin/java.lua` (extends)

- **Implementation**: Rust (Lua bridge only — zero pure-Lua AST or regex scanning)

---

## Architecture

**Lua is a bridge to the Rust backend. All Spring Boot detection, main class extraction, and JVM debug argument generation live in Rust.**

```
Neovim (<leader>ds)  →  Lua (rust.detect_springboot_app)  →  cumulus-core detect-springboot-app  →  JSON Payload  →  DAP Launch
```

- **Enterprise Parity Target**: Replicate IntelliJ IDEA Ultimate's Spring Boot Run/Debug Configuration runner (`Spring Boot` Run Configuration) with automatic JPDA debugging and Spring Loaded / DevTools hot swap for enterprise microservices.
- **Rust Engine (`crates/cumulus-core`)**: `detect-springboot-app --dir <path>` scans Java/Kotlin source trees for `@SpringBootApplication` or `@EnableAutoConfiguration`, extracts package and main class names, detects active build system (Maven vs Gradle), parses active profiles from `application.yml`/`application.properties`, and returns JSON payload:
  ```json
  {
    "main_class": "com.example.demo.DemoApplication",
    "project_name": "demo",
    "build_tool": "maven",
    "jvm_args": "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005",
    "profiles": ["dev"]
  }
  ```
- **Lua Bridge (`lua/cumulus/util/rust.lua`)**: `rust.detect_springboot_app(dir_path)` invokes `cumulus-core` and returns the decoded launch configuration table.
- **UI & DAP Integration**: Injects dynamic launch configuration into `dap.configurations.java` and triggers `<leader>ds` to start application debugging and open DAP UI.

---

## Goal & Intent
Provide one-keypress Spring Boot debugging (`<leader>ds`) matching IntelliJ IDEA Ultimate's Spring Boot run configuration, with automatic JPDA (Java Debug Wire Protocol) socket configuration on port 5005 and hotcode reload enabled.

---

## Scope Boundaries

**In scope:**
- High-speed Rust scanner (`detect-springboot-app`) for Spring Boot entrypoints and configuration files.
- IPC binding in `lua/cumulus/util/rust.lua`.
- Standard DAP Java launch configuration injection in `ftplugin/java.lua`.
- Global `<leader>ds` keymap registration.

**Out of scope:**
- Non-JVM frameworks (Node, Python, Go).
- Modifying frozen DevOps DAP files (`tools-dap-devops.lua`).

---

## Prerequisite Analysis

- `crates/cumulus-core/src/endpoints.rs` already contains regex/AST scanning patterns for Spring annotations; `springboot_debug.rs` reuses these Rust primitives.
- `tools-dap-ui.lua` already registers DAP UI layouts.

---

## Constraints & Guardrails

1. **Rust-First Directive**: All Java/Kotlin source scanning and main class resolution MUST be in Rust (`crates/cumulus-core/src/springboot_debug.rs`). No Lua regex scanning of `.java`/`.kt` files.
2. **DevOps Guardrail**: Never touch `tools-dap-devops.lua` or `cloud-*.lua`.
3. **Zero Free Files**: All Lua functions live in `lua/cumulus/util/rust.lua`.
4. **Performance Budget**: Rust detection completes under 5ms.

---

## Execution Checklist

- [ ] **Task 1: Rust Engine Implementation (`crates/cumulus-core`)**
  - Create `crates/cumulus-core/src/springboot_debug.rs`.
  - Add `DetectSpringbootApp { dir: PathBuf }` subcommand to `main.rs`.
  - Scan `src/main/java` and `src/main/kotlin` for `@SpringBootApplication`.
  - Parse build tool (`pom.xml` vs `build.gradle`) and construct JDWP arguments.
  - Add Rust unit tests in `springboot_debug.rs`.

- [ ] **Task 2: Lua Bridge Binding (`lua/cumulus/util/rust.lua`)**
  - Add `M.detect_springboot_app(dir_path)` function calling `cumulus-core detect-springboot-app`.

- [ ] **Task 3: DAP Integration & Keymap Wiring**
  - Extend `ftplugin/java.lua` to dynamically populate `dap.configurations.java` using `rust.detect_springboot_app()`.
  - Add `<leader>ds` (Debug SpringBoot) keymap in `lua/cumulus/core/keymaps.lua`.

---

## Verification Commands & Acceptance Criteria

```bash
cargo test --manifest-path crates/cumulus-core/Cargo.toml
bash scripts/validate.sh
luac -p lua/cumulus/util/rust.lua lua/cumulus/core/keymaps.lua ftplugin/java.lua
nvim --headless "+checkhealth cumulus" +qa
```

### Acceptance Criteria
- [ ] `cargo test` passes with new `springboot_debug` tests.
- [ ] `detect-springboot-app` subcommand detects `@SpringBootApplication` and outputs valid JSON configuration.
- [ ] `<leader>ds` launches Spring Boot with JDWP debug arguments on port 5005.
- [ ] DAP UI opens automatically on debug session start.
- [ ] Zero pure-Lua regex scanning of source files.
