---
title: 'Enterprise Headless Setup & Telemetry'
type: 'feature'
created: '2026-09-03'
status: 'review'
baseline_commit: '2aed206f8ed62da7adafba63f2504eb244a203cd'
review_loop_iteration: 0
context: []
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** A DevOps engineer provisioning TetraVim in a cloud workspace (Codespaces, Coder, a CI image) has no non-interactive install path, no machine-readable health output to gate a compliance check on, and no way to collect notification/diagnostic history when troubleshooting an isolated corporate box where a screen-share is not an option.

**Approach:** Harden `scripts/headless-setup.sh` into a fully non-interactive provisioning run (plugin sync + Mason tool-chain + Tree-sitter parsers + a health snapshot), have the healthcheck emit a single JSON object via `tetravim.core.health.json()` / `:CheckHealthJson`, and route TetraVim's primary notifier (`tetravim.util.ui`) through `tetravim.util.notify` so an opt-in telemetry sink captures every subsystem notification to `telemetry.log`.

## Boundaries & Constraints

**Always:** Keep the headless run non-interactive and exit-code honest — hard-fail on a missing `nvim` or a failed plugin sync, warn-and-continue on a best-effort step (Mason, parsers). Telemetry is opt-in (`vim.g.tetravim_telemetry_enabled`), local-only (a file under `stdpath('config')`), and never blocks a notification. Keep the JSON shape stable and documented.

**Ask First:** Enabling telemetry by default. Sending telemetry anywhere off the box. Auto-running the headless setup on first launch.

**Never:** Open a UI, prompt, or require a TTY during the headless run. Emit telemetry when the flag is unset. Put anything in `telemetry.log` other than one JSON object per line.

</frozen-after-approval>

## Code Map

- `scripts/headless-setup.sh` -- 4-step non-interactive provisioning: `Lazy! sync` (hard-fail), `MasonToolsInstall` (warn-continue), Tree-sitter parser install (warn-continue), machine-readable health snapshot; exports `TETRAVIM_HEADLESS=1`
- `lua/tetravim/core/health.lua` -- `M.json()` + `:CheckHealthJson`; keys `neovim_version`, `lsp_clients`, `plugin_count`, `pending_async_tasks` (in-flight LSP requests across clients), `telemetry_enabled`
- `lua/tetravim/util/notify.lua` -- owns the default title/level vocabulary and the opt-in telemetry sink (`enable_telemetry`/`disable_telemetry`; one `vim.json.encode` line per notification to `stdpath('config')/telemetry.log`)
- `lua/tetravim/util/ui.lua` -- `M.notify` delegates to `util/notify` so all ~20 call sites are captured
- `lua/tetravim/core/options.lua` -- bridges `$TETRAVIM_HEADLESS` -> `vim.g.tetravim_headless`
- `lua/tetravim/health.lua` -- "TetraVim Headless Setup & Telemetry (Story 5.2)" section
- `.gitignore` -- ignores `telemetry.log`
- `lua/tetravim/tests/headless_spec.lua`, `scripts/validate-5.sh`

## Tasks & Acceptance

- [x] `scripts/headless-setup.sh` -- hardened non-interactive provisioning (`set -euo pipefail`, `TETRAVIM_HEADLESS=1`, hard-fail vs warn-continue steps), executable
- [x] `core/health.lua` -- `json()` emits a stable object; `:CheckHealthJson` prints it; fix the `joblist`/`get_active_clients` bugs so the output is valid JSON headless
- [x] `util/notify.lua` opt-in telemetry sink; `util/ui.lua` `M.notify` routed through it so every subsystem notification is captured
- [x] `core/options.lua` `$TETRAVIM_HEADLESS` -> `vim.g.tetravim_headless` bridge
- [x] health section + `.gitignore` entry + `headless_spec.lua` tests + `validate-5.sh` smoke test

**Acceptance Criteria:**
- Given a clean box with `nvim` on `$PATH`, when `scripts/headless-setup.sh` runs, then plugins/tools/parsers install with no prompt and it exits 0 (or non-zero only if `nvim` is missing or `Lazy! sync` fails).
- Given `:CheckHealthJson` (or the setup's step 4), when it runs headless, then it prints exactly one line of valid JSON carrying `neovim_version`, `lsp_clients`, `plugin_count`, `pending_async_tasks`, `telemetry_enabled`.
- Given `vim.g.tetravim_telemetry_enabled` is unset, when any `tetravim.util.ui` notification fires, then nothing is written to `telemetry.log`.
- Given telemetry is enabled, when a `tetravim.util.ui` notification fires, then one JSON line (`timestamp`, `level`, `msg`, `source`) is appended to `telemetry.log`.
- Given `$TETRAVIM_HEADLESS=1`, when `tetravim.core.options` loads, then `vim.g.tetravim_headless` is `true`.

## Verification

- `bash scripts/validate-5.sh` -- step 1 (headless_spec), step 2 (script + telemetry wiring shape), step 4 (telemetry capture of a `util.ui` notification), step 5 (health JSON + the Story 5.2 health section)
- `:checkhealth tetravim` -- "Headless Setup & Telemetry (Story 5.2)" section reports the script executable and the JSON valid
- Manual: run `scripts/headless-setup.sh` in a container with only `nvim` installed; confirm it provisions and the final line parses as JSON. Set `vim.g.tetravim_telemetry_enabled = true`, trigger a few notifications, inspect `telemetry.log`.
