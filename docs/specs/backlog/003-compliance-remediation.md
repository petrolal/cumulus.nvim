# Specification: SPEC-003 - Compliance Remediation (Lazy-Loading, DB Client Parity, SPEC-001 Drift)

## Metadata
- **Spec ID**: SPEC-003
- **Title**: Compliance Remediation (Lazy-Loading, DataGrip DB Parity, SPEC-001 Drift)
- **Status**: BACKLOG
- **Author**: AI Systems Architect
- **Target Files/Paths**:
  - `lua/cumulus/plugins/editor-completion.lua`
  - `lua/cumulus/plugins/tools-dap-cortex.lua`
  - `lua/cumulus/plugins/tools-dap-ui.lua`
  - `lua/cumulus/plugins/tools-formatting.lua`
  - `lua/cumulus/plugins/ui-config.lua`
  - `lua/cumulus/plugins/ui-noice.lua`
  - `lua/cumulus/plugins/editor-snacks.lua`
  - `lua/cumulus/plugins/ui-theme.lua`
  - `lua/cumulus/plugins/tools-dadbod.lua` (new)
  - `lua/cumulus/core/keymaps.lua`
  - `lua/cumulus/plugins/ui-whichkey.lua`
  - `ftplugin/sql.lua` (new)
  - `docs/specs/active/001-neovim-intellij-polyglot-setup.md`

- **Implementation**: Rust-First Alignment / Minimal Lua Specs & Keymap Glue

---

## Architecture

**Lua is a bridge to the Rust backend and editor UI for IntelliJ Ultimate & DataGrip enterprise parity.**

```
Neovim UI  →  Lua Spec/Keymap Glue  →  tpope/vim-dadbod / vim-dadbod-ui (DataGrip Parity)
```

- **Enterprise Parity Target**: Match IntelliJ IDEA Ultimate's database tooling (DataGrip) and startup performance standards for enterprise software engineering.
- **Rust-First Rule**: All data analysis, SQL AST parsing, and heavy migration validation must be executed in `crates/cumulus-core` (e.g. `validate-migrations`).
- **Lua Layer**: Minimal plugin triggers, buffer conventions, and global keymap bindings.

---

## Goal & Intent
Remediate three compliance audit findings to uphold enterprise production quality:
1. **Lazy-Loading Triggers**: Ensure all plugin specs have real lazy-loading triggers or documented eager-load exceptions.
2. **DataGrip Parity**: Install `vim-dadbod` and `vim-dadbod-ui` with buffer-local SQL conventions and `<leader>D` keymaps.
3. **SPEC-001 SDD Alignment**: Reconcile status and dadbod claims in SPEC-001.

---

## Scope Boundaries

**In scope:**
- Add `event`/`ft`/`cmd` triggers to completion, DAP UI, formatting, Trouble, and Noice specs.
- Document eager-loading rationale for Snacks, themes, and web-devicons.
- Register `vim-dadbod` and `vim-dadbod-ui` with `<leader>D` group (`<leader>Du`, `<leader>Df`, `<leader>Da`).
- Create `ftplugin/sql.lua` with buffer-local SQL settings.

**Out of scope:**
- Modifying frozen DevOps specs (`cloud-*.lua`, `lsp-devops.lua`, `tools-dap-devops.lua`).

---

## Execution Checklist

- [ ] **Part A: Lazy-Loading Fixes**
  - Add `event = "InsertEnter"` to `editor-completion.lua`.
  - Add `ft = { "c", "cpp" }` to `tools-dap-cortex.lua`.
  - Add `event = "VeryLazy"` to `tools-dap-ui.lua` and `ui-noice.lua`.
  - Add `event = { "BufReadPre", "BufNewFile" }` to `tools-formatting.lua`.
  - Add `cmd = "Trouble"` to `ui-config.lua`.
  - Add `-- NOTE:` rationale comments to eager plugins (`editor-snacks.lua`, `ui-theme.lua`).

- [ ] **Part B: DataGrip Parity (`vim-dadbod`)**
  - Create `lua/cumulus/plugins/tools-dadbod.lua` with `cmd = { "DB", "DBUI", ... }`.
  - Create `ftplugin/sql.lua` setting `shiftwidth = 2`, `commentstring = "-- %s"`.
  - Bind `<leader>Du`, `<leader>Df`, `<leader>Da` in `lua/cumulus/core/keymaps.lua`.
  - Add `<leader>D` group in `lua/cumulus/plugins/ui-whichkey.lua`.

- [ ] **Part C: Reconcile SPEC-001**
  - Fix status in `docs/specs/active/001-neovim-intellij-polyglot-setup.md`.

---

## Verification Commands & Acceptance Criteria

```bash
bash scripts/validate.sh
luac -p lua/cumulus/plugins/tools-dadbod.lua ftplugin/sql.lua
nvim --headless "+checkhealth cumulus" +qa
nvim --headless "+lua assert(vim.fn.exists(':DBUIToggle') == 2); print('dadbod ok')" +qa
git status --short lua/cumulus/plugins/cloud-* lua/cumulus/plugins/lsp-devops.lua lua/cumulus/plugins/tools-dap-devops.lua
```

### Acceptance Criteria
- [ ] All target plugins carry proper lazy-loading triggers or rationale comments.
- [ ] `vim-dadbod` and `vim-dadbod-ui` are registered and functional for DataGrip parity.
- [ ] `<leader>D` database keymaps operate correctly.
- [ ] DevOps guardrail assertion returns zero modified files.
- [ ] Startup latency remains under 50ms.
