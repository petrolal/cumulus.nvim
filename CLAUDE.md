# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

`tetravim.nvim` is a Neovim **distribution** (not a plugin) targeting JVM backend
work (Java, Kotlin, Scala, Gradle/Maven) and cloud-native/DevOps development. It is
pure native Neovim — standard LSPs, Tree-sitter, Mason, `nvim-dap`, Lua utilities.
There is no companion backend/engine/bridge. Requires **Neovim ≥ 0.11** (uses
`vim.lsp.config`/`vim.lsp.enable`, `vim.diagnostic.jump`, `winborder`).

The repo is meant to be cloned to `~/.config/nvim` (or symlinked there for
development).

## Commands

```bash
# Full dependency install (Neovim, npm/go/python tools, Mason, Tree-sitter, scanners)
bash bootstrap.sh

# Local dev: symlink ~/.config/nvim -> repo, then sync plugins
bash scripts/dev-init.sh

# Non-interactive provisioning for CI / Codespaces / Coder (no TTY, exits 0 with a
# DEGRADED summary if best-effort steps fail)
bash scripts/headless-setup.sh

# Full smoke test (shell syntax, headless load, core modules, theme, plugins, DevOps suite)
bash scripts/validate.sh

# Component validation suites (each is a standalone headless assertion script)
bash scripts/validate-refactor.sh      # safe rename / move
bash scripts/validate-extract.sh       # method/variable/interface extraction
bash scripts/validate-db.sh            # dadbod DB explorer
bash scripts/validate-http.sh          # kulala HTTP client + OpenAPI
bash scripts/validate-dap-jvm.sh       # JVM DAP debugger
bash scripts/validate-devops.sh        # Terraform/CFN/Ansible root discovery
# ...see scripts/ for the rest

# Lua test suite (plenary busted)
nvim --headless -u init.lua -c "Lazy! load plenary.nvim" \
  -c "PlenaryBustedDirectory lua/tetravim/tests/" -c "qa"

# Single test file
nvim --headless -u init.lua -c "Lazy! load plenary.nvim" \
  -c "PlenaryBustedFile lua/tetravim/tests/theme_integration_spec.lua" -c "qa"

# Format Lua (stylua.toml: 2-space indent, 120 column)
stylua .

# In-editor health
nvim +"checkhealth tetravim"
```

## Architecture

### Load order

`init.lua` → `tetravim.util.notify` → `tetravim.core` (`core/init.lua` loads
`options`, `keymaps`, `autocmds`, `diagnostics`, `notify`, `health`) →
`tetravim.core.lazy` (bootstraps lazy.nvim, then `{ import = "tetravim.plugins" }`).

`core/lazy.lua` auto-imports **every** file in `lua/tetravim/plugins/`; each such
file returns a lazy.nvim spec (single spec table or a list of them). `defaults.lazy
= false` — plugins load eagerly unless a spec opts into `event`/`ft`/`keys`.

### Directory map

| Path | Role |
| --- | --- |
| `lua/tetravim/core/` | Editor bootstrap: options, global keymaps, autocmds, diagnostics, health JSON, devops keymap engine, `lang-keymaps` |
| `lua/tetravim/plugins/` | One lazy.nvim spec file per concern. Prefixes: `lsp-*`, `tools-*`, `editor-*`, `ui-*`, `cloud-*`, `core-*` |
| `lua/tetravim/util/` | Pure Lua logic modules (`jvm`, `spring`, `refactor`, `extract`, `db`, `http`, `grpc`, `cve`, `sonar`, `forge`, `lsp_async`, `lsp_resilience`, `format`, `git`, `maven`, `gradle`, …). Keymaps call into these; business logic lives here, not in the keymap files |
| `lua/tetravim/theme/` | `tetris.lua` = canonical palette + highlight table; `init.lua` = loader/persistence shim |
| `colors/tetravim.lua` | `:colorscheme tetravim` entry point |
| `lua/tetravim/tests/` | `*_spec.lua` plenary busted specs |
| `ftplugin/*.lua` | Per-filetype auto-launchers (notably `java.lua` starting `nvim-jdtls`) |
| `scripts/` | Bootstrap / headless / `validate-*.sh` scripts |
| `docs/README.md` | Stale — references removed `_bmad-output/*` planning artifacts that no longer exist |

### Keymap system

There are four registration channels, deliberately layered so `<leader>` groups
only show keys relevant to the current buffer:

1. **Global** — `core/keymaps.lua` (`<leader>c` code/LSP, `<leader>w` windows,
   `<leader>H` HTTP, `<leader>G` gRPC, `<leader>x` quality/security, file ops).
2. **JVM platform** — `<leader>j`, registered unconditionally via
   `require("tetravim.util.jvm").setup_keymaps()`.
3. **DevOps/infra** — `<leader>o`, registered globally via
   `require("tetravim.core.devops").setup_keymaps()`; which-key groups come from
   `devops.whichkey_spec()`.
4. **Language-scoped** — `core/lang-keymaps.lua`. Each language stack calls
   `M.register{ filetypes=…, group=…, keys=… }`; a `FileType` autocmd installs the
   keys **buffer-local** only for matching filetypes, so `<leader>c` never mixes
   e.g. Maven keys into a Terraform buffer. Java/Kotlin build stacks are gated
   behind `util/build-sync-state` until the first Maven/Gradle dependency sync
   completes.

### LSP

`plugins/lsp-core.lua` collects `opts.servers` contributed by every `lsp-*.lua` /
`cloud-*.lua` spec and enables each with `vim.lsp.config()` + `vim.lsp.enable()`.
Java is special-cased through `nvim-jdtls` in `ftplugin/java.lua` (bundles
java-debug/java-test, Spring DAP, workspace under `stdpath("cache")/jdtls/`); Scala
uses `nvim-metals`; Kotlin uses `kotlin_language_server`.

Resilience layer:
- `util/lsp_resilience` — bounds the JDTLS JVM heap (`apply_memory_limit`) and
  auto-restarts a crashed server (max 3 restarts / 180s, then stops and points at
  `:LspLog`). `on_attach` calls `reset()` to open a fresh window.
- `util/lsp_async.request_all_async` — fans a request out to all attached clients
  and calls back on `vim.schedule` after the last reply, so project-wide operations
  (e.g. safe-rename reference scan) never block the UI thread.

### Theme

Single canonical palette. `theme/tetris.lua` holds the hex values
(`bg #111216`, `cyan #00F0F0`, `purple #A000F0`, …) and the highlight table;
`theme/init.lua` is a thin loader (`apply()` / `load_saved_theme()` /
`setup()` shim) invoked from `core/options.lua` on startup. A previous
multi-provider "cloud theme switcher" was removed — do not reintroduce provider
palette tables.

### Health & headless

- `:checkhealth tetravim` → `lua/tetravim/health.lua` (per-feature dependency
  probes).
- `:CheckHealthJson` / `require("tetravim.core.health").json()` → one-line JSON
  (`neovim_version`, `lsp_clients`, `plugin_count`, `pending_async_tasks`,
  `telemetry_enabled`) for CI gating.
- `TETRAVIM_HEADLESS=1` → `vim.g.tetravim_headless` (bridged in `core/options.lua`).
- Telemetry is opt-in and local-only: `:TetraVimTelemetryEnable` appends JSON lines
  to `telemetry.log` (git-ignored, rolls at ~1 MiB) for notifications routed
  through `tetravim.util.ui` → `tetravim.util.notify`.

## Conventions

- `<leader>` is Space, `<localleader>` is `\`.
- Comments frequently cite `Story X.Y` / `SPEC-N.M` tags — these are historical
  planning references; the BMAD planning tree they came from has been removed. Do
  not treat missing `_bmad*` paths as a bug.
- Helper output (HTTP/gRPC responses, generated templates) always renders in a
  persistent split via the shared `tetravim_http_open_in_split` helper, never a
  floating window.
- New user-facing logic: put the implementation in a `util/` module and keep the
  keymap file a thin dispatcher; guard every optional binary/plugin with a
  `pcall`/`executable` check that degrades to a single `ui.notify_*` call.
- Every feature that touches an external tool should add a probe to
  `lua/tetravim/health.lua`.
