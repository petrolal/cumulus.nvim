---
description: Check project compliance against Zero Free Files, Frozen DevOps, SDD specs, Lua standards, and IntelliJ parity
disable-model-invocation: true
---

# TASK: Project Compliance & Governance Audit

## Context
You are a Principal Neovim Architect and Systems Auditor. Conduct a strict compliance check across the entire workspace to ensure the project adheres to all architectural constraints, governance rules, and IntelliJ IDEA parity targets.

---

## Compliance Rules to Verify

### 1. Zero Free Files Policy
- **Rule:** Every configuration script, LSP handler, plugin spec, or utility script MUST reside inside `lua/` or `ftplugin/`.
- **Check:** Scan the repository root. Apart from standard root configs (`init.lua`, `CLAUDE.md`, `.gitignore`, `lazy-lock.json`, `README.md`, `ARCHITECTURE.md`), ensure NO loose `.lua`, `.sh`, or unmanaged scripts exist at the project root.

### 2. Immutable DevOps Guardrail (CRITICAL)
- **Rule:** All files handling DevOps, Docker, Kubernetes, DevContainers, or Remote SSH (`lua/plugins/devops/` or similar) are **STRICTLY FROZEN**.
- **Check:** Execute `git log` / `git diff` on DevOps configuration paths. Confirm zero unapproved modifications or refactors have touched these modules.

### 3. Lightweight SDD Alignment & File Placement
- **Rule:** Specifications must follow the exact Agile SDD directory layout (`backlog` → `active` → `review` → `completed`):
  - `docs/spec_template.md` (Template)
  - `docs/specs/backlog/` (Queued / planned features)
  - `docs/specs/active/` (In-progress features)
  - `docs/specs/review/` (Features undergoing review / verification)
  - `docs/specs/completed/` (Archived / built features)
- **Check:** Verify `docs/` structure matches this 4-stage layout and that code implementation aligns with active and review specs in `docs/specs/active/` and `docs/specs/review/`.

### 4. Neovim Lua & Idiomatic Standards
- **APIs:** Ensure code uses `vim.api.nvim_*`, `vim.keymap.set`, `vim.diagnostic.*`, and `vim.lsp.*` instead of legacy Vimscript (`vim.cmd`).
- **Lazy Loading:** Confirm plugins in `lazy.nvim` use lazy-loading triggers (`cmd`, `ft`, `keys`, `event`) to maintain sub-50ms startup times.
- **Scope Safety:** Confirm no global variables (`_G`) leak and modules explicitly return tables.

### 5. IntelliJ IDEA Parity Standards
- **JVM & Polyglot LSPs:** Verify that LSP configurations exist for Java (`jdtls` with Lombok agent pathing), Kotlin (`kotlin-language-server`), Groovy (`groovy-language-server`), HTML (`html-lsp`), and XML (`lemminx`).
- **Framework & Build Support:** Check for Spring Boot tooling (`spring-boot.nvim`), Maven/Gradle task runners (`overseer.nvim` or terminal task hooks), and automatic workspace synchronization settings.
- **Debugging & Testing Subsystems:** Verify that Debug Adapter Protocol (`nvim-dap` + `nvim-dap-ui` + `java-debug-adapter`) and test runners (`neotest` for JUnit/Kotest) are properly mapped to keybindings.
- **Database Client (DataGrip Equivalent):** Confirm SQL client tooling (`vim-dadbod` + `vim-dadbod-ui`) is configured and accessible via standard keymappings.

---

## Execution Instructions

1. Inspect the workspace file tree and git state.
2. Run automated checks across all compliance areas.
3. Output a structured report directly to the terminal using the following format:

### 📋 Compliance Audit Report

- **Zero Free Files:** [ PASS | FAIL ]
- **DevOps Freeze Safeguard:** [ PASS | FAIL ]
- **SDD Directory Structure:** [ PASS | FAIL ]
- **Lua & Lazy-Loading Standards:** [ PASS | FAIL ]
- **IntelliJ Parity Coverage:** [ PASS | FAIL ]

#### 🚨 Non-Compliance Issues Found
*(List any violations with exact file paths and required fixes, or state "None" if all checks pass.)*
