-- TetraVim Markdown Language Server -- IntelliJ IDEA bundled Markdown parity
--
-- editor-markdown.lua already handles rendering (render-markdown.nvim) and
-- live preview (markdown-preview.nvim). This adds the language-server half:
-- marksman gives cross-file heading/link completion, go-to-definition on
-- `[wiki]` and `[](relative.md)` links, rename-heading, and broken-link
-- diagnostics -- the editing intelligence IDEA's Markdown plugin provides.
--
-- Tree-sitter markdown/markdown_inline parsers are already in the
-- core-treesitter.lua base list.

return {
  {
    "neovim/nvim-lspconfig",
    opts = {
      servers = {
        marksman = {
          filetypes = { "markdown", "markdown.mdx" },
        },
      },
    },
  },
}
