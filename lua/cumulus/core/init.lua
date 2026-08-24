-- Cumulus Core initialization
require("cumulus.core.options")
require("cumulus.core.keymaps")
require("cumulus.core.autocmds")

-- Register :CumulusInstallEngine command (Story 7.2)
vim.api.nvim_create_user_command("CumulusInstallEngine", function()
  require("cumulus.util.engine").install()
end, { desc = "Download and install pre-built cumulus-engine binary" })
