-- TetraVim Groovy Full Stack Integration (Story 40.1)

-- Jenkinsfile has no file extension, so Neovim's built-in filetype
-- detection never maps it to "groovy" on its own.
vim.filetype.add({
  filename = {
    Jenkinsfile = "groovy",
  },
})

return {
  {
    "nvim-treesitter/nvim-treesitter",
    opts = function(_, opts)
      if type(opts.ensure_installed) == "table" then
        vim.list_extend(opts.ensure_installed, { "groovy" })
      end
    end,
  },

  {
    "neovim/nvim-lspconfig",
    opts = {
      servers = {
        groovyls = {},
      },
    },
  },
}
