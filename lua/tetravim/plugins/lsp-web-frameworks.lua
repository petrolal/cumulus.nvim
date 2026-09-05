-- TetraVim Web-Framework Language Stack
-- IntelliJ IDEA Ultimate bundled Vue / Svelte / Astro / Angular plugin parity.
--
-- Each of these ships a real language server that lspconfig knows how to
-- launch; the only nuance is Vue. `vue_ls` (Volar) defaults to "hybrid mode"
-- where a companion tsserver does the TS heavy lifting via a plugin -- that
-- wiring is version-fragile, so we pin `hybridMode = false` and let Volar run
-- its own embedded TS server. Zero project config required.
--
-- JS/TS itself is lsp-typescript.lua; Tailwind / ESLint / Emmet are
-- lsp-web-tooling.lua.

return {
  {
    "nvim-treesitter/nvim-treesitter",
    opts = function(_, opts)
      if type(opts.ensure_installed) == "table" then
        vim.list_extend(opts.ensure_installed, { "vue", "svelte", "astro" })
      end
    end,
  },

  {
    "neovim/nvim-lspconfig",
    opts = {
      servers = {
        vue_ls = {
          settings = {
            vue = {
              hybridMode = false,
            },
          },
        },
        svelte = {},
        astro = {},
        -- angularls builds its own cmd from the bundled @angular/language-server
        -- (Mason: angular-language-server); it only attaches inside a project
        -- that has an angular.json, so it is inert elsewhere.
        angularls = {},
      },
    },
  },
}
