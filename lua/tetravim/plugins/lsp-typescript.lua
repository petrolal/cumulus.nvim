-- TetraVim JavaScript & TypeScript Language Stack Integration
--
-- Provides JS/TS/JSX/TSX support via typescript-language-server (ts_ls)
-- and Tree-sitter parsers.

return {
  {
    "nvim-treesitter/nvim-treesitter",
    opts = function(_, opts)
      if type(opts.ensure_installed) == "table" then
        vim.list_extend(opts.ensure_installed, { "javascript", "typescript", "tsx" })
      end
    end,
  },

  {
    "neovim/nvim-lspconfig",
    opts = {
      servers = {
        ts_ls = {},
      },
    },
  },
}
