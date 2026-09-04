-- TetraVim TypeScript Buffer Conventions
-- Sets buffer-local formatting and comment handling for TypeScript files

-- Indentation: 2-space soft tabs
vim.bo.shiftwidth = 2
vim.bo.tabstop = 2
vim.bo.softtabstop = 2
vim.bo.expandtab = true

-- Comment formatting: // ... and /* ... */
vim.bo.commentstring = "// %s"
vim.bo.comments = "sO:* -,mO:* ,ex:*/,s1:/*,mb:*,ex:*/,:///"
