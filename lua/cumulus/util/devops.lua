-- Cumulus DevOps & Infrastructure Tooling Suite (Story 8.1, Story 8.2, Story 8.3, Story 8.4, Story 9.1, Story 9.2)
--
-- Interactive and non-blocking compilers, validators, linters, and runners
-- for Terraform/OpenTofu, AWS CloudFormation/SAM, Ansible, Docker, and Helm.

local M = {}

local uv = vim.uv or vim.loop

local function is_dir(path)
  if not path or path == "" then return false end
  local ok, stat = pcall(uv.fs_stat, path)
  if ok and stat ~= nil then
    return stat.type == "directory"
  end
  if not ok and stat then
    -- pcall returned error message as second arg on permission denied, etc
  end
  return false
end

local function is_file(path)
  if not path or path == "" then return false end
  local ok, stat = pcall(uv.fs_stat, path)
  if ok and stat ~= nil then
    return stat.type == "file" or stat.type == "link"
  end
  if not ok and stat then
    -- pcall returned error message as second arg on permission denied, etc
  end
  return false
end

local function scan_dir_for_match(dir, matcher)
  if not is_dir(dir) then return false end
  local ok, handle = pcall(uv.fs_scandir, dir)
  if not ok or not handle then return false end
  while true do
    local ok_next, name, entry_type = pcall(uv.fs_scandir_next, handle)
    -- Break if pcall failed or name is nil (end of directory)
    if not ok_next or name == nil then break end
    local full_path = vim.fs.normalize(dir .. "/" .. name)
    if matcher(name, full_path, entry_type) then
      return true
    end
  end
  return false
end

--- Resolves the starting search directory from a buffer number, file path, or directory path.
--- Defaults gracefully to the active buffer or the current working directory.
--- @param buf_or_path? number|string Buffer handle or directory/file path
--- @return string canonical absolute directory path
function M.resolve_search_dir(buf_or_path)
  if not buf_or_path then
    local ok_buf, buf = pcall(vim.api.nvim_get_current_buf)
    if ok_buf and type(buf) == "number" and vim.api.nvim_buf_is_valid(buf) then
      local ok_name, name = pcall(vim.api.nvim_buf_get_name, buf)
      if ok_name and name and name ~= "" then
        buf_or_path = name
      end
    end
  elseif type(buf_or_path) == "number" then
    if vim.api.nvim_buf_is_valid(buf_or_path) then
      local ok_name, name = pcall(vim.api.nvim_buf_get_name, buf_or_path)
      if ok_name and name and name ~= "" then
        buf_or_path = name
      else
        buf_or_path = nil
      end
    else
      buf_or_path = nil
    end
  end

  if not buf_or_path or buf_or_path == "" then
    return vim.fs.normalize(vim.fn.getcwd())
  end

  local abs_path = vim.fs.normalize(vim.fn.fnamemodify(tostring(buf_or_path), ":p"))
  if is_dir(abs_path) then
    return abs_path
  elseif is_file(abs_path) then
    return vim.fs.normalize(vim.fn.fnamemodify(abs_path, ":h"))
  else
    local parent = vim.fs.normalize(vim.fn.fnamemodify(abs_path, ":h"))
    if is_dir(parent) then
      return parent
    end
    return vim.fs.normalize(vim.fn.getcwd())
  end
end

--- Generic root discoverer using upward search via vim.fs.root followed by convention scan.
--- @param buf_or_path? number|string
--- @param matcher fun(name: string, path: string, entry_type: string?): boolean
--- @param convention_dirs? string[]
--- @return string|nil
local function discover_root(buf_or_path, matcher, convention_dirs)
  local search_dir = M.resolve_search_dir(buf_or_path)
  if not search_dir or search_dir == "" then
    return nil
  end

  -- 1. Upward discovery using vim.fs.root
  local ok, root = pcall(vim.fs.root, search_dir, function(name, path)
    return matcher(name, path)
  end)

  if ok and root and root ~= "" and root ~= "/" then
    return vim.fs.normalize(root)
  end

  -- 2. Convention directory scan if upward discovery yielded nil
  if convention_dirs and #convention_dirs > 0 then
    local max_depth = 3
    for _, subdir in ipairs(convention_dirs) do
      local cand = vim.fs.normalize(search_dir .. "/" .. subdir)
      if is_dir(cand) then
        if scan_dir_for_match(cand, matcher) then
          return cand
        end
        -- Also scan nested folders inside convention directory (e.g. charts/my-chart/Chart.yaml)
        -- Limit depth to prevent infinite loops on symlink cycles
        local ok_h, handle = pcall(uv.fs_scandir, cand)
        if ok_h and handle then
          local depth = 0
          while true do
            local ok_n, name, entry_type = pcall(uv.fs_scandir_next, handle)
            if not ok_n or not name then break end
            if entry_type == "directory" then
              depth = depth + 1
              if depth > max_depth then
                break
              end
              local sub_cand = vim.fs.normalize(cand .. "/" .. name)
              if scan_dir_for_match(sub_cand, matcher) then
                return sub_cand
              end
            end
          end
        end
      end
    end
  end

  return nil
end

--- Discover Terraform / OpenTofu root directory
--- @param buf_or_path? number|string
--- @return string|nil
function M.find_tf_root(buf_or_path)
  local function matcher(name)
    if name == "main.tf" or name == "terragrunt.hcl" or name == "versions.tf"
       or name == "backend.tf" or name == "providers.tf" or name == "variables.tf"
       or name == "outputs.tf" or name == ".terraform" then
      return true
    end
    if name:match("%.tf$") or name:match("%.tofu$") or name:match("%.tfvars$") then
      return true
    end
    return false
  end

  local conventions = { "terraform", "infra/terraform", "infra", "deploy/terraform", "deploy", "terragrunt" }
  return discover_root(buf_or_path, matcher, conventions)
end

--- Discover AWS CloudFormation / SAM root directory
--- @param buf_or_path? number|string
--- @return string|nil
function M.find_cfn_root(buf_or_path)
  local function matcher(name)
    if name == "template.yaml" or name == "template.yml" or name == "template.json"
       or name == "samconfig.toml" or name == "samconfig.yaml" or name == "samconfig.yml" then
      return true
    end
    if name:match("%.cfn%.ya?ml$") or name:match("%.cfn%.json$")
       or name:match("%.sam%.ya?ml$") or name:match("%.sam%.json$")
       or name:match("^cfn.*%.ya?ml$") or name:match("^sam.*%.ya?ml$") then
      return true
    end
    return false
  end

  local conventions = { "sam", "cfn", "cloudformation", "infra/sam", "infra/cfn", "infra/cloudformation", "infra", "deploy/sam", "deploy/cfn", "deploy" }
  return discover_root(buf_or_path, matcher, conventions)
end

--- Discover Ansible root directory
--- @param buf_or_path? number|string
--- @return string|nil
function M.find_ansible_root(buf_or_path)
  local function matcher(name, path)
    if name == "ansible.cfg" or name == "site.yaml" or name == "site.yml"
       or name == "playbook.yaml" or name == "playbook.yml" or name == "hosts"
       or name == "inventory" or name == "inventory.ini" or name == "inventory.yaml"
       or name == "inventory.yml" or name == "requirements.yml" or name == "requirements.yaml" then
      return true
    end
    if name == "roles" or name == "playbooks" or name == "tasks" or name == "handlers" then
      -- If checking directory name, check if it contains Ansible structure
      if path and is_dir(path) then
        return is_file(path .. "/main.yml") or is_file(path .. "/main.yaml") or is_dir(path .. "/tasks")
      end
      return true
    end
    return false
  end

  local conventions = { "ansible", "playbooks", "infra/ansible", "deploy/ansible", "infra/playbooks" }
  return discover_root(buf_or_path, matcher, conventions)
end

--- Discover Docker / Compose root directory
--- @param buf_or_path? number|string
--- @return string|nil
function M.find_docker_root(buf_or_path)
  local function matcher(name)
    if name == "Dockerfile" or name == "Containerfile" or name == "docker-compose.yaml"
       or name == "docker-compose.yml" or name == "compose.yaml" or name == "compose.yml"
       or name == ".dockerignore" then
      return true
    end
    if name:match("^Dockerfile") or name:match("^Containerfile")
       or name:match("%.Dockerfile$") or name:match("%.Containerfile$")
       or name:match("^docker%-compose.*%.ya?ml$") or name:match("^compose.*%.ya?ml$") then
      return true
    end
    return false
  end

  local conventions = { "docker", "deploy/docker", "infra/docker", "deploy", "containers", ".docker" }
  return discover_root(buf_or_path, matcher, conventions)
end

--- Discover Helm root directory
--- @param buf_or_path? number|string
--- @return string|nil
function M.find_helm_root(buf_or_path)
  local function matcher(name)
    if name == "Chart.yaml" or name == "Chart.yml" or name == "values.yaml" or name == "values.yml" then
      return true
    end
    return false
  end

  local conventions = { "charts", "helm", "deploy/charts", "deploy/helm", "infra/helm", "infra/charts" }
  return discover_root(buf_or_path, matcher, conventions)
end

--- Run a command in an interactive, non-blocking terminal
--- Uses Snacks.terminal when available, otherwise falls back to a split buffer.
function M.run_term(cmd, opts)
  opts = opts or {}
  local term_cwd = opts.cwd or vim.fn.getcwd()
  local timeout = opts.timeout or 3600000 -- default 1 hour
  local snacks = _G.Snacks or package.loaded["snacks"]
  if snacks and snacks.terminal then
    snacks.terminal(cmd, { cwd = term_cwd })
  else
    vim.cmd("botright 15split")
    local win = vim.api.nvim_get_current_win()
    local buf = vim.api.nvim_create_buf(false, true)
    vim.api.nvim_win_set_buf(win, buf)
    local job_id = vim.fn.termopen(cmd, {
      cwd = term_cwd,
      on_exit = function(_, code)
        if code == 0 or code == 130 then
          vim.notify("Process finished (exit code " .. code .. ")", vim.log.levels.INFO, { title = "Cumulus DevOps" })
        else
          vim.notify("Process exited with code " .. code, vim.log.levels.ERROR, { title = "Cumulus DevOps" })
        end
      end,
    })
    -- Set timeout to prevent indefinite hangs
    if timeout > 0 then
      vim.fn.timer_start(timeout, function()
        if vim.fn.jobwait({ job_id }, 0)[1] == -1 then
          vim.fn.jobstop(job_id)
          vim.notify("Process timeout (" .. (timeout / 1000) .. "s), job terminated", vim.log.levels.WARN, { title = "Cumulus DevOps" })
        end
      end)
    end
    vim.keymap.set("n", "q", "<cmd>q<cr>", { buffer = buf, silent = true })
    vim.keymap.set("t", "<Esc>", [[<C-\><C-n>]], { buffer = buf, silent = true })
    vim.cmd("startinsert")
  end
end

-- =============================================================================
-- Helper Guards for Global Root Execution & Missing Workspace Diagnostics
-- =============================================================================

local function with_root(finder, missing_msg, callback)
  local root = finder()
  if not root then
    vim.notify(missing_msg, vim.log.levels.WARN, { title = "Cumulus DevOps" })
    return
  end
  local ok, err = pcall(callback, root)
  if not ok then
    vim.notify("Error executing operation: " .. err, vim.log.levels.ERROR, { title = "Cumulus DevOps" })
  end
end

-- =============================================================================
-- Terraform & OpenTofu
-- =============================================================================

function M.get_tf_cmd()
  if vim.fn.executable("tofu") == 1 then
    return "tofu"
  elseif vim.fn.executable("terraform") == 1 then
    return "terraform"
  end
  return nil
end

local function with_tf(callback)
  local tf = M.get_tf_cmd()
  if not tf then
    vim.notify(
      "Neither 'tofu' nor 'terraform' was found in your PATH. Please install OpenTofu or Terraform.",
      vim.log.levels.WARN,
      { title = "Cumulus DevOps" }
    )
    return
  end
  with_root(M.find_tf_root, "No Terraform/OpenTofu configuration found in workspace", function(root)
    callback(tf, root)
  end)
end

function M.terraform_init()
  with_tf(function(tf, root)
    M.run_term(tf .. " init", { cwd = root })
  end)
end

function M.terraform_validate()
  with_tf(function(tf, root)
    M.run_term(tf .. " validate", { cwd = root })
  end)
end

function M.terraform_plan()
  with_tf(function(tf, root)
    M.run_term(tf .. " plan", { cwd = root })
  end)
end

function M.terraform_apply()
  with_tf(function(tf, root)
    M.run_term(tf .. " apply", { cwd = root })
  end)
end

function M.terraform_fmt()
  with_tf(function(tf, root)
    local file = vim.fn.expand("%:p")
    local is_tf_file = file ~= "" and (file:match("%.tf$") or file:match("%.tofu$") or file:match("%.tfvars$"))
    if is_tf_file and vim.fn.filereadable(file) == 1 then
      local ok_save, err_save = pcall(vim.cmd, "update")
      if not ok_save then
        vim.notify("Failed to save file: " .. err_save, vim.log.levels.ERROR, { title = "Cumulus DevOps" })
        return
      end
      local out = vim.fn.system({ tf, "fmt", file })
      local ok_reload, err_reload = pcall(vim.cmd, "edit!")
      if not ok_reload then
        vim.notify("Failed to reload file after format: " .. err_reload, vim.log.levels.WARN, { title = "Cumulus DevOps" })
      end
      if vim.v.shell_error == 0 then
        vim.notify("Formatted with " .. tf .. " fmt", vim.log.levels.INFO, { title = "Cumulus DevOps" })
      else
        vim.notify("Formatting error: " .. out, vim.log.levels.ERROR, { title = "Cumulus DevOps" })
      end
    else
      if not is_tf_file then
        vim.notify("Current file is not a Terraform (.tf, .tofu, .tfvars) file.", vim.log.levels.WARN, { title = "Cumulus DevOps" })
      elseif vim.fn.filereadable(file) ~= 1 then
        vim.notify("Cannot read file: " .. file, vim.log.levels.WARN, { title = "Cumulus DevOps" })
      end
      M.run_term(tf .. " fmt", { cwd = root })
    end
  end)
end

function M.terraform_lint()
  with_root(M.find_tf_root, "No Terraform/OpenTofu configuration found in workspace", function(root)
    if vim.fn.executable("tflint") == 1 then
      M.run_term("tflint", { cwd = root })
    else
      vim.notify("tflint is not installed in PATH. Install via Mason (:MasonInstall tflint).", vim.log.levels.WARN, { title = "Cumulus DevOps" })
    end
  end)
end

function M.terraform_security()
  with_root(M.find_tf_root, "No Terraform/OpenTofu configuration found in workspace", function(root)
    if vim.fn.executable("trivy") == 1 then
      M.run_term("trivy config .", { cwd = root })
    elseif vim.fn.executable("tfsec") == 1 then
      M.run_term("tfsec .", { cwd = root })
    else
      vim.notify("Neither 'trivy' nor 'tfsec' is installed in PATH.", vim.log.levels.WARN, { title = "Cumulus DevOps" })
    end
  end)
end

function M.terraform_output()
  with_tf(function(tf, root)
    M.run_term(tf .. " output", { cwd = root })
  end)
end

-- =============================================================================
-- AWS CloudFormation & SAM
-- =============================================================================

--- Check if current buffer is likely a CloudFormation / SAM template
function M.is_cloudformation_buffer(buf)
  buf = buf or vim.api.nvim_get_current_buf()
  if not vim.api.nvim_buf_is_valid(buf) then
    return false
  end
  local ft = vim.bo[buf].filetype
  if ft == "yaml.cfn" or ft == "yaml.sam" or ft == "cloudformation" or ft == "sam" then
    return true
  end
  if ft == "yaml" or ft == "json" then
    local lines = vim.api.nvim_buf_get_lines(buf, 0, 30, false)
    local header = table.concat(lines, "\n")
    if header:match("AWSTemplateFormatVersion") or header:match("AWS::Serverless") or header:match("Transform:%s*AWS::Serverless") then
      return true
    end
  end
  return false
end

local function with_cfn(callback)
  with_root(M.find_cfn_root, "No CloudFormation/SAM configuration found in workspace", callback)
end

local function resolve_cfn_target_file(root)
  local file = vim.fn.expand("%:p")
  if file ~= "" and M.is_cloudformation_buffer() then
    return file
  end
  if root then
    if is_file(root .. "/template.yaml") then
      return root .. "/template.yaml"
    elseif is_file(root .. "/template.yml") then
      return root .. "/template.yml"
    elseif is_file(root .. "/template.json") then
      return root .. "/template.json"
    end
  end
  return nil
end

function M.cfn_validate()
  with_cfn(function(root)
    local file = resolve_cfn_target_file(root)
    if not file then
      vim.notify("No CloudFormation/SAM template file found to validate.", vim.log.levels.WARN, { title = "Cumulus DevOps" })
      return
    end
    local cwd = root or vim.fs.normalize(vim.fn.fnamemodify(file, ":h"))
    if vim.fn.executable("aws") == 1 then
      M.run_term("aws cloudformation validate-template --template-body file://" .. vim.fn.shellescape(file), { cwd = cwd })
    elseif vim.fn.executable("cfn-lint") == 1 then
      M.run_term("cfn-lint " .. vim.fn.shellescape(file), { cwd = cwd })
    else
      vim.notify(
        "Neither 'aws' CLI nor 'cfn-lint' was found in PATH. Please install the AWS CLI or cfn-lint (:MasonInstall cfn-lint).",
        vim.log.levels.WARN,
        { title = "Cumulus DevOps" }
      )
    end
  end)
end

function M.cfn_lint()
  with_cfn(function(root)
    local file = resolve_cfn_target_file(root)
    if vim.fn.executable("cfn-lint") == 1 then
      if file and file ~= "" then
        M.run_term("cfn-lint " .. vim.fn.shellescape(file), { cwd = root })
      else
        M.run_term("cfn-lint", { cwd = root })
      end
    else
      vim.notify(
        "cfn-lint is not installed in PATH. Install via Mason (:MasonInstall cfn-lint) or pip ('pip install cfn-lint').",
        vim.log.levels.WARN,
        { title = "Cumulus DevOps" }
      )
    end
  end)
end

function M.sam_validate()
  with_cfn(function(root)
    if vim.fn.executable("sam") == 1 then
      M.run_term("sam validate", { cwd = root })
    else
      vim.notify(
        "AWS SAM CLI ('sam') is not installed in PATH. Visit https://docs.aws.amazon.com/serverless-application-model/latest/developerguide/install-sam-cli.html",
        vim.log.levels.WARN,
        { title = "Cumulus DevOps" }
      )
    end
  end)
end

function M.sam_build()
  with_cfn(function(root)
    if vim.fn.executable("sam") == 1 then
      M.run_term("sam build", { cwd = root })
    else
      vim.notify(
        "AWS SAM CLI ('sam') is not installed in PATH. Visit https://docs.aws.amazon.com/serverless-application-model/latest/developerguide/install-sam-cli.html",
        vim.log.levels.WARN,
        { title = "Cumulus DevOps" }
      )
    end
  end)
end

function M.sam_local_invoke()
  with_cfn(function(root)
    if vim.fn.executable("sam") ~= 1 then
      vim.notify(
        "AWS SAM CLI ('sam') is not installed in PATH. Visit https://docs.aws.amazon.com/serverless-application-model/latest/developerguide/install-sam-cli.html",
        vim.log.levels.WARN,
        { title = "Cumulus DevOps" }
      )
      return
    end

    local file = resolve_cfn_target_file(root) or ""
    local engine = require("cumulus.util.engine")
    local functions = {}

    if file ~= "" and engine.is_available() then
      local info = engine.inspect_cfn_template(file, { silent = true })
      if info and info.functions and #info.functions > 0 then
        for _, fn in ipairs(info.functions) do
          table.insert(functions, fn.logical_id)
        end
      end
    end

    local tmpl_flag = (file ~= "") and (" -t " .. vim.fn.shellescape(file)) or ""

    if #functions > 1 then
      vim.ui.select(functions, { prompt = "Select Lambda Function to Invoke:" }, function(choice)
        if choice and choice ~= "" then
          M.run_term("sam local invoke " .. vim.fn.shellescape(choice) .. tmpl_flag, { cwd = root })
        end
      end)
    elseif #functions == 1 then
      M.run_term("sam local invoke " .. vim.fn.shellescape(functions[1]) .. tmpl_flag, { cwd = root })
    else
      vim.ui.input({ prompt = "Lambda Function Logical ID (optional, Esc to cancel): " }, function(input)
        if input == nil then
          return
        elseif input ~= "" then
          if not input:match("^[a-zA-Z0-9_-]+$") then
            vim.notify("Invalid Lambda function name. Only alphanumeric, underscore, and hyphen allowed.", vim.log.levels.WARN, { title = "Cumulus DevOps" })
            return
          end
          M.run_term("sam local invoke " .. vim.fn.shellescape(input) .. tmpl_flag, { cwd = root })
        else
          M.run_term("sam local invoke" .. tmpl_flag, { cwd = root })
        end
      end)
    end
  end)
end

function M.sam_local_start_api()
  with_cfn(function(root)
    if vim.fn.executable("sam") == 1 then
      M.run_term("sam local start-api", { cwd = root })
    else
      vim.notify(
        "AWS SAM CLI ('sam') is not installed in PATH. Visit https://docs.aws.amazon.com/serverless-application-model/latest/developerguide/install-sam-cli.html",
        vim.log.levels.WARN,
        { title = "Cumulus DevOps" }
      )
    end
  end)
end

function M.cfn_guard_validate()
  with_cfn(function(root)
    if vim.fn.executable("cfn-guard") == 1 then
      local file = resolve_cfn_target_file(root) or (vim.fn.expand("%:p") ~= "" and vim.fn.expand("%:p") or nil)
      if not file then
        vim.notify("No CloudFormation template found to validate with cfn-guard.", vim.log.levels.WARN, { title = "Cumulus DevOps" })
        return
      end
      M.run_term("cfn-guard validate --template " .. vim.fn.shellescape(file), { cwd = root })
    else
      vim.notify(
        "cfn-guard is not installed in PATH. Install CloudFormation Guard via cargo or homebrew ('brew install cloudformation-guard').",
        vim.log.levels.WARN,
        { title = "Cumulus DevOps" }
      )
    end
  end)
end

-- =============================================================================
-- Ansible Automation
-- =============================================================================

--- Check if current buffer is likely an Ansible playbook/role
function M.is_ansible_buffer(buf)
  buf = buf or vim.api.nvim_get_current_buf()
  if not vim.api.nvim_buf_is_valid(buf) then
    return false
  end
  local ft = vim.bo[buf].filetype
  if ft == "yaml.ansible" or ft == "ansible" then
    return true
  end
  if ft == "yaml" then
    local path = vim.api.nvim_buf_get_name(buf)
    if path:match("playbook") or path:match("roles/") or path:match("tasks/") or path:match("handlers/") then
      return true
    end
    local lines = vim.api.nvim_buf_get_lines(buf, 0, 20, false)
    local content = table.concat(lines, "\n")
    if content:match("%-%s*hosts:") or (content:match("%-%s*name:") and content:match("tasks:")) then
      return true
    end
  end
  return false
end

local function with_ansible(callback)
  with_root(M.find_ansible_root, "No Ansible configuration found in workspace", callback)
end

local function resolve_ansible_target_file(root)
  local file = vim.fn.expand("%:p")
  if file ~= "" and M.is_ansible_buffer() then
    return file
  end
  if root then
    if is_file(root .. "/site.yml") then
      return root .. "/site.yml"
    elseif is_file(root .. "/site.yaml") then
      return root .. "/site.yaml"
    elseif is_file(root .. "/playbook.yml") then
      return root .. "/playbook.yml"
    elseif is_file(root .. "/playbook.yaml") then
      return root .. "/playbook.yaml"
    end
  end
  return file ~= "" and file or nil
end

function M.ansible_syntax_check()
  with_ansible(function(root)
    local file = resolve_ansible_target_file(root)
    if not file then
      vim.notify("No Ansible playbook found to check syntax.", vim.log.levels.WARN, { title = "Cumulus DevOps" })
      return
    end
    if vim.fn.executable("ansible-playbook") == 1 then
      M.run_term("ansible-playbook --syntax-check " .. vim.fn.shellescape(file), { cwd = root })
    else
      vim.notify("ansible-playbook is not installed in PATH.", vim.log.levels.WARN, { title = "Cumulus DevOps" })
    end
  end)
end

function M.ansible_lint()
  with_ansible(function(root)
    local file = resolve_ansible_target_file(root)
    if vim.fn.executable("ansible-lint") == 1 then
      if file and file ~= "" then
        M.run_term("ansible-lint " .. vim.fn.shellescape(file), { cwd = root })
      else
        M.run_term("ansible-lint", { cwd = root })
      end
    else
      vim.notify("ansible-lint is not installed in PATH. Install via Mason (:MasonInstall ansible-lint).", vim.log.levels.WARN, { title = "Cumulus DevOps" })
    end
  end)
end

function M.ansible_dry_run()
  with_ansible(function(root)
    local file = resolve_ansible_target_file(root)
    if not file then
      vim.notify("No Ansible playbook found to dry run.", vim.log.levels.WARN, { title = "Cumulus DevOps" })
      return
    end
    if vim.fn.executable("ansible-playbook") == 1 then
      M.run_term("ansible-playbook --check " .. vim.fn.shellescape(file), { cwd = root })
    else
      vim.notify("ansible-playbook is not installed in PATH.", vim.log.levels.WARN, { title = "Cumulus DevOps" })
    end
  end)
end

function M.ansible_run_playbook()
  with_ansible(function(root)
    local file = resolve_ansible_target_file(root)
    if not file then
      vim.notify("No Ansible playbook found to run.", vim.log.levels.WARN, { title = "Cumulus DevOps" })
      return
    end
    if vim.fn.executable("ansible-playbook") == 1 then
      M.run_term("ansible-playbook " .. vim.fn.shellescape(file), { cwd = root })
    else
      vim.notify("ansible-playbook is not installed in PATH.", vim.log.levels.WARN, { title = "Cumulus DevOps" })
    end
  end)
end

function M.ansible_inventory_graph()
  with_ansible(function(root)
    if vim.fn.executable("ansible-inventory") == 1 then
      M.run_term("ansible-inventory --graph", { cwd = root })
    else
      vim.notify("ansible-inventory is not installed in PATH.", vim.log.levels.WARN, { title = "Cumulus DevOps" })
    end
  end)
end

function M.ansible_doc_lookup()
  with_ansible(function(root)
    if vim.fn.executable("ansible-doc") == 1 then
      vim.ui.input({ prompt = "Ansible Module / Plugin Doc: " }, function(input)
        if input == nil then
          return
        end
        if input ~= "" then
          if not input:match("^[a-z0-9_.:-]+$") then
            vim.notify("Invalid Ansible module name. Use lowercase alphanumeric, dots, underscores, hyphens, or colons.", vim.log.levels.WARN, { title = "Cumulus DevOps" })
            return
          end
          M.run_term("ansible-doc " .. vim.fn.shellescape(input), { cwd = root })
        end
      end)
    else
      vim.notify("ansible-doc is not installed in PATH.", vim.log.levels.WARN, { title = "Cumulus DevOps" })
    end
  end)
end

function M.ansible_vault_action()
  with_ansible(function(root)
    if vim.fn.executable("ansible-vault") ~= 1 then
      vim.notify("ansible-vault is not installed in PATH.", vim.log.levels.WARN, { title = "Cumulus DevOps" })
      return
    end
    local file = vim.fn.expand("%:p")
    local actions = { "view", "encrypt", "decrypt", "edit" }

    local function run_vault_on_file(target_file)
      vim.ui.select(actions, { prompt = "Select Ansible Vault Action:" }, function(choice)
        if choice then
          M.run_term("ansible-vault " .. choice .. " " .. vim.fn.shellescape(target_file), { cwd = root })
        end
      end)
    end

    if file and file ~= "" then
      run_vault_on_file(file)
    else
      vim.ui.input({ prompt = "Ansible Vault File: " }, function(input)
        if input == nil then
          return
        end
        if input ~= "" then
          local full_target = vim.fs.normalize(root .. "/" .. input)
          run_vault_on_file(full_target)
        end
      end)
    end
  end)
end

-- =============================================================================
-- Docker & Container Automation
-- =============================================================================

local function with_docker(callback)
  with_root(M.find_docker_root, "No Docker configuration found in workspace", callback)
end

local function resolve_docker_target_file(root)
  local file = vim.fn.expand("%:p")
  if file ~= "" and (vim.bo.filetype == "dockerfile" or file:match("Dockerfile") or file:match("Containerfile")) then
    return file
  end
  if root then
    if is_file(root .. "/Dockerfile") then
      return root .. "/Dockerfile"
    elseif is_file(root .. "/Containerfile") then
      return root .. "/Containerfile"
    end
  end
  return file ~= "" and file or nil
end

function M.docker_build()
  with_docker(function(root)
    local raw_name = vim.fs.normalize(root):match("([^/]+)$") or "app"
    local target_dir_name = raw_name:lower():gsub("[^%w%._%-]", "-"):gsub("^%-+", ""):gsub("%-+$", "")
    if target_dir_name == "" then
      target_dir_name = "app"
    end
    M.run_term("docker build -t " .. vim.fn.shellescape(target_dir_name) .. " .", { cwd = root })
  end)
end

function M.docker_lint()
  with_docker(function(root)
    local file = resolve_docker_target_file(root)
    if vim.fn.executable("hadolint") == 1 then
      if file and file ~= "" then
        M.run_term("hadolint " .. vim.fn.shellescape(file), { cwd = root })
      else
        local fallback = is_file(root .. "/Containerfile") and "Containerfile" or "Dockerfile"
        M.run_term("hadolint " .. fallback, { cwd = root })
      end
    else
      vim.notify("hadolint is not installed in PATH. Install via Mason (:MasonInstall hadolint).", vim.log.levels.WARN, { title = "Cumulus DevOps" })
    end
  end)
end

-- =============================================================================
-- Helm & Kubernetes Automation
-- =============================================================================

local function with_helm(callback)
  with_root(M.find_helm_root, "No Helm configuration found in workspace", callback)
end

function M.helm_lint()
  with_helm(function(root)
    if vim.fn.executable("helm") == 1 then
      M.run_term("helm lint .", { cwd = root })
    else
      vim.notify("helm CLI is not installed in PATH.", vim.log.levels.WARN, { title = "Cumulus DevOps" })
    end
  end)
end

function M.helm_template()
  with_helm(function(root)
    if vim.fn.executable("helm") == 1 then
      M.run_term("helm template .", { cwd = root })
    else
      vim.notify("helm CLI is not installed in PATH.", vim.log.levels.WARN, { title = "Cumulus DevOps" })
    end
  end)
end

-- =============================================================================
-- Global Keymaps & WhichKey Registration
-- =============================================================================

M.keymaps_registered = false

function M.whichkey_spec()
  return {
    { "<leader>o", group = "devops/infra", icon = "󱁢 " },
    { "<leader>ot", group = "terraform/opentofu", icon = "󱁢 " },
    { "<leader>oc", group = "cloudformation/sam", icon = "󰅟 " },
    { "<leader>oy", group = "ansible", icon = "󰚰 " },
    { "<leader>od", group = "docker", icon = "󰡨 " },
    { "<leader>ok", group = "helm/k8s", icon = "󱃾 " },
  }
end

function M.setup_keymaps(force)
  if M.keymaps_registered and not force then
    return
  end
  M.keymaps_registered = true

  local function safe_map(mode, lhs, rhs, opts)
    local ok, err = pcall(vim.keymap.set, mode, lhs, rhs, opts)
    if not ok then
      vim.notify("Failed to register keymap " .. lhs .. ": " .. err, vim.log.levels.WARN, { title = "Cumulus DevOps" })
    end
    return ok
  end

  -- Terraform & OpenTofu (<leader>ot)
  safe_map("n", "<leader>oti", M.terraform_init, { desc = "Terraform: Init", silent = true })
  safe_map("n", "<leader>otv", M.terraform_validate, { desc = "Terraform: Validate", silent = true })
  safe_map("n", "<leader>otp", M.terraform_plan, { desc = "Terraform: Plan", silent = true })
  safe_map("n", "<leader>ota", M.terraform_apply, { desc = "Terraform: Apply", silent = true })
  safe_map("n", "<leader>otf", M.terraform_fmt, { desc = "Terraform: Format", silent = true })
  safe_map("n", "<leader>otl", M.terraform_lint, { desc = "Terraform: Lint (tflint)", silent = true })
  safe_map("n", "<leader>ots", M.terraform_security, { desc = "Terraform: Security Scan (trivy/tfsec)", silent = true })
  safe_map("n", "<leader>oto", M.terraform_output, { desc = "Terraform: Output", silent = true })

  -- AWS CloudFormation & SAM (<leader>oc)
  safe_map("n", "<leader>ocv", M.cfn_validate, { desc = "CloudFormation: Validate Template", silent = true })
  safe_map("n", "<leader>ocl", M.cfn_lint, { desc = "CloudFormation: Lint (cfn-lint)", silent = true })
  safe_map("n", "<leader>ocV", M.sam_validate, { desc = "SAM: Validate", silent = true })
  safe_map("n", "<leader>ocb", M.sam_build, { desc = "SAM: Build", silent = true })
  safe_map("n", "<leader>oci", M.sam_local_invoke, { desc = "SAM: Local Invoke", silent = true })
  safe_map("n", "<leader>ocr", M.sam_local_start_api, { desc = "SAM: Local Start API", silent = true })
  safe_map("n", "<leader>ocg", M.cfn_guard_validate, { desc = "CloudFormation: Policy Check (cfn-guard)", silent = true })

  -- Ansible Automation (<leader>oy)
  safe_map("n", "<leader>oys", M.ansible_syntax_check, { desc = "Ansible: Syntax Check", silent = true })
  safe_map("n", "<leader>oyl", M.ansible_lint, { desc = "Ansible: Lint Playbook", silent = true })
  safe_map("n", "<leader>oyc", M.ansible_dry_run, { desc = "Ansible: Dry Run (--check)", silent = true })
  safe_map("n", "<leader>oyr", M.ansible_run_playbook, { desc = "Ansible: Run Playbook", silent = true })
  safe_map("n", "<leader>oyi", M.ansible_inventory_graph, { desc = "Ansible: Inventory Graph", silent = true })
  safe_map("n", "<leader>oyd", M.ansible_doc_lookup, { desc = "Ansible: Module Documentation", silent = true })
  safe_map("n", "<leader>oyv", M.ansible_vault_action, { desc = "Ansible: Vault Action", silent = true })

  -- Docker & Containers (<leader>od)
  safe_map("n", "<leader>odb", M.docker_build, { desc = "Docker: Build Image", silent = true })
  safe_map("n", "<leader>odl", M.docker_lint, { desc = "Docker: Lint Dockerfile", silent = true })

  -- Helm & Kubernetes (<leader>ok)
  safe_map("n", "<leader>okl", M.helm_lint, { desc = "Helm: Lint Chart", silent = true })
  safe_map("n", "<leader>okt", M.helm_template, { desc = "Helm: Render Template", silent = true })
end

return M
