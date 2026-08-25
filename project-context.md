<!-- NOTE: This file is maintained for backwards compatibility with older skills. The true source of project context is AGENTS.md. -->
<!-- bmad:context -->
<!-- Verified 2026-08-25 against 76bae38. Managed by bmad-project-context; edits inside this block are replaced on refresh. Keep anything you want preserved outside the markers. -->

## cumulus.nvim

Enterprise-ready Neovim distribution for JVM backend engineering. Lua, Lazy.nvim, Mason. Migrating strictly to native Neovim LSPs and plugins instead of a custom backend.

## Policy

- Never add Scala or `sbt` code; the custom `cumulus-engine` is deprecated and removed. Delegate heavy lifting to standard community LSPs.

## Where things are

- Plugin specifications (Lazy): `lua/cumulus/plugins/`
- DevOps toolings & bridges: `lua/cumulus/util/devops.lua`

## Running and verifying

- Run `bash scripts/validate.sh` to perform headless validation of shell scripts, module loading, global keymaps, and DevOps utilities. 

## Conventions that differ from defaults

- DevOps keymaps (e.g., `<leader>ot` for Terraform, `<leader>oy` for Ansible) are registered globally, not buffer-scoped.

<!-- /bmad:context -->

## Commands

- Full smoke suite: `bash scripts/validate.sh`
- JVM debugger behavioral smoke test (fails the process on assertion failure via `cquit`, unlike `validate.sh`'s `+lua assert(...)` pattern which doesn't): `bash scripts/validate-dap-jvm.sh`
- Lightweight dev setup (just symlinks `~/.config/nvim` and syncs plugins, no dependency checks): `bash scripts/dev-init.sh`
- Format: `stylua lua/ ftplugin/ init.lua` (2-space indent, 120 col width — `stylua.toml`)
- Reload config after editing `lua/` in a running instance (it's symlinked, changes are live): `:source init.lua`
- Tests: busted-style specs in `lua/cumulus/tests/*_spec.lua`, run via `:PlenaryBustedDirectory lua/cumulus/tests/`. These deliberately avoid loading plugins that depend on lazy.nvim's FileType/keymap load events (plenary's harness doesn't fire those) — that behavioral coverage instead lives in `scripts/validate-*.sh`, each spawning its own fresh `nvim --headless` process. Follow this same split for new tests: static shape/registration in a `_spec.lua`, real-plugin-runtime behavior in a `validate-*.sh` script.
- No CI is configured (`.github/` has no workflows) — the validate scripts are the whole verification story; run them before calling work done.

## Architecture notes

- Load order: `init.lua` → `cumulus.core` (options, keymaps, autocmds, registers `:CumulusInstallEngine`) → `cumulus.core.lazy` (bootstraps lazy.nvim, loads every spec under `lua/cumulus/plugins/`).
- Correction to "Where things are" above: DevOps tooling lives at `lua/cumulus/core/devops.lua`, not `lua/cumulus/util/devops.lua`.
- `lua/cumulus/core/lang-keymaps.lua` registers **buffer-local**, FileType-scoped keymaps under `<leader>c` per language stack, so the which-key popup only shows e.g. Maven/Gradle commands while editing a matching buffer — this is the deliberate opposite of the global DevOps `<leader>o` keymaps noted above; don't "fix" the inconsistency without checking why (it's intentional, see the comment header in `lang-keymaps.lua`).
- `lua/cumulus/util/engine.lua` (~1500 lines) is still a load-bearing bridge to an externally built/installed `cumulus-engine` native binary (GraalVM native-image, located via `$PATH`, a local build, `~/.local/share/nvim/cumulus/bin/`, or `:CumulusInstallEngine`), despite the Policy above and the project's stated migration away from the custom Scala backend. The Scala source tree (`engine/`) really is gone — never resurrect it — but this Lua-side bridge is still actively called by `core/devops.lua`, `health.lua`, `session.lua`, `jvm.lua`, `theme/init.lua`, and others, and gracefully no-ops via `engine.is_available()`/`assert_available()` when the binary is missing. Don't assume `engine.lua` itself is dead code; `command grep -rln "cumulus.util.engine" lua` before touching it. Several thin wrapper stubs that used to front individual engine features were already purged (`rust.lua`, `beans.lua`, `endpoints.lua`, `import-optimizer.lua`, `k8s-validator.lua`, `migrations.lua`, `conflicts.lua`, `coverage.lua`, `log-indexer.lua`) — `validate.sh` asserts they stay gone, so don't reintroduce them. New heavy-lifting features should prefer native LSPs/Mason over new `engine.lua` surface.
- `core/devops.lua` root discovery has **no local fallback**: if `engine.discover_devops_roots()` finds nothing (or the engine binary is unavailable), the command notifies "No `<tool>` configuration found in workspace" and does not execute, rather than guessing a root. Preserve this contract when adding new DevOps commands.
- `.agents/`, `_bmad/`, `_bmad-output/` are BMAD agent-skill scaffolding used to plan/track this project's own development, not runtime code for the distribution itself. `_bmad-output/planning-artifacts/` holds the current migration plan, feature spec, and architecture spine — useful for roadmap context, but treat as planning intent rather than ground truth when it conflicts with the code (e.g. its "zero internal cache" architectural invariant is contradicted by `engine.lua`'s actual workspace-classification cache).
