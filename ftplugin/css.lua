-- TetraVim CSS Buffer Conventions
-- Sets buffer-local formatting and comment handling for CSS files

-- Indentation: 2-space soft tabs
vim.bo.shiftwidth = 2
vim.bo.tabstop = 2
vim.bo.softtabstop = 2
vim.bo.expandtab = true

-- Comment formatting: /* ... */
vim.bo.commentstring = "/* %s */"
vim.bo.comments = "s1:/*,mb:*,ex:*/"
