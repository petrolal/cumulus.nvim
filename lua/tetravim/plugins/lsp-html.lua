-- TetraVim HTML Language Stack Integration (Story 40.2)
--
-- Uses superhtml (Zig binary) rather than vscode-html-language-server:
-- TetraVim dropped its Node/JS toolchain in Epic 39, and superhtml provides
-- both LSP diagnostics and `<leader>cf` formatting without pulling npm back
-- in as a runtime dependency.

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
        superhtml = {},
      },
    },
  },
}
