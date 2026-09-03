-- TetraVim Unified Diagnostics Presentation
--
-- One place that decides how every LSP / linter / DevOps diagnostic looks,
-- so the distro reads consistently instead of inheriting Neovim's plain
-- defaults:
--   * gutter signs use Nerd Font glyphs, coloured by the existing
--     DiagnosticSign* highlights (Z-piece red for errors, etc.);
--   * the sign's severity colour bleeds onto the line-number column;
--   * inline virtual text is prefixed with a slim bar and only shows its
--     source when more than one is reporting on the line;
--   * the hover float (`<leader>cd` / `vim.diagnostic.open_float`) gets a
--     rounded border to match `winborder`;
--   * signs stack most-severe-first.

local severity = vim.diagnostic.severity

local icons = {
  [severity.ERROR] = "󰅚 ",
  [severity.WARN] = "󰀪 ",
  [severity.INFO] = "󰋽 ",
  [severity.HINT] = "󰌶 ",
}

local sign_hl = {
  [severity.ERROR] = "DiagnosticSignError",
  [severity.WARN] = "DiagnosticSignWarn",
  [severity.INFO] = "DiagnosticSignInfo",
  [severity.HINT] = "DiagnosticSignHint",
}

vim.diagnostic.config({
  severity_sort = true,
  update_in_insert = false,
  underline = true,
  signs = {
    text = icons,
    numhl = sign_hl,
  },
  virtual_text = {
    spacing = 4,
    source = "if_many",
    prefix = "▎",
  },
  float = {
    border = "rounded",
    source = "if_many",
    header = "",
    prefix = "",
  },
})
