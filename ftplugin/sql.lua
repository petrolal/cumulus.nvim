-- Cumulus SQL Buffer Conventions (DataGrip Parity follow-up)
-- Sets buffer-local formatting and comment handling for SQL files

-- Indentation: 2-space soft tabs (DataGrip default)
vim.bo.shiftwidth = 2
vim.bo.tabstop = 2
vim.bo.softtabstop = 2
vim.bo.expandtab = true

-- Comment formatting: Supports -- line comments and /* */ block comments
vim.bo.commentstring = "-- %s"
vim.bo.comments = "s1:/*,mb:*,ex:*/,://,b:--"
