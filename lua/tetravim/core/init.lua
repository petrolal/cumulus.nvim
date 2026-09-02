-- TetraVim Core initialization
require("tetravim.core.options")
require("tetravim.core.keymaps")
require("tetravim.core.autocmds")

-- Register :TetraVimInstallEngine command (Story 7.2)
vim.api.nvim_create_user_command("TetraVimInstallEngine", function()
  require("tetravim.util.engine").install()
end, { desc = "Download and install pre-built tetravim-engine binary" })
