-- Cumulus Scala LSP + DAP Integration (scalameta/nvim-metals) -- SPEC-1.1
--
-- Metals self-registers its own DAP adapter/configurations the same way
-- jdtls does (see ftplugin/java.lua's jdtls.setup_dap call): once attached,
-- calling metals.setup_dap() populates dap.configurations.scala with no
-- manual launch JSON required from the user.

return {
  {
    "scalameta/nvim-metals",
    ft = { "scala", "sbt" },
    dependencies = {
      "nvim-lua/plenary.nvim",
      "mfussenegger/nvim-dap",
    },
    opts = function()
      local metals_config = require("metals").bare_config()

      metals_config.on_attach = function(client, bufnr)
        -- Mirrors jdtls.setup_dap({ hotcodereplace = "auto" }) in
        -- ftplugin/java.lua: registers dap.adapters.scala and
        -- dap.configurations.scala from Metals' own DAP discovery, so no
        -- manual launch JSON is ever required (AC-1).
        require("metals").setup_dap()
      end

      return metals_config
    end,
    config = function(self, metals_config)
      local metals_group = vim.api.nvim_create_augroup("cumulus_nvim_metals", { clear = true })
      vim.api.nvim_create_autocmd("FileType", {
        group = metals_group,
        pattern = self.ft,
        callback = function()
          require("metals").initialize_or_attach(metals_config)
        end,
      })
    end,
  },
}
