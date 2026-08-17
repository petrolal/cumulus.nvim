-- Cumulus Diagnostic Linting Specs (Story 3.1, 3.2, 3.3, 40.1)

return {
  {
    "mfussenegger/nvim-lint",
    event = { "BufReadPre", "BufNewFile" },
    config = function()
      local lint = require("lint")
      lint.linters_by_ft = {
        terraform = { "tflint" },
        tf = { "tflint" },
        hcl = { "tflint" },
        ["terraform-vars"] = { "tflint" },
        yaml = { "cfn_lint", "ansible_lint" },
        ["yaml.ansible"] = { "ansible_lint" },
        ansible = { "ansible_lint" },
        ["yaml.cfn"] = { "cfn_lint" },
        ["yaml.sam"] = { "cfn_lint" },
        cloudformation = { "cfn_lint" },
        sam = { "cfn_lint" },
        dockerfile = { "hadolint" },
        kotlin = { "ktlint" },
        groovy = { "npm-groovy-lint" },
        java = { "checkstyle" },
      }

      local lint_augroup = vim.api.nvim_create_augroup("cumulus_lint", { clear = true })
      vim.api.nvim_create_autocmd({ "BufEnter", "BufWritePost", "InsertLeave" }, {
        group = lint_augroup,
        callback = function()
          local ft = vim.bo.filetype
          local linters = lint.linters_by_ft[ft]
          if linters then
            local valid_linters = {}
            for _, linter in ipairs(linters) do
              local name = type(linter) == "table" and linter.cmd or linter
              local linter_obj = lint.linters[name]
              local cmd = (linter_obj and linter_obj.cmd) or name
              if vim.fn.executable(cmd) == 1 then
                table.insert(valid_linters, linter)
              end
            end
            if #valid_linters > 0 then
              lint.try_lint(valid_linters)
            end
          else
            lint.try_lint()
          end
        end,
      })
    end,
  },
}
