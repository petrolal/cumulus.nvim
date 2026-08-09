---
description: Analyze project state and generate Lightweight SDD specs + CLAUDE.md directly to disk
disable-model-invocation: false
---

# TASK: Generate Lightweight SDD Documentation Suite & CLAUDE.md

## Context
You are an AI Systems Architect working with Claude Code. You have full authorization to inspect the repository and write files to disk.

We are establishing a **Lightweight Spec-Driven Development (SDD)** framework tailored for automated agents executing file operations.

### Target Architecture & Constraints
1. **JVM & Polyglot Stack:** Java (`jdtls`), Kotlin (`kotlin-language-server`), Groovy (`groovy-language-server`), HTML (`html-lsp`), XML (`lemminx`).
2. **DevOps Immutable Guardrail (CRITICAL):** All existing DevOps, Cloud, Container (Docker, Kubernetes), and Infrastructure configurations are **STRICTLY FROZEN**. Never modify these files.
3. **Zero Free Files Policy:** Every Lua configuration, keymapping, or LSP setup must reside inside `lua/` or `ftplugin/`. No unmanaged scripts at the root.

---

## Directives & Execution Instructions

1. **Direct File System Output:** Use your file creation tools (`Write` / `Edit`) to write all generated markdown files directly to disk. Do not just output raw markdown text to the chat console.
2. **Directory Guarantee:** Ensure `docs/specs/active/` and `docs/specs/completed/` exist before writing.

### Required File Deliverables

#### File 1: `CLAUDE.md` (Project Root)
Write `CLAUDE.md` at the project root to store repository instructions:
- Project overview & lightweight SDD rules.
- Mandatory constraints: Zero Free Files policy & Frozen DevOps files.
- Command cheat sheet for Claude Code (`/check-project`, `/review`, `/gen-sdd`).

#### File 2: `docs/spec_template.md`
Write the standardized agent-readable SDD template:
- Metadata block (`Spec ID`, `Status`, `Target Paths`).
- In-scope vs Out-of-scope boundaries.
- Sequential Claude Code task checklist (`- [ ]`).
- Verification terminal commands.

#### File 3: `docs/specs/completed/001-neovim-intellij-polyglot-setup.md`
Write the archived spec representing the finished baseline:
- Status: `COMPLETED`.
- Mapping of finished plugins (`lazy.nvim`), LSPs (`jdtls`, `kotlin-language-server`), debug adapters (`nvim-dap`), and SQL clients (`vim-dadbod`) to their exact file paths.
- Recorded verification proofs.

#### File 4: `docs/specs/active/002-html-xml-markup-expansion.md`
Write the active spec for HTML/XML markup expansion:
- Status: `ACTIVE`.
- Goal: Add `lemminx` (XML) and `html-lsp` (HTML) for schema validation, formatting, tag auto-closing, and Tree-sitter syntax highlighting.
- Guardrail Assertion: Explicit check confirming `lua/plugins/devops/` remains untouched.
- Step-by-step task checklist for Claude Code to execute.

---

## Output Rule
Once all files are written to disk, output a short summary table listing the created files and their status.
