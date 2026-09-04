-- TetraVim CSS Language Stack Integration
--
-- Provides CSS, SCSS, and LESS support via vscode-css-language-server (cssls)
-- and Tree-sitter parsers.

return {
  {
    "nvim-treesitter/nvim-treesitter",
    opts = function(_, opts)
      if type(opts.ensure_installed) == "table" then
        vim.list_extend(opts.ensure_installed, { "css" })
      end
    end,
  },

  {
    "neovim/nvim-lspconfig",
    opts = {
      servers = {
        cssls = {},
      },
    },
  },
}
