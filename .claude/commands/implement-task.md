---
description: Move spec from backlog/ to active/, execute implementation tasks, and transition spec to review/
disable-model-invocation: false
---

# TASK: Implement Active Specification Tasks

## Context
You are an expert AI Systems Architect and Neovim Developer working with Claude Code.

Your objective is to locate a target specification, transition it from `docs/specs/backlog/` to `docs/specs/active/`, execute its implementation tasks sequentially, and upon completion move it to `docs/specs/review/` with status `REVIEW`.

---

## Non-Negotiable Guardrails

1. **Zero Free Files Policy:** Every new configuration script, LSP handler, or keymapping MUST be placed inside `lua/` or `ftplugin/`. Do NOT create unmanaged root scripts.
2. **Immutable DevOps Guardrail (CRITICAL):** All existing DevOps, Cloud, Container (Docker, Kubernetes), DevContainers, and Infrastructure modules (`lua/cumulus/plugins/cloud-*.lua`, `lsp-devops.lua`, `tools-dap-devops.lua`) are **STRICTLY FROZEN**. Never modify, rename, or delete these files.
3. **Neovim / Lua Quality Standards:** Use native Lua APIs (`vim.api.nvim_*`, `vim.keymap.set`), ensure all `lazy.nvim` plugin declarations include lazy-loading triggers (`cmd`, `ft`, `keys`, `event`) to keep startup under 50ms, and avoid global variable leaks (`_G`).

---

## Execution Directives

1. **Locate Target Spec & Move to Active:**
   - Parse `$ARGUMENTS` for the spec ID or file name (e.g., `002` or `002-html-xml-markup-expansion.md`).
   - If `$ARGUMENTS` is empty, check `docs/specs/active/` first, or select the lowest-numbered spec in `docs/specs/backlog/`.
   - Move the spec file from `docs/specs/backlog/<filename>.md` to `docs/specs/active/<filename>.md` and update `- **Status:** ACTIVE` in the file header (if already in `docs/specs/active/`, ensure status is `ACTIVE`).

2. **Execute Checklist Items:**
   - Read the **Execution Checklist** section of the active spec.
   - For each pending task item (`- [ ]`):
     - Inspect existing target files in `lua/` or `ftplugin/`.
     - Write or edit the code following Neovim Lua best practices.
     - Verify syntax and functionality using terminal commands specified in the spec.
     - Mark the task item as completed (`- [x]`) in the active spec file on disk.

3. **Transition to Review:**
   - Run verification commands specified in the spec (`scripts/validate.sh`, etc.).
   - Once all tasks are checked (`[x]`) and verification passes, update `- **Status:** REVIEW`.
   - Move the spec file from `docs/specs/active/<filename>.md` to `docs/specs/review/<filename>.md`.

---

## Output Rule
Once execution is complete, display a summary of:
1. Tasks completed during this run.
2. Files modified or created in `lua/` or `ftplugin/`.
3. Verification command results.
4. Updated spec location (`docs/specs/review/<filename>.md`).
