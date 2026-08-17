-- Cumulus DevOps & Infrastructure Tooling Suite (Story 8.1, Story 8.2, Story 8.3, Story 8.4)
--
-- Interactive and non-blocking compilers, validators, linters, and runners
-- for Terraform/OpenTofu, AWS CloudFormation/SAM, and Ansible.

local M = {}

--- Run a command in an interactive, non-blocking terminal
--- Uses Snacks.terminal when available, otherwise falls back to a split buffer.
function M.run_term(cmd, opts)
  opts = opts or {}
  if _G.Snacks and _G.Snacks.terminal then
    Snacks.terminal(cmd, opts)
  else
    vim.cmd("botright 15split")
    local win = vim.api.nvim_get_current_win()
    local buf = vim.api.nvim_create_buf(false, true)
    vim.api.nvim_win_set_buf(win, buf)
    vim.fn.termopen(cmd, {
      cwd = opts.cwd or vim.fn.getcwd(),
      on_exit = function(_, code)
        local level = (code == 0) and vim.log.levels.INFO or vim.log.levels.ERROR
        vim.notify("Process exited with code " .. code, level)
      end,
    })
    vim.keymap.set("n", "q", "<cmd>q<cr>", { buffer = buf, silent = true })
    vim.keymap.set("t", "<Esc>", [[<C-\><C-n>]], { buffer = buf, silent = true })
    vim.cmd("startinsert")
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
  callback(tf)
end

function M.terraform_init()
  with_tf(function(tf)
    M.run_term(tf .. " init")
  end)
end

function M.terraform_validate()
  with_tf(function(tf)
    M.run_term(tf .. " validate")
  end)
end

function M.terraform_plan()
  with_tf(function(tf)
    M.run_term(tf .. " plan")
  end)
end

function M.terraform_apply()
  with_tf(function(tf)
    M.run_term(tf .. " apply")
  end)
end

function M.terraform_fmt()
  with_tf(function(tf)
    local file = vim.fn.expand("%:p")
    if file ~= "" then
      vim.cmd("update")
      local out = vim.fn.system({ tf, "fmt", file })
      vim.cmd("edit!")
      if vim.v.shell_error == 0 then
        vim.notify("Formatted with " .. tf .. " fmt", vim.log.levels.INFO)
      else
        vim.notify("Formatting error: " .. out, vim.log.levels.ERROR)
      end
    else
      M.run_term(tf .. " fmt")
    end
  end)
end

function M.terraform_lint()
  if vim.fn.executable("tflint") == 1 then
    M.run_term("tflint")
  else
    vim.notify("tflint is not installed in PATH. Install via Mason (:MasonInstall tflint).", vim.log.levels.WARN)
  end
end

function M.terraform_security()
  if vim.fn.executable("trivy") == 1 then
    M.run_term("trivy config .")
  elseif vim.fn.executable("tfsec") == 1 then
    M.run_term("tfsec .")
  else
    vim.notify("Neither 'trivy' nor 'tfsec' is installed in PATH.", vim.log.levels.WARN)
  end
end

function M.terraform_output()
  with_tf(function(tf)
    M.run_term(tf .. " output")
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

function M.cfn_validate()
  local file = vim.fn.expand("%:p")
  if file == "" then
    vim.notify("No file to validate", vim.log.levels.WARN)
    return
  end
  if vim.fn.executable("aws") == 1 then
    M.run_term("aws cloudformation validate-template --template-body file://" .. vim.fn.shellescape(file))
  elseif vim.fn.executable("cfn-lint") == 1 then
    M.run_term("cfn-lint " .. vim.fn.shellescape(file))
  else
    vim.notify("Neither 'aws' CLI nor 'cfn-lint' was found in PATH.", vim.log.levels.WARN)
  end
end

function M.cfn_lint()
  local file = vim.fn.expand("%:p")
  if vim.fn.executable("cfn-lint") == 1 then
    if file ~= "" then
      M.run_term("cfn-lint " .. vim.fn.shellescape(file))
    else
      M.run_term("cfn-lint")
    end
  else
    vim.notify("cfn-lint is not installed in PATH. Install via Mason (:MasonInstall cfn-lint).", vim.log.levels.WARN)
  end
end

function M.sam_validate()
  if vim.fn.executable("sam") == 1 then
    M.run_term("sam validate")
  else
    vim.notify("AWS SAM CLI ('sam') is not installed in PATH.", vim.log.levels.WARN)
  end
end

function M.sam_build()
  if vim.fn.executable("sam") == 1 then
    M.run_term("sam build")
  else
    vim.notify("AWS SAM CLI ('sam') is not installed in PATH.", vim.log.levels.WARN)
  end
end

function M.sam_local_invoke()
  if vim.fn.executable("sam") == 1 then
    M.run_term("sam local invoke")
  else
    vim.notify("AWS SAM CLI ('sam') is not installed in PATH.", vim.log.levels.WARN)
  end
end

function M.sam_local_start_api()
  if vim.fn.executable("sam") == 1 then
    M.run_term("sam local start-api")
  else
    vim.notify("AWS SAM CLI ('sam') is not installed in PATH.", vim.log.levels.WARN)
  end
end

function M.cfn_guard_validate()
  if vim.fn.executable("cfn-guard") == 1 then
    local file = vim.fn.expand("%:p")
    M.run_term("cfn-guard validate --template " .. vim.fn.shellescape(file))
  else
    vim.notify("cfn-guard is not installed in PATH.", vim.log.levels.WARN)
  end
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
    if content:match("%-%s*hosts:") or content:match("%-%s*name:") and content:match("tasks:") then
      return true
    end
  end
  return false
end

function M.ansible_syntax_check()
  local file = vim.fn.expand("%:p")
  if file == "" then
    vim.notify("No file to check", vim.log.levels.WARN)
    return
  end
  if vim.fn.executable("ansible-playbook") == 1 then
    M.run_term("ansible-playbook --syntax-check " .. vim.fn.shellescape(file))
  else
    vim.notify("ansible-playbook is not installed in PATH.", vim.log.levels.WARN)
  end
end

function M.ansible_lint()
  local file = vim.fn.expand("%:p")
  if vim.fn.executable("ansible-lint") == 1 then
    if file ~= "" then
      M.run_term("ansible-lint " .. vim.fn.shellescape(file))
    else
      M.run_term("ansible-lint")
    end
  else
    vim.notify("ansible-lint is not installed in PATH. Install via Mason (:MasonInstall ansible-lint).", vim.log.levels.WARN)
  end
end

function M.ansible_dry_run()
  local file = vim.fn.expand("%:p")
  if file == "" then
    vim.notify("No file to run", vim.log.levels.WARN)
    return
  end
  if vim.fn.executable("ansible-playbook") == 1 then
    M.run_term("ansible-playbook --check " .. vim.fn.shellescape(file))
  else
    vim.notify("ansible-playbook is not installed in PATH.", vim.log.levels.WARN)
  end
end

function M.ansible_run_playbook()
  local file = vim.fn.expand("%:p")
  if file == "" then
    vim.notify("No file to run", vim.log.levels.WARN)
    return
  end
  if vim.fn.executable("ansible-playbook") == 1 then
    M.run_term("ansible-playbook " .. vim.fn.shellescape(file))
  else
    vim.notify("ansible-playbook is not installed in PATH.", vim.log.levels.WARN)
  end
end

function M.ansible_inventory_graph()
  if vim.fn.executable("ansible-inventory") == 1 then
    M.run_term("ansible-inventory --graph")
  else
    vim.notify("ansible-inventory is not installed in PATH.", vim.log.levels.WARN)
  end
end

function M.ansible_doc_lookup()
  if vim.fn.executable("ansible-doc") == 1 then
    vim.ui.input({ prompt = "Ansible Module / Plugin Doc: " }, function(input)
      if input and input ~= "" then
        M.run_term("ansible-doc " .. vim.fn.shellescape(input))
      end
    end)
  else
    vim.notify("ansible-doc is not installed in PATH.", vim.log.levels.WARN)
  end
end

function M.ansible_vault_action()
  if vim.fn.executable("ansible-vault") ~= 1 then
    vim.notify("ansible-vault is not installed in PATH.", vim.log.levels.WARN)
    return
  end
  local file = vim.fn.expand("%:p")
  if file == "" then
    vim.notify("No active file for vault operation", vim.log.levels.WARN)
    return
  end

  local actions = { "view", "encrypt", "decrypt", "edit" }
  vim.ui.select(actions, { prompt = "Select Ansible Vault Action:" }, function(choice)
    if choice then
      M.run_term("ansible-vault " .. choice .. " " .. vim.fn.shellescape(file))
    end
  end)
end

return M
