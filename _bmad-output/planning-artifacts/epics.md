stepsCompleted:
  - step-01-validate-prerequisites
  - step-02-design-epics
inputDocuments:
  - /home/petrolal/tetravim.nvim/_bmad-output/specs/spec-tetravim.nvim/SPEC.md
  - /home/petrolal/tetravim.nvim/_bmad-output/planning-artifacts/architecture/architecture-tetravim.nvim-2026-08-25/ARCHITECTURE-SPINE.md
  - /home/petrolal/tetravim.nvim/_bmad-output/planning-artifacts/ux-designs/ux-tetravim.nvim-2026-08-25/DESIGN.md
  - /home/petrolal/tetravim.nvim/_bmad-output/planning-artifacts/ux-designs/ux-tetravim.nvim-2026-08-25/EXPERIENCE.md
---

# tetravim.nvim - Epic Breakdown

## Overview
This document provides the complete epic and story breakdown for tetravim.nvim, decomposing the requirements from the new Lua migration Spec, UX Design, and Architecture requirements into implementable stories.

## Epic 1: Core Lua Migration & UI Primitives

The core migration from the custom Scala backend to a pure Lua and LSP-based architecture.

### Story 1.1: Stateless File Management (oil.nvim)
As a developer,
I want to manage files using oil.nvim,
So that I can leverage standard Neovim buffer commands rather than a complex sidebar widget.

**Acceptance Criteria:**
**Given** neo-tree is completely uninstalled
**When** I press `<leader>e`
**Then** oil.nvim opens the current directory in a buffer
**And** I can create and save a new file using standard buffer commands.

### Story 1.2: Native Spring Boot Discovery
As a Java developer,
I want to discover Spring Beans and Endpoints using native Lua and Tree-sitter,
So that I can navigate my project without relying on the legacy tetravim-engine.

**Acceptance Criteria:**
**Given** I am in a Spring Boot project
**When** I trigger the endpoint picker
**Then** Tree-sitter parses the controller AST
**And** a Telescope picker displays the endpoints.

### Story 1.4: Event-Driven Headless Builds
As a backend engineer,
I want Maven/Gradle to build headlessly in the background,
So my editor does not freeze during compilation.

**Acceptance Criteria:**
**Given** a project with a failing build
**When** I trigger a build command
**Then** `vim.system` runs the build in the background
**And** automatically populates the quickfix list when done without blocking the UI.
