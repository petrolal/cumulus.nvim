# TetraVim IDE - Feature Specifications

This document outlines all core features that `tetravim.nvim` must maintain and support as it transitions from a custom Scala backend to a pure Neovim-plugin architecture. Any AI agents or developers contributing to this project must ensure these features are fully implemented using standard Lua plugins and Language Servers.

## 1. Cloud Theme System
- **Feature:** Multi-cloud theme provider that alters UI colors, highlighting, and icons based on the target cloud environment.
- **Supported Themes:** `aws`, `azure`, `gcp`, `oci` (Oracle Cloud Infrastructure).
- **Keymap:** `<leader>ct` to switch themes dynamically.
- **Migration Strategy:** Implement a custom Lua module (`lua/tetravim/theme/init.lua`) that bridges with base color schemes (e.g., Catppuccin or Tokyonight) and applies specific highlight overrides (e.g., AWS orange, Azure blue).

## 2. Spring Ecosystem Integration
- **Feature:** Deep insight into Spring Boot applications.
- **Capabilities:**
  - Detect Spring Boot application roots.
  - Interactive Spring Bean picker (visualize dependency graphs).
  - REST endpoint extraction (discover `@GetMapping`, `@PostMapping`, JAX-RS) and interactive picker.
- **Keymaps:**
  - `<leader>jsb`: Select Spring Bean
  - `<leader>jse`: Select REST Endpoint
  - `<leader>js`: Detect Spring Boot app and debug configurations.
- **Migration Strategy:** Integrate `spring-boot.nvim`, extending it where necessary using Tree-sitter queries for AST parsing of Java/Kotlin endpoints.

## 3. Build System Mastery (Maven / Gradle / SBT)
- **Feature:** Intelligent build execution and dependency management.
- **Capabilities:**
  - Multi-module DAG topological build order computation.
  - Parse build logs (Maven / Gradle) and populate Neovim diagnostics (with column precision).
- **Keymap:** `<leader>jb` to build the project with error diagnostics.
- **Migration Strategy:** Use `compiler.nvim` or `makeprg` coupled with `errorformat` tailored for Maven and Gradle. Utilize `nvim-jdtls` / `nvim-metals` for classpath sync checking.

## 4. Diagnostics, Testing, and QA
- **Feature:** First-class testing and code quality tools.
- **Capabilities:**
  - JUnit test output parser.
  - Nearest test context detector.
  - Stack trace symbol resolver (jump to file/line from logs).
  - JaCoCo XML code coverage overlay.
  - Checkstyle reporting.
- **Keymap:** `<leader>jt` to run the nearest Java/Kotlin test under the cursor.
- **Migration Strategy:** 
  - Integrate `neotest` with `neotest-java` / `neotest-gradle` / `neotest-scala`.
  - Use `nvim-coverage` mapped to parse JaCoCo reports.
  - Use `efm-langserver` or `none-ls` for Checkstyle.

## 5. DevOps & Cloud Native Tools
- **Feature:** Validation and execution of infrastructure and database scripts.
- **Capabilities:**
  - Flyway migration validation.
  - Kubernetes manifest checker.
  - Interactive Git conflict resolver.
- **Migration Strategy:** 
  - Integrate `yamlls` (with kubernetes schemas) and `helm_ls` via `nvim-lspconfig`.
  - Use `diffview.nvim` for advanced conflict resolution.
  - Integrate `vim-dadbod` for database and Flyway exploration.

## 6. Environment & Tooling
- **Capabilities:**
  - Host JDK auto-discovery.
  - Automated installation of LSP servers (JDTLS, Metals, Kotlin LS) via Mason.
  - Terminal UI provided completely via Snacks (`folke/snacks.nvim`).
- **Migration Strategy:** Ensure `mason.nvim` and `mason-lspconfig.nvim` are correctly configured to install and manage the underlying binaries.
