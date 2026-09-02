-- TetraVim Document Formatting Specs (Story 3.1, FR3, Story 40.1, 40.2, 40.3)

return {
  {
    "stevearc/conform.nvim",
    event = { "BufReadPre", "BufNewFile" },
    opts = {
      formatters_by_ft = {
        terraform = { "terraform_fmt" },
        tf = { "terraform_fmt" },
        hcl = { "terraform_fmt" },
        ["terraform-vars"] = { "terraform_fmt" },
        kotlin = { "ktlint" },
        java = { "google-java-format" },
        groovy = { "npm-groovy-lint" },
        html = { "superhtml" },
        toml = { "taplo" },
        lua = { "stylua" },
        sh = { "shfmt" },
        bash = { "shfmt" },
        yaml = { "prettier", "yamlfmt", stop_after_first = true },
        ["yaml.ansible"] = {},
        ["yaml.cfn"] = {},
        ["yaml.sam"] = {},
        ansible = {},
        cloudformation = {},
        sam = {},
      },
      format_on_save = function(bufnr)
        if not require("tetravim.util.format").enabled(bufnr) then
          return
        end
        return {
          timeout_ms = 3000,
          async = false,
          quiet = false,
          lsp_fallback = true,
        }
      end,
    },
  },
}
