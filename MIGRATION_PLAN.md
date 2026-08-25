# Cumulus Migration Plan (Scala Engine -> Native Neovim Ecosystem)

## Goal
Migrate `cumulus.nvim` away from the custom Scala-based `cumulus-engine` and re-align the project strictly with the native Neovim plugin ecosystem. The end state is a full, stable IntelliJ IDEA replacement focused on JVM (Java, Kotlin, Scala, Gradle) and Cloud Native tools, without the maintenance overhead of a custom backend engine.

## Phase 1: Purge & Cleanup (Completed)
- [x] Remove the `engine/` directory (Scala codebase).
- [x] Delete Scala-engine specific documentation (`CLI_ARCHITECTURE.md`, `SCALA_CLI_SUMMARY.md`, etc.).
- [x] Update `bootstrap.sh` to remove `sbt` and `coursier` build steps.
- [x] Update `INSTALL.md` and `README.md` to reflect the new architecture.
- [x] Remove engine verification steps from `scripts/validate.sh`.
- [x] Rewrite `EPICS_AND_STORIES.md` to focus on IntelliJ parity via native plugins.

## Phase 2: Refactoring the Lua Bridge
The `cumulus-engine` was previously bridged through Lua (`lua/cumulus/util/engine.lua`). Now that the engine is gone, we must replace these specific functions with native Neovim alternatives:

1. **Build Systems (Maven/Gradle)**
   - **Old:** Engine parsed POMs and Gradle DAGs.
   - **New:** Use `nvim-jdtls` native build system detection and `neotest-java` / `neotest-gradle`.

2. **Spring Boot Intelligence**
   - **Old:** Engine extracted `@GetMapping` endpoints and Beans via AST parsing.
   - **New:** Integrate `spring-boot.nvim` for live JVM process attachment and bean visualization.

3. **Kubernetes & Cloud Validation**
   - **Old:** Engine validated YAMLs.
   - **New:** Configure `helm_ls` and `yamlls` (with Kubernetes schemas) via `nvim-lspconfig`.

4. **Diagnostics & Testing**
   - **Old:** Engine parsed stack traces and output logs.
   - **New:** Use `nvim-dap` for debugging and `neotest` for testing with proper UI formatting.

## Phase 3: Plugin Ecosystem Enhancements
To achieve true IDE parity:
- **Java:** `nvim-jdtls` (already partially there, needs tuning for enterprise monorepos).
- **Kotlin:** `kotlin-language-server` and `nvim-dap-kotlin`.
- **Scala:** `nvim-metals` (provides deep SBT and Scala CLI integration).
- **Database:** `vim-dadbod` and `vim-dadbod-ui`.
- **Git/ALM:** `diffview.nvim` and `neogit`.

## Summary
By removing the custom engine, we drastically reduce the complexity and installation footprint of Cumulus. We delegate heavy lifting to the highly optimized, community-supported LSPs (JDTLS, Metals, etc.) while Cumulus acts as the opinionated, perfectly-tuned "glue" distribution that brings it all together for backend engineers.
