-- Cumulus Core Keymaps (Story 1.1, Story 4.1 & Epic 9)

local map = vim.keymap.set

-- Leader alternatives for window navigation
map("n", "<leader>ww", "<C-w>w", { desc = "Cycle windows" })
map("n", "<leader>wh", "<C-w>h", { desc = "Focus left window" })
map("n", "<leader>wj", "<C-w>j", { desc = "Focus lower window" })
map("n", "<leader>wk", "<C-w>k", { desc = "Focus upper window" })
map("n", "<leader>wl", "<C-w>l", { desc = "Focus right window" })

-- Visual Selection & Line Movement Chords (Story 9.2)
map("v", "J", ":m '>+1<cr>gv=gv", { desc = "Move Down" })
map("v", "K", ":m '<-2<cr>gv=gv", { desc = "Move Up" })
map("v", "<", "<gv", { desc = "Outdent and Reselect" })
map("v", ">", ">gv", { desc = "Indent and Reselect" })
map("n", "n", "nzzzv", { desc = "Next Search Centered" })
map("n", "N", "Nzzzv", { desc = "Prev Search Centered" })

-- LSP Diagnostics & Symbol Navigation Chords (Story 9.3 & Story 13.2)
map("n", "[d", function()
  vim.diagnostic.jump({ count = -1 })
end, { desc = "Prev Diagnostic" })
map("n", "]d", function()
  vim.diagnostic.jump({ count = 1 })
end, { desc = "Next Diagnostic" })
map("n", "[e", function()
  vim.diagnostic.jump({ count = -1, severity = vim.diagnostic.severity.ERROR })
end, { desc = "Prev Error" })
map("n", "]e", function()
  vim.diagnostic.jump({ count = 1, severity = vim.diagnostic.severity.ERROR })
end, { desc = "Next Error" })
map("n", "<leader>ca", function()
  vim.lsp.buf.code_action()
end, { desc = "Code Action" })
map("n", "<leader>cr", function()
  vim.lsp.buf.rename()
end, { desc = "Rename Symbol" })

-- Global code group keymaps: format, diagnostics, codelens, organize
-- imports, source action, rename file, lsp info (Story 34.2)
map("n", "<leader>cd", function()
  vim.diagnostic.open_float()
end, { desc = "Line Diagnostics" })
map({ "n", "x" }, "<leader>cf", function()
  require("cumulus.util.format").format({ force = true })
end, { desc = "Format" })
map({ "n", "x" }, "<leader>cF", function()
  require("conform").format({ formatters = { "injected" }, timeout_ms = 3000 })
end, { desc = "Format Injected Langs" })
map({ "n", "x" }, "<leader>cc", function()
  vim.lsp.codelens.run()
end, { desc = "Run Codelens" })
map("n", "<leader>cC", function()
  vim.lsp.codelens.refresh()
end, { desc = "Refresh & Display Codelens" })
map("n", "<leader>co", function()
  vim.lsp.buf.code_action({
    context = { only = { "source.organizeImports" }, diagnostics = {} },
    apply = true,
  })
end, { desc = "Organize Imports" })
map("n", "<leader>cA", function()
  vim.lsp.buf.code_action({
    context = { only = { "source" }, diagnostics = {} },
  })
end, { desc = "Source Action" })
map("n", "<leader>cR", function()
  local old_name = vim.api.nvim_buf_get_name(0)
  vim.ui.input({ prompt = "New file name: ", default = vim.fn.fnamemodify(old_name, ":t") }, function(new_name)
    if not new_name or new_name == "" then
      return
    end
    local new_path = vim.fn.fnamemodify(old_name, ":h") .. "/" .. new_name
    vim.lsp.util.rename(old_name, new_path)
    vim.cmd("edit " .. vim.fn.fnameescape(new_path))
  end)
end, { desc = "Rename File" })
-- `:LspInfo` (nvim-lspconfig) is a dead command on Neovim 0.11+: its
-- plugin/lspconfig.lua skips defining LspInfo/LspStart/LspStop entirely
-- once Neovim's own native `:lsp` command exists (see
-- `vim.fn.exists(':lsp')` check in nvim-lspconfig's plugin file). That
-- native `:lsp` only has enable/disable/restart/stop subcommands, no info
-- view, so `:checkhealth vim.lsp` is the actual replacement -- it lists
-- active clients, capabilities, and file-watcher status.
map("n", "<leader>cl", "<cmd>checkhealth vim.lsp<cr>", { desc = "Lsp Info" })

-- Per-language <leader>c* subgroups (Story 34.1): build/lint/format commands
-- for a given language stack only appear as buffer-local keymaps while
-- editing a matching filetype, so <leader>c no longer mixes e.g. Maven
-- keymaps into a Python or Terraform buffer's popup. See lang-keymaps.lua.
local lang_keymaps = require("cumulus.core.lang-keymaps")

-- ==============================================================================
-- ⭐ JVM PLATFORM KEYMAP SUITE (<leader>j) - Unconditionally Registered
-- ==============================================================================
local jvm = require("cumulus.util.jvm")
local jvm_ok, jvm_err = pcall(jvm.setup_keymaps)
if not jvm_ok then
  vim.notify("Failed to register JVM keymaps: " .. tostring(jvm_err), vim.log.levels.WARN, { title = "Cumulus JVM" })
end

-- ==============================================================================
-- 󱁢 Infrastructure & DevOps Platform Suite (<leader>o) - Globally Registered
-- ==============================================================================
local devops = require("cumulus.core.devops")
local ok, err = pcall(devops.setup_keymaps)
if not ok then
  vim.notify("Failed to register DevOps keymaps: " .. tostring(err), vim.log.levels.WARN, { title = "Cumulus DevOps" })
end

lang_keymaps.setup()

-- Plugin & Package Management Keymaps (<leader>l)
map("n", "<leader>ll", "<cmd>Lazy<cr>", { desc = "Lazy Plugin Manager" })
map("n", "<leader>lm", "<cmd>Mason<cr>", { desc = "Mason Tool Manager" })
map("n", "<leader>lc", "<cmd>checkhealth<cr>", { desc = "Checkhealth System" })

-- Buffer Navigation Keymaps (<leader>b)
map("n", "<leader>bp", "<cmd>bprevious<cr>", { desc = "Previous Buffer" })
map("n", "<leader>bn", "<cmd>bnext<cr>", { desc = "Next Buffer" })

-- Window Management Splits & Navigation (<leader>w)
map("n", "<leader>ws", "<cmd>split<cr>", { desc = "Split Window Horizontally" })
map("n", "<leader>wv", "<cmd>vsplit<cr>", { desc = "Split Window Vertically" })
map("n", "<leader>wd", "<cmd>close<cr>", { desc = "Close Window" })

-- Session & Quit Keymaps (Story 10.1 & Story 29.1)
map("n", "<leader>qq", "<cmd>confirm qa<cr>", { desc = "Quit Neovim (Confirm)" })
map("n", "<leader>qQ", "<cmd>qa!<cr>", { desc = "Force Quit Neovim (No Save)" })

-- Cloud Theme Switcher Keymap (Story 31.2)
map("n", "<leader>ut", function()
  require("cumulus.theme").select_theme()
end, { desc = "Select Cloud Theme (AWS/Azure/GCP/OCI)" })

-- Database Client Keymaps (vim-dadbod UI)
map("n", "<leader>Du", "<cmd>DBUIToggle<cr>", { desc = "Toggle Database UI" })
map("n", "<leader>Df", "<cmd>DBUIFindBuffer<cr>", { desc = "Find DB Buffer" })
map("n", "<leader>Da", "<cmd>DBUIAddConnection<cr>", { desc = "Add DB Connection" })

-- HTTP Client & REST API Explorer Keymaps (kulala.nvim -- SPEC-3.2). The
-- plugin itself is wired up in tools-http.lua; the two custom pieces this
-- story adds (OpenAPI-to-.http generation, jq response filtering) live in
-- cumulus.util.openapi / cumulus.util.http. Response/generated-template
-- output always renders in a persistent split, never a floating window,
-- per this epic's established UX pattern.
local function cumulus_http_open_in_split(text, filetype, name_hint)
  -- Vertical, matching tools-http.lua's kulala.nvim `split_direction = "right"`
  -- so generated-template/jq-filtered output opens in the same orientation
  -- as kulala's own response split.
  vim.cmd("botright vsplit")
  local bufnr = vim.api.nvim_create_buf(true, false)
  vim.api.nvim_win_set_buf(0, bufnr)
  vim.api.nvim_buf_set_lines(bufnr, 0, -1, false, vim.split(text, "\n", { plain = true }))
  vim.bo[bufnr].filetype = filetype
  vim.bo[bufnr].swapfile = false
  vim.bo[bufnr].modified = false
  pcall(vim.api.nvim_buf_set_name, bufnr, name_hint .. "-" .. tostring(bufnr))
end

map("n", "<leader>Hr", function()
  if vim.bo.filetype ~= "http" then
    require("cumulus.util.ui").notify_err(
      "Open a .http file first -- <leader>Hr only runs requests from a .http buffer"
    )
    return
  end
  local ok, kulala = pcall(require, "kulala")
  if not ok then
    require("cumulus.util.ui").notify_err("kulala.nvim is not available -- open a .http file first")
    return
  end
  kulala.run()
end, { desc = "Run HTTP Request" })

map("n", "<leader>Ho", function()
  vim.ui.input({ prompt = "OpenAPI JSON spec path: ", completion = "file" }, function(spec_path)
    if not spec_path or spec_path == "" then
      return
    end
    local http_text = require("cumulus.util.openapi").generate_http_from_spec(spec_path)
    if not http_text then
      return -- cumulus.util.openapi already warned via ui.notify_warn
    end
    cumulus_http_open_in_split(http_text, "http", "generated")
    require("cumulus.util.ui").notify_info("Generated .http request template from " .. spec_path)
  end)
end, { desc = "Generate .http from OpenAPI Spec" })

map("n", "<leader>Hj", function()
  local json_text = table.concat(vim.api.nvim_buf_get_lines(0, 0, -1, false), "\n")
  if json_text == "" then
    require("cumulus.util.ui").notify_warn("Current buffer is empty -- nothing to filter")
    return
  end
  vim.ui.input({ prompt = "jq filter (e.g. .data): " }, function(filter_expr)
    if not filter_expr or filter_expr == "" then
      return
    end
    require("cumulus.util.http").jq_filter(json_text, filter_expr, function(result_text)
      cumulus_http_open_in_split(result_text, "json", "jq-filtered")
    end)
  end)
end, { desc = "jq-Filter Last Response" })

-- Autoformat toggle (Story 34.2): <leader>uf toggles for the current
-- buffer only, <leader>uF toggles the global default
map("n", "<leader>uf", function()
  require("cumulus.util.format").toggle(true)
end, { desc = "Toggle Autoformat (Buffer)" })
map("n", "<leader>uF", function()
  require("cumulus.util.format").toggle(false)
end, { desc = "Toggle Autoformat (Global)" })

-- Universal File Operations: Save, Save All, Save As (Epic 33)
local function save_current_file()
  vim.cmd("update")
  local name = vim.fn.expand("%:t")
  if name == "" then
    name = "[No Name]"
  end
  vim.notify("Saved " .. name, vim.log.levels.INFO)
end

map({ "n", "i" }, "<C-s>", save_current_file, { desc = "Save Current File" })
map("n", "<leader>fs", save_current_file, { desc = "Save Current File" })

map("n", "<leader>fa", function()
  vim.cmd("wall")
  vim.notify("Saved all modified files", vim.log.levels.INFO)
end, { desc = "Save All Files" })

map("n", "<leader>fS", function()
  local current = vim.fn.expand("%:p")
  vim.ui.input({ prompt = " Save As: ", default = current }, function(input)
    if input and #input > 0 then
      vim.cmd("saveas! " .. vim.fn.fnameescape(input))
      vim.notify("Saved as: " .. input, vim.log.levels.INFO)
    end
  end)
end, { desc = "Save As..." })
