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

  local engine = require("cumulus.util.engine")
  local health_response = engine.check_health()

  if health_response and health_response.data then
    local report = health_response.data

    -- Display binary status
    if report.binaries then
      for _, binary in ipairs(report.binaries) do
        if binary.status == "ok" then
          if binary.version then
            vim.health.ok(string.format("%s: installed (v%s)", binary.name, binary.version))
          else
            vim.health.ok(string.format("%s: installed", binary.name))
          end
        elseif binary.status == "missing" then
          -- Determine if it's required or optional
          local required_binaries = { rg = true, git = true }
          if required_binaries[binary.name] then
            vim.health.warn(string.format("%s: NOT found on $PATH (required)", binary.name))
          else
            vim.health.info(string.format("%s: NOT found on $PATH (optional)", binary.name))
          end
        elseif binary.status == "error" then
          vim.health.warn(string.format("%s: error checking binary (%s)", binary.name, binary.path or "unknown"))
        end
      end
    end
  else
    vim.health.warn("Cumulus Scala Engine ('cumulus-engine'): check-health failed or not available")
  end

  -- Engine availability
  if engine.is_available() then
    local info = engine.ping()
    if info then
      vim.health.ok(
        string.format(
          "Cumulus Scala Engine ('cumulus-engine'): active (%s, v%s, Scala %s, commit %s)",
          engine.get_bin(),
          info.version or "unknown",
          info.scala or "3.x",
          info.commit or "HEAD"
        )
      )
    else
      vim.health.ok(string.format("Cumulus Scala Engine ('cumulus-engine'): active (%s)", engine.get_bin()))
    end
  else
    vim.health.warn(
      "Cumulus Scala Engine ('cumulus-engine'): not compiled or not found. Build via 'cd engine && sbt nativeImage' or run ':CumulusInstallEngine'"
    )
  end

  vim.health.start("Gradle Wrapper & Build Lock (SPEC-012)")

  if health_response and health_response.data then
    if health_response.data.gradle_wrapper then
      local gradle_info = health_response.data.gradle_wrapper

      if gradle_info.local_version then
        vim.health.ok(string.format("Local Gradle version: %s", gradle_info.local_version))
      else
        vim.health.warn("Local Gradle version: not found in gradle-wrapper.properties")
      end

      if gradle_info.ci_version then
        vim.health.ok(string.format("CI Gradle version: %s", gradle_info.ci_version))
      else
        vim.health.info("CI Gradle version: not configured in CI workflows")
      end

      if gradle_info.sha256_configured then
        vim.health.ok("SHA-256 checksum: configured")
      else
        vim.health.warn("SHA-256 checksum: NOT configured (security risk for supply chain)")
      end

      if gradle_info.issues and #gradle_info.issues > 0 then
        for _, issue in ipairs(gradle_info.issues) do
          vim.health.warn("Gradle Wrapper: " .. issue)
        end
      else
        vim.health.ok("Gradle wrapper: no issues detected")
      end
    elseif health_response.data.build_tool == "gradle" then
      vim.health.warn("Gradle project detected but wrapper verification failed")
    else
      vim.health.info("Gradle project not detected")
    end
  else
    vim.health.info("Engine health check failed or unavailable")
  end

  vim.health.start("Cumulus Project-Wide Safe Rename (SPEC-2.1)")

  if vim.fn.executable("rg") == 1 then
    vim.health.ok("rg (ripgrep): installed and executable (Spring XML/@Autowired/stereotype reference scan)")
  elseif vim.fn.executable("grep") == 1 then
    vim.health.info(
      "rg (ripgrep): NOT found on $PATH -- falling back to grep (slower). Suggestion: install ripgrep for a faster Spring-reference scan"
    )
  else
    vim.health.warn(
      "Neither 'rg' nor 'grep' found on $PATH -- the Spring XML/@Autowired/stereotype reference scan is "
        .. "unavailable; project-wide rename will only cover LSP-visible locations. Suggestion: install ripgrep or grep"
    )
  end

  vim.health.start("AWS CloudFormation & SAM DevOps Tooling (Story 8.2)")

  local cfn_tools = {
    {
      name = "aws",
      desc = "AWS CLI (required for 'aws cloudformation validate-template')",
      install = "Install via https://aws.amazon.com/cli/",
    },
    {
      name = "sam",
      desc = "AWS SAM CLI (required for 'sam build', 'sam local invoke', 'sam validate')",
      install = "Install via https://docs.aws.amazon.com/serverless-application-model/latest/developerguide/install-sam-cli.html",
    },
    {
      name = "cfn-lint",
      desc = "CloudFormation Linter (cfn-lint)",
      install = ":MasonInstall cfn-lint or pip install cfn-lint",
    },
    {
      name = "cfn-guard",
      desc = "CloudFormation Guard Policy Evaluator (cfn-guard)",
      install = "Install via brew install cloudformation-guard or cargo install cfn-guard",
    },
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
    {
      name = "ansible-playbook",
      desc = "Ansible Playbook CLI (required for '--syntax-check', '--check', execution)",
      install = "Install via pip install ansible or brew install ansible",
    },
    {
      name = "ansible-lint",
      desc = "Ansible Playbook Linter",
      install = ":MasonInstall ansible-lint or pip install ansible-lint",
    },
    {
      name = "ansible-inventory",
      desc = "Ansible Inventory CLI (required for '--graph')",
      install = "Included with ansible package (pip install ansible)",
    },
    {
      name = "ansible-vault",
      desc = "Ansible Vault CLI (encrypt/decrypt/view secrets)",
      install = "Included with ansible package (pip install ansible)",
    },
    {
      name = "ansible-doc",
      desc = "Ansible Module Documentation Browser",
      install = "Included with ansible package (pip install ansible)",
    },
  }

  for _, tool in ipairs(ansible_tools) do
    if vim.fn.executable(tool.name) == 1 then
      vim.health.ok(string.format("%s: installed and executable", tool.name))
    else
      vim.health.info(string.format("%s: NOT found on $PATH (%s. Suggestion: %s)", tool.name, tool.desc, tool.install))
    end
  end

  vim.health.start("Cumulus Embedded Database Explorer (SPEC-3.1)")

  local dadbod_completion_ok = pcall(require, "vim_dadbod_completion")
  if dadbod_completion_ok then
    vim.health.ok("vim-dadbod-completion: resolvable (SQL buffer completion source available)")
  else
    vim.health.warn(
      "vim-dadbod-completion: not resolvable -- open a sql/mysql/plsql buffer to lazy-load it, or run :Lazy sync"
    )
  end

  local sql_parser_ok = pcall(vim.treesitter.language.add, "sql")
  if sql_parser_ok then
    vim.health.ok("sql Tree-sitter parser: installed (SQL buffer syntax highlighting available)")
  else
    vim.health.info("sql Tree-sitter parser: NOT installed. Suggestion: :TSInstall sql")
  end

  vim.health.start("Cumulus HTTP Client & REST API Explorer (Story 3.2)")

  local http_tools = {
    {
      name = "jq",
      desc = "jq JSON processor (required for the <leader>Hj response-filtering keymap)",
      install = "Install via apt install jq / brew install jq / pacman -S jq",
    },
  }

  for _, tool in ipairs(http_tools) do
    if vim.fn.executable(tool.name) == 1 then
      vim.health.ok(string.format("%s: installed and executable", tool.name))
    else
      vim.health.info(string.format("%s: NOT found on $PATH (%s. Suggestion: %s)", tool.name, tool.desc, tool.install))
    end
  end

  local kulala_ok = pcall(require, "kulala")
  if kulala_ok then
    vim.health.ok("kulala.nvim: resolvable (.http execution engine available)")
  else
    vim.health.warn("kulala.nvim: not resolvable -- open a .http file to lazy-load it, or run :Lazy sync")
  end

  vim.health.start("Cumulus Advanced Git Conflict Resolution (Story 4.1)")

  if vim.fn.executable("git") == 1 then
    local git_ok, git_res = pcall(function()
      return vim.system({ "git", "--version" }, { text = true, timeout = 2000 }):wait()
    end)

    local stdout
    if git_ok and type(git_res) == "table" and git_res.code == 0 then
      stdout = git_res.stdout or ""
    end
    local major, minor = (stdout or ""):match("(%d+)%.(%d+)")
    major, minor = tonumber(major), tonumber(minor)

    if not major then
      vim.health.warn(
        "git: installed, but `git --version` did not return a recognizable version -- ensure it is git >= 2.30"
      )
    elseif major > 2 or (major == 2 and minor >= 30) then
      vim.health.ok(string.format("git: installed (v%d.%d; >= 2.30 advised)", major, minor))
    else
      vim.health.warn(
        string.format("git: v%d.%d found -- git >= 2.30 is advised for the merge-conflict workflow", major, minor)
      )
    end
  else
    vim.health.error(
      "git: NOT found on $PATH -- the <leader>gc conflict/compare commands are unavailable. Suggestion: install git"
    )
  end

  local diffview_ok = pcall(require, "diffview")
  if diffview_ok then
    vim.health.ok("diffview.nvim: resolvable (3-way merge tool & file-history engine available)")
  else
    vim.health.warn(
      "diffview.nvim: not resolvable -- press <leader>gco / run :DiffviewOpen to lazy-load it, or run :Lazy sync"
    )
  end

  vim.health.start("Cumulus Signature Cloud Themes (Story 5.1)")

  -- Test engine theme support (Story 5.1: theme data from engine, not Lua files)
  local engine = require("cumulus.util.engine")
  local providers = { "aws", "azure", "gcp", "oci" }

  for _, provider in ipairs(providers) do
    local result = engine.generate_theme_highlights(provider)
    if result and result.highlights then
      vim.health.ok(string.format("Cloud Theme '%s-theme': engine-generated highlights available", provider))
    else
      vim.health.error(string.format("Cloud Theme '%s-theme': FAILED to load from engine", provider))
    end
  end
end

return M
