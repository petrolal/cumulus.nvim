-- Cumulus Healthcheck Module (Story 27.2, Story 35.1 & Story 6.1)

local M = {}

function M.check()
  vim.health.start("Cumulus Neovim Core & Platform")

  if vim.fn.has("nvim-0.10.0") == 1 then
    vim.health.ok(string.format("Neovim version: %s (>= 0.10.0 required)", vim.version()))
  else
    vim.health.warn(string.format("Neovim version: %s (v0.10.0+ recommended)", vim.version()))
  end

  if vim.opt.confirm:get() == true then
    vim.health.ok("Global exit confirmation (vim.opt.confirm = true) is active")
  else
    vim.health.warn("Global exit confirmation is disabled")
  end

  vim.health.start("Cumulus System Dependencies")

  local binaries = {
    { name = "rg", desc = "ripgrep (required for Telescope live_grep & Snacks picker)", level = "warn" },
    { name = "fd", desc = "fd (optional high-speed file finder)", level = "info" },
    { name = "git", desc = "git (required for version control & git_files picker)", level = "warn" },
    { name = "sbt", desc = "sbt / GraalVM native-image (for building Scala native engine)", level = "info" },
    { name = "npm", desc = "npm (required for markdown-preview.nvim build)", level = "info" },
    { name = "node", desc = "node (required for markdown-preview & npm-based DevOps LSP servers)", level = "info" },
    { name = "python3", desc = "python3 (required for pynvim, ansible-lint, cfn-lint & BMad scripts)", level = "info" },
  }

  for _, b in ipairs(binaries) do
    if vim.fn.executable(b.name) == 1 then
      vim.health.ok(string.format("%s: installed and executable", b.name))
    else
      if b.level == "warn" then
        vim.health.warn(string.format("%s: NOT found on $PATH (%s)", b.name, b.desc))
      else
        vim.health.info(string.format("%s: NOT found on $PATH (%s)", b.name, b.desc))
      end
    end
  end

  local engine = require("cumulus.util.engine")
  if engine.is_available() then
    local info = engine.ping()
    if info then
      vim.health.ok(string.format("Cumulus Scala Engine ('cumulus-engine'): active (%s, v%s, Scala %s, commit %s)",
        engine.get_bin(), info.version or "unknown", info.scala or "3.x", info.commit or "HEAD"))
    else
      vim.health.ok(string.format("Cumulus Scala Engine ('cumulus-engine'): active (%s)", engine.get_bin()))
    end
  else
    vim.health.warn("Cumulus Scala Engine ('cumulus-engine'): not compiled or not found. Build via 'cd engine && sbt nativeImage' or run ':CumulusInstallEngine'")
  end

  vim.health.start("Gradle Wrapper & Build Lock (SPEC-012)")

  local gradle = require("cumulus.util.gradle")
  if gradle.find_gradle() then
    local cwd = vim.fn.getcwd()
    local status = engine.verify_gradle_wrapper(cwd)
    if status then
      if status.local_version then
        vim.health.ok(string.format("Local Gradle version: %s", status.local_version))
      else
        vim.health.warn("Local Gradle version: not found in gradle-wrapper.properties")
      end

      if status.ci_version then
        vim.health.ok(string.format("CI Gradle version: %s", status.ci_version))
      else
        vim.health.info("CI Gradle version: not configured in CI workflows")
      end

      if status.sha256_configured then
        vim.health.ok("SHA-256 checksum: configured")
      else
        vim.health.warn("SHA-256 checksum: NOT configured (security risk for supply chain)")
      end

      if status.issues and #status.issues > 0 then
        for _, issue in ipairs(status.issues) do
          vim.health.warn("Gradle Wrapper: " .. issue)
        end
      else
        vim.health.ok("Gradle wrapper: no issues detected")
      end
    else
      vim.health.warn("Failed to verify Gradle wrapper")
    end
  else
    vim.health.info("Gradle project not detected (gradle-wrapper.properties not found)")
  end

  vim.health.start("AWS CloudFormation & SAM DevOps Tooling (Story 8.2)")

  local cfn_tools = {
    { name = "aws", desc = "AWS CLI (required for 'aws cloudformation validate-template')", install = "Install via https://aws.amazon.com/cli/" },
    { name = "sam", desc = "AWS SAM CLI (required for 'sam build', 'sam local invoke', 'sam validate')", install = "Install via https://docs.aws.amazon.com/serverless-application-model/latest/developerguide/install-sam-cli.html" },
    { name = "cfn-lint", desc = "CloudFormation Linter (cfn-lint)", install = ":MasonInstall cfn-lint or pip install cfn-lint" },
    { name = "cfn-guard", desc = "CloudFormation Guard Policy Evaluator (cfn-guard)", install = "Install via brew install cloudformation-guard or cargo install cfn-guard" },
  }

  for _, tool in ipairs(cfn_tools) do
    if vim.fn.executable(tool.name) == 1 then
      vim.health.ok(string.format("%s: installed and executable", tool.name))
    else
      vim.health.info(string.format("%s: NOT found on $PATH (%s. Suggestion: %s)", tool.name, tool.desc, tool.install))
    end
  end

  vim.health.start("Ansible Automation Tooling (Story 8.3)")

  local ansible_tools = {
    { name = "ansible-playbook", desc = "Ansible Playbook CLI (required for '--syntax-check', '--check', execution)", install = "Install via pip install ansible or brew install ansible" },
    { name = "ansible-lint", desc = "Ansible Playbook Linter", install = ":MasonInstall ansible-lint or pip install ansible-lint" },
    { name = "ansible-inventory", desc = "Ansible Inventory CLI (required for '--graph')", install = "Included with ansible package (pip install ansible)" },
    { name = "ansible-vault", desc = "Ansible Vault CLI (encrypt/decrypt/view secrets)", install = "Included with ansible package (pip install ansible)" },
    { name = "ansible-doc", desc = "Ansible Module Documentation Browser", install = "Included with ansible package (pip install ansible)" },
  }

  for _, tool in ipairs(ansible_tools) do
    if vim.fn.executable(tool.name) == 1 then
      vim.health.ok(string.format("%s: installed and executable", tool.name))
    else
      vim.health.info(string.format("%s: NOT found on $PATH (%s. Suggestion: %s)", tool.name, tool.desc, tool.install))
    end
  end

  vim.health.start("Cumulus Signature Cloud Themes")


  local themes = { "aws-theme", "azure-theme", "gcp-theme", "oci-theme" }
  for _, theme in ipairs(themes) do
    local ok, _ = pcall(vim.cmd, "colorscheme " .. theme)
    if ok then
      vim.health.ok(string.format("Cloud Theme '%s': verified & loadable", theme))
    else
      vim.health.error(string.format("Cloud Theme '%s': FAILED to load", theme))
    end
  end
end

return M
