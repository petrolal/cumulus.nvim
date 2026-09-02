-- TetraVim DevOps Data & Markup LSP Stack (Epic 11 - Story 11.2, Epic 39 - Story 39.1)

return {
  {
    "nvim-treesitter/nvim-treesitter",
    opts = function(_, opts)
      if type(opts.ensure_installed) == "table" then
        vim.list_extend(opts.ensure_installed, {
          "json",
          "xml",
          "bash",
        })
      end
    end,
  },

  {
    "neovim/nvim-lspconfig",
    opts = {
      servers = {
        jsonls = {},
        lemminx = {},
        bashls = {
          filetypes = { "sh", "bash" },
        },
      },
    },
  },
}
