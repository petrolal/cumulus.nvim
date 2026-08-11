# Specification: SPEC-002 - HTML & XML Markup Language Expansion

## Metadata
- **Spec ID**: SPEC-002
- **Title**: HTML & XML Markup Language Expansion (IntelliJ Ultimate Enterprise Parity)
- **Status**: REVIEW
- **Author**: AI Systems Architect
- **Target Files/Paths**:
  - `ftplugin/html.lua` (new)
  - `ftplugin/xml.lua` (new)

- **Implementation**: Native Neovim buffer options (zero unnecessary plugin specs or duplicate LSP wiring)

---

## Architecture

**Lua is a bridge to the editor UI. Heavy operations go to Rust. Editor defaults stay minimal.**

```
Neovim Buffer  →  ftplugin/{html,xml}.lua (buffer-local options)  →  Native LSP (superhtml / lemminx)
```

- **Enterprise Parity Target**: Replicate IntelliJ IDEA Ultimate's HTML/XML editing and formatting capabilities for production enterprise web/markup development.
- **Rust Engine (`crates/cumulus-core`)**: Owns heavy AST parsing, markup validation, and structural extraction where needed.
- **Lua Layer**: Buffer-local editor options only (`shiftwidth`, `tabstop`, `commentstring`).
- **No Duplicate Wiring**: LSP servers (`superhtml`, `lemminx`) and Treesitter parsers are already registered in existing plugin specs.

---

## Goal & Intent
Achieve full 1:1 parity with IntelliJ IDEA Ultimate's HTML and XML editing environment by establishing explicit repository-owned buffer-local formatting conventions matching `ftplugin/java.lua`, without creating duplicate LSP server registrations, duplicate Treesitter parser specs, or redundant keymaps.

---

## Scope Boundaries

**In scope:**
- Creating `ftplugin/html.lua` and `ftplugin/xml.lua` for buffer-local options (`shiftwidth = 2`, `tabstop = 2`, `softtabstop = 2`, `expandtab = true`, `commentstring`).

**Out of scope:**
- `lua/cumulus/plugins/lsp-devops.lua` (FROZEN — already owns XML Treesitter parser and `lemminx` LSP server).
- Creating duplicate `lsp-xml.lua` files or editing `core-treesitter.lua` / `tools-mason.lua` / `tools-formatting.lua`.
- Global keymaps (`<leader>cf`, `<leader>ca`, `<leader>cr`, diagnostics), which are already globally wired in `lua/cumulus/core/keymaps.lua`.

---

## Prerequisite Analysis

1. **XML Treesitter + `lemminx` LSP**: Registered in `lua/cumulus/plugins/lsp-devops.lua` (**FROZEN**). Do NOT create `lsp-xml.lua`.
2. **HTML Treesitter + `superhtml` LSP**: Registered in `lua/cumulus/plugins/lsp-html.lua`.
3. **Mason Tool Installer**: `lemminx` and `superhtml` are already present in `lua/cumulus/plugins/tools-mason.lua`.
4. **Formatting**: `html` uses `superhtml` in `tools-formatting.lua`. XML uses `lemminx` LSP fallback in `lua/cumulus/util/format.lua`.

---

## Constraints & Guardrails

1. **DevOps Immutability Guardrail**: Never modify `lsp-devops.lua`, `cloud-*.lua`, or `tools-dap-devops.lua`.
2. **Zero Free Files Policy**: Only add files under sanctioned `ftplugin/` directory (`ftplugin/html.lua`, `ftplugin/xml.lua`).
3. **No Duplicate Wiring**: Do not re-register LSP servers or Treesitter parsers.
4. **Performance Budget**: Zero startup cost (files load lazily on filetype trigger).

---

## Execution Checklist

- [x] **Task 1: Verify Prerequisite Assumptions**
  - Confirm `lemminx` and `superhtml` are registered in existing specs.

- [x] **Task 2: Create `ftplugin/html.lua`**
  ```lua
  -- Cumulus HTML Buffer Conventions (IntelliJ Ultimate Parity)

  vim.bo.shiftwidth = 2
  vim.bo.tabstop = 2
  vim.bo.softtabstop = 2
  vim.bo.expandtab = true
  vim.bo.commentstring = "<!-- %s -->"
  ```

- [x] **Task 3: Create `ftplugin/xml.lua`**
  ```lua
  -- Cumulus XML Buffer Conventions (IntelliJ Ultimate Parity; LSP/formatting owned by lsp-devops.lua)

  vim.bo.shiftwidth = 2
  vim.bo.tabstop = 2
  vim.bo.softtabstop = 2
  vim.bo.expandtab = true
  vim.bo.commentstring = "<!-- %s -->"
  ```

- [x] **Task 4: Post-Execution Verification**
  - Run verification suite and assert zero DevOps diffs.

---

## Verification Commands & Acceptance Criteria

```bash
bash scripts/validate.sh
luac -p ftplugin/html.lua ftplugin/xml.lua
nvim --headless "+checkhealth cumulus" +qa
nvim --headless -c "edit /tmp/check.html" -c "lua assert(vim.bo.shiftwidth == 2); print('html ok')" +qa
nvim --headless -c "edit /tmp/check.xml" -c "lua assert(vim.bo.shiftwidth == 2); print('xml ok')" +qa
git status --short lua/cumulus/plugins/cloud-* lua/cumulus/plugins/lsp-devops.lua lua/cumulus/plugins/tools-dap-devops.lua
```

### Acceptance Criteria
- [x] `ftplugin/html.lua` and `ftplugin/xml.lua` exist with buffer options only.
- [x] `luac` syntax validation succeeds for both files.
- [x] `scripts/validate.sh` passes completely.
- [x] DevOps guardrail assertion returns zero modified files.
