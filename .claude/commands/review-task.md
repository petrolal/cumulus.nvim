---
description: Review spec in docs/specs/review/ against compliance standards and transition approved specs to completed/
disable-model-invocation: false
---

# TASK: Code & Compliance Review & Spec Archiving

## Context
You are a Principal Neovim Architect and Systems Auditor. Conduct an uncompromising code review of uncommitted changes or recent commits to ensure compliance with project standards, and audit specifications in `docs/specs/review/` to transition approved work to `docs/specs/completed/`.

We follow the SDD Agile Lifecycle:
`docs/specs/backlog/` → `docs/specs/active/` → `docs/specs/review/` → `docs/specs/completed/`

---

## Audit Checklist

Inspect all recent changes (`git diff`) or specified paths against these 5 Non-Negotiable Criteria:

### 1. Zero Free Files Check
- Confirm that NO loose `.lua`, `.sh`, or unmanaged scripts exist at the root directory. All logic must reside inside `lua/` or `ftplugin/`.

### 2. Immutable DevOps Guardrail (CRITICAL)
- Confirm that NO files in `lua/cumulus/plugins/cloud-*.lua`, `lsp-devops.lua`, `tools-dap-devops.lua`, Docker configurations, Kubernetes manifests, or DevContainers were modified or renamed.

### 3. Lightweight SDD Alignment
- Verify that changes accurately match the tasks listed in the target spec under `docs/specs/review/`.

### 4. Neovim Lua & Performance Standards
- Confirm use of `vim.api.nvim_*` and `vim.keymap.set` instead of legacy `vim.cmd`.
- Verify that all new plugins in `lazy.nvim` include lazy-loading triggers (`cmd`, `ft`, `keys`, `event`).
- Confirm zero global leaks (`_G`).

### 5. IntelliJ IDEA Parity Integrity
- Verify that LSP configurations (Java, Kotlin, Groovy, HTML, XML), DAP debuggers, test runners, and database connections remain functional without breaking existing keymappings.

---

## Execution Directives

1. **Audit Spec & Code:**
   - If `$ARGUMENTS` specifies a spec or file, inspect that target.
   - If `$ARGUMENTS` is empty, check `docs/specs/review/` for specs awaiting final audit, or inspect `git diff`.

2. **Transition Approved Specs to Completed:**
   - If a spec in `docs/specs/review/` passes all compliance and functional checks with zero blockers:
     - Update `- **Status:** COMPLETED` in its metadata.
     - Append an **Archived Date** and **Verification Proof** entry at the bottom of the spec.
     - Move the spec file from `docs/specs/review/<filename>.md` to `docs/specs/completed/<filename>.md`.

3. **Output Report:**
   - Output a structured report in the chat using this format:

### 📋 Code Review & Compliance Summary

- **Zero Free Files:** [ PASS | FAIL ]
- **DevOps Freeze Safeguard:** [ PASS | FAIL ]
- **SDD Task Alignment:** [ PASS | FAIL ]
- **Lua & Performance Standards:** [ PASS | FAIL ]

#### 🚨 Action Items & Fixes Needed
*(List any violations with exact file paths and suggested fixes, or state "None" if all checks pass.)*

