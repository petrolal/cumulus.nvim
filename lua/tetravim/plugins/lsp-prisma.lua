-- TetraVim Prisma Language Stack -- IntelliJ IDEA Ultimate bundled "Prisma ORM" parity
--
-- prismals wraps @prisma/language-server (Mason: prisma-language-server):
-- schema diagnostics, completion, hover, formatting and go-to-definition for
-- `.prisma` files.

return {
  {
    "nvim-treesitter/nvim-treesitter",
    opts = function(_, opts)
      if type(opts.ensure_installed) == "table" then
        vim.list_extend(opts.ensure_installed, { "prisma" })
      end
    end,
  },

  {
    "neovim/nvim-lspconfig",
    opts = {
      servers = {
        prismals = {},
      },
    },
  },
}
