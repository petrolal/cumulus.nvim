# Specification: SPEC-003 - Compliance Remediation (Lazy-Loading, DB Client Parity, SPEC-001 Drift)

## Metadata
- **Spec ID**: SPEC-003
- **Title**: Compliance Remediation (Lazy-Loading, DB Client Parity, SPEC-001 Drift)
- **Status**: REVIEW
- **Implementation**: Rust (minimal Lua)
- **Implementation**: Rust (minimal Lua)
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

---

## Goal & Intent
This spec bundles three findings from the `/check-project` compliance audit dated 2026-08-08 into one remediation pass:

**A. Lazy-Loading Standards** — nine plugin specs violate CLAUDE.md's "every plugin needs a real lazy-loading trigger, or a documented reason to load eagerly" rule. Six have no `event`/`ft`/`cmd`/`keys` anywhere in the merged spec tree and silently inherit `defaults.lazy = false` from `lua/cumulus/core/lazy.lua:26`; three set `lazy = false` explicitly with no rationale comment (unlike `tools-mason.lua`'s documented exception).

**B. IntelliJ DataGrip Parity** — no SQL client tooling (`vim-dadbod` / `vim-dadbod-ui`) exists anywhere in the repo, despite `docs/specs/active/001-neovim-intellij-polyglot-setup.md` claiming it as delivered.

**C. SDD Drift on SPEC-001** — `docs/specs/active/001-neovim-intellij-polyglot-setup.md` sits in `active/` (correct, since B above proves it isn't done) but its own `Status:` metadata still reads `COMPLETED`, and its "Historical Verification & Benchmarks" section asserts the dadbod stack is already verified. Both are false today and must be corrected so the spec accurately reflects the working tree.

---

## Scope Boundaries

**In scope:**
- Adding lazy-loading triggers (or a documented `lazy = false` rationale comment) to the nine plugins listed in Part A.
- Registering `tpope/vim-dadbod` + `kristijanhusak/vim-dadbod-ui`, buffer-local SQL conventions, and a `<leader>D` "database" which-key group with `DBUI*` command keymaps.
- Correcting SPEC-001's `Status:` field and its false "already verified" dadbod claim.

**Out of scope:**
- `kristijanhusak/vim-dadbod-completion` / wiring a `vim-dadbod-completion` source into `nvim-cmp`. Prerequisite check (below) found `hrsh7th/nvim-cmp`'s base `opts.sources` table is not populated anywhere in this repo today (`lua/cumulus/plugins/editor-completion.lua` only ever *removes* an `"emoji"` entry from a list nothing else ever adds to) — that is a pre-existing gap unrelated to this audit. Wiring a completion source onto a foundation that doesn't exist yet is scope creep; deferred to a future spec that first establishes `nvim-cmp`'s base source list.
- Any file under the DevOps Immutability Guardrail (unaffected by this spec regardless).
- Any capability already satisfied — see Prerequisite Analysis per part below.

---

## Part A — Lazy-Loading Trigger Fixes

### Prerequisite Analysis
- `lua/cumulus/core/lazy.lua:25-28` sets `defaults = { lazy = false }`. lazy.nvim only infers `lazy = true` when a spec (or any spec fragment merged into the same plugin name) declares `event`/`ft`/`cmd`/`keys`. Confirmed via full-repo grep that these six plugin names appear in exactly one spec file each, with no trigger field anywhere:
  - `hrsh7th/nvim-cmp` — `lua/cumulus/plugins/editor-completion.lua`
  - `jedrzejboczar/nvim-dap-cortex-debug` — `lua/cumulus/plugins/tools-dap-cortex.lua`
  - `rcarriga/nvim-dap-ui` — `lua/cumulus/plugins/tools-dap-ui.lua`
  - `stevearc/conform.nvim` — `lua/cumulus/plugins/tools-formatting.lua`
  - `folke/trouble.nvim` — `lua/cumulus/plugins/ui-config.lua`
  - `folke/noice.nvim` — `lua/cumulus/plugins/ui-noice.lua`
- By contrast `mfussenegger/nvim-dap` is defined across three files (`tools-dap-devops.lua`, `tools-dap-kotlin.lua`, plus as a `dependencies` entry in `tools-dap-cortex.lua`/`tools-dap-ui.lua`); `tools-dap-devops.lua` (frozen) already sets `keys = {...}`, so lazy.nvim's merge already resolves that plugin as lazy. **Do not add a trigger to `tools-dap-kotlin.lua` — it is not part of this gap.**
- Three further plugins set `lazy = false` explicitly with no comment explaining why, unlike the documented exception at `tools-mason.lua:44-52`:
  - `folke/snacks.nvim` — `lua/cumulus/plugins/editor-snacks.lua:16`
  - `cumulus/aws-theme` — `lua/cumulus/plugins/ui-theme.lua:8`
  - `nvim-tree/nvim-web-devicons` — `lua/cumulus/plugins/ui-theme.lua:138`
  - These three are genuinely load-bearing at startup (dashboard shown on a bare `nvim` invocation, colorscheme must apply before first paint, icons consumed immediately by the dashboard/statusline/telescope) — the fix is a documentation comment, not a trigger change.

### Execution Checklist
- [x] **A1**: `lua/cumulus/plugins/editor-completion.lua` — add `event = "InsertEnter"` to the `hrsh7th/nvim-cmp` spec (completion is only relevant once the user enters insert mode).
- [x] **A2**: `lua/cumulus/plugins/tools-dap-cortex.lua` — add `ft = { "c", "cpp" }` to the `jedrzejboczar/nvim-dap-cortex-debug` spec (it only registers `dap.configurations.c`/`.cpp`, so it is only relevant in C/C++ buffers).
- [x] **A3**: `lua/cumulus/plugins/tools-dap-ui.lua` — add `event = "VeryLazy"` to the `rcarriga/nvim-dap-ui` spec, matching the existing `VeryLazy` idiom this repo already uses for `lualine.nvim`/`bufferline.nvim`/`which-key.nvim`/`persistence.nvim`.
- [x] **A4**: `lua/cumulus/plugins/tools-formatting.lua` — add `event = { "BufReadPre", "BufNewFile" }` to the `stevearc/conform.nvim` spec, matching the trigger already used by `nvim-lspconfig`/`nvim-lint`/`gitsigns.nvim`/Treesitter so the formatter is ready before `<leader>cf` can be pressed.
- [x] **A5**: `lua/cumulus/plugins/ui-config.lua` — add `cmd = "Trouble"` to the `folke/trouble.nvim` spec.
- [x] **A6**: `lua/cumulus/plugins/ui-noice.lua` — add `event = "VeryLazy"` to the `folke/noice.nvim` spec (the standard trigger recommended by noice.nvim itself).
- [x] **A7**: `lua/cumulus/plugins/editor-snacks.lua:16` — above the existing `lazy = false,` line, add a one-line `-- NOTE:` comment stating it must stay eager because the dashboard renders at `VimEnter` on a bare `nvim` invocation, matching the rationale style already used in `tools-mason.lua`.
- [x] **A8**: `lua/cumulus/plugins/ui-theme.lua:8` — same treatment for `cumulus/aws-theme` (`lazy = false` required so the colorscheme applies before first paint).
- [x] **A9**: `lua/cumulus/plugins/ui-theme.lua:138` — same treatment for `nvim-tree/nvim-web-devicons` (icons consumed immediately by dashboard/statusline/telescope, all of which can render before any lazy-load event fires).

---

## Part B — `vim-dadbod` DataGrip Parity

### Prerequisite Analysis
- `grep -rin "dadbod" lazy-lock.json lua/` returns nothing — confirmed genuinely missing, not misfiled elsewhere.
- `<leader>d` is already claimed by the frozen `tools-dap-devops.lua` as the "debug/dap" which-key group (`<leader>db`, `<leader>dc`, `<leader>di`, `<leader>do`, `<leader>dO`, `<leader>dr`, `<leader>du`, `<leader>dt` are all taken by DAP breakpoint/continue/step keymaps). A new `<leader>D` (capital) top-level group avoids every collision.
- No Mason package is required — `vim-dadbod`/`vim-dadbod-ui` are pure Lua/Vimscript plugins that shell out to already-installed DB CLI clients (`psql`, `mysql`, `sqlite3`, ...) at connection time, not LSP servers or formatters Mason would manage.
- `lua/cumulus/plugins/tools-formatting.lua`'s `formatters_by_ft` has no `sql` entry and none is added here — out of scope per the Goal & Intent above (no formatter requested, none exists in Mason's `ensure_installed` either).

### Execution Checklist
- [x] **B1**: Create `lua/cumulus/plugins/tools-dadbod.lua`:
  ```lua
  -- Cumulus SQL Database Client (DataGrip Parity)

  return {
    {
      "tpope/vim-dadbod",
      cmd = { "DB", "DBUI", "DBUIToggle", "DBUIAddConnection", "DBUIFindBuffer" },
      dependencies = {
        "kristijanhusak/vim-dadbod-ui",
      },
      -- vim-dadbod-ui reads these g: globals from its own plugin/ script,
      -- which only runs once lazy.nvim loads the plugin -- they must be
      -- set in init() (runs before load), not config() (runs after).
      init = function()
        vim.g.db_ui_use_nerd_fonts = 1
        vim.g.db_ui_show_help = 0
      end,
    },
  }
  ```
- [x] **B2**: Create `ftplugin/sql.lua` (buffer-local options only, mirroring `ftplugin/java.lua`'s header style):
  ```lua
  -- Cumulus SQL Buffer Conventions (DataGrip Parity follow-up)

  vim.bo.shiftwidth = 2
  vim.bo.tabstop = 2
  vim.bo.softtabstop = 2
  vim.bo.expandtab = true
  vim.bo.commentstring = "-- %s"
  ```
- [x] **B3**: `lua/cumulus/core/keymaps.lua` — add global keymaps (not buffer-local; DBUI is a workspace-wide tool like Telescope, not tied to one filetype):
  ```lua
  map("n", "<leader>Du", "<cmd>DBUIToggle<cr>", { desc = "Toggle Database UI" })
  map("n", "<leader>Df", "<cmd>DBUIFindBuffer<cr>", { desc = "Find DB Buffer" })
  map("n", "<leader>Da", "<cmd>DBUIAddConnection<cr>", { desc = "Add DB Connection" })
  ```
- [x] **B4**: `lua/cumulus/plugins/ui-whichkey.lua` — add `{ "<leader>D", group = "database", icon = "󰆼 " }` to the `opts.spec` list alongside the other top-level groups.

---

## Part C — Reconcile SPEC-001 SDD Drift

### Prerequisite Analysis
- `docs/specs/active/001-neovim-intellij-polyglot-setup.md` metadata reads `**Status**: COMPLETED` while physically located in `active/` — a directory/status mismatch.
- Its "SQL & Database Management (Dadbod)" bullet under "Historical Verification & Benchmarks" asserts the dadbod stack is implemented and verified; Part B of this spec proves that was false until now.

### Execution Checklist
- [x] **C1**: In `docs/specs/active/001-neovim-intellij-polyglot-setup.md`, change `- **Status**: COMPLETED` to `- **Status**: ACTIVE`, matching its actual location.
- [x] **C2**: Rewrite the "SQL & Database Management (Dadbod)" bullet to state the dadbod stack is delivered by SPEC-003 (this spec) rather than claiming it was already verified as part of SPEC-001.
- [x] **C3**: Once Part B's Verification Commands (below) pass, transition SPEC-001 to `docs/specs/review/` (and upon `/review-task` approval to `docs/specs/completed/`, restoring `Status: COMPLETED`) — SPEC-003 itself moves from `active/` to `review/` upon `/implement-task` completion, then archives to `completed/` via `/review-task`.

---

## Constraints & Guardrails
1. **DevOps Immutability Guardrail**: none of Part A/B/C's target files touch `cloud-*.lua`, `lsp-devops.lua`, or `tools-dap-devops.lua`. Verify zero diff on those paths after execution.
2. **Zero Free Files Policy**: the only new files are `lua/cumulus/plugins/tools-dadbod.lua` (sanctioned `lua/cumulus/plugins/`) and `ftplugin/sql.lua` (sanctioned `ftplugin/`).
3. **No Duplicate Wiring Guardrail**: Part A adds triggers only — no `opts`, servers, or parsers are re-registered. Part B does not touch `tools-mason.lua`, `tools-formatting.lua`, or `tools-linting.lua`.
4. **Global Keymap Guardrail**: `<leader>D*` keymaps are global (workspace-wide tool, not filetype-gated), consistent with how `<leader>f`/`<leader>s`/`<leader>t` are already global rather than `lang_keymaps` stacks.
5. **Performance Budget**: every Part A fix either adds a trigger (reducing startup cost) or documents an already-necessary eager load (zero cost change). Part B's `cmd`-gated dadbod spec adds zero startup cost. Re-measure startup after all changes; must stay under 50ms.

---

## Verification Commands & Acceptance Checklist

```bash
# 1. Run the project's canonical verification suite
bash scripts/validate.sh

# 2. Validate Lua syntax of all new/changed files
luac -p lua/cumulus/plugins/editor-completion.lua lua/cumulus/plugins/tools-dap-cortex.lua \
  lua/cumulus/plugins/tools-dap-ui.lua lua/cumulus/plugins/tools-formatting.lua \
  lua/cumulus/plugins/ui-config.lua lua/cumulus/plugins/ui-noice.lua \
  lua/cumulus/plugins/editor-snacks.lua lua/cumulus/plugins/ui-theme.lua \
  lua/cumulus/plugins/tools-dadbod.lua lua/cumulus/core/keymaps.lua \
  lua/cumulus/plugins/ui-whichkey.lua ftplugin/sql.lua

# 3. Headless Lazy sync
nvim --headless "+Lazy! sync" +qa

# 4. Health report
nvim --headless "+checkhealth cumulus" +qa

# 5. Confirm the six previously-eager plugins now report lazy=true
nvim --headless "+lua for _, n in ipairs({'nvim-cmp','nvim-dap-cortex-debug','nvim-dap-ui','conform.nvim','trouble.nvim','noice.nvim'}) do local p = require('lazy.core.config').spec.plugins[n]; assert(p and p.lazy, n .. ' still eager') end; print('✔ all six now lazy')" +qa

# 6. Confirm DBUI commands are registered
nvim --headless "+lua assert(vim.fn.exists(':DBUIToggle') == 2, 'DBUIToggle missing'); print('✔ dadbod-ui commands registered')" +qa

# 7. Spot-check SQL buffer conventions
nvim --headless -c "edit /tmp/spec003-check.sql" -c "lua assert(vim.bo.shiftwidth == 2); assert(vim.bo.commentstring == '-- %s'); print('✔ sql ftplugin ok')" +qa

# 8. Measure startup latency
nvim --headless --startuptime /tmp/nvim-startuptime.log +qa && grep "NVIM STARTED" /tmp/nvim-startuptime.log

# 9. Assert DevOps Immutability Guardrail (must return zero diffs)
git status --short lua/cumulus/plugins/cloud-* lua/cumulus/plugins/lsp-devops.lua lua/cumulus/plugins/tools-dap-devops.lua

# 10. Confirm SPEC-001 no longer claims false completion
grep -n "Status" docs/specs/active/001-neovim-intellij-polyglot-setup.md
```

### Acceptance Criteria
- [ ] All nine Part A plugins either carry a real lazy-loading trigger or a documented `-- NOTE:` rationale comment matching the `tools-mason.lua` pattern.
- [ ] `lua/cumulus/plugins/tools-dadbod.lua` and `ftplugin/sql.lua` exist and pass `luac -p`.
- [ ] `<leader>Du`/`<leader>Df`/`<leader>Da` are registered global keymaps with `desc`, and `<leader>D` appears as a which-key group.
- [ ] SPEC-001's `Status:` field reads `ACTIVE` and no longer claims dadbod is already verified (Part C1/C2).
- [ ] `scripts/validate.sh`, `nvim --headless "+Lazy! sync" +qa`, and the lazy-flag assertion (Verification Command 5) all pass.
- [ ] DevOps guardrail assertion (Verification Command 9) returns zero diffs.
- [ ] Startup latency remains under the 50ms budget.
- [ ] Once every box above is checked, SPEC-001 is moved back to `docs/specs/completed/` with `Status: COMPLETED` restored, and this spec (SPEC-003) is moved to `docs/specs/completed/` alongside it.
