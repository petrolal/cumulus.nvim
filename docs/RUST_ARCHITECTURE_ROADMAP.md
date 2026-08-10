# Rust Native Architecture & Feature Roadmap — Cumulus Neovim

## 1. Executive Summary & Design Principles

`cumulus.nvim` adopts a **Rust-First Engine + Minimal Lua Bridge** design pattern:
- **Minimal Lua Core (`lua/cumulus/`)**: Serves strictly as minimal editor glue (owning Neovim API events, plugin specifications in `lazy.nvim`, keymap definitions, and UI rendering via `vim.diagnostic` and `vim.ui.select`).
- **High-Performance Rust Native Helper (`crates/cumulus-core`)**: Owns ALL heavy text processing, XML/TOML/YAML AST parsing, multi-module dependency resolution, network socket checks, log regex parsers, test runner output extraction, microservices endpoint extraction, coverage parsing, migration validation, Spring Bean dependency graphing, import optimization, and Git conflict resolution.
- **IPC Mechanism (`lua/cumulus/util/rust.lua`)**: Executes `cumulus-core` asynchronously or synchronously via `vim.system`, exchanging JSON data over `stdout`. Provides 100% automatic fallback to Lua if the Rust binary is uncompiled or missing.

> [!IMPORTANT]
> **RUST-FIRST DIRECTIVE FOR HUMANS AND AI ASSISTANTS**:
> All new features, project analysis, background parsing, file scanning, and backlog specifications MUST be implemented in Rust inside `crates/cumulus-core/` first, exposing a CLI subcommand returning JSON to Lua.

---

## 2. Native Rust Crate (`crates/cumulus-core`) API Matrix

| Subcommand | Description | Rust Module | Primary Consumers |
| :--- | :--- | :--- | :--- |
| `ping` | Status & version readiness check | `main.rs` | `health.lua` |
| `parse-pom` | Extract Maven goals & plugin targets | `maven.rs` | `util/maven.lua` |
| `parse-gradle-tasks` | Extract Gradle tasks from console output | `gradle.rs` | `util/gradle.lua` |
| `parse-build-log` | Convert Maven/Gradle build errors to JSON diagnostics | `log_parser.rs` | `util/build-diagnostics.lua` |
| `parse-modules` | Resolve Maven/Gradle sub-module directory paths | `multimodule.rs` | `util/multimodule.lua` |
| `parse-stacktrace` | Extract clickable `file:line` locations from stack traces | `log_parser.rs` | `util/rust.lua` |
| `generate-java-header` | Compute package statement and Java class header template | `java_gen.rs` | `core/autocmds.lua` |
| `parse-test-output` | Parse JUnit 5 / Surefire / Gradle test results | `test_parser.rs` | `util/test-runner.lua` |
| `check-network` | Non-blocking TCP socket connectivity check to remote repos | `network.rs` | `util/maven.lua`, `util/gradle.lua` |
| `parse-checkstyle` | Parse Checkstyle XML reports into JSON diagnostics | `inspection_parser.rs` | `plugins/tools-linting.lua` |
| `extract-endpoints` | Extract Spring Boot / JAX-RS REST endpoints | `endpoints.rs` | `util/endpoints.lua` |
| `parse-coverage` | Parse JaCoCo coverage XML reports | `coverage.rs` | `util/coverage.lua` |
| `validate-migrations` | Validate Flyway migration versioning & file naming | `migrations.rs` | `util/migrations.lua` |
| `parse-spring-beans` | Extract Spring `@Component`/`@Service`/`@Bean` dependency graphs | `beans.rs` | `util/beans.lua` |
| `index-log` | Index large log files for `ERROR` and `WARN` messages | `log_indexer.rs` | `util/log-indexer.lua` |
| `optimize-imports` | Sort and deduplicate Java/Kotlin import statements | `imports.rs` | `util/import-optimizer.lua` |
| `validate-k8s-manifest` | Validate Kubernetes manifest & Helm values YAML structure | `k8s_validator.rs` | `util/k8s-validator.lua` |
| `parse-git-conflicts` | Parse Git conflict markers (`<<<<<<<`) in buffers/files | `conflicts.rs` | `util/conflicts.lua` |

---

## 3. Backlog Specifications Roadmap (Rust Implementation Strategy)

1. **`SPEC-002` (HTML/XML Markup Expansion)**:
   - *Rust Role*: Implement `cumulus-core parse-html-structure` for high-speed XML/HTML tag matching and tag closing.

2. **`SPEC-005` (JDTLS Classpath Sync Health Check)**:
   - *Rust Role*: Implement `cumulus-core check-classpath-sync` to compare `mtime` of `pom.xml`/`build.gradle` against JDTLS launch time and signal stale classpaths.

3. **`SPEC-006` (SpringBoot Debug Configuration & Hotswap)**:
   - *Rust Role*: Implement `cumulus-core find-spring-main-class` to scan `@SpringBootApplication` annotated classes and construct JPDA debug launch configurations (`port 5005`).

4. **`SPEC-008` (Multi-Module Telescope Navigation)**:
   - *Rust Role*: Connect existing `cumulus-core parse-modules` to Telescope/Snacks pickers in `lua/cumulus/plugins/editor-telescope.lua`.

5. **`SPEC-009` (Dependency Lens & Version Checker)**:
   - *Rust Role*: Implement `cumulus-core check-dependency-versions` to query Maven Central / Gradle Plugin Portal over non-blocking TCP/HTTP sockets and return updated version CodeLens data.

6. **`SPEC-010` (Runtime Exception Stack Trace Drill-Down)**:
   - *Rust Role*: Connect existing `cumulus-core parse-stacktrace` to quickfix window (`vim.fn.setqflist`).

7. **`SPEC-012` (Gradle Wrapper Lock & SHA-256 Checksum Verification)**:
   - *Rust Role*: Implement `cumulus-core verify-gradle-wrapper --properties <path>` using `sha2` crate to compute SHA-256 of `gradle-wrapper.jar` before execution.

8. **`SPEC-014` (Platform & Environment Health Checks)**:
   - *Rust Role*: Implement `cumulus-core run-environment-checks` to verify JDK version, JAVA_HOME, Gradle home, and Mason binary paths.

9. **`SPEC-015` (Project Nvimrc Template & Local Overrides)**:
   - *Rust Role*: Implement `cumulus-core parse-local-config` to safely load and sanitize `.cumulus.json` workspace settings.

---

## 4. Developer & AI Assistant Guidelines

1. **Rust-First Rule**: Write all heavy computational logic, parsing, background analysis, and file operations in Rust inside `crates/cumulus-core/src/`.
2. **Zero Free Files**: All Rust code lives in `crates/cumulus-core/src/`. All Lua code lives under `lua/cumulus/` or `ftplugin/`.
3. **DevOps Immutability**: `cloud-*.lua`, `lsp-devops.lua`, and `tools-dap-devops.lua` remain frozen.
4. **Verification Command**:
   ```bash
   bash scripts/validate.sh && cargo test --manifest-path crates/cumulus-core/Cargo.toml
   ```
