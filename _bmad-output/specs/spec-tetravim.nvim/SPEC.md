---
status: final
updated: 2026-08-25
companions:
  - ../../planning-artifacts/ux-designs/ux-tetravim.nvim-2026-08-25/DESIGN.md
  - ../../planning-artifacts/ux-designs/ux-tetravim.nvim-2026-08-25/EXPERIENCE.md
  - ../../planning-artifacts/architecture/architecture-tetravim.nvim-2026-08-25/ARCHITECTURE-SPINE.md
sources:
  - ../../planning-artifacts/FEATURES_SPEC.md
  - ../../planning-artifacts/EPICS_AND_STORIES.md
---

# SPEC: TetraVim IDE Lua Migration

## Why
TetraVim IDE is built on a pure Lua and LSP-based architecture to minimize execution overhead, avoid JVM blocking, and align with native Neovim community standards. There is no custom backend of any kind.

## Capabilities
- **CAP-1**: Canonical "Tetris" Visual Identity - Self-contained semantic highlight system based on seven tetromino colors bound to fixed token roles across all UI components.
- **CAP-2**: Spring Ecosystem Integration - Pick and navigate to Spring Beans and REST endpoints via pure Lua Tree-sitter AST parsing.
- **CAP-3**: Build System Mastery - Execute Maven/Gradle builds headlessly (`vim.system`) and populate inline error diagnostics via standard errorformats.
- **CAP-4**: Buffer-based File Explorer - Navigate and mutate the file system strictly via `oil.nvim` buffers.

## Constraints
- **Native Boundary**: Must not add any Scala or SBT code, and must not introduce an external backend, engine, or bridge. All heavy lifting goes through standard community LSPs and Lua.
- **Event-Driven UI**: Core logic must remain UI-agnostic by emitting autocommands rather than calling UI render functions directly to prevent editor freezes.
- **Stateless Context**: No global project state caches; context must be fetched on-the-fly from the disk or the language server.

## Non-goals
- Creating adapter facades for UI plugins (direct coupling to `snacks.nvim` and Telescope is explicitly mandated).
- Retaining traditional sidebars for file navigation (e.g., `neo-tree`).
- Supporting non-JVM language ecosystems as first-class integration citizens in this migration.

## Success Signal
A developer can debug a JVM application, discover Spring endpoints, and run builds entirely through native Neovim and standard LSPs, without experiencing any editor UI freezes during background tasks.
