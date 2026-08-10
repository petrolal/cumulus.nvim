---
description: Create a new task specification in docs/specs/backlog/ with status BACKLOG based on user intent and codebase state
disable-model-invocation: false
---

# TASK: Generate Specification in docs/specs/backlog/

## Context
You are an AI Systems Architect working with Claude Code. You have full authorization to inspect the repository and write files to disk.

We manage features using an **Agile Spec-Driven Development (SDD)** lifecycle:
`docs/specs/backlog/` → `docs/specs/active/` → `docs/specs/review/` → `docs/specs/completed/`

All new task specifications are strictly created in `docs/specs/backlog/` with status `BACKLOG`.

### Mandatory Architectural Constraints
1. **Zero Free Files Policy:** Every new configuration, keymapping, or LSP setup MUST reside inside `lua/` or `ftplugin/`. No unmanaged scripts at the root directory.
2. **DevOps Immutable Guardrail (CRITICAL):** All existing DevOps, Cloud, Container (Docker, Kubernetes), DevContainers, and Infrastructure configurations (`lua/cumulus/plugins/cloud-*.lua`, `lsp-devops.lua`, `tools-dap-devops.lua`) are **STRICTLY FROZEN**. Never modify these files.
3. **IntelliJ Parity Alignment:** Ensure any feature additions match IntelliJ IDEA workflows without degrading startup times (<50ms).

---

## Directives & Execution Instructions

1. **Parse Input:** Use `$ARGUMENTS` as the task title/feature description. If `$ARGUMENTS` is empty, inspect uncommitted git changes or ask the user for a summary.
2. **Determine Spec ID:** Scan `docs/specs/backlog/`, `docs/specs/active/`, `docs/specs/review/`, and `docs/specs/completed/` to determine the next sequential ID (e.g., `004-feature-name.md`).
3. **Inspect Codebase:** Read relevant files in `lua/` and `ftplugin/` to perform Prerequisite Analysis and ensure file paths and module locations in the spec are exact.
4. **Write File to Disk:** Use your file creation tools (`Write`) to save the generated specification into `docs/specs/backlog/<ID>-<feature-name>.md` with status `BACKLOG` based on `docs/spec_template.md`.

---

## Output Rule
After generating the spec, display a brief summary displaying:
1. Created file path (`docs/specs/backlog/<ID>-<feature-name>.md`).
2. Current status (`BACKLOG`).
3. Next steps in the SDD Agile flow (`backlog` → `active` → `review` → `completed`).
