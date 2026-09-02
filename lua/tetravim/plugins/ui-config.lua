return {
  -- UI diagnostics and icons configuration
  {
    "folke/trouble.nvim",
    cmd = "Trouble",
    opts = {
      icons = {
        diagnostics = {
          Error = "󱗼 ",
          Warn = "󱁊 ",
          Hint = "󱁐 ",
          Info = "󰠮 ",
        },
      },
    },
  },
}
