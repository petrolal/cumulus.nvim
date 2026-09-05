-- TetraVim Diagnostic Linting Specs (Story 3.1, 3.2, 3.3, 40.1)

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

      local function linter_executable(name)
        local linter_obj = lint.linters[name]
        local cmd = (linter_obj and linter_obj.cmd) or name
        if type(cmd) == "function" then
          cmd = cmd()
        end
        return type(cmd) == "string" and vim.fn.executable(cmd) == 1
      end

      -- Classify a YAML buffer by CI system from its path (Epic 39). GitHub
      -- workflow and GitLab CI files are plain `yaml` to Neovim, so the generic
      -- `yaml` linters (cfn_lint, ansible_lint) would otherwise fire on them and
      -- flood the buffer with "'Resources' is a required property" noise.
      local function ci_kind(path)
        if type(path) ~= "string" then
          return nil
        end
        if path:match("[/\\]%.github[/\\]workflows[/\\][^/\\]+%.ya?ml$") then
          return "github"
        end
        if
          path:match("[/\\]%.gitlab%-ci%.yml$")
          or path:match("^%.gitlab%-ci%.yml$")
          or path:match("[/\\]%.gitlab[/\\].+%.ya?ml$")
        then
          return "gitlab"
        end
        return nil
      end

      -- Linters that only make sense for a hand-authored CloudFormation / Ansible
      -- document, never for a CI pipeline file.
      local generic_yaml_noise = { cfn_lint = true, ansible_lint = true }

      local lint_augroup = vim.api.nvim_create_augroup("tetravim_lint", { clear = true })
      vim.api.nvim_create_autocmd({ "BufEnter", "BufWritePost", "InsertLeave" }, {
        group = lint_augroup,
        callback = function(args)
          local ft = vim.bo.filetype
          local linters = lint.linters_by_ft[ft]
          local on_ci = ci_kind(vim.api.nvim_buf_get_name(args.buf)) ~= nil
          if linters then
            local valid_linters = {}
            for _, linter in ipairs(linters) do
              local name = type(linter) == "table" and linter.cmd or linter
              if not (on_ci and generic_yaml_noise[name]) and linter_executable(name) then
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

      -- CI/CD YAML linters are path-scoped, not filetype-scoped (Epic 39):
      -- `actionlint` only understands GitHub workflow files and errors loudly on
      -- any other YAML, and `yamllint` is our generic fallback aimed at GitLab
      -- CI. Both run only when the buffer path matches, on top of whatever the
      -- filetype autocmd above already dispatched.
      local ci_augroup = vim.api.nvim_create_augroup("tetravim_lint_ci", { clear = true })
      vim.api.nvim_create_autocmd({ "BufReadPost", "BufWritePost" }, {
        group = ci_augroup,
        pattern = { "*.yml", "*.yaml" },
        callback = function(args)
          local kind = ci_kind(vim.api.nvim_buf_get_name(args.buf))
          if kind == "github" and linter_executable("actionlint") then
            lint.try_lint("actionlint")
          elseif kind == "gitlab" and linter_executable("yamllint") then
            lint.try_lint("yamllint")
          end
        end,
      })
    end,
  },
}
