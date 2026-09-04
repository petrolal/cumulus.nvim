-- Auto‑refresh Mason registry before mason‑tool‑installer runs
vim.api.nvim_create_autocmd("VimEnter", {
  once = true,
  callback = function()
    -- Defer a short time so other VimEnter autocmds (e.g., mason-tool-installer) run first
    vim.defer_fn(function()
      vim.cmd("MasonUpdate")
    end, 100)
  end,
})
