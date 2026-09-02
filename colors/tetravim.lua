-- TetraVim "Tetris" palette colour scheme.
-- Usage: `:colorscheme tetravim` (or `vim.cmd.colorscheme("tetravim")`).
-- The heavy lifting lives in `tetravim.theme.tetris` so the same highlight
-- set can also be applied directly by the theme loader without going
-- through `:colorscheme`.
require("tetravim.theme.tetris").apply()
