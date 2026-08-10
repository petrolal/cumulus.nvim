-- Cumulus Core Autocmds (Story 1.1)

local function augroup(name)
  return vim.api.nvim_create_augroup("cumulus_" .. name, { clear = true })
end

-- Discard any stray keystrokes typed into the terminal while Neovim was
-- still starting up (e.g. an extra "n" or "g" pressed right after
-- `nvim<CR>`). Without this, that buffered input gets replayed as
-- normal-mode commands the instant the dashboard buffer's single-key
-- mappings become active, unexpectedly opening a scratch buffer or the
-- grep picker instead of showing the dashboard.
-- NOTE: this must be deferred with vim.schedule rather than run inline in
-- the VimEnter callback -- some terminals (e.g. kitty) negotiate extended
-- keyboard protocol support over the same input stream right around
-- startup, and draining raw getchar() input synchronously at VimEnter can
-- race with and corrupt that in-flight handshake, which then shows up as
-- every keystroke getting duplicated for the rest of the session.
vim.api.nvim_create_autocmd("VimEnter", {
  group = augroup("flush_typeahead"),
  callback = function()
    vim.schedule(function()
      while vim.fn.getchar(1) ~= 0 do
        vim.fn.getchar()
      end
    end)
  end,
})

-- Auto-open README.md once when Neovim is started on a project directory
-- (e.g. `nvim .`), so the project's landing doc is visible instead of a
-- blank "[No Name]" buffer. If there's no README, that blank buffer is left
-- alone -- it's already what shows up by default.
--
-- Snacks' netrw-replacement explorer opens as a *floating* picker on top of
-- that plain background window/buffer rather than replacing it, so editing
-- the README into the background window (instead of `:edit`-ing in the
-- window that's current when this fires, or opening a new tab) shows the
-- README and keeps the explorer float visible at the same time, with no
-- extra tab and no leftover blank buffer.
vim.api.nvim_create_autocmd("VimEnter", {
  group = augroup("open_readme"),
  once = true,
  callback = function()
    local dir = nil
    for _, arg in ipairs(vim.fn.argv() --[[@as string[] ]]) do
      if vim.fn.isdirectory(arg) == 1 then
        dir = vim.fn.fnamemodify(arg, ":p")
        break
      end
    end
    if not dir then
      return
    end

    local matches = vim.fn.globpath(dir, "[Rr][Ee][Aa][Dd][Mm][Ee].md", false, true)
    if #matches == 0 then
      return
    end

    vim.schedule(function()
      for _, win in ipairs(vim.api.nvim_tabpage_list_wins(0)) do
        local buf = vim.api.nvim_win_get_buf(win)
        if vim.bo[buf].buftype == "" then
          vim.api.nvim_win_call(win, function()
            vim.cmd.edit(vim.fn.fnameescape(matches[1]))
            -- `:edit` on an already-loaded buffer keeps its last cursor
            -- position; force it back to the top so the README always
            -- opens at line 1, not wherever it was last left off.
            vim.api.nvim_win_set_cursor(win, { 1, 0 })
          end)
          break
        end
      end

      -- `nvim_win_call` above never moves focus (it runs the callback in
      -- the target window's context and restores focus immediately after),
      -- but explicitly refocusing the explorer here removes any dependency
      -- on that implicit behavior and any ordering with Snacks' own
      -- explorer-open scheduling -- land back on the explorer as directly
      -- as possible instead of leaving focus sitting in the README window.
      local ok, picker_mod = pcall(require, "snacks.picker")
      if ok then
        local explorer = picker_mod.get({ source = "explorer" })[1]
        if explorer then
          explorer:focus()
        end
      end
    end)
  end,
})

-- Sync Maven/Gradle dependencies once per session, at startup -- mirrors
-- the background "Syncing project..." step IDEs run automatically on open.
-- Runs on VimEnter regardless of which (if any) file gets opened first, so
-- it doesn't depend on happening to open a .java/.kt/.groovy/pom.xml/
-- build.gradle buffer -- it just checks whether the project has a pom.xml
-- or build.gradle(.kts) at all and syncs if so.
vim.api.nvim_create_autocmd("VimEnter", {
  group = augroup("build_sync"),
  once = true,
  callback = function()
    vim.schedule(function()
      require("cumulus.util.build-sync-state").run()
    end)
  end,
})

-- Re-sync Maven/Gradle dependencies whenever the project's build file is
-- saved -- mirrors IntelliJ's "auto-reload changed Maven/Gradle projects"
-- behavior instead of requiring a full Neovim restart to pick up new
-- dependencies. build-sync-state.lua's M.syncing guard (see M.run()) makes
-- this safe against overlapping saves -- a save that lands while a sync is
-- already in flight is a no-op, not a second process.
vim.api.nvim_create_autocmd("BufWritePost", {
  group = augroup("build_sync_on_save"),
  -- The first three are bare filenames -- Neovim matches those against just
  -- the tail, regardless of directory. The version catalog needs a leading
  -- "*/" since it contains a path separator: unlike shell globs, "*" in
  -- autocmd patterns crosses "/" boundaries, so "*/gradle/libs.versions.toml"
  -- matches that file at any project root, not just one directory up.
  pattern = { "pom.xml", "build.gradle", "build.gradle.kts", "*/gradle/libs.versions.toml" },
  callback = function()
    local sync_state = require("cumulus.util.build-sync-state")
    sync_state.reset()
    sync_state.run()
  end,
})

-- Highlight on yank
vim.api.nvim_create_autocmd("TextYankPost", {
  group = augroup("highlight_yank"),
  callback = function()
    (vim.hl or vim.highlight).on_yank()
  end,
})

-- Resize splits if window got resized
vim.api.nvim_create_autocmd({ "VimResized" }, {
  group = augroup("resize_splits"),
  callback = function()
    local current_tab = vim.fn.tabpagenr()
    vim.cmd("tabdo wincmd =")
    vim.cmd("tabnext " .. current_tab)
  end,
})

-- Close some filetypes with <q>
vim.api.nvim_create_autocmd("FileType", {
  group = augroup("close_with_q"),
  pattern = {
    "PlenaryTestPopup",
    "grug-far",
    "help",
    "lspinfo",
    "notify",
    "qf",
    "spectre_panel",
    "startuptime",
    "tsplayground",
    "neotest-output-panel",
    "checkhealth",
    "neotest-summary",
    "neotest-output",
  },
  callback = function(event)
    vim.bo[event.buf].buflisted = false
    vim.keymap.set("n", "q", "<cmd>close<cr>", {
      buffer = event.buf,
      silent = true,
      desc = "Quit buffer",
    })
  end,
})

-- Auto-insert Java package declaration and class header for new .java files (Story 38.3 & SPEC-017)
vim.api.nvim_create_autocmd("BufNewFile", {
  group = augroup("java_new_file"),
  pattern = "*.java",
  callback = function(event)
    local filepath = vim.api.nvim_buf_get_name(event.buf)
    local filename = vim.fn.fnamemodify(filepath, ":t:r")
    if filename == "" then
      return
    end

    local rust = require("cumulus.util.rust")
    local lines = nil
    if rust.is_available() then
      lines = rust.generate_java_header(filepath)
    end

    if not lines then
      local package_path = filepath:match("src/[^/]+/java/(.+)%/" .. filename .. "%.java")
        or filepath:match("src/(.+)%/" .. filename .. "%.java")

      lines = {}
      if package_path then
        local package_name = package_path:gsub("/", ".")
        table.insert(lines, "package " .. package_name .. ";")
        table.insert(lines, "")
      end

      table.insert(lines, "public class " .. filename .. " {")
      table.insert(lines, "    ")
      table.insert(lines, "}")
    end

    vim.api.nvim_buf_set_lines(event.buf, 0, -1, false, lines)
    vim.api.nvim_win_set_cursor(0, { #lines - 1, 4 })
  end,
})
