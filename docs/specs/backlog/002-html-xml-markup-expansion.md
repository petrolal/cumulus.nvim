# Specification: SPEC-002 - HTML & XML Markup Language Expansion

## Metadata
- **Spec ID**: SPEC-002
- **Title**: HTML & XML Markup Language Expansion
- **Status**: BACKLOG
- **Author**: AI Systems Architect
- **Target Files/Paths** (new/modified — see Prerequisite Analysis for what is already done and must NOT be touched):
  - `ftplugin/html.lua` (new)
  - `ftplugin/xml.lua` (new)

---

## Goal & Intent
Close the remaining IDE-parity gap for HTML and XML editing (buffer-local formatting conventions, matching the pattern already established by `ftplugin/java.lua`) without re-registering LSP servers, Treesitter parsers, Mason packages, or keymaps that a repository scan shows already exist. An earlier draft of this spec proposed creating a new `lsp-xml.lua`, editing `core-treesitter.lua` directly, and duplicating `gd`/`K`/`<leader>cf` keymaps per-filetype — all three would have either collided with the frozen DevOps stack or duplicated global wiring that already works. This revision corrects course.

---

## Scope Boundaries

**In scope:**
- Creating `ftplugin/html.lua` and `ftplugin/xml.lua` (buffer-local options only: `shiftwidth`, `tabstop`, `expandtab`, `commentstring`).

**Out of scope:**
- `lua/cumulus/plugins/lsp-devops.lua` (frozen — already owns XML Treesitter/`lemminx`).
- `lua/cumulus/plugins/lsp-html.lua`, `tools-mason.lua`, `tools-formatting.lua`, `core-treesitter.lua` — already satisfy their part per the Prerequisite Analysis below; editing them here is scope creep.
- Any new keymaps — `<leader>cf`/`<leader>ca`/`<leader>cr`/diagnostics are already global; no HTML/XML-specific build commands are needed today.

---

## Prerequisite Analysis — What Already Exists (do not re-implement)

1. **XML Treesitter parser + `lemminx` LSP server** are already registered in `lua/cumulus/plugins/lsp-devops.lua` (**frozen**, Epic 11/Epic 39):
   ```lua
   vim.list_extend(opts.ensure_installed, { "json", "xml", "bash" })
   -- ...
   servers = { jsonls = {}, lemminx = {}, bashls = { filetypes = { "sh", "bash" } } }
   ```
   `lemminx` is registered with no `filetypes` override, so `nvim-lspconfig`'s built-in default filetypes apply (`xml`, `xsd`, `xsl`, `xslt`, `svg`). A new `lsp-xml.lua` would duplicate this registration — lazy.nvim would merge both `opts.servers.lemminx` tables, which is redundant and makes it unclear which file owns XML behavior. **Do not create `lsp-xml.lua`.**

2. **HTML Treesitter parser + `superhtml` LSP server** are already registered in `lua/cumulus/plugins/lsp-html.lua` (Story 40.2, not frozen — this is a normal editable file, just already complete):
   ```lua
   vim.list_extend(opts.ensure_installed, { "html" })
   -- ...
   servers = { superhtml = {} }
   ```

3. **Mason `ensure_installed`**: `lemminx` and `superhtml` are both already present in `lua/cumulus/plugins/tools-mason.lua`. No change needed there.

4. **Formatting**: `lua/cumulus/plugins/tools-formatting.lua` already maps `html = { "superhtml" }`. XML has no explicit `formatters_by_ft` entry, but `lua/cumulus/util/format.lua`'s `M.format()` (bound to `<leader>cf`/`<leader>cF`) always calls `conform.format({ ..., lsp_format = "fallback" })`, so XML already formats through `lemminx`'s LSP formatting capability without an entry. No change needed there.

5. **Global code keymaps**: `<leader>cf` (format), `<leader>ca` (code action), `<leader>cr` (rename), `<leader>cd`/`[d`/`]d` (diagnostics) are global in `lua/cumulus/core/keymaps.lua` and apply to any buffer with an attached LSP client — HTML and XML included, automatically, the moment `superhtml`/`lemminx` attach via the generic loop in `lua/cumulus/plugins/lsp-core.lua`. No per-filetype keymap file is needed for these.

6. **`pom.xml` build ergonomics**: the `<leader>cj` Java/JVM build stack registered in `lua/cumulus/core/keymaps.lua` already lists `xml` in its `filetypes`, gated on an actual `pom.xml`/`build.gradle` being present (`lua/cumulus/util/maven.lua` / `gradle.lua`). Editing `pom.xml` inside a Maven project already surfaces Maven/Gradle build commands under `<leader>cj`. No change needed there.

## Actual Remaining Gap
Everything functional (parsing, diagnostics, completion, formatting, code actions, build integration) already works for HTML and XML out of the box. The one gap: unlike `ftplugin/java.lua`, there is no `ftplugin/html.lua` or `ftplugin/xml.lua` pinning buffer-local editing conventions (`shiftwidth`, `tabstop`, `expandtab`, `commentstring`) explicitly in the Cumulus config, so today HTML/XML buffers rely entirely on Neovim's bundled `$VIMRUNTIME/ftplugin/{html,xml}.vim` defaults. That's functionally fine, but it means those conventions aren't owned by this repo and could silently drift on a future Neovim upgrade. This spec pins them explicitly, matching the established `ftplugin/` pattern.

---

## Constraints & Guardrails
1. **DevOps Immutability Guardrail**:
   - **MANDATORY ASSERTION**: Under no circumstances shall `lua/cumulus/plugins/lsp-devops.lua`, `lua/cumulus/plugins/cloud-cloudformation-ansible.lua`, `lua/cumulus/plugins/cloud-containers-k8s.lua`, `lua/cumulus/plugins/cloud-terraform.lua`, `lua/cumulus/plugins/tools-dap-devops.lua`, `.devcontainer/`, `Dockerfile`, or any Kubernetes/Terraform files be edited, moved, renamed, or deleted — including to "improve" the already-working XML/lemminx registration.
   - Claude Code must verify that no DevOps modules are modified during execution.

2. **Zero Free Files Policy**:
   - The only new files are `ftplugin/html.lua` and `ftplugin/xml.lua`, both inside the sanctioned `ftplugin/` location. No root-level scripts or unmanaged config files.

3. **No Duplicate Wiring Guardrail**:
   - Do not create `lsp-xml.lua`, do not add `html`/`xml` to `core-treesitter.lua`'s own `ensure_installed` seed list, do not add `lemminx`/`superhtml` to `tools-mason.lua` (already present), and do not add HTML/XML entries to `tools-formatting.lua`/`tools-linting.lua` unless the Prerequisite Analysis is re-run at execution time and a genuine new gap is found.
   - Do not add `gd`, `K`, `<leader>cf`, `<leader>ca`, or `<leader>cr` to the new `ftplugin/` files — these are already global.

4. **Performance Budget**:
   - `ftplugin/*.lua` files only set buffer-local options; they load lazily via Neovim's native filetype dispatch and have zero measurable startup cost. No lazy.nvim `event`/`ft` triggers to configure since no plugin spec is involved.

5. **Code Quality & Maintenance**:
   - Mirror the structure of the existing `ftplugin/java.lua` header comment style (a one-line Story/Epic reference), and keep both new files free of dead code or placeholder TODOs.

---

## Atomic Execution Checklist for Claude Code

### Task 1: Re-verify Prerequisite Analysis
- **Action**: Grep `lua/cumulus/plugins/*.lua` for `lemminx`, `superhtml`, `"xml"`, `"html"` to reconfirm items 1–4 above still hold (specs can go stale between drafting and execution). Abort and update this spec if any assumption no longer matches the working tree.

### Task 2: Create `ftplugin/html.lua`
- **Target File**: `ftplugin/html.lua`
- **Action**: Set buffer-local options only — no keymaps.
```lua
-- Cumulus HTML Buffer Conventions (Story 40.2 follow-up)

vim.bo.shiftwidth = 2
vim.bo.tabstop = 2
vim.bo.softtabstop = 2
vim.bo.expandtab = true
vim.bo.commentstring = "<!-- %s -->"
```

### Task 3: Create `ftplugin/xml.lua`
- **Target File**: `ftplugin/xml.lua`
- **Action**: Set buffer-local options only — no keymaps.
```lua
-- Cumulus XML Buffer Conventions (Epic 11/39 follow-up; LSP/formatting owned by lsp-devops.lua)

vim.bo.shiftwidth = 2
vim.bo.tabstop = 2
vim.bo.softtabstop = 2
vim.bo.expandtab = true
vim.bo.commentstring = "<!-- %s -->"
```

### Task 4: Post-Execution Verification
- Run the Verification Commands below and confirm the frozen-path assertion returns zero diffs.

---

## Verification Commands & Acceptance Checklist

```bash
# 1. Run the project's canonical verification suite
bash scripts/validate.sh

# 2. Validate Lua syntax of the new files
luac -p ftplugin/html.lua ftplugin/xml.lua

# 3. Run headless Neovim Lazy sync (should be a no-op — no plugin specs changed)
nvim --headless "+Lazy! sync" +qa

# 4. Check Neovim Health Report
nvim --headless "+checkhealth cumulus" +qa

# 5. Spot-check buffer conventions took effect
nvim --headless -c "edit /tmp/spec002-check.html" -c "lua assert(vim.bo.shiftwidth == 2); assert(vim.bo.commentstring == '<!-- %s -->'); print('html ok')" +qa
nvim --headless -c "edit /tmp/spec002-check.xml" -c "lua assert(vim.bo.shiftwidth == 2); assert(vim.bo.commentstring == '<!-- %s -->'); print('xml ok')" +qa

# 6. Assert DevOps Immutability Guardrail (must return NO modified files)
git status --short lua/cumulus/plugins/cloud-* lua/cumulus/plugins/lsp-devops.lua lua/cumulus/plugins/tools-dap-devops.lua

# 7. Assert No Duplicate Wiring (must return NO new lsp-xml.lua, no diff in already-complete files)
test ! -f lua/cumulus/plugins/lsp-xml.lua && echo "OK: no duplicate lsp-xml.lua"
git status --short lua/cumulus/plugins/lsp-html.lua lua/cumulus/plugins/tools-mason.lua lua/cumulus/plugins/tools-formatting.lua lua/cumulus/plugins/core-treesitter.lua
```

### Acceptance Criteria
- [ ] `ftplugin/html.lua` and `ftplugin/xml.lua` exist, contain buffer-options only, no keymaps.
- [ ] `luac` syntax validation returns exit code 0 for both new files.
- [ ] `scripts/validate.sh` and `nvim --headless "+Lazy! sync" +qa` complete cleanly.
- [ ] DevOps guardrail assertion (step 6) returns zero diffs.
- [ ] No Duplicate Wiring assertion (step 7) confirms `lsp-xml.lua` was not created and `lsp-html.lua`/`tools-mason.lua`/`tools-formatting.lua`/`core-treesitter.lua` are untouched.
- [ ] Neovim startup latency remains below the 50ms budget (unaffected — no plugin spec changed).
