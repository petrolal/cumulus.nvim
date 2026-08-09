# Specification Template: SPEC-XXX - [Specification Title]

## Metadata
- **Spec ID**: SPEC-XXX
- **Title**: [Insert Concise Feature/Integration Name]
- **Status**: DRAFT
- **Author**: AI Systems Architect
- **Target Files/Paths**:
  - `lua/cumulus/plugins/[plugin-module].lua`
  - `ftplugin/[filetype].lua`

---

## Goal & Intent
[State the primary objective of this specification. Describe the specific Developer Experience (DX) gap or IDE feature being introduced into Neovim (e.g., polyglot LSP support, automated debugging, schema validation). Detail how the implementation integrates cleanly with existing lazy.nvim plugin specs, Treesitter parsers, and Mason toolchains while preserving the IntelliJ-like developer workflow.]

---

## Scope Boundaries

**In scope:**
- [List the exact filetypes/languages/tools this spec introduces or changes.]
- [List the exact files this spec is authorized to create or edit.]

**Out of scope:**
- Any file under the DevOps Immutability Guardrail (below) — even if it already partially overlaps with this spec's filetype.
- Any capability already satisfied per the Prerequisite Analysis — re-registering it is scope creep, not implementation.
- [Anything explicitly deferred to a future spec.]

---

## Prerequisite Analysis (mandatory before writing tasks)
lazy.nvim merges `opts` tables from every spec file that targets the same plugin name (`nvim-treesitter`, `nvim-lspconfig`, `conform.nvim`, `nvim-lint`). That means a Treesitter parser, LSP server, or formatter may already be registered by an unrelated file — most commonly the frozen `lua/cumulus/plugins/lsp-devops.lua`, which already owns `json`, `xml`, and `bash`. Before drafting the Execution Checklist:

- [ ] `grep` every `lua/cumulus/plugins/*.lua` for the target filetype/server/formatter name to confirm it is not already registered.
- [ ] Check `lua/cumulus/plugins/tools-mason.lua`'s `ensure_installed` table for the required Mason package name.
- [ ] Check `lua/cumulus/core/keymaps.lua` (global keymaps + `lang_keymaps.register()` stacks) for an existing keymap/build command before adding a new one.
- [ ] List anything already satisfied, with a file citation, so the checklist below only covers the genuine gap.

---

## Constraints & Guardrails
1. **DevOps Immutability Guardrail**:
   - **STRICT REQUIREMENT**: Do not modify, rename, relocate, or delete any files in `lua/cumulus/plugins/cloud-*.lua`, `lua/cumulus/plugins/lsp-devops.lua`, `lua/cumulus/plugins/tools-dap-devops.lua`, `.devcontainer/`, `Dockerfile`, or any container/K8s/Terraform configuration files.
   - All IaC and DevOps automation infrastructure is frozen and immutable, even when it already touches a filetype this spec cares about (e.g. `lsp-devops.lua` owns XML). Extend around it, never edit it.

2. **Zero Free Files Policy**:
   - All code, plugin specifications, and keybindings must reside strictly inside valid Neovim modules within `lua/cumulus/` or filetype handlers in `ftplugin/`.
   - No floating scripts, orphaned shell helpers, or unmanaged top-level configuration files are permitted at workspace root. `scripts/validate.sh` is the one established exception (the project's verification entrypoint) — do not add siblings to it without explicit instruction.

3. **No Duplicate Wiring Guardrail**:
   - Never re-register a Treesitter parser, LSP server, formatter, or linter that another spec file already owns. If a capability already exists (per the Prerequisite Analysis), reference it read-only and scope the checklist to the real gap.
   - Treesitter parsers are extended, never redefined: add to the owning `lsp-*.lua`/`cloud-*.lua` file via
     ```lua
     opts = function(_, opts)
       if type(opts.ensure_installed) == "table" then
         vim.list_extend(opts.ensure_installed, { "parser_name" })
       end
     end
     ```
     `lua/cumulus/plugins/core-treesitter.lua` only seeds the base list and installs/highlights it — it is not where individual languages get added.

4. **Global Keymap Guardrail**:
   - `<leader>cf` (format), `<leader>ca` (code action), `<leader>cr` (rename), `<leader>cd`/`[d`/`]d` (diagnostics) are already global in `lua/cumulus/core/keymaps.lua` and apply to any buffer with an attached LSP client. Do **not** redefine these as buffer-local keymaps in `ftplugin/*.lua`.
   - Extra language-specific build/lint commands (Maven, Terraform, Ansible, Docker, Helm, ...) are registered as buffer-local stacks via `lang_keymaps.register()` inside `lua/cumulus/core/keymaps.lua`, not inside `ftplugin/`. Only add a new stack if the language genuinely needs commands beyond generic LSP actions.
   - `ftplugin/[filetype].lua` is reserved for buffer-local **options** (`shiftwidth`, `tabstop`, `expandtab`, `commentstring`) and, where the LSP client requires manual bootstrapping (e.g. `ftplugin/java.lua` launching JDTLS), the launcher logic itself.

5. **Performance Budget**:
   - Total startup time impact must remain under 50ms.
   - All LSP servers and auxiliary plugins must leverage lazy-loading events (`BufReadPre`, `BufNewFile`, `ft`) to prevent eager initialization overhead.

6. **Code Quality & Maintenance**:
   - Use standard Lua table merge idioms for lazy.nvim specs.
   - Use explicit Neovim API calls (`vim.keymap.set`, `vim.api.nvim_create_autocmd`, `vim.lsp.buf`) with non-silent error handling (`pcall` where external binaries may be missing).
   - Zero placeholders allowed: every code snippet must be fully written out.

---

## Execution Checklist (for Claude Code)

- [ ] **Task 1: Pre-Execution Guardrail & Prerequisite Verification**
  - Verify that no frozen DevOps files (`lua/cumulus/plugins/cloud-*.lua`, `lsp-devops.lua`, `tools-dap-devops.lua`) are listed in target changes.
  - Re-confirm the Prerequisite Analysis findings against current file contents (specs can go stale between drafting and execution).

- [ ] **Task 2: Plugin Spec & Package Definition Update** *(only if genuinely missing)*
  - File: `lua/cumulus/plugins/[target-plugin-spec].lua`
  - Register required Mason packages in `lua/cumulus/plugins/tools-mason.lua`'s `ensure_installed` table only if not already present.
  - Define lazy.nvim specification with appropriate triggers (`event`, `ft`, `cmd`).

- [ ] **Task 3: LSP Server & Treesitter Integration** *(only if genuinely missing)*
  - File: `lua/cumulus/plugins/[target-lsp-spec].lua`
  - Extend Treesitter `ensure_installed` via the `vim.list_extend` pattern above.
  - Add LSP server settings under `opts.servers.[server_name]` — `lua/cumulus/plugins/lsp-core.lua` attaches every registered server generically, no extra wiring required.

- [ ] **Task 4: Formatting & Linting** *(only if a real gap exists)*
  - `lua/cumulus/plugins/tools-formatting.lua`: add a `formatters_by_ft` entry only if the filetype needs an explicit CLI formatter. `cumulus.util.format.format()` already passes `lsp_format = "fallback"`, so any filetype with a formatting-capable LSP server works without an entry.
  - `lua/cumulus/plugins/tools-linting.lua`: add a `linters_by_ft` entry only if diagnostics aren't already covered by the attached LSP server.

- [ ] **Task 5: Filetype Buffer Settings**
  - File: `ftplugin/[filetype].lua`
  - Configure buffer options (`shiftwidth`, `tabstop`, `expandtab`, `commentstring`) only.
  - If a build/lint command genuinely needs adding, register it as a `lang_keymaps` stack in `lua/cumulus/core/keymaps.lua`, not here.

- [ ] **Task 6: Post-Execution Verification**
  - Run `scripts/validate.sh` and headless plugin synchronization.
  - Verify startup latency benchmark remains under target threshold.
  - Confirm frozen DevOps paths show zero diff.

---

## Verification Commands

Execute the following bash commands sequentially to validate execution success:

```bash
# 1. Run the project's canonical verification suite
bash scripts/validate.sh

# 2. Validate Lua code syntax across target modules
luac -p lua/cumulus/plugins/[target-plugin-spec].lua ftplugin/[filetype].lua

# 3. Run headless Neovim to verify Lazy.nvim plugin synchronization
nvim --headless "+Lazy! sync" +qa

# 4. Check Neovim health and log status
nvim --headless "+checkhealth cumulus" +qa

# 5. Measure Neovim startup time latency
nvim --headless --startuptime /tmp/nvim-startuptime.log +qa && grep "TOTAL" /tmp/nvim-startuptime.log

# 6. Assert DevOps Immutability Guardrail (must return zero diffs)
git status --short lua/cumulus/plugins/cloud-* lua/cumulus/plugins/lsp-devops.lua lua/cumulus/plugins/tools-dap-devops.lua
```
