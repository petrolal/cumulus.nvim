# TetraVim IDE - Feature Specifications

This document outlines all core features that `tetravim.nvim` must maintain and support. The distribution is pure native Neovim — standard LSPs, Tree-sitter, Mason tools, and Lua utilities, with no custom backend. Any AI agents or developers contributing to this project must ensure these features are fully implemented using standard Lua plugins and Language Servers.

## 1. Spring Ecosystem Integration
- **Feature:** Deep insight into Spring Boot applications.
- **Capabilities:**
  - Detect Spring Boot application roots and main class without blocking the UI.
  - Interactive Spring Bean picker (`<leader>jsb`) with dependency graph preview (direct deps & dependents).
  - REST endpoint extraction (discover `@GetMapping`, `@PostMapping`, `@PutMapping`, `@DeleteMapping`, `@PatchMapping`, `@RequestMapping`, and JAX-RS `@GET`/`@POST`/`@PUT`/`@DELETE`) with jump to mapping annotation (`<leader>jse`).
  - Native Spring Boot DAP launch and attach configuration generation.
- **Keymaps:**
  - `<leader>jsb`: Select Spring Bean (Telescope picker + dependency graph preview)
  - `<leader>jse`: Select REST Endpoint (Telescope picker + code preview)
  - `<leader>jsd`: Detect Spring Boot App
  - `<leader>jrd`: Launch Spring Boot DAP debug session
- **Implementation:** Pure Lua Tree-sitter AST queries (`lua/tetravim/util/spring.lua`), Telescope pickers (`lua/tetravim/util/spring-picker.lua`), and native DAP config generation (`lua/tetravim/util/springboot-debug.lua`). No external backend is involved at any point.

## 2. Advanced Refactoring & Code Actions
- **Feature:** Safe, project-wide refactorings and code extractions.
- **Capabilities:**
  - Project-wide safe rename and move for Java and Kotlin, updating references across code, Spring XML, and annotations.
  - Interactive extraction: extract method (with automatic parameter resolution), extract variable, extract constant, extract interface, and inline variable/method.
  - Shared concurrency lock (`action-lock.lua`) preventing race conditions during refactoring.
  - Quickfix dry-run preview before applying changes.
- **Keymaps:**
  - `<leader>cr`: Project-wide rename (buffer-local for Java/Kotlin)
  - `<leader>cm`: Extract Method
  - `<leader>cv`: Extract Variable / Constant
  - `<leader>ci`: Extract Interface / Inline
- **Implementation:** Native LSP (`nvim-jdtls`, `kotlin-language-server`) combined with Tree-sitter candidate filtering and validation (`lua/tetravim/util/refactor.lua`, `lua/tetravim/util/refactor-treesitter.lua`, `lua/tetravim/util/extract.lua`).

## 3. Database & Cloud Services Integration
- **Feature:** Native database exploration and HTTP/REST client tools.
- **Capabilities:**
  - Embedded database explorer: schema browsing and SQL query execution directly in editor buffers.
  - Automatic credential and datasource discovery from Spring Boot `application.properties`, `application.yml`, and `application.yaml` (with environment variable and `.env` interpolation).
  - Native HTTP request execution for `.http` files.
  - OpenAPI spec exploration and request template generation.
  - `jq` response filtering directly into editor splits.
- **Keymaps:**
  - `<leader>db*`: Database explorer operations (`vim-dadbod-ui`)
  - `<leader>Hr`: Run HTTP request under cursor (`kulala.nvim`)
  - `<leader>Ho`: Generate `.http` file from OpenAPI specification
  - `<leader>Hj`: Apply `jq` filter to JSON response
- **Implementation:** `lua/tetravim/util/db.lua` (`vim-dadbod`, `vim-dadbod-ui`), `lua/tetravim/util/http.lua`, `lua/tetravim/util/openapi.lua`.

## 4. Git Collaboration & Conflict Resolution
- **Feature:** Advanced 3-way merge conflict resolution and forge code reviews.
- **Capabilities:**
  - 3-way visual merge conflict resolution (`diff4_mixed`) with region-picking keymaps.
  - Pure Lua git worktree guards preventing hanging subprocesses.
  - In-editor code reviews for GitHub (`gh`) and GitLab (`glab`): list PRs/MRs, view diffs, post line comments, and checkout branches.
- **Keymaps:**
  - `<leader>gco`: Open merge conflict resolver
  - `<leader>gcq`: Close conflict view
  - `<leader>gch`: Explore file history
  - `<leader>gx*` / `<leader>gX*`: Pick conflict chunks / whole files
  - `<leader>gro`: Open PR / MR code review
  - `<leader>grc`: Add review comment
- **Implementation:** `lua/tetravim/plugins/tools-diffview.lua`, `lua/tetravim/util/git.lua`, `lua/tetravim/util/forge.lua`.

## 5. Build System Mastery (Maven / Gradle)
- **Feature:** Intelligent build execution and dependency management.
- **Capabilities:**
  - Headless build execution via `vim.system` without blocking UI.
  - Parse build logs (Maven / Gradle) and populate Neovim diagnostics / quickfix.
  - Dependency version checking and resyncing.
- **Keymaps:**
  - `<leader>jb`: Build project
  - `<leader>jbS` / `<leader>jds`: Resync dependencies
  - `<leader>jbo`: Toggle offline mode
- **Implementation:** Event-driven background executors with quickfix population (`lua/tetravim/util/build-sync-state.lua`, `lua/tetravim/util/jvm.lua`).

## 6. Diagnostics, Testing, and QA
- **Feature:** First-class testing and code quality tools.
- **Capabilities:**
  - JUnit test execution and output parsing via `neotest`.
  - Nearest test context detection.
  - Continuous profiling via `async-profiler` integration (`lua/tetravim/util/profiling.lua`).
  - JaCoCo XML code coverage overlay.
- **Keymaps:**
  - `<leader>jt`: Test runner operations
  - `<leader>jpp` / `<leader>jps` / `<leader>jpf`: Profiler start, stop, flamegraph
- **Implementation:** `neotest`, `lua/tetravim/util/profiling.lua`, `lua/tetravim/plugins/tools-test.lua`.

## 7. DevOps & Infrastructure Tooling
- **Feature:** Validation and execution of infrastructure and configuration files.
- **Capabilities:**
  - Terraform, OpenTofu, CloudFormation, SAM, Ansible, Docker, and Helm validation and linting.
  - Group-aligned keymap structure under `<leader>o`.
- **Keymaps:**
  - `<leader>ot` / `<leader>otV`: Terraform operations & validation
  - `<leader>oc` / `<leader>ocC`: CloudFormation operations & validation
  - `<leader>oy` / `<leader>oyV`: Ansible operations & validation
  - `<leader>od` / `<leader>odV`: Docker operations & validation
  - `<leader>ok` / `<leader>okV`: Helm / Kubernetes operations & validation
- **Implementation:** `lua/tetravim/core/devops.lua`.

## 8. Canonical Visual Identity (Tetris Theme)
- **Feature:** Distinctive, distraction-free semantic highlight system.
- **Capabilities:**
  - Fixed "Tetris" palette with 7 semantic tetromino token colors.
  - Self-contained pure-Lua implementation with no external dependencies.
  - Cohesive integration across Lualine, Bufferline, Snacks, and syntax highlights.
- **Implementation:** `lua/tetravim/theme/tetris.lua`, `lua/tetravim/theme/init.lua`, `lua/tetravim/util/theme_colors.lua`. (Legacy cloud theme switcher retired).
