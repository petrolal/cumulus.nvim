-- TetraVim HTML Language Stack Integration (Story 40.2)
--
-- Provides HTML support via vscode-html-language-server (html)
-- and superhtml with Tree-sitter parsers.

return {
  {
    "nvim-treesitter/nvim-treesitter",
    opts = function(_, opts)
      if type(opts.ensure_installed) == "table" then
        vim.list_extend(opts.ensure_installed, { "html" })
      end
    end,
  },

  {
    "neovim/nvim-lspconfig",
    opts = {
      servers = {
        html = {},
        superhtml = {
          filetypes = { "superhtml" },
        },
      },
    },
  },
}
