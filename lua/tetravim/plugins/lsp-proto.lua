-- TetraVim Protobuf / gRPC Language Stack Integration (SPEC-3.4)
--
-- Mirrors lsp-toml.lua / lsp-html.lua: one file with a bare nvim-lspconfig
-- `servers` fragment consumed by lsp-core.lua, plus an nvim-treesitter
-- opts-function fragment that extends `ensure_installed`. `.proto`
-- intelligence (hover, go-to-definition, diagnostics) comes entirely from
-- the `protols` language server + the `proto` Tree-sitter parser -- no
-- hand-written proto parsing. `protols` resolves to a known
-- nvim-lspconfig default config (lsp/protols.lua), so no custom
-- `vim.lsp.config` block is written here.

return {
  {
    "nvim-treesitter/nvim-treesitter",
    opts = function(_, opts)
      if type(opts.ensure_installed) == "table" then
        vim.list_extend(opts.ensure_installed, { "proto" })
      end
    end,
    -- Guard the `.proto` -> filetype `proto` mapping. Harmless if the
    -- installed Neovim already maps it (vim.filetype.add just re-registers
    -- the same association).
    init = function()
      vim.filetype.add({ extension = { proto = "proto" } })
    end,
  },

  {
    "neovim/nvim-lspconfig",
    opts = {
      servers = {
        protols = {},
      },
    },
  },
}
