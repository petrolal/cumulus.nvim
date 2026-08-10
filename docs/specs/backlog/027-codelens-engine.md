# Specification: SPEC-027 - Instant Java & Kotlin CodeLens Engine

## Metadata
- **Spec ID**: SPEC-027
- **Title**: Instant Java & Kotlin CodeLens Engine
- **Status**: BACKLOG
- **Author**: Antigravity Assistant & AI Systems Architect
- **Target Files/Paths**:
  - `crates/cumulus-core/src/codelens.rs` (new)
  - `crates/cumulus-core/src/main.rs` (extends)
  - `lua/cumulus/util/rust.lua` (extends)
  - `lua/cumulus/core/autocmds.lua` (extends)

---

## Goal & Intent
Scan Java/Kotlin source files on buffer open (`cumulus-core extract-codelens`) for `@Test` methods, `main` methods, and `@Scheduled` cron jobs, emitting native Neovim CodeLens items before JDTLS finishes warming up.

---

## Scope Boundaries

**In scope:**
- Fast regex/AST scanner in Rust for `@Test`, `public static void main`, `@Scheduled`, `@EventListener`.
- Output JSON array of `{ line, command, title, args }`.
- Display via `vim.lsp.codelens` or inline virtual text.

---

## Execution Checklist
- [ ] Implement `crates/cumulus-core/src/codelens.rs`
- [ ] Add `extract-codelens` subcommand in `main.rs`
- [ ] Add Rust unit tests
- [ ] Add Lua bridge binding in `lua/cumulus/util/rust.lua`
- [ ] Connect to `autocmds.lua` on `BufReadPost`
