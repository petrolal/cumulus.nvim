-- TetraVim CI/CD YAML LSP -- GitHub Actions & GitLab CI (Epic 39)
--
-- Before this spec `yamlls` only carried the CloudFormation/SAM schemas from
-- `cloud-cloudformation-ansible.lua`, so `.github/workflows/*.yml` and
-- `.gitlab-ci.yml` had no schema validation, completion or hover -- a typo in a
-- job key or a bad `uses:` line surfaced nothing until the pipeline failed.
--
-- `SchemaStore.nvim` ships the full JSON Schema Store catalog (GitHub Workflow,
-- GitHub Action, GitLab CI, Dependabot, renovate, ...). We feed that catalog to
-- `yamlls` / `jsonls` and additionally enable `gh_actions_ls` for the
-- Actions-specific checks (`uses:` resolution, expression + input validation)
-- that a raw JSON schema cannot express. `gh_actions_ls` self-gates to
-- `.github/workflows/` through its own `root_dir`, so it never attaches to
-- unrelated YAML buffers.

return {
  {
    "neovim/nvim-lspconfig",
    dependencies = {
      -- Catalog-only Lua module, no build step -- pinned to the rolling default
      -- branch (SchemaStore.nvim ships catalog updates there, not on tags).
      { "b0o/SchemaStore.nvim", version = false },
    },
    opts = function(_, opts)
      opts.servers = opts.servers or {}

      local ok, schemastore = pcall(require, "schemastore")

      -- yamlls -----------------------------------------------------------------
      -- Merge the SchemaStore YAML catalog with whatever schemas earlier specs
      -- (CloudFormation / SAM) already contributed, and switch off yamlls' own
      -- legacy store so the catalog is the single source of truth.
      local yamlls = opts.servers.yamlls or {}
      yamlls.settings = yamlls.settings or {}
      yamlls.settings.yaml = yamlls.settings.yaml or {}
      local yaml = yamlls.settings.yaml
      yaml.schemaStore = { enable = false, url = "" }
      yaml.validate = true
      yaml.schemas = yaml.schemas or {}
      if ok then
        -- `keep` so existing CloudFormation/SAM globs win on any key clash.
        yaml.schemas = vim.tbl_extend("keep", yaml.schemas, schemastore.yaml.schemas())
      end
      -- Explicit fallbacks in case a catalog entry name ever drifts.
      yaml.schemas["https://json.schemastore.org/github-workflow.json"] = yaml.schemas["https://json.schemastore.org/github-workflow.json"]
        or { "/.github/workflows/*.yml", "/.github/workflows/*.yaml" }
      yaml.schemas["https://json.schemastore.org/github-action.json"] = yaml.schemas["https://json.schemastore.org/github-action.json"]
        or { "/action.yml", "/action.yaml" }
      yaml.schemas["https://json.schemastore.org/gitlab-ci.json"] = yaml.schemas["https://json.schemastore.org/gitlab-ci.json"]
        or { "*.gitlab-ci.yml", "/.gitlab-ci.yml", "/.gitlab/**/*.yml" }
      opts.servers.yamlls = yamlls

      -- jsonls ---------------------------------------------------------------
      -- Same catalog for JSON (dependabot.json, renovate.json, tsconfig, ...).
      local jsonls = opts.servers.jsonls or {}
      jsonls.settings = jsonls.settings or {}
      jsonls.settings.json = jsonls.settings.json or {}
      jsonls.settings.json.validate = { enable = true }
      if ok then
        jsonls.settings.json.schemas = vim.list_extend(jsonls.settings.json.schemas or {}, schemastore.json.schemas())
      end
      opts.servers.jsonls = jsonls

      -- Dedicated GitHub Actions language server (npm: gh-actions-language-server).
      opts.servers.gh_actions_ls = opts.servers.gh_actions_ls or {}

      return opts
    end,
  },
}
