# CLAUDE.md — Cumulus Neovim Distribution

## Project Overview
Cumulus is a from-scratch Neovim configuration (`init.lua` → `lua/cumulus/core` → `lua/cumulus/core/lazy`) that replicates IntelliJ IDEA Ultimate's developer experience for three domains, entirely through lazy.nvim plugin specs under `lua/cumulus/plugins/`:

1. **JVM & Polyglot stack** — Java (`jdtls`), Kotlin (`kotlin-language-server`), Groovy (`groovyls`), plus TOML (`taplo`) and HTML (`superhtml`).
2. **Web/Markup (HTML/XML)** — HTML via `superhtml` (`lsp-html.lua`), XML/JSON/Bash via `lemminx`/`jsonls`/`bashls` (`lsp-devops.lua`, frozen — see below).
3. **Frozen DevOps/Cloud** — Terraform, Kubernetes/Helm/Docker, CloudFormation/Ansible (`cloud-*.lua`), remote debugging (`tools-dap-devops.lua`).

Every server registered anywhere attaches automatically through a single generic loop in `lua/cumulus/plugins/lsp-core.lua`; there is no per-language attach wiring to duplicate. Global code keymaps (`<leader>cf` format, `<leader>ca` code action, `<leader>cr` rename, `[d`/`]d`/`<leader>cd` diagnostics) live once in `lua/cumulus/core/keymaps.lua` and apply to every buffer with an attached LSP client — do not re-add them per filetype. Extra per-language build/lint commands (Maven, Gradle, Terraform, Ansible, Docker, Helm) are registered as buffer-local `lang_keymaps` stacks in that same file, gated by filetype and/or a project-file condition (see `lua/cumulus/core/lang-keymaps.lua`).

Startup budget: **under 50ms total**, verified via `nvim --headless --startuptime`. All plugins must lazy-load on real triggers (`event`, `ft`, `cmd`, `keys`) — nothing eager except `mason.nvim`/`mason-tool-installer.nvim` (their install trigger is a one-shot `VimEnter` autocmd, so they must load eagerly to fire it) and `nvim-lspconfig`/`nvim-lint` (event-gated on `BufReadPre`/`BufNewFile`).

---

## Mandatory Constraints

### 1. Zero Free Files Policy
Every configuration script, LSP handler, plugin spec, formatter/linter rule, or keymap MUST live inside a real Neovim module under `lua/cumulus/` or a filetype handler under `ftplugin/`. No loose `.lua`, `.sh`, or unmanaged config at the project root.

Sanctioned root-level files (do not treat as violations): `init.lua`, `CLAUDE.md`, `.gitignore`, `.neoconf.json`, `stylua.toml`, `lazy-lock.json`, `LICENSE`. The one sanctioned non-root exception is `scripts/validate.sh`, the project's canonical headless verification entrypoint — don't add siblings to it without explicit instruction.

### 2. Immutable DevOps Guardrail (CRITICAL)
The following files and paths are **STRICTLY FROZEN** — never modify, rename, relocate, or delete them, and never re-implement functionality they already own elsewhere:
- `lua/cumulus/plugins/cloud-terraform.lua`
- `lua/cumulus/plugins/cloud-containers-k8s.lua`
- `lua/cumulus/plugins/cloud-cloudformation-ansible.lua`
- `lua/cumulus/plugins/lsp-devops.lua` (owns the `json`/`xml`/`bash` Treesitter parsers and `jsonls`/`lemminx`/`bashls` LSP servers — treat XML support here as already complete and read-only for any HTML/XML spec work)
- `lua/cumulus/plugins/tools-dap-devops.lua`
- Any `.devcontainer/`, `Dockerfile`, or Kubernetes/Terraform config files, should they be introduced

Before touching any file, `grep` these paths for the capability you're about to add — lazy.nvim merges `opts` tables across every spec targeting the same plugin, so it's easy to accidentally duplicate a server/parser/formatter that a frozen file already registers.

### 3. Lightweight SDD Alignment
Specifications live under a fixed layout:
- `docs/spec_template.md` — the reusable template (metadata block, prerequisite analysis, guardrails, execution checklist, verification commands).
- `docs/specs/active/NNN-slug.md` — in-progress work. A spec stays here until every acceptance checkbox is verified.
- `docs/specs/completed/NNN-slug.md` — archived, finished work. A given spec ID belongs in exactly one of these two directories, never both.

Code changes should be checked against whatever is currently in `docs/specs/active/` — undocumented features or specs whose acceptance criteria don't match the code are SDD drift.

### 4. Neovim Lua & Idiomatic Standards
- Use native APIs: `vim.api.nvim_*`, `vim.keymap.set`, `vim.diagnostic.*`, `vim.lsp.*` — avoid legacy `vim.cmd("...")` Vimscript strings where a Lua API exists.
- Every `lazy.nvim` plugin spec needs a real lazy-loading trigger (`event`, `ft`, `cmd`, or `keys`) unless it has a documented reason to load eagerly (see startup budget note above).
- No global (`_G`) leakage; every module returns an explicit table.
- Keymaps should include `desc` for which-key discoverability.

---

## Command Cheat Sheet
- **`/gen-sdd`** — generate/refresh the lightweight SDD template and spec files (this file included) from the current repo state.
- **`/check-project`** — run a compliance audit (Zero Free Files, DevOps freeze, SDD directory structure, Lua/lazy-loading standards) and report PASS/FAIL per rule.
- **`/review`** — code-review a diff or path against the same five non-negotiable rules, grouped into Blockers / Warnings / SDD Drift / Passes.

---

## Verification
Run before considering any change complete:
```bash
bash scripts/validate.sh
nvim --headless "+Lazy! sync" +qa
nvim --headless "+checkhealth cumulus" +qa
nvim --headless --startuptime /tmp/nvim-startuptime.log +qa && grep "TOTAL" /tmp/nvim-startuptime.log
git status --short lua/cumulus/plugins/cloud-* lua/cumulus/plugins/lsp-devops.lua lua/cumulus/plugins/tools-dap-devops.lua
```
The last command must return no output — any diff there is a guardrail violation.
