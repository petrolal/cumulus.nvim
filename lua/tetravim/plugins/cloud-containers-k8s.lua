-- TetraVim Containers & Kubernetes (Docker & Helm) Specs (Story 3.3 & FR6)

return {
  {
    "nvim-treesitter/nvim-treesitter",
    opts = function(_, opts)
      if type(opts.ensure_installed) == "table" then
        vim.list_extend(opts.ensure_installed, { "dockerfile", "helm", "yaml" })
      end
    end,
  },

  {
    "neovim/nvim-lspconfig",
    opts = {
      servers = {
        dockerls = {},
        helm_ls = {},
      },
    },
  },
}
