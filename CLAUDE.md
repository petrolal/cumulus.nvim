# CLAUDE.md — Cumulus Neovim Distribution

## Project Overview
Cumulus is a from-scratch Neovim configuration (`init.lua` → `lua/cumulus/core` → `lua/cumulus/core/lazy`) that replicates IntelliJ IDEA Ultimate's developer experience for three domains, entirely through lazy.nvim plugin specs under `lua/cumulus/plugins/` backed by a high-performance compiled Rust native engine (`crates/cumulus-core`):

1. **JVM & Polyglot stack** — Java (`jdtls`), Kotlin (`kotlin-language-server`), Groovy (`groovyls`), plus TOML (`taplo`) and HTML (`superhtml`).
2. **Web/Markup (HTML/XML)** — HTML via `superhtml` (`lsp-html.lua`), XML/JSON/Bash via `lemminx`/`jsonls`/`bashls` (`lsp-devops.lua`, frozen — see below).
3. **Frozen DevOps/Cloud** — Terraform, Kubernetes/Helm/Docker, CloudFormation/Ansible (`cloud-*.lua`), remote debugging (`tools-dap-devops.lua`).
4. **Rust Native Engine (`cumulus-core`)** — High-performance Rust CLI binary (`crates/cumulus-core`) providing JSON IPC for POM/Gradle parsing, log diagnostic parsing, test runner result extraction, multi-module resolution, Java boilerplate generation, Checkstyle XML parsing, and non-blocking TCP network checks.

Every server registered anywhere attaches automatically through a single generic loop in `lua/cumulus/plugins/lsp-core.lua`; there is no per-language attach wiring to duplicate. Global code keymaps (`<leader>cf` format, `<leader>ca` code action, `<leader>cr` rename, `[d`/`]d`/`<leader>cd` diagnostics) live once in `lua/cumulus/core/keymaps.lua` and apply to every buffer with an attached LSP client — do not re-add them per filetype. Extra per-language build/lint/test commands (Maven, Gradle, Terraform, Ansible, Docker, Helm, JUnit 5) are registered as buffer-local `lang_keymaps` stacks in that same file, gated by filetype and/or a project-file condition (see `lua/cumulus/core/lang-keymaps.lua`).

Startup budget: **under 50ms total**, verified via `nvim --headless --startuptime` (~24.8ms achieved). All plugins must lazy-load on real triggers (`event`, `ft`, `cmd`, `keys`) — nothing eager except `mason.nvim`/`mason-tool-installer.nvim` (their install trigger is a one-shot `VimEnter` autocmd, so they must load eagerly to fire it) and `nvim-lspconfig`/`nvim-lint` (event-gated on `BufReadPre`/`BufNewFile`).

---

## Architecture: Rust-First Engine + Minimal Lua Bridge

To preserve Neovim's Native Lua API while eliminating UI thread blocking and maximizing execution speed:
* **RUST-FIRST DIRECTIVE (CRITICAL FOR HUMANS & AI)**: All new features, business logic, heavy text processing, XML/TOML/YAML AST parsing, project directory scanning, network checks, static analysis, log regex parsing, and backlog specs MUST be implemented in Rust inside `crates/cumulus-core/` first, exposing a CLI subcommand returning JSON.
* **Lua Layer (`lua/cumulus/`)**: Serves strictly as minimal editor glue (autocmd hooks, keymap registration, `lazy.nvim` specs, UI/diagnostic rendering via `vim.diagnostic`, and `vim.ui.select` wrappers).
* **Glue/IPC (`lua/cumulus/util/rust.lua`)**: Executes `cumulus-core` asynchronously or via `vim.system`, decoding stdout JSON streams into Lua tables. Lua fallback routines execute automatically if `cumulus-core` is absent.

---

## Mandatory Constraints

### 1. Zero Free Files Policy
Every configuration script, LSP handler, plugin spec, formatter/linter rule, or keymap MUST live inside a real Neovim module under `lua/cumulus/` or a filetype handler under `ftplugin/`. Native Rust code MUST reside inside the sanctioned `crates/cumulus-core/` crate. No loose `.lua`, `.sh`, or unmanaged config at the project root.

Sanctioned root-level files (do not treat as violations): `init.lua`, `CLAUDE.md`, `.gitignore`, `.neoconf.json`, `stylua.toml`, `lazy-lock.json`, `LICENSE`. Sanctioned non-root exceptions: `scripts/validate.sh` (verification entrypoint) and `crates/cumulus-core/` (Rust native helper package).

### 2. Immutable DevOps Guardrail (CRITICAL)
The following files and paths are **STRICTLY FROZEN** — never modify, rename, relocate, or delete them, and never re-implement functionality they already own elsewhere:
- `lua/cumulus/plugins/cloud-terraform.lua`
- `lua/cumulus/plugins/cloud-containers-k8s.lua`
- `lua/cumulus/plugins/cloud-cloudformation-ansible.lua`
- `lua/cumulus/plugins/lsp-devops.lua` (owns the `json`/`xml`/`bash` Treesitter parsers and `jsonls`/`lemminx`/`bashls` LSP servers — treat XML support here as already complete and read-only for any HTML/XML spec work)
- `lua/cumulus/plugins/tools-dap-devops.lua`
- Any `.devcontainer/`, `Dockerfile`, or Kubernetes/Terraform config files, should they be introduced

Before touching any file, `grep` these paths for the capability you're about to add — lazy.nvim merges `opts` tables across every spec targeting the same plugin, so it's easy to accidentally duplicate a server/parser/formatter that a frozen file already registers.

### 3. Lightweight SDD Alignment
Specifications follow an Agile development flow (`backlog` → `active` → `review` → `completed`):
- `docs/spec_template.md` — the reusable template (metadata block, prerequisite analysis, guardrails, execution checklist, verification commands).
- `docs/specs/backlog/NNN-slug.md` — planned/queued work prior to implementation (created via `/create-task` with status `BACKLOG`).
- `docs/specs/active/NNN-slug.md` — work currently in active development/implementation (moved from `backlog/` via `/implement-task` with status `ACTIVE`).
- `docs/specs/review/NNN-slug.md` — implemented work undergoing review and verification checks (moved from `active/` upon completing `/implement-task` with status `REVIEW`).
- `docs/specs/completed/NNN-slug.md` — fully verified and archived work (moved from `review/` via `/review-task` with status `COMPLETED`).

Active/Review Specifications:
- `SPEC-007` (Review): Test Runner Integration (JUnit 5, Gradle, Maven)
- `SPEC-011` (Review): Gradle/Maven Offline Mode Detection & Toggle
- `SPEC-013` (Review): Inline IntelliJ Code Inspections & Checkstyle Integration
- `SPEC-016` (Review): Rust Helper Migration for Build & Log Diagnostics Parsing
- `SPEC-017` (Review): Rust Helper Expansion: Multi-Module, Stack Trace & Java Header Generators
- `SPEC-018` (Review): Spring Boot & Microservice Endpoint Extractor
- `SPEC-019` (Review): JaCoCo & SonarQube Code Coverage Parser
- `SPEC-020` (Review): Flyway & Liquibase Migration Validator
- `SPEC-021` (Review): Spring Bean Dependency Graph Generator
- `SPEC-022` (Review): High-Speed Log File Indexer & Stream Parser
- `SPEC-023` (Review): Java/Kotlin Import Optimizer
- `SPEC-024` (Review): Helm Chart Values & Kubernetes Schema Validator
- `SPEC-025` (Review): Multi-Module Git Conflict Resolution & Marker Parser

Remaining Backlog Specifications:
- `SPEC-002`: HTML/XML Markup Extension
- `SPEC-005`: JDTLS Classpath Sync Health Check
- `SPEC-006`: SpringBoot Debug Configuration & Hotswap (`<leader>ds`)
- `SPEC-008`: Multi-Module Telescope Navigation (`:Telescope maven_modules` / `gradle_modules`)
- `SPEC-009`: Dependency Lens & Version Checker
- `SPEC-010`: Runtime Exception Stack Trace Drill-Down
- `SPEC-012`: Gradle Wrapper Lock & SHA-256 Checksum Verification
- `SPEC-014`: Platform & Environment Health Checks
- `SPEC-015`: Project Nvimrc Template & Local Overrides

### 4. Neovim Lua & Idiomatic Standards
- Use native APIs: `vim.api.nvim_*`, `vim.keymap.set`, `vim.diagnostic.*`, `vim.lsp.*` — avoid legacy `vim.cmd("...")` Vimscript strings where a Lua API exists.
- Every `lazy.nvim` plugin spec needs a real lazy-loading trigger (`event`, `ft`, `cmd`, or `keys`) unless it has a documented reason to load eagerly (see startup budget note above).
- No global (`_G`) leakage; every module returns an explicit table.
- Keymaps should include `desc` for which-key discoverability.

---

## Command Cheat Sheet
- **`/create-task`** — create a new task specification in `docs/specs/backlog/` with status `BACKLOG`.
- **`/implement-task`** — move spec from `docs/specs/backlog/` to `docs/specs/active/` (status `ACTIVE`), execute implementation tasks, and transition to `docs/specs/review/` (status `REVIEW`) upon completion.
- **`/review-task`** — audit spec in `docs/specs/review/` against compliance standards and transition approved specs to `docs/specs/completed/` (status `COMPLETED`).
- **`/check-project`** — run a compliance audit (Zero Free Files, DevOps freeze, SDD directory structure, Lua/lazy-loading standards) and report PASS/FAIL per rule.
- **`/review`** — code-review a diff or path against the same five non-negotiable rules, grouped into Blockers / Warnings / SDD Drift / Passes.
- **`/gen-sdd`** — generate/refresh the lightweight SDD template and spec files (this file included) from the current repo state.

---

## Verification
Run before considering any change complete:
```bash
bash scripts/validate.sh
cargo test --manifest-path crates/cumulus-core/Cargo.toml
nvim --headless "+Lazy! sync" +qa
nvim --headless "+checkhealth cumulus" +qa
nvim --headless --startuptime /tmp/nvim-startuptime.log +qa && grep "TOTAL" /tmp/nvim-startuptime.log
git status --short lua/cumulus/plugins/cloud-* lua/cumulus/plugins/lsp-devops.lua lua/cumulus/plugins/tools-dap-devops.lua
```
The last command must return no output — any diff there is a guardrail violation.
