-- TetraVim Core Keymaps (Story 1.1, Story 4.1 & Epic 9)

local map = vim.keymap.set

-- Leader alternatives for window navigation
map("n", "<leader>ww", "<C-w>w", { desc = "Cycle windows" })
map("n", "<leader>wh", "<C-w>h", { desc = "Focus left window" })
map("n", "<leader>wj", "<C-w>j", { desc = "Focus lower window" })
map("n", "<leader>wk", "<C-w>k", { desc = "Focus upper window" })
map("n", "<leader>wl", "<C-w>l", { desc = "Focus right window" })

-- File explorer shortcut required by the smoke test
vim.keymap.set('n', '<leader>e', function()
  require('oil').open()
end, { desc = 'Open file explorer (oil.nvim)' })

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
  require("tetravim.util.format").format({ force = true })
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
local lang_keymaps = require("tetravim.core.lang-keymaps")

-- ==============================================================================
-- ⭐ JVM PLATFORM KEYMAP SUITE (<leader>j) - Unconditionally Registered
-- ==============================================================================
local jvm = require("tetravim.util.jvm")
local jvm_ok, jvm_err = pcall(jvm.setup_keymaps)
if not jvm_ok then
  vim.notify("Failed to register JVM keymaps: " .. tostring(jvm_err), vim.log.levels.WARN, { title = "TetraVim JVM" })
end

-- ==============================================================================
-- 󱁢 Infrastructure & DevOps Platform Suite (<leader>o) - Globally Registered
-- ==============================================================================
local devops = require("tetravim.core.devops")
local ok, err = pcall(devops.setup_keymaps)
if not ok then
  vim.notify("Failed to register DevOps keymaps: " .. tostring(err), vim.log.levels.WARN, { title = "TetraVim DevOps" })
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

-- Database Client Keymaps (vim-dadbod UI)
map("n", "<leader>Du", "<cmd>DBUIToggle<cr>", { desc = "Toggle Database UI" })
map("n", "<leader>Df", "<cmd>DBUIFindBuffer<cr>", { desc = "Find DB Buffer" })
map("n", "<leader>Da", "<cmd>DBUIAddConnection<cr>", { desc = "Add DB Connection" })

-- HTTP Client & REST API Explorer Keymaps (kulala.nvim -- SPEC-3.2). The
-- plugin itself is wired up in tools-http.lua; the two custom pieces this
-- story adds (OpenAPI-to-.http generation, jq response filtering) live in
-- tetravim.util.openapi / tetravim.util.http. Response/generated-template
-- output always renders in a persistent split, never a floating window,
-- per this epic's established UX pattern.
local function tetravim_http_open_in_split(text, filetype, name_hint)
  -- Reuse a result window from a previous invocation -- its buffer name
  -- starts with "<name_hint>-" -- instead of stacking a fresh split on every
  -- call.
  local target_win
  for _, win in ipairs(vim.api.nvim_tabpage_list_wins(0)) do
    local buf = vim.api.nvim_win_get_buf(win)
    local ok_name, bufname = pcall(vim.api.nvim_buf_get_name, buf)
    if ok_name and vim.fs.basename(bufname):match("^" .. vim.pesc(name_hint) .. "%-") then
      target_win = win
      break
    end
  end

  if target_win and vim.api.nvim_win_is_valid(target_win) then
    vim.api.nvim_set_current_win(target_win)
  else
    -- Vertical, matching tools-http.lua's kulala.nvim `split_direction = "right"`
    -- so generated-template/jq-filtered output opens in the same orientation
    -- as kulala's own response split.
    vim.cmd("botright vsplit")
  end

  -- Unlisted scratch buffer: buftype=nofile + bufhidden=wipe + noswapfile so
  -- a stray `:w` can never dump this helper output into the repo and the
  -- buffer is discarded when its window goes away.
  local bufnr = vim.api.nvim_create_buf(false, true)
  vim.api.nvim_win_set_buf(0, bufnr)
  vim.api.nvim_buf_set_lines(bufnr, 0, -1, false, vim.split(text, "\n", { plain = true }))
  vim.bo[bufnr].buftype = "nofile"
  vim.bo[bufnr].bufhidden = "wipe"
  vim.bo[bufnr].swapfile = false
  vim.bo[bufnr].filetype = filetype
  vim.bo[bufnr].modified = false
  pcall(vim.api.nvim_buf_set_name, bufnr, name_hint .. "-" .. tostring(bufnr))
end

map("n", "<leader>Hr", function()
  if vim.bo.filetype ~= "http" then
    require("tetravim.util.ui").notify_err(
      "Open a .http file first -- <leader>Hr only runs requests from a .http buffer"
    )
    return
  end
  local ok, kulala = pcall(require, "kulala")
  if not ok then
    require("tetravim.util.ui").notify_err("kulala.nvim is not available -- open a .http file first")
    return
  end
  -- Guard kulala.run() so a malformed .http buffer surfaces a clean
  -- notification instead of a raw Lua stack trace.
  local run_ok, run_err = pcall(kulala.run)
  if not run_ok then
    require("tetravim.util.ui").notify_err("Failed to run HTTP request: " .. tostring(run_err))
  end
end, { desc = "Run HTTP Request" })

map("n", "<leader>Ho", function()
  vim.ui.input({ prompt = "OpenAPI JSON spec path: ", completion = "file" }, function(spec_path)
    if not spec_path or spec_path == "" then
      return
    end
    local http_text = require("tetravim.util.openapi").generate_http_from_spec(spec_path)
    if not http_text then
      return -- tetravim.util.openapi already warned via ui.notify_warn
    end
    tetravim_http_open_in_split(http_text, "http", "generated")
    require("tetravim.util.ui").notify_info("Generated .http request template from " .. spec_path)
  end)
end, { desc = "Generate .http from OpenAPI Spec" })

map("n", "<leader>Hj", function()
  local ui = require("tetravim.util.ui")
  local ft = vim.bo.filetype

  -- A .http source buffer is not a response -- jq has nothing useful to do
  -- with it. Send the user to the response window (or a JSON buffer) rather
  -- than shelling out to jq on request syntax.
  if ft == "http" then
    ui.notify_err(
      "<leader>Hj filters a JSON response, not a .http source -- move to the response window or a JSON buffer first"
    )
    return
  end

  local json_text = table.concat(vim.api.nvim_buf_get_lines(0, 0, -1, false), "\n")
  if json_text == "" then
    ui.notify_warn("Current buffer is empty -- nothing to filter")
    return
  end

  -- Soft heads-up only -- jq can still usefully filter JSON Lines / a
  -- concatenated stream of JSON values that vim.json.decode (which expects
  -- exactly one top-level value) rejects, so a decode failure here warns but
  -- does NOT abort the filter. kulala's own response window
  -- (filetype=kulala_ui) renders a known-good body, so skip the check there.
  if ft ~= "kulala_ui" and not require("tetravim.util.http").looks_like_json(json_text) then
    ui.notify_warn("Current buffer does not look like valid JSON -- jq may fail or produce unexpected output")
  end

  vim.ui.input({ prompt = "jq filter (e.g. .data): " }, function(filter_expr)
    if not filter_expr or filter_expr == "" then
      return
    end
    require("tetravim.util.http").jq_filter(json_text, filter_expr, function(result_text)
      tetravim_http_open_in_split(result_text, "json", "jq-filtered")
    end)
  end)
end, { desc = "jq-Filter JSON Response/Buffer" })

-- gRPC & Protobufs Integration Keymaps (SPEC-3.4). The `.proto` LSP
-- (protols), Tree-sitter parser and `buf` formatter are wired in
-- lsp-proto.lua / core-treesitter.lua / tools-formatting.lua; the two
-- custom pieces this story adds -- reflection-driven service/method
-- browsing and structured RPC execution -- live in tetravim.util.grpc.
-- Every gRPC output renders in the shared persistent split via
-- tetravim_http_open_in_split, never a floating window.
local function tetravim_grpc_prompt_addr(cb)
  vim.ui.input({ prompt = "gRPC server (host:port): ", default = "localhost:50051" }, function(addr)
    if not addr or vim.trim(addr) == "" then
      return
    end
    cb(vim.trim(addr))
  end)
end

-- Open the editable JSON request skeleton in a persistent "grpc-request"
-- split and bind a buffer-local <CR> that reads it back, refuses malformed
-- JSON (never handing it to grpcurl), invokes the RPC async and renders the
-- response in a persistent "grpc-response" json split.
local function tetravim_grpc_open_request(addr, method, skeleton_text)
  local ui = require("tetravim.util.ui")
  tetravim_http_open_in_split(skeleton_text, "json", "grpc-request")
  local bufnr = vim.api.nvim_get_current_buf()
  vim.keymap.set("n", "<CR>", function()
    local grpc = require("tetravim.util.grpc")
    local payload = table.concat(vim.api.nvim_buf_get_lines(bufnr, 0, -1, false), "\n")
    if not require("tetravim.util.http").looks_like_json(payload) then
      ui.notify_err("gRPC request buffer is not valid JSON -- fix it before pressing <CR> (nothing sent)")
      return
    end
    grpc.invoke(addr, method, payload, function(response)
      tetravim_http_open_in_split(response, "json", "grpc-response")
    end)
  end, { buffer = bufnr, desc = "gRPC: Invoke RPC with this payload" })
  ui.notify_info("Edit the payload, then press <CR> in this buffer to invoke " .. method)
end

-- Given a fully-qualified method ("pkg.Service/Method" or
-- "pkg.Service.Method"), resolve its request message type via `grpcurl
-- describe`, then a second `describe -msg-template` for that type, and open
-- the generated skeleton for editing.
local function tetravim_grpc_build_request(addr, method)
  local grpc = require("tetravim.util.grpc")
  local ui = require("tetravim.util.ui")
  grpc.describe(addr, (method:gsub("/", ".")), function(method_desc)
    local parsed = grpc.parse_methods(method_desc)
    if #parsed == 0 or parsed[1].request_type == "" then
      ui.notify_err("Could not determine the request type for " .. method)
      return
    end
    grpc.describe(addr, parsed[1].request_type, function(type_desc)
      local template = grpc.extract_msg_template(type_desc)
      local skeleton = grpc.request_skeleton(template or "")
      if not skeleton then
        return -- request_skeleton already warned
      end
      tetravim_grpc_open_request(addr, method, skeleton)
    end)
  end)
end

map("n", "<leader>G", function()
  require("grpcui").open()
end, { desc = "gRPC UI (grpcurl)" })
map("n", "<leader>Gl", function()
  local grpc = require("tetravim.util.grpc")
  local ui = require("tetravim.util.ui")
  tetravim_grpc_prompt_addr(function(addr)
    grpc.list_services(addr, function(out)
      local services = grpc.parse_service_list(out)
      if #services == 0 then
        ui.notify_warn("No gRPC services reported by " .. addr)
        return
      end
      vim.ui.select(services, { prompt = "gRPC service:" }, function(service)
        if not service then
          return
        end
        grpc.describe(addr, service, function(service_desc)
          local methods = grpc.parse_methods(service_desc)
          if #methods == 0 then
            tetravim_http_open_in_split(service_desc, "proto", "grpc-describe")
            return
          end
          local labels = {}
          for _, m in ipairs(methods) do
            table.insert(labels, m.name)
          end
          vim.ui.select(labels, { prompt = service .. " method:" }, function(choice, idx)
            if not choice or not idx then
              return
            end
            tetravim_grpc_build_request(addr, service .. "/" .. methods[idx].name)
          end)
        end)
      end)
    end)
  end)
end, { desc = "gRPC: List Services & Methods" })

map("n", "<leader>Gm", function()
  local grpc = require("tetravim.util.grpc")
  local default_symbol = vim.fn.expand("<cword>")
  vim.ui.input({ prompt = "gRPC symbol to describe: ", default = default_symbol }, function(symbol)
    if not symbol or vim.trim(symbol) == "" then
      return
    end
    tetravim_grpc_prompt_addr(function(addr)
      grpc.describe(addr, vim.trim(symbol), function(desc)
        tetravim_http_open_in_split(desc, "proto", "grpc-describe")
      end)
    end)
  end)
end, { desc = "gRPC: Describe Symbol" })

map("n", "<leader>Gi", function()
  vim.ui.input({ prompt = "gRPC method (pkg.Service/Method): " }, function(method)
    if not method or vim.trim(method) == "" then
      return
    end
    tetravim_grpc_prompt_addr(function(addr)
      tetravim_grpc_build_request(addr, vim.trim(method))
    end)
  end)
end, { desc = "gRPC: Generate Request Skeleton" })

map("n", "<leader>Gf", function()
  local ui = require("tetravim.util.ui")
  if vim.bo.filetype ~= "proto" then
    ui.notify_err("<leader>Gf formats a .proto buffer -- open one first")
    return
  end
  local ok, conform = pcall(require, "conform")
  if not ok then
    ui.notify_err("conform.nvim is not available")
    return
  end
  conform.format({ bufnr = 0, async = false, lsp_fallback = true })
end, { desc = "gRPC: Format .proto Buffer (buf)" })

-- Autoformat toggle (Story 34.2): <leader>uf toggles for the current
-- buffer only, <leader>uF toggles the global default
map("n", "<leader>uf", function()
  require("tetravim.util.format").toggle(true)
end, { desc = "Toggle Autoformat (Buffer)" })
map("n", "<leader>uF", function()
  require("tetravim.util.format").toggle(false)
end, { desc = "Toggle Autoformat (Global)" })

-- Background transparency toggle: blanks the editor / float surfaces so a
-- translucent terminal shows through, restores the Tetris surfaces on the
-- way back.
map("n", "<leader>ut", function()
  require("tetravim.util.transparency").toggle()
end, { desc = "Toggle Transparency" })

-- Universal File Operations: Save, Save All, Save As (Epic 33)
local function save_current_file()
  local name = vim.api.nvim_buf_get_name(0)
  if name == "" then
    -- Unnamed/scratch buffer: prompt for a file name instead of crashing with E32
    vim.notify("Buffer has no file name — use :saveas or :w <filename>", vim.log.levels.WARN)
    return
  end
  vim.cmd("update")
  local short = vim.fn.fnamemodify(name, ":t")
  vim.notify("Saved " .. short, vim.log.levels.INFO)
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
