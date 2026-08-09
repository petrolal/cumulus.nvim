---
description: Check project compliance against Zero Free Files, Frozen DevOps, SDD specs, and Lua standards
disable-model-invocation: true
---

# TASK: Project Compliance & Governance Audit

## Context
You are a Principal Neovim Architect and Systems Auditor. Conduct a strict compliance check across the entire workspace to ensure the project adheres to all architectural constraints and governance rules.

---

## Compliance Rules to Verify

### 1. Zero Free Files Policy
- **Rule:** Every configuration script, LSP handler, plugin spec, or utility script MUST reside inside `lua/` or `ftplugin/`.
- **Check:** Scan the repository root. Apart from standard root configs (`init.lua`, `CLAUDE.md`, `.gitignore`, `lazy-lock.json`, `README.md`, `ARCHITECTURE.md`), ensure NO loose `.lua`, `.sh`, or unmanaged scripts exist at the project root.

### 2. Immutable DevOps Guardrail (CRITICAL)
- **Rule:** All files handling DevOps, Docker, Kubernetes, DevContainers, or Remote SSH (`lua/plugins/devops/` or similar) are **STRICTLY FROZEN**.
- **Check:** Execute `git log` / `git diff` on DevOps configuration paths. Confirm zero unapproved modifications or refactors have touched these modules.

### 3. Lightweight SDD Alignment & File Placement
- **Rule:** Specifications must follow the exact SDD directory layout:
  - `docs/spec_template.md` (Template)
  - `docs/specs/active/` (In-progress features)
  - `docs/specs/completed/` (Archived/built features)
- **Check:** Verify `docs/` structure matches this layout and that code implementation aligns with active specs in `docs/specs/active/`.

### 4. Neovim Lua & Idiomatic Standards
- **APIs:** Ensure code uses `vim.api.nvim_*`, `vim.keymap.set`, `vim.diagnostic.*`, and `vim.lsp.*` instead of legacy Vimscript (`vim.cmd`).
- **Lazy Loading:** Confirm plugins in `lazy.nvim` use lazy-loading triggers (`cmd`, `ft`, `keys`, `event`) to maintain sub-50ms startup times.
- **Scope Safety:** Confirm no global variables (`_G`) leak and modules explicitly return tables.

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

#### 🚨 Non-Compliance Issues Found
*(List any violations with exact file paths and required fixes, or state "None" if all checks pass.)*
