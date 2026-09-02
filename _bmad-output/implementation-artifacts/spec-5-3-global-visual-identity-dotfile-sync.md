---
title: 'Story 5.3: Global Visual Identity & Dotfile Sync'
type: 'feature'
created: '2026-09-01'
status: 'done'
baseline_commit: '70e20519671bd1286694a81c9179c5b8d81a9a46'
review_loop_iteration: 0
context: []
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** The previous theme engine was tied to a deprecated Scala backend (`cumulus-engine`), leaving the theme module reliant on legacy logic that crashes or uses incomplete fallbacks. The environment lacks a unified visual identity synced with external `cumulus.dotfile` themes.

**Approach:** Implement a native Lua mechanism to extract and sync colors from an external dotfile source (e.g., JSON or Lua file in `~/.config/cumulus/theme/`), replacing all residual `cumulus-engine` usages in the theme modules (`theme/init.lua` and `theme_colors.lua`). Sync these updates automatically across UI primitives like Lualine and Telescope.

## Boundaries & Constraints

**Always:** Use native Neovim Lua APIs and standard JSON/Lua parsing for reading the external dotfile configuration.
**Ask First:** If the external dotfile format needs to change significantly from a simple key-value color palette.
**Never:** Re-introduce or rely on `cumulus.util.engine` for any theme operations.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Dotfile theme exists | `~/.config/cumulus/theme/palette.json` is present and valid | Parse colors and apply highlights | N/A |
| Dotfile missing | `palette.json` not found | Apply default `aws-theme` fallback | Log warning, use defaults |
| Invalid JSON | `palette.json` is malformed | Retain previous colors or apply fallback | Log error, use defaults |

</frozen-after-approval>

## Code Map

- `lua/cumulus/theme/init.lua` -- Remove fallback dummy highlights, implement parsing of dotfile palette and dynamic highlight generation.
- `lua/cumulus/util/theme_colors.lua` -- Refactor `init_theme_colors` to read directly from the new native palette instead of `cumulus.util.engine`.
- `lua/cumulus/plugins/ui-theme.lua` -- Update if necessary to ensure it plays well with the new native mechanism.

## Tasks & Acceptance

**Execution:**
- [x] `lua/cumulus/theme/init.lua` -- Refactor `load_provider_highlights` to load palettes natively from the dotfile.
- [x] `lua/cumulus/util/theme_colors.lua` -- Refactor `init_theme_colors` to rely on the native `theme/init.lua` logic instead of engine.
- [x] `lua/cumulus/theme/init.lua` -- Create fallback logic for when dotfile does not exist.

**Acceptance Criteria:**
- Given a valid color palette configuration in `cumulus.dotfile`, when the editor starts, then it applies the global visual identity across all UI primitives.
- Given no external theme configuration, when the theme is loaded, then it correctly falls back to a sensible default without errors.
- Given the codebase, when searching for `cumulus.util.engine`, then it is completely removed from all theme-related modules.

## Spec Change Log

## Verification
**Manual checks (if no CLI):**
- Create a test JSON color palette and ensure Neovim dynamically applies it.
- Delete the file and ensure Neovim falls back gracefully.

## Suggested Review Order

**Dotfile Theme Parsing**

- Replaces `manage_theme` call with native dotfile reading and parsing
  [`init.lua:67`](../../lua/cumulus/theme/init.lua#L67)

**Theme Colors Cache Updates**

- Modifies caching logic to read from loaded highlights instead of engine
  [`theme_colors.lua:37`](../../lua/cumulus/util/theme_colors.lua#L37)
