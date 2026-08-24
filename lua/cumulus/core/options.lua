-- Cumulus Core Options (Story 1.1 & Story 2.1)

vim.g.mapleader = " "
vim.g.maplocalleader = "\\"

vim.g.have_nerd_font = true

-- Enable 24-bit true color support for theme highlights (Story 5.1)
vim.opt.termguicolors = true

-- Sync yank/paste with the OS clipboard (fixes y/p not reaching system clipboard)
vim.opt.clipboard = "unnamedplus"

-- Load persisted Cloud Theme (Story 31.2)
vim.schedule(function()
  require("cumulus.theme").load_saved_theme()
end)
