-- Cumulus Noice UI Integration (Story 14.2 & Story 24.1)

return {
  {
    "folke/noice.nvim",
    event = "VeryLazy",
    opts = {
      cmdline = {
        enabled = true,
        view = "cmdline_popup",
        opts = {
          position = {
            row = "10%",
            col = "50%",
          },
        },
      },
      lsp = {
        progress = { enabled = false },
        override = {
          ["vim.lsp.util.convert_input_to_markdown_lines"] = true,
          ["vim.lsp.util.set_hl_from_snippet"] = true,
        },
      },
      messages = {
        enabled = true,
        view = "mini",
      },
      popupmenu = {
        enabled = true,
        backend = "nui",
      },
      notify = {
        enabled = false,
      },
      views = {
        mini = {
          win_options = {
            winblend = 0,
          },
        },
        cmdline_popup = {
          position = {
            row = "10%",
            col = "50%",
          },
          size = {
            width = 60,
            height = "auto",
          },
        },
      },
    },
  },
}
