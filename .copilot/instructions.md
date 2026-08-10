# Copilot Instructions — Cumulus Neovim Distribution

## Project Overview
Cumulus is a Neovim configuration (`init.lua` → `lua/cumulus/core` → `lua/cumulus/core/lazy`) providing an IntelliJ IDEA Ultimate developer experience for JVM, Web/Markup, and DevOps domains, backed by a high-performance compiled Rust native engine (`crates/cumulus-core`).

## Architecture Directive: Rust-First Engine + Minimal Lua Bridge
- **Rust-First Rule**: All new business logic, text processing, XML/TOML/YAML AST parsing, file scanning, network checks, static analysis, log parsing, and backlog task implementations MUST be written in Rust inside `crates/cumulus-core/`.
- **Lua Layer (`lua/cumulus/`)**: Serves strictly as minimal editor glue (autocmd hooks, keymap registration, `lazy.nvim` specs, UI/diagnostic rendering, `vim.ui.select` pickers).
- **Glue/IPC (`lua/cumulus/util/rust.lua`)**: Executes `cumulus-core` subcommands via `vim.system`, decoding stdout JSON streams into Lua tables. Automatic Lua fallback routines execute if `cumulus-core` is absent.

## Mandatory Constraints
1. **Zero Free Files Policy**: Every configuration script, LSP handler, plugin spec, or keymap MUST live under `lua/cumulus/` or `ftplugin/`. Native Rust code MUST reside in `crates/cumulus-core/`.
2. **Immutable DevOps Guardrail**: The following paths are STRICTLY FROZEN — never edit, rename, or relocate:
   - `lua/cumulus/plugins/cloud-terraform.lua`
   - `lua/cumulus/plugins/cloud-containers-k8s.lua`
   - `lua/cumulus/plugins/cloud-cloudformation-ansible.lua`
   - `lua/cumulus/plugins/lsp-devops.lua`
   - `lua/cumulus/plugins/tools-dap-devops.lua`
3. **Startup Budget**: Total startup latency under 50ms (achieved ~25.8ms). All plugins must lazy-load on real triggers.

## Verification
Run before considering any task complete:
```bash
bash scripts/validate.sh && cargo test --manifest-path crates/cumulus-core/Cargo.toml
```
