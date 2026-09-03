# Epic 2 Context: Advanced Refactoring & Code Actions

<!-- Compiled from planning artifacts. Edit freely. Regenerate with compile-epic-context if planning docs change. -->

## Goal

This epic delivers project-wide, safe, and intelligent refactoring so a solo enterprise developer can make large architectural changes with the same confidence IntelliJ IDEA provides, without leaving Neovim. It covers LSP-backed cross-file rename and move (including keeping Spring bean wiring correct), interactive extraction of methods, variables, and interfaces, and — folded in from the backlog Story 2.3 — native Lua/Tree-sitter discovery of Spring Boot beans and REST endpoints that replaces the legacy Scala `tetravim-engine` for this feature area. All of it must run without ever blocking the editor UI. Planning coverage is good: the canonical epics file only enumerates Stories 2.1 and 2.2, so Story 2.3's scope is drawn from the older epic draft and the feature spec rather than a formal story writeup.

## Stories

- Story 2.1: Project-Wide Safe Rename & Move
- Story 2.2: Intelligent Extraction (Methods, Variables, Interfaces)
- Story 2.3: Native Spring Boot Discovery & Legacy Engine Deprecation

## Requirements & Constraints

- Rename of classes, methods, and packages, plus moving files, must update every reference, import, and reflection-style usage across the whole workspace. Renaming a public class updates all references project-wide.
- Rename must also correct Spring bean references declared in XML and in annotations; those references are validated with `spring-boot.nvim` plus Tree-sitter during the rename.
- Every refactoring shows a dry-run preview of all pending changes in a dedicated window before anything is written to disk.
- Extraction: extract a method from a visual selection with automatic resolution of parameters and return values; extract an interface from an existing concrete Java or Kotlin class; inline a variable or method.
- Spring discovery (Story 2.3): detect Spring Boot application roots; provide an interactive bean picker that can visualize dependency relationships; extract REST endpoints (`@GetMapping`/`@PostMapping`/`@PutMapping`, JAX-RS) by parsing controller ASTs and present them in a fuzzy picker that jumps straight to the mapping annotation.
- Discovery and any new parsing logic must be pure Lua + Tree-sitter or delegated to a standard LSP — no new work added to the legacy Scala engine, and no external Bash/Python parsing helpers. Story 2.3 also removes remaining reliance on the `engine.lua` facade for this area.
- Scope is Java and Kotlin only; Scala and `sbt` are explicitly out of scope even though the epic text mentions Metals.
- All LSP and background work is strictly asynchronous; the UI must never freeze during indexing, preview generation, or discovery.
- JDTLS heap is capped (~2GB) to prevent OOM; accept slower first-time indexing on large monorepos as the trade-off.

## Technical Decisions

- Strict native intelligence: new feature logic, AST parsing, and project discovery live in pure Lua with Tree-sitter, or are delegated to standard LSPs (`nvim-jdtls` for Java, `kotlin-language-server` for Kotlin). `spring-boot.nvim` is used and extended with custom Tree-sitter queries where it falls short.
- Stateless project context: keep no in-memory cache of workspace topology. Query project facts on demand from the file system (e.g. parse `pom.xml`) or synchronously from the LSP. Do not introduce global state caches or Neovim file-watchers.
- Event-driven UI: refactoring executors, LSP response handlers, and discovery routines emit Neovim autocommands; UI components listen and render asynchronously via `vim.schedule`. Never call render functions imperatively from a background task.
- Decentralized plugin orchestration: configuration is split by filetype/command under `lua/tetravim/plugins/` and lazy-loaded via `Lazy.nvim`. No monolithic LSP config file.
- Direct UI coupling: feature modules call `telescope.nvim` and `snacks.nvim` directly; do not build a `tetravim.ui` facade or adapter layer.
- Headless external tooling: any heavy shell-out (e.g. build/classpath checks triggered by a refactor) runs through `vim.system` with output piped to quickfix and notifications, not a `:terminal` split.
- The legacy Scala `tetravim-engine` is deprecated; Story 2.3 is the concrete step that retires it for Spring discovery.
- LSP binaries (JDTLS, Kotlin LS) are installed and managed via `mason.nvim` / `mason-lspconfig.nvim`.

## UX & Interaction Patterns

- Keymap hierarchy is mnemonic and `<leader>`-based: `<leader>c` for Code operations (rename, code action, format), `<leader>ca` for the code-action floating menu, `<leader>j*` for JVM operations — `<leader>jsb` bean picker, `<leader>jse` REST endpoint picker, `<leader>js` detect Spring Boot app.
- Rename, code actions, and pickers are ephemeral floating modals with rounded borders and at least one column/row of padding. Long output (build logs, large diffs) belongs in a bottom split, not a float; errors surface in quickfix lists.
- Pickers are centered floating Telescope/Snacks windows, fuzzy-searchable, with the preview pane on by default and syntax highlighting preserved. Endpoint flow: `<leader>jse` opens the picker, it parses all controller ASTs in the workspace, typing filters instantly, and `<enter>` moves the cursor onto the mapping annotation in the source file.
- Async feedback: show a statusline spinner or a Snacks notification while a refactor or scan runs; a toast reports success or failure and diagnostics/quickfix populate on completion. Success is quiet; errors are explicit and persist longer.
- Messages are concise and actionable — state what happened and how to fix it, using standard JVM terminology.

## Cross-Story Dependencies

- Stories 2.1 and 2.3 share Tree-sitter query infrastructure for Java/Kotlin Spring constructs: 2.1 needs it to validate bean references during rename, 2.3 builds the bean and endpoint discovery on top of it.
- Stories 2.1 and 2.2 share the same LSP code-action plumbing and the dry-run preview UI.
- The epic depends on the core Lua migration LSP setup (JDTLS and Kotlin LS installed via Mason, filetype-scoped loading) being in place from Epic 1.
- Story 2.3 completes deprecation of the legacy Scala engine for discovery; other epics assume native Spring discovery is available.
