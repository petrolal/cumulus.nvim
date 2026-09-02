-- TetraVim Terraform & OpenTofu Integration (Story 3.1 & FR3)

return {
  {
    "nvim-treesitter/nvim-treesitter",
    opts = function(_, opts)
      if type(opts.ensure_installed) == "table" then
        vim.list_extend(opts.ensure_installed, { "hcl", "terraform" })
      end
    end,
  },

  {
    "neovim/nvim-lspconfig",
    opts = {
      servers = {
        terraformls = {
          filetypes = { "terraform", "terraform-vars", "hcl" },
        },
        tflint = {},
      },
    },
  },
}
