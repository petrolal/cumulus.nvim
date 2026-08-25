---
title: 'Story 1.1: Stateless File Management (oil.nvim)'
type: 'feature'
created: '2026-08-25'
status: 'done'
review_loop_iteration: 1
context: []
baseline_commit: '2d5be6b5093b60e568bfed3b8589195f68d10b5c'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** The current setup uses a traditional file explorer (Snacks.explorer), which relies on a specialized widget rather than standard buffer semantics, disrupting the keyboard-driven flow.

**Approach:** Introduce `stevearc/oil.nvim` as the primary file manager, mapping it to `<leader>e` instead of the Snacks explorer, enabling users to create, delete, rename, and manage files using standard Neovim buffer commands (like `dd`, `cw`, `:w`).

## Boundaries & Constraints

**Always:** Ensure that `oil.nvim` is lazy-loaded (e.g. loaded on `cmd = "Oil"` or the `<leader>e` keymap) to keep startup time fast.
**Ask First:** If any other plugins conflict with the `<leader>e` keymap or oil's standard mappings, ask before overriding them.
**Never:** Do not install or configure any complex sidebars (like `neo-tree` or `nvim-tree`); this must be a pure buffer-based experience.

</frozen-after-approval>

## Code Map

- `lua/cumulus/plugins/editor-snacks.lua` -- Contains the current `<leader>e` mapping which must be removed.
- `lua/cumulus/plugins/editor-oil.lua` (New File) -- Will contain the `stevearc/oil.nvim` plugin specification and keymaps.

## Tasks & Acceptance

**Execution:**
- [x] `lua/cumulus/plugins/editor-snacks.lua` -- Remove the `<leader>e` keymap bound to `Snacks.explorer()` in the `keys` table, and remove any leftover `explorer` configuration from the `opts` block.
- [x] `lua/cumulus/plugins/editor-oil.lua` -- Add a Lazy specification for `stevearc/oil.nvim` that returns the table directly without unnecessary nesting. Map `<leader>e` to open Oil (`<cmd>Oil<cr>`) with description "File Explorer".
- [x] `lua/cumulus/plugins/editor-oil.lua` -- Configure setup options: `default_file_explorer = true`, `delete_to_trash = true`, and `view_options.show_hidden = false`.
- [x] `lua/cumulus/plugins/editor-oil.lua` -- To support directory opening (`nvim .`) while respecting the lazy-loading constraint, add an `init` hook in the Lazy spec that eagerly loads `oil.nvim` only if the first argument is a directory.
- [x] `scripts/validate.sh` -- Add a verification step to ensure `<leader>e` is mapped to `<cmd>Oil<cr>` and `oil` module is available.

**Acceptance Criteria:**
- Given I am in a Neovim buffer, when I press `<leader>e`, then the current directory is opened in an `oil.nvim` buffer.
- Given `oil.nvim` is open, when I make buffer edits and save (`:w`), then the file system reflects those changes.

## Spec Change Log

- **Finding:** `oil.nvim` broke `nvim .` due to lazy loading; missing `delete_to_trash = true`; leftover Snacks config; unverified file explorer keymap.
- **Amended:** Added task to load `oil.nvim` conditionally on startup if a directory is passed. Added `delete_to_trash = true`. Added task to clean up Snacks config. Added task to verify keymap in `validate.sh`.
- **Known-bad state avoided:** `nvim .` crashing or loading netrw; permanent file deletion; broken tests passing.
- **KEEP:** The basic separation of `editor-oil.lua` and the removal of the Snacks keymap worked well and should be preserved.

## Verification

**Commands:**
- `bash scripts/validate.sh` -- expected: Clean exit, ensuring the new plugin specification does not break module loading or headless validation, and correctly verifies the new keymap.

## Suggested Review Order

**Core Feature: Stateless File Management**

- Configure stevearc/oil.nvim as the primary file manager, mapping to `<leader>e` and lazy-loading effectively.
  [`editor-oil.lua:1`](../../lua/cumulus/plugins/editor-oil.lua#L1)

- Remove the legacy Snacks file explorer keymap.
  [`editor-snacks.lua:159`](../../lua/cumulus/plugins/editor-snacks.lua#L159)

**Verification**

- Add headless verification step for oil.nvim and the `<leader>e` keymap.
  [`validate.sh:37`](../../scripts/validate.sh#L37)
