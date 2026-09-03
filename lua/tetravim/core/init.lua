-- TetraVim Core initialization
require("tetravim.core.options")
require("tetravim.core.keymaps")
require("tetravim.core.autocmds")

-- Legacy command compatibility shim
vim.api.nvim_create_user_command("TetraVimInstallEngine", function()
  vim.notify(
    "TetraVim engine binary is decommissioned. TetraVim now uses native Neovim LSPs and Mason tools (:Mason).",
    vim.log.levels.INFO
  )
end, { desc = "Deprecated: engine is replaced by native LSPs and Mason" })
