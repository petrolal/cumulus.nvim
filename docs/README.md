# TetraVim Project Knowledge & Documentation

Welcome to the **TetraVim** documentation repository. This directory serves as the project knowledge base configured for BMAD (`project_knowledge: "{project-root}/docs"`).

## Core Documentation References

- **Agent Guidelines & Policy**: [`AGENTS.md`](../AGENTS.md)
- **Installation Guide**: [`INSTALL.md`](../INSTALL.md)
- **Quickstart & Commands**: [`README.md`](../README.md)
- **Architecture Specification**: [`_bmad-output/planning-artifacts/architecture/architecture-tetravim.nvim-2026-08-25/ARCHITECTURE-SPINE.md`](../_bmad-output/planning-artifacts/architecture/architecture-tetravim.nvim-2026-08-25/ARCHITECTURE-SPINE.md)
- **Features Specification**: [`_bmad-output/planning-artifacts/FEATURES_SPEC.md`](../_bmad-output/planning-artifacts/FEATURES_SPEC.md)
- **Epics & Stories Breakdown**: [`_bmad-output/planning-artifacts/epics.md`](../_bmad-output/planning-artifacts/epics.md)
- **Sprint Status**: [`_bmad-output/implementation-artifacts/sprint-status.yaml`](../_bmad-output/implementation-artifacts/sprint-status.yaml)

## Architecture Overview

`tetravim.nvim` is an enterprise-ready Neovim distribution for JVM backend engineering (Java, Kotlin, Scala). It is **pure native Neovim** — standard LSPs (`nvim-jdtls`, Kotlin Language Server, `nvim-metals`), Tree-sitter, Mason tools, and Lua utilities. There is no `tetravim-engine`, no Scala backend, and no bridge.

### Key Modules

- **Core**: `lua/tetravim/core/` (Options, keymaps, autocmds, devops, lazy bootstrap)
- **Plugins**: `lua/tetravim/plugins/` (Lazy.nvim plugin specifications)
- **JVM Utilities**: `lua/tetravim/util/jvm.lua`, `lua/tetravim/util/spring.lua`, `lua/tetravim/util/spring-picker.lua`, `lua/tetravim/util/refactor.lua`, `lua/tetravim/util/extract.lua`, `lua/tetravim/util/db.lua`, `lua/tetravim/util/http.lua`, `lua/tetravim/util/openapi.lua`, `lua/tetravim/util/git.lua`, `lua/tetravim/util/forge.lua`
- **Canonical Tetris Theme**: `lua/tetravim/theme/tetris.lua`, `lua/tetravim/theme/init.lua`, `lua/tetravim/util/theme_colors.lua`
