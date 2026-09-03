---
status: final
updated: 2026-08-25
---

# Architecture Spine: TetraVim.nvim

**Paradigm:** Event-Driven, Stateless UI Architecture  
**Primary Driver:** Absolute editor stability, native Neovim standards, and zero-blocking UI constraints during heavy JVM/Cloud operations.

## Architectural Decisions (Invariants)

### AD-01: Event-Driven UI Paradigm
- **Rule:** Core logic, external executors, and DevOps bridges must emit Neovim autocommands (`vim.api.nvim_exec_autocmds`) rather than directly calling UI render functions. UI components listen for these events and schedule their renders asynchronously (`vim.schedule`).
- **Binds:** All background operations, build runners, and LSP response handlers.
- **Prevents:** Direct imperative UI updates from background tasks that could freeze the editor thread.

### AD-02: Decentralized Plugin Orchestration
- **Rule:** Plugin configurations must be isolated by filetype or specific command via `Lazy.nvim`. There is no global "LSP monolith" file.
- **Binds:** All files under `lua/tetravim/plugins/`.
- **Prevents:** Centralized monolithic configs and global startup memory bloat when editing non-JVM files.

### AD-03: Headless External Tooling
- **Rule:** Heavy external commands (Maven/Gradle builds, project generation, DevOps scripts) must run headlessly via `vim.system`. Results and errors are captured and piped to native Neovim UI elements (Quickfix, notifications).
- **Binds:** The core external command executor utility.
- **Prevents:** Relying on raw `:terminal` splits for standard tool execution.

### AD-04: Stateless Project Context
- **Rule:** TetraVim maintains zero internal cache of the workspace topology. Project context (dependencies, main classes, project type) is queried on the fly from the file system (e.g., parsing `pom.xml`) or synchronously from the LSP.
- **Binds:** Any feature or utility requiring project knowledge.
- **Prevents:** Global in-memory state caches, desync bugs, and the use of flaky Neovim file-watchers.

### AD-05: Buffer-Based Explorer Workflows
- **Rule:** Directory navigation and file management must be handled via `oil.nvim`, treating the file system as standard Neovim buffers.
- **Binds:** File management and project exploration features.
- **Prevents:** Stateful, traditional sidebar abstraction layers (e.g., `neo-tree`) that deviate from standard Neovim mode interactions.

### AD-06: Direct UI Coupling
- **Rule:** Feature modules should call UI primitives (`snacks.nvim`, `telescope.nvim`) directly.
- **Binds:** Any feature presenting data to the user.
- **Prevents:** Over-engineered facade or adapter layers (`tetravim.ui`) intended to "future-proof" UI framework swaps.

### AD-07: Strict Native Intelligence
- **Rule:** New feature logic, AST parsing, and project discovery must be implemented in pure Lua (leveraging Tree-sitter) or delegated to standard LSPs.
- **Binds:** All new feature development (e.g., Spring endpoint discovery).
- **Prevents:** Introducing any external backend, engine, or bridge (there is no `tetravim-engine`), or external Bash/Python parsing scripts.

## Structural Seed

```mermaid
graph TD
    subgraph UI Layer
        UI[Native UI / Quickfix / Snacks / Telescope]
        Oil[Oil.nvim Buffers]
    end

    subgraph Core
        EH[Event Hub / Autocmds]
        Exec[Headless Executor - vim.system]
        Lua[Pure Lua / Tree-sitter Logic]
    end

    subgraph Plugins
        Lazy[Lazy.nvim Orchestrator]
        LSP[LSPs: JDTLS / Metals]
    end

    subgraph File System
        Disk[(Workspace / pom.xml)]
    end

    Exec -- Emits Event --> EH
    Lua -- Emits Event --> EH
    EH -- Schedules Render --> UI
    LSP -- Queries Truth --> Disk
    Lua -- Parses AST --> Disk
    Exec -- Executes --> Disk
    Oil -- Mutates --> Disk
    Lazy -. Lazy loads on Filetype .-> LSP
```

## Deferred Decisions
- _(none open)_

## Resolved Decisions
- **Legacy Engine Cleanup (resolved 2026-09-03):** `engine.lua` and every reference to the `tetravim-engine` binary have been removed. The genuinely-native helpers were relocated (`util/notify.lua`, `util/term.lua`, native `vim.fs` root discovery in `core/devops.lua`); binary-only features with no native path were dropped. AD-07 stands going forward.
