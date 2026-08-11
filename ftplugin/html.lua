-- Cumulus HTML Buffer Conventions (IntelliJ Ultimate Parity)
-- Sets buffer-local formatting and comment handling for HTML files

-- Indentation: 2-space soft tabs (IntelliJ default)
vim.bo.shiftwidth = 2
vim.bo.tabstop = 2
vim.bo.softtabstop = 2
vim.bo.expandtab = true

-- Comment formatting: Supports <!-- --> single & multi-line comment blocks
vim.bo.commentstring = "<!-- %s -->"
vim.bo.comments = "s:<!--,m:  ,e:-->"
