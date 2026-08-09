-- Cumulus SQL Database Client (DataGrip Parity)

return {
  {
    "tpope/vim-dadbod",
    cmd = { "DB", "DBUI", "DBUIToggle", "DBUIAddConnection", "DBUIFindBuffer" },
    dependencies = {
      "kristijanhusak/vim-dadbod-ui",
    },
    -- vim-dadbod-ui reads these g: globals from its own plugin/ script,
    -- which only runs once lazy.nvim loads the plugin -- they must be
    -- set in init() (runs before load), not config() (runs after).
    init = function()
      vim.g.db_ui_use_nerd_fonts = 1
      vim.g.db_ui_show_help = 0
    end,
  },
}
