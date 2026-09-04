---
title: 'SonarQube & SonarLint Integration'
type: 'feature'
created: '2026-09-03'
status: 'review'
baseline_commit: '2aed206f8ed62da7adafba63f2504eb244a203cd'
review_loop_iteration: 0
context: []
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** JVM developers rely on SonarQube for code-quality gates but only see rule violations in CI or the SonarQube web UI, long after the code is written. There is no in-editor feedback for Sonar rules on Java/Kotlin/Scala.

**Approach:** Attach the SonarLint language server (Mason package `sonarlint-language-server`, with its bundled analyzer jars) to Java/Kotlin/Scala buffers via the `sonarlint.nvim` plugin. Surface Sonar rule violations as native LSP diagnostics, expose rule descriptions on demand, and honour a project's `sonar-project.properties` (quality-profile / project-key binding) when present.

## Boundaries & Constraints

**Always:** Degrade gracefully when the plugin or language server is absent (notify + no-op, never error). Reuse the existing `tetravim.util.ui` notifier, health module, which-key and Mason conventions. Keep parser logic pure and unit-tested.

**Ask First:** Adding SonarQube *connected mode* (server URL + token storage) — out of scope for this story; only local analyzers + a static properties file are wired.

**Never:** Bundle or vendor Sonar analyzer jars in the repo. Block buffer load on the language server. Reimplement Sonar rule logic in Lua.

</frozen-after-approval>

## Code Map

- `lua/tetravim/util/sonar.lua` -- pure helpers: `FILETYPES`, `settings_from_properties`, `project_key`, `find_project_settings`, `analyzer_paths`, `language_server_cmd`, `has_language_server`
- `lua/tetravim/plugins/lsp-sonarlint.lua` -- `sonarlint.nvim` plugin spec; pcall-guarded `require('sonarlint').setup`
- `lua/tetravim/core/keymaps.lua` -- `<leader>xr` (rule description), `<leader>xd` (line diagnostics)
- `lua/tetravim/plugins/ui-whichkey.lua` -- `<leader>x` "quality/security" group
- `lua/tetravim/plugins/tools-mason.lua` -- `sonarlint-language-server` in `ensure_installed`
- `lua/tetravim/health.lua` -- "Code Quality & Security -- SonarLint (Story 6.1)" section
- `bootstrap.sh` -- picked up via `:MasonToolsInstall`
- `lua/tetravim/tests/sonar_spec.lua`, `scripts/validate-6.sh`

## Tasks & Acceptance

- [x] `util/sonar.lua` -- properties parser + LS command builder, pure & guarded
- [x] `plugins/lsp-sonarlint.lua` -- attach LS to java/kotlin/scala, bind project settings when a properties file exists
- [x] `core/keymaps.lua` -- `<leader>xr` / `<leader>xd`
- [x] which-key group, Mason entry, health section
- [x] `sonar_spec.lua` unit tests + `validate-6.sh` smoke test

**Acceptance Criteria:**
- Given a Java buffer in a project with `sonarlint-language-server` installed, when it opens, then SonarLint attaches and rule violations appear as diagnostics.
- Given the cursor on a Sonar diagnostic, when `<leader>xr` is pressed, then the rule description is shown.
- Given a `sonar-project.properties` with `sonar.projectKey`, when SonarLint starts, then that key is passed through in the server settings.
- Given the plugin or language server is missing, when a Java buffer opens, then a single warning notifies and the editor is otherwise unaffected.
- Scala: rules require SonarQube connected mode (no free standalone analyzer) — health reports this as info.

## Verification

- `bash scripts/validate-6.sh` -- steps 1, 2, 5 cover the parser, wiring and health section
- `:checkhealth tetravim` -- "SonarLint (Story 6.1)" section reports LS + analyzer-jar status
- Manual: open a Java file with a known code smell, confirm a Sonar diagnostic and `<leader>xr` description
