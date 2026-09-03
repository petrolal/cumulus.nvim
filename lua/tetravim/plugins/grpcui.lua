-- TetraVim gRPC UI plugin (letieu/grpcui.nvim)
return {
  {
    "letieu/grpcui.nvim",
    dependencies = { "ibhagwan/fzf-lua" },
    ft = { "proto", "http" },
    config = function()
      require("grpcui").setup({
        -- optional: customize JSON LSP if desired
        -- jsonls_cmd = { "vscode-json-languageserver", "--stdio" },
      })
    end,
  },
}
