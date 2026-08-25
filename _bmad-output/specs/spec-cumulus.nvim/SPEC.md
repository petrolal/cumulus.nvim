---
status: final
updated: 2026-08-25
companions:
  - ../../planning-artifacts/ux-designs/ux-cumulus.nvim-2026-08-25/DESIGN.md
  - ../../planning-artifacts/ux-designs/ux-cumulus.nvim-2026-08-25/EXPERIENCE.md
  - ../../planning-artifacts/architecture/architecture-cumulus.nvim-2026-08-25/ARCHITECTURE-SPINE.md
sources:
  - ../../planning-artifacts/FEATURES_SPEC.md
  - ../../planning-artifacts/EPICS_AND_STORIES.md
---

# SPEC: Cumulus IDE Lua Migration

## Why
Cumulus IDE needs to migrate from a custom Scala backend (`cumulus-engine`) to a pure Lua and LSP-based architecture to reduce execution overhead, eliminate JVM blocking, and align with native Neovim community standards.

## Capabilities
- **CAP-1**: Cloud Theme System - Dynamically switch UI accents based on AWS, Azure, GCP, or OCI environments using native highlight overrides.
- **CAP-2**: Spring Ecosystem Integration - Pick and navigate to Spring Beans and REST endpoints via pure Lua Tree-sitter AST parsing.
- **CAP-3**: Build System Mastery - Execute Maven/Gradle builds headlessly (`vim.system`) and populate inline error diagnostics via standard errorformats.
- **CAP-4**: Buffer-based File Explorer - Navigate and mutate the file system strictly via `oil.nvim` buffers.

## Constraints
- **Legacy Boundary**: Must not add any new Scala or SBT code. The legacy `cumulus-engine` must not gain new surface area.
- **Event-Driven UI**: Core logic must remain UI-agnostic by emitting autocommands rather than calling UI render functions directly to prevent editor freezes.
- **Stateless Context**: No global project state caches; context must be fetched on-the-fly from the disk or the language server.

## Non-goals
- Creating adapter facades for UI plugins (direct coupling to `snacks.nvim` and Telescope is explicitly mandated).
- Retaining traditional sidebars for file navigation (e.g., `neo-tree`).
- Supporting non-JVM language ecosystems as first-class integration citizens in this migration.

## Success Signal
A developer can debug a JVM application, discover Spring endpoints, and run builds without the legacy scala engine binary running on their machine, and without experiencing any editor UI freezes during background tasks.
