-- TetraVim Healthcheck Module (Story 27.2, Story 35.1 & Story 6.1)

local M = {}

function M.check()
  vim.health.start("TetraVim Neovim Core & Platform")

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

  vim.health.start("TetraVim System Dependencies")

  local binaries = {
    { name = "rg", required = true, label = "ripgrep (fast project-wide search)" },
    { name = "git", required = true, label = "git (VCS integration)" },
    { name = "fd", required = false, label = "fd (file finder)" },
    { name = "make", required = false, label = "make (native build steps)" },
    { name = "node", required = false, label = "node (LSP servers, formatters)" },
  }
  for _, bin in ipairs(binaries) do
    if vim.fn.executable(bin.name) == 1 then
      vim.health.ok(string.format("%s: found on $PATH (%s)", bin.name, bin.label))
    elseif bin.required then
      vim.health.warn(string.format("%s: NOT found on $PATH -- %s", bin.name, bin.label))
    else
      vim.health.info(string.format("%s: NOT found on $PATH (optional -- %s)", bin.name, bin.label))
    end
  end

  vim.health.start("Gradle Wrapper & Build Lock")

  local uv = vim.uv or vim.loop
  local cwd = vim.fn.getcwd()
  local is_gradle = uv.fs_stat(cwd .. "/build.gradle") ~= nil
    or uv.fs_stat(cwd .. "/build.gradle.kts") ~= nil
    or uv.fs_stat(cwd .. "/settings.gradle") ~= nil
    or uv.fs_stat(cwd .. "/settings.gradle.kts") ~= nil

  if not is_gradle then
    vim.health.info("Gradle project not detected in current directory")
  else
    if uv.fs_stat(cwd .. "/gradlew") then
      vim.health.ok("Gradle wrapper script (gradlew): present")
    else
      vim.health.warn("Gradle wrapper script (gradlew): missing -- run 'gradle wrapper' to add it")
    end

    if uv.fs_stat(cwd .. "/gradle/wrapper/gradle-wrapper.jar") then
      vim.health.ok("gradle-wrapper.jar: present")
    else
      vim.health.warn("gradle-wrapper.jar: missing under gradle/wrapper/")
    end

    local props = cwd .. "/gradle/wrapper/gradle-wrapper.properties"
    if uv.fs_stat(props) then
      local ok_read, lines = pcall(vim.fn.readfile, props)
      local content = ok_read and table.concat(lines, "\n") or ""
      local dist = content:match("distributionUrl=.-gradle%-([%d%.]+)%-")
      if dist then
        vim.health.ok(string.format("Gradle distribution pinned: %s", dist))
      else
        vim.health.info("gradle-wrapper.properties: present (distribution version not parsed)")
      end
      if content:match("distributionSha256Sum=") then
        vim.health.ok("SHA-256 checksum: configured (distributionSha256Sum)")
      else
        vim.health.warn("SHA-256 checksum: NOT configured -- add distributionSha256Sum for supply-chain safety")
      end
    else
      vim.health.warn("gradle-wrapper.properties: missing under gradle/wrapper/")
    end

    local locks = vim.fn.glob(cwd .. "/.gradle/*.lock", false, true)
    if locks and #locks > 0 then
      vim.health.warn(string.format("Stale Gradle build lock(s) present: %s", table.concat(locks, ", ")))
    else
      vim.health.ok("No stale Gradle build locks under .gradle/")
    end
  end

  vim.health.start("TetraVim Project-Wide Safe Rename (SPEC-2.1)")

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

  vim.health.start("TetraVim Spring Boot Discovery (Story 2.3)")
  local spring = require("tetravim.util.spring")

  if spring.has_parser("java") then
    vim.health.ok("Tree-sitter java parser: installed")
  else
    vim.health.warn("Tree-sitter java parser: NOT installed (required for Spring Boot discovery)")
  end

  if vim.fn.executable("rg") == 1 then
    vim.health.ok("rg (ripgrep): installed and executable (Spring candidate scan)")
  elseif vim.fn.executable("grep") == 1 then
    vim.health.ok("grep: installed and executable (fallback for Spring candidate scan)")
  else
    vim.health.warn("Neither 'rg' nor 'grep' found on $PATH (required for Spring discovery)")
  end

  local root_info = spring.detect_root()
  if root_info then
    vim.health.ok(
      string.format(
        "Spring Boot / JVM project root: %s (%s, %s)",
        root_info.root,
        root_info.build_tool,
        root_info.project_name
      )
    )
  else
    vim.health.info("Spring Boot / JVM project root: not detected in current directory")
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

  vim.health.start("TetraVim Embedded Database Explorer (SPEC-3.1)")

  local dadbod_completion_ok = pcall(require, "vim_dadbod_completion")
  if dadbod_completion_ok then
    vim.health.ok("vim-dadbod-completion: resolvable (SQL buffer completion source available)")
  else
    vim.health.warn(
      "vim-dadbod-completion: not resolvable -- open a sql/mysql/plsql buffer to lazy-load it, or run :Lazy sync"
    )
  end

  -- vim.treesitter.language.add() does not throw when the parser is absent
  -- (it returns nil, nil), so pcall always reports success. Use get_string_parser
  -- which raises an error when the parser is not installed.
  local sql_parser_ok = pcall(vim.treesitter.get_string_parser, "", "sql")
  if sql_parser_ok then
    vim.health.ok("sql Tree-sitter parser: installed (SQL buffer syntax highlighting available)")
  else
    vim.health.info("sql Tree-sitter parser: NOT installed. Suggestion: :TSInstall sql")
  end

  vim.health.start("TetraVim HTTP Client & REST API Explorer (Story 3.2)")

  local http_tools = {
    {
      name = "jq",
      desc = "jq JSON processor (required for the <leader>Hj response-filtering keymap)",
      install = "Install via apt install jq / brew install jq / pacman -S jq",
    },
    {
      name = "curl",
      desc = "curl (kulala.nvim's request backend -- required for <leader>Hr to execute .http requests)",
      install = "Install via apt install curl / brew install curl",
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

  vim.health.start("TetraVim Advanced Git Conflict Resolution (Story 4.1)")

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

  -- Distinguish "diffview.nvim is not installed at all" (an error the user
  -- fixes with :Lazy install) from "installed but not yet lazy-loaded" (a
  -- benign warn -- pressing <leader>gco loads it).
  local lz_ok, lz_cfg = pcall(require, "lazy.core.config")
  local diffview_plugin = lz_ok and lz_cfg.plugins and lz_cfg.plugins["diffview.nvim"] or nil
  if not diffview_plugin then
    vim.health.error(
      "diffview.nvim: not installed -- run :Lazy install (spec lives in lua/tetravim/plugins/tools-diffview.lua)"
    )
  elseif not package.loaded["diffview"] then
    vim.health.warn(
      "diffview.nvim: installed but not yet lazy-loaded -- press <leader>gco / run :DiffviewOpen to load it"
    )
  else
    vim.health.ok("diffview.nvim: loaded (3-way merge tool & file-history engine available)")
  end

  -- diffview.nvim's hard dependency -- without it diffview cannot load at all.
  if pcall(require, "plenary") then
    vim.health.ok("plenary.nvim: resolvable (diffview.nvim's hard dependency)")
  else
    vim.health.warn("plenary.nvim: not resolvable -- diffview's hard dependency; run :Lazy sync")
  end

  vim.health.start("TetraVim Code Reviews (GitHub/GitLab) (Story 4.2)")
  if vim.fn.executable("gh") == 1 then
    vim.health.ok("gh: installed and executable (GitHub PR review support available)")
  else
    vim.health.info("gh: NOT found on $PATH (GitHub PR review support unavailable). Suggestion: install gh")
  end
  if vim.fn.executable("glab") == 1 then
    vim.health.ok("glab: installed and executable (GitLab PR review support available)")
  else
    vim.health.info("glab: NOT found on $PATH (GitLab PR review support unavailable). Suggestion: install glab")
  end

  vim.health.start("TetraVim Colour Scheme")

  local theme_ok, tetris = pcall(require, "tetravim.theme.tetris")
  if not theme_ok then
    vim.health.error("tetravim.theme.tetris: failed to load (" .. tostring(tetris) .. ")")
  else
    local pal = tetris.palette or {}
    if pal.bg == "#111216" and pal.cyan == "#00F0F0" and pal.purple == "#A000F0" then
      vim.health.ok("Tetris palette module loaded (canonical hex values present)")
    else
      vim.health.warn("Tetris palette module loaded but hex values are not the canonical TetraVim set")
    end

    if vim.g.colors_name == "tetravim" then
      vim.health.ok("Active colourscheme: 'tetravim'")
    else
      vim.health.warn(
        "colors_name is '"
          .. tostring(vim.g.colors_name)
          .. "' (expected 'tetravim') -- run ':colorscheme tetravim' or check core/options.lua"
      )
    end
  end
end

return M
