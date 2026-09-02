-- Kotlin Ftplugin (SPEC-2.1: Project-Wide Safe Rename)
--
-- Mirrors the unconditional <leader>cr override installed for Java in
-- ftplugin/java.lua. Installed for every Kotlin buffer regardless of LSP
-- attach so that pressing <leader>cr with no Kotlin LS client still yields
-- project_rename's visible "no project-wide rename available" notify (I/O
-- matrix "No JVM LSP attached" row) rather than silently falling through to
-- the global vim.lsp.buf.rename(). The global mapping for non-JVM
-- filetypes is untouched.
vim.keymap.set("n", "<leader>cr", function()
  require("cumulus.util.refactor").project_rename()
end, { buffer = 0, desc = "Project-Wide Rename (Kotlin)" })
