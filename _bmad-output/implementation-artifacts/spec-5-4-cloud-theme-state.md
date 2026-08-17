---
title: 'Epic 5 Story 5.4: Cloud Theme State Management'
type: 'feature'
created: '2026-08-15'
status: 'done'
baseline_commit: '5ac10fe'
review_loop_iteration: 0
context: ['_bmad-output/implementation-artifacts/epic-5-context.md']
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Cloud theme persistence and state synchronization (`~/.config/cumulus/theme/state` and `~/.local/state/nvim/cumulus_theme`) currently perform direct Lua filesystem I/O and text manipulation, which should be absorbed into the Scala engine.

**Approach:** Implement `ThemeManager` in Scala 3 using `os-lib` and uPickle compile-time serialization, exposed via `cumulus-engine manage-theme --action <get|set> [--theme <name>] [--variant <variant>] [--file <path>]` CLI subcommand returning standardized `CumulusResponse[ThemeState]` envelopes.

## Boundaries & Constraints

**Always:**
- All subcommands must return standard `CumulusResponse[T]` envelope (`{ "success": Boolean, "data": Option[T], "error": Option[String], "error_code": Option[String] }`).
- Zero runtime reflection: use `uPickle` compile-time macros (`derives ReadWriter`).
- Stdout is strictly reserved for JSON payloads; all errors and diagnostics go to stderr or error envelope fields.
- All filesystem operations must strictly use `os-lib` (NFR7).
- `manage-theme --action get` reads the dotfiles state file (`~/.config/cumulus/theme/state`) or internal state file (`~/.local/state/nvim/cumulus_theme`), returning `ThemeState(theme: String, variant: Option[String])`. If files are missing, returns default theme (`"aws"`) gracefully with `success: true`.
- `manage-theme --action set --theme <name>` updates or creates the `KEY=VALUE` state file preserving existing keys (`FLAVOR`, `MODE`, `WALLPAPER`, etc.) and sets `NVIM_COLORSCHEME` / `FLAVOR`.

**Ask First:**
- N/A

**Never:**
- Never crash on missing directories or state files.
- Never write non-JSON text to stdout.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Get theme with existing dotfiles state | `~/.config/cumulus/theme/state` contains `NVIM_COLORSCHEME=azure-theme` and `MODE=dark` | `ThemeState(theme = "azure-theme", variant = Some("dark"))` | N/A |
| Get theme with missing state files | No state file exists | `ThemeState(theme = "aws", variant = Some("dark"))` with `success = true` | Return default theme |
| Set theme in dotfiles state | `manage-theme --action set --theme gcp-theme` | Updates `NVIM_COLORSCHEME` / `FLAVOR` in state file, preserving other keys | N/A |
| Set theme without specifying --theme | `manage-theme --action set` (missing `--theme`) | Error envelope with `INVALID_INPUT` error code | INVALID_INPUT |
| Unknown action | `manage-theme --action delete` | Error envelope with `INVALID_INPUT` error code | INVALID_INPUT |

</frozen-after-approval>

## Code Map

- `engine/src/main/scala/cumulus/devops/DevopsModels.scala` -- Add `ThemeState` case class deriving `ReadWriter`.
- `engine/src/main/scala/cumulus/theme/ThemeManager.scala` -- Implement `ThemeManager.getTheme(...)` and `ThemeManager.setTheme(...)` using `os-lib`.
- `engine/src/main/scala/cumulus/Main.scala` -- Wire `manage-theme` into CLI router.
- `engine/src/test/scala/cumulus/theme/ThemeManagerTest.scala` -- Unit tests for theme retrieval, default fallback, and KEY=VALUE state preservation.
- `engine/src/test/scala/cumulus/MainTest.scala` -- CLI integration tests for `manage-theme`.

## Tasks & Acceptance

**Execution:**
- [ ] `engine/src/main/scala/cumulus/devops/DevopsModels.scala` -- Add `case class ThemeState(theme: String, variant: Option[String] = Some("dark")) derives ReadWriter`.
- [ ] `engine/src/main/scala/cumulus/theme/ThemeManager.scala` -- Implement get/set theme operations with key-value preservation and default fallbacks.
- [ ] `engine/src/main/scala/cumulus/Main.scala` -- Wire `manage-theme` into CLI router.
- [ ] `engine/src/test/scala/cumulus/theme/ThemeManagerTest.scala` -- Test state retrieval, fallback defaults, and state update preservation.
- [ ] `engine/src/test/scala/cumulus/MainTest.scala` -- Add CLI tests for `manage-theme` get/set.

**Acceptance Criteria:**
- Given `manage-theme --action get`, the engine returns the current persisted cloud theme.
- Given `manage-theme --action set --theme <theme>`, the engine updates the state file.
- All test suites pass via `sbt test`.

## Spec Change Log

_None._

## Design Notes

- **Precedence Order for Get**:
  1. Custom file path passed via `--file` argument (if provided and exists)
  2. Dotfiles state file: `~/.config/cumulus/theme/state` (`NVIM_COLORSCHEME` or `FLAVOR` key)
  3. Internal Neovim state file: `~/.local/state/nvim/cumulus_theme`
  4. Default: theme `"aws"`, variant `"dark"`

## Verification

**Commands:**
- `sbt "testOnly cumulus.theme.ThemeManagerTest"` -- expected: ThemeManager unit tests pass.
- `sbt "testOnly cumulus.MainTest"` -- expected: CLI integration tests pass.
- `sbt test` -- expected: All unit and integration tests pass cleanly.

## Suggested Review Order

**Data Models**

- ThemeState model with compile-time uPickle macro derivation
  [`DevopsModels.scala:125`](../../engine/src/main/scala/cumulus/devops/DevopsModels.scala#L125)

**Theme Manager**

- Cloud theme state manager supporting get, set, dotfiles synchronization, and defaults
  [`ThemeManager.scala:11`](../../engine/src/main/scala/cumulus/theme/ThemeManager.scala#L11)

**CLI Subcommand Routing**

- CLI dispatch wiring for manage-theme with action and property options
  [`Main.scala:719`](../../engine/src/main/scala/cumulus/Main.scala#L719)

**Test Suites**

- Unit tests for ThemeManager state loading, key preservation, quotes, and comments
  [`ThemeManagerTest.scala:5`](../../engine/src/test/scala/cumulus/theme/ThemeManagerTest.scala#L5)

- CLI integration tests for manage-theme subcommand
  [`MainTest.scala:505`](../../engine/src/test/scala/cumulus/MainTest.scala#L505)
