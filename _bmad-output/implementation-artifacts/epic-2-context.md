# Epic 2 Context: Advanced Refactoring & Code Actions

<!-- Compiled from planning artifacts. Edit freely. Regenerate with compile-epic-context if planning docs change. -->

## Goal

Provide project-wide, safe, and intelligent refactoring tools that give developers confidence to make large-scale architectural changes. This is the "maintenance parity" epic against IntelliJ IDEA: renaming, moving, and extracting code must be as trustworthy in Neovim as in a full Java IDE, so a solo enterprise backend developer never has to fall back to a heavier tool just to restructure code safely.

## Stories

- Story 2.1: Project-Wide Safe Rename & Move
- Story 2.2: Intelligent Extraction (Methods, Variables, Interfaces)

## Requirements & Constraints

- Refactoring must be project-wide and safe: renaming a public class/method/package must update all references and imports across the workspace, not just the local buffer.
- Renames must account for non-code references that LSP alone won't catch — specifically Spring XML/annotation-based bean references (reflection-based usages), which need Tree-sitter-assisted validation alongside the language server.
- Changes must be previewable before being applied (dry-run), giving the developer a chance to review the full diff of a refactor across files prior to commit.
- All LSP-backed refactor operations must be asynchronous and must never block or freeze the editor, consistent with the project's zero-tolerance-for-freezing stability goal.
- Language scope is Java and Kotlin; Scala/`sbt` is explicitly out of scope for this product. Note: the epic's acceptance criteria mention Metals (a Scala LSP) alongside JDTLS for cross-file rename — treat JDTLS (Java) and the Kotlin language server as the primary refactor engines, and confirm Metals' role before building on it, since it conflicts with the stated Java/Kotlin-only scope.
- Heavy LSP servers (e.g., JDTLS) are subject to enforced memory limits (capped heap) — refactor tooling should not assume unbounded LSP capacity on large monorepos.

## Technical Decisions

- Refactor logic must not introduce a centralized "LSP monolith" — plugin/config wiring stays isolated per filetype/command under `lua/cumulus/plugins/`, lazy-loaded on filetype.
- No internal cache of workspace topology: project/reference state for a refactor must be queried live from disk (e.g., `pom.xml`) or synchronously from the LSP at the moment of the operation — no stale in-memory index.
- Background/refactor operations should emit autocommands rather than call UI render functions directly; UI listens and renders via `vim.schedule` to avoid blocking the editor thread.
- UI for refactor results should call primitives directly (`snacks.nvim`, `telescope.nvim`) — no adapter/facade layer is to be introduced for "future-proofing."
- New refactor/extraction logic (AST analysis, reference discovery) must be implemented in pure Lua via Tree-sitter or delegated to the standard LSPs (JDTLS/Kotlin LS) — no new surface area on the legacy Scala `cumulus-engine` and no external Bash/Python parsing scripts.
- Moving files as part of a refactor should compose with the project's buffer-based file management approach (`oil.nvim`) rather than a custom mover.

## UX & Interaction Patterns

- Refactor actions live under the `<leader>c` (Code) keymap group: Rename, Code Action, Format.
- `<leader>ca` opens a floating "Code Action" menu listing available refactorings/fixes; renaming itself is also presented in a floating window per the project's modal conventions.
- Floating windows use rounded borders with at least 1 column/row of padding, consistent with other ephemeral pickers (Spring Bean/endpoint pickers).
- Preview panes are on by default — the dry-run diff preview for a refactor should follow this same expectation of always-visible context before the user commits.
- Floating windows are reserved for short, task-focused interactions; a refactor preview that spans many files/long output should use a bottom-drawer split rather than a floating window, matching the pattern used for build logs and test runners.
- Success on a refactor should be a quiet confirmation (statusline flash/toast); failures (e.g., a rename that can't be safely resolved) must surface as explicit, visible errors.

## Cross-Story Dependencies

- Story 2.2 (Extract Method/Variable/Interface, Inline) builds on the same LSP foundation (JDTLS/Kotlin LS) and dry-run preview pattern established for rename/move in Story 2.1 — the preview UI and reference-resolution approach should be shared rather than reimplemented.
- Both stories depend on JDTLS, Metals, and Kotlin LS being installed and managed via Mason (established as part of environment/tooling setup outside this epic).
