<!-- bmad:context -->
<!-- Verified 2026-09-03 against c9e3d4e. Managed by bmad-project-context; edits inside this block are replaced on refresh. Keep anything you want preserved outside the markers. -->

## tetravim.nvim

Enterprise-ready Neovim distribution for JVM backend engineering. Lua, Lazy.nvim, Mason. Pure native Neovim — LSPs, Tree-sitter, Mason tools, and Lua utilities, with no custom backend.

## Policy

- Never add Scala or `sbt` code to this distribution. Delegate heavy lifting to standard community LSPs (`nvim-jdtls`, Kotlin LS, `nvim-metals` for user projects).
- There is no `tetravim-engine`, no Scala backend, no bridge, and no `:TetraVimInstallEngine`. All Spring Boot discovery (endpoints, beans, DAP) is native Lua + Tree-sitter. Never introduce an external engine.

## Where things are

- Plugin specifications (Lazy): `lua/tetravim/plugins/`
- DevOps toolings & bridges: `lua/tetravim/core/devops.lua`
- Language & JVM utilities: `lua/tetravim/util/jvm.lua`, `lua/tetravim/util/spring.lua`, `lua/tetravim/util/spring-picker.lua`
- Refactoring & Code actions: `lua/tetravim/util/refactor.lua`, `lua/tetravim/util/extract.lua`
- Database & HTTP explorers: `lua/tetravim/util/db.lua`, `lua/tetravim/util/http.lua`, `lua/tetravim/util/openapi.lua`
- Git & Forge integration: `lua/tetravim/util/git.lua`, `lua/tetravim/util/forge.lua`
- Theme & Visual Identity: `lua/tetravim/theme/tetris.lua`, `lua/tetravim/theme/init.lua`, `lua/tetravim/util/theme_colors.lua`

## Running and verifying

- Full smoke suite: `bash scripts/validate.sh`
- Component smoke tests: `bash scripts/validate-2-3.sh` (Spring), `scripts/validate-refactor.sh` (Rename/Move), `scripts/validate-extract.sh` (Extraction), `scripts/validate-db.sh` (Dadbod / DB), `scripts/validate-http.sh` (HTTP / REST), `scripts/validate-4-1.sh` (Git Conflicts), `scripts/validate-4-2.sh` (Forge Reviews), `scripts/validate-dap-jvm.sh` (JVM Debugger), `scripts/validate-test-coverage.sh` (Test Runner & Coverage)
- Busted unit specs: `nvim --headless -u init.lua -c "Lazy! load plenary.nvim" -c "PlenaryBustedDirectory lua/tetravim/tests/"`

## Conventions that differ from defaults

- DevOps keymaps under `<leader>o` (e.g., `<leader>ot` Terraform, `<leader>oc` CloudFormation, `<leader>oy` Ansible, `<leader>od` Docker, `<leader>ok` Helm/K8s) and their validation counterparts (`<leader>otV`, `<leader>ocC`, `<leader>oyV`, `<leader>odV`, `<leader>okV`) are registered globally, not buffer-scoped.
- Language keymaps under `<leader>c` and JVM refactoring (`<leader>cr`, `<leader>cm`, `<leader>cv`, `<leader>ci`) are registered buffer-locally per FileType (Java, Kotlin).
- Spring Boot discovery keymaps are under `<leader>js`: `<leader>jse` (Endpoints), `<leader>jsb` (Beans), `<leader>jsd` (Detect App); bare `<leader>js` is a which-key group prefix.
- Test runner and coverage keymaps are under `<leader>jt` (Tests) and `<leader>jc` (Coverage); global test runner keymaps are under `<leader>t`.
- Theme system is strictly standardized on the canonical "Tetris" palette; dynamic cloud switching (`<leader>ct`) and any external theme state are removed. The palette is pure Lua with no external dependency.

<!-- /bmad:context -->

## Commands

- Full smoke suite: `bash scripts/validate.sh`
- Component smoke suites: `bash scripts/validate-2-3.sh`, `bash scripts/validate-refactor.sh`, `bash scripts/validate-extract.sh`, `bash scripts/validate-db.sh`, `bash scripts/validate-http.sh`, `bash scripts/validate-4-1.sh`, `bash scripts/validate-4-2.sh`, `bash scripts/validate-dap-jvm.sh`, `bash scripts/validate-test-coverage.sh`
- Lightweight dev setup (just symlinks `~/.config/nvim` and syncs plugins, no dependency checks): `bash scripts/dev-init.sh`
- Format: `stylua lua/ ftplugin/ init.lua` (2-space indent, 120 col width — `stylua.toml`)
- Reload config after editing `lua/` in a running instance (it's symlinked, changes are live): `:source init.lua`
- Tests: busted-style specs in `lua/tetravim/tests/*_spec.lua`, run via `:PlenaryBustedDirectory lua/tetravim/tests/` (or headlessly via `nvim --headless -u init.lua -c "Lazy! load plenary.nvim" -c "PlenaryBustedDirectory lua/tetravim/tests/"`). These deliberately avoid loading plugins that depend on lazy.nvim's FileType/keymap load events (plenary's harness doesn't fire those) — that behavioral coverage instead lives in `scripts/validate-*.sh`, each spawning its own fresh `nvim --headless` process. Follow this same split for new tests: static shape/registration in a `_spec.lua`, real-plugin-runtime behavior in a `validate-*.sh` script.
- No CI is configured (`.github/` has no workflows) — the validate scripts are the whole verification story; run them before calling work done.

## Architecture notes

- Load order: `init.lua` → `tetravim.core` (options, keymaps, autocmds, diagnostics) → `tetravim.core.lazy` (bootstraps lazy.nvim, loads every spec under `lua/tetravim/plugins/`).
- DevOps tooling lives at `lua/tetravim/core/devops.lua`.
- `lua/tetravim/core/lang-keymaps.lua` registers **buffer-local**, FileType-scoped keymaps under `<leader>c` per language stack, so the which-key popup only shows e.g. Maven/Gradle commands while editing a matching buffer — this is the deliberate opposite of the global DevOps `<leader>o` keymaps noted above; don't "fix" the inconsistency without checking why (it's intentional, see the comment header in `lang-keymaps.lua`).
- There is no `lua/tetravim/util/engine.lua` and no `tetravim-engine` binary — the module and the Scala source tree (`engine/`) are permanently removed. Spring discovery is 100% native Lua/Tree-sitter (`lua/tetravim/util/spring.lua`, `lua/tetravim/util/spring-picker.lua`). The relocated helpers live at `lua/tetravim/util/notify.lua` (`vim.notify` wrappers) and `lua/tetravim/util/term.lua` (`run_term`). Code coverage is a self-contained native module (`lua/tetravim/util/coverage.lua`) — a pure-Lua JaCoCo XML parser plus a sign/virtual-text buffer overlay. Older wrapper stubs stay purged (`rust.lua`, `beans.lua`, `endpoints.lua`, `import-optimizer.lua`, `k8s-validator.lua`, `migrations.lua`, `conflicts.lua`, `log-indexer.lua`). Never introduce an external engine module.
- `core/devops.lua` root discovery is native `vim.fs`-based marker search (`resolve_search_dir` + `create_root_finder`, upward from the buffer/path, stopping at `$HOME`): if no marker matches, the command notifies "No `<tool>` configuration found in workspace" and does not execute, rather than guessing a root. Preserve this contract when adding new DevOps commands.
- `.agents/`, `_bmad/`, `_bmad-output/` are BMAD agent-skill scaffolding used to plan/track this project's own development, not runtime code for the distribution itself. `_bmad-output/planning-artifacts/` holds the current migration plan, feature spec, and architecture spine — useful for roadmap context, but treat as planning intent rather than ground truth when it conflicts with the code.

## BMAD Persona
TetraVim Copilot: An ultra-efficient assistant specialized in JVM development (Java/Kotlin, Spring Boot, Quarkus, Gradle) and modern Neovim Lua configurations.

## Toolchain Rule
Never alter the active toolchain during maintenance tasks.
