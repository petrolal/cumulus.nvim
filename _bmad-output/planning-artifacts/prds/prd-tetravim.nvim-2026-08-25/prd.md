---
title: TetraVim.nvim PRD
status: final
created: 2026-08-25
updated: 2026-08-25
---

# TetraVim.nvim

## 1. Product Vision & Goals
**TetraVim.nvim** is a customized, thematic, and highly stable JVM IDE built purely on the Neovim ecosystem with cloud superpowers. Its primary goal is to fully replace IntelliJ IDEA for daily, mission-critical enterprise backend development in Java and Kotlin.

**Core Objectives:**
- **Absolute Stability:** Serve as a reliable daily driver for a professional engineer. Zero tolerance for freezing or blocking the UI.
- **Enterprise JVM Parity:** Provide essential enterprise development features for Java and Kotlin without the overhead of heavy Java-based IDEs, embracing a keyboard-driven Neovim philosophy.
- **Native Ecosystem:** Shift entirely away from custom background engines and rely on community-standard LSPs and plugins.

## 2. Target Audience & Stakes
- **Primary User:** Solo enterprise backend developer.
- **Stakes:** High. This is a principal work tool used for a real job. It must be strictly dependable.
- **Form Factor:** Terminal-based desktop application (Neovim).

## 3. Core Features & Capabilities

### 3.1. Language & Framework Intelligence
- **[F-01] Java & Kotlin Support:** Full LSP-backed intelligence via `nvim-jdtls` and `kotlin-language-server`.
  - *AC:* Auto-completion, go-to-definition, and signature help respond in <200ms.
- **[F-02] Spring Boot Integration:** Auto-discovery of REST endpoints and bean visualization via `spring-boot.nvim`.
  - *AC:* User can trigger a telescope picker to search all mapped REST endpoints in the project.

### 3.2. Build, Test, and Refactoring
- **[F-03] Neovim-Native Project Generator:** A dedicated Telescope/UI layer to initialize new Maven and Gradle projects from scratch. Captures the essence of IntelliJ's generator (e.g., Spring Initializr integration, archetype selection) but tailored to Neovim's keyboard-centric philosophy.
  - *AC:* User can execute a single command to scaffold a running Spring Boot Gradle/Maven project without leaving Neovim.
- **[F-04] Build System Integration:** Intelligent detection and handling of Maven and Gradle lifecycle phases.
  - *AC:* User can trigger `clean install` or `build` via native keybinds, with output piped asynchronously.
- **[F-05] Testing Suite:** JUnit output parsing, nearest-test execution, and JaCoCo coverage overlays.
  - *AC:* User can press a hotkey on a `@Test` method to run only that test, and see success/fail icons in the gutter.
- **[F-06] Refactoring:** Project-wide safe renaming, moving, and extraction powered by native LSPs.
  - *AC:* Renaming a public class automatically updates all references across the workspace.

### 3.3. Debugging & Profiling
- **[F-07] DAP Integration:** Advanced debugging setup with `nvim-dap`, including conditional breakpoints and hotswap capabilities.
  - *AC:* User can set a breakpoint, attach to a running JVM, and inspect variables seamlessly.
- **[F-08] Continuous Profiling:** Integration with `async-profiler` to generate and view CPU/Memory flamegraphs directly.
  - *AC:* Profiler can be attached/detached without blocking the main editor thread.

### 3.4. Cloud & DevOps Superpowers
- **[F-09] Infrastructure as Code:** Validation for Kubernetes manifests and Helm charts via `yamlls` and `helm_ls`.
  - *AC:* Missing required K8s fields are highlighted as diagnostics in the buffer.
- **[F-10] Database Tooling:** Embedded database exploration and Flyway script validation using `vim-dadbod` and `vim-dadbod-ui`.
  - *AC:* User can execute SQL queries against a live local Postgres database directly from a `.sql` buffer.
- **[F-11] Thematic Cloud Workspaces:** Dynamic multi-cloud theme switching tailored to specific environments (AWS, Azure, GCP, OCI).
  - *AC:* Executing a theme toggle command updates the global Neovim colorscheme immediately.

## 4. Constraints & Non-Functional Requirements
- **[NFR-01] Performance & Async UI:** The editor UI must remain completely unblocked. All LSP and background operations must be strictly asynchronous.
- **[NFR-02] Resource Limits & Trade-offs:** Hard memory limits will be enforced on heavy LSPs (e.g., JDTLS heap capped at 2GB) to prevent OOM crashes. *Trade-off:* We accept slightly slower initial project indexing on massive monorepos in exchange for guaranteed editor stability.
- **[NFR-03] Hardware Profile:** Assumes a modern development machine with **32GB+ RAM**.
- **[NFR-04] Network Profile:** Assumes standard internet access for downloading dependencies and LSP binaries on the fly.
- **[NFR-05] Installation:** Must support a 100% headless automated installation (`bootstrap.sh`) and headless validation (`validate.sh`).

## 5. Out of Scope
- Building or maintaining custom backend language engines (the legacy Scala `tetravim-engine` is deprecated).
- Scala or `sbt` support is strictly out of scope; focus is entirely on Java and Kotlin.
- Complex multi-user collaborative features (focused strictly on single-operator efficiency).
- Air-gapped/offline-first installations.
