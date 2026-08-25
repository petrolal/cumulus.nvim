#!/usr/bin/env bash
# Quick smoke validation test script for cumulus.nvim

set -e

echo "=== Cumulus Neovim Distribution Smoke Test ==="

echo "[1/7] Verifying Shell Scripts Syntax (bootstrap.sh, install.sh, validate.sh)..."
if bash -n bootstrap.sh && bash -n scripts/install.sh && bash -n scripts/validate.sh; then
  echo "✔ Shell scripts syntax PASSED."
else
  echo "✖ Shell scripts syntax FAILED."
  exit 1
fi

echo "[2/7] Verifying Neovim Loading & Startup..."
if nvim -u init.lua --headless "+lua print('✔ Core init.lua loads without error')" +qa; then
  echo "✔ Headless core init.lua PASSED."
else
  echo "✖ Headless core init.lua FAILED."
  exit 1
fi

echo "[3/7] Verifying Core Modules (Options, Keymaps, Autocmds, Health)..."
if nvim -u init.lua --headless "+lua require('cumulus.core.options'); require('cumulus.core.keymaps'); require('cumulus.core.autocmds'); require('cumulus.health'); print('✔ Core modules loaded successfully')" +qa; then
  echo "✔ Core modules PASSED."
else
  echo "✖ Core modules FAILED."
  exit 1
fi

echo "[4/7] Verifying Theme System (AWS, Azure, GCP, OCI)..."
if nvim -u init.lua --headless "+lua require('cumulus.theme').setup(); print('✔ Theme system initialized')" +qa; then
  echo "✔ Theme system PASSED."
else
  echo "✖ Theme system FAILED."
  exit 1
fi

echo "[5/7] Verifying LSP, Completion & UI Specs..."
if nvim -u init.lua --headless "+lua assert(pcall(require, 'blink.cmp')); assert(pcall(require, 'nvim-lspconfig')); assert(pcall(require, 'render-markdown')); assert(pcall(require, 'persistence')); print('✔ Plugins and UI specs verified')" +qa; then
  echo "✔ Plugins and UI specs PASSED."
else
  echo "✖ Plugins and UI specs FAILED."
  exit 1
fi

echo "[6/7] Verifying Engine Bridge & DevOps Suite (Terraform, CloudFormation, Ansible, WhichKey, Mason & Scoped Buffers)..."
if nvim -u init.lua --headless "+lua
  local e = require('cumulus.util.engine')
  assert(type(e.detect_platform) == 'function', 'detect_platform not found')
  assert(type(e.install) == 'function', 'install not found')
  assert(type(e.run_command) == 'function', 'run_command not found')
  assert(type(e.classify_workspace) == 'function', 'classify_workspace not found')
  assert(type(e.discover_devops_roots) == 'function', 'discover_devops_roots not found')
  assert(type(e.inspect_cfn_template) == 'function', 'inspect_cfn_template not found')
  assert(type(e.validate_cfn_template) == 'function', 'validate_cfn_template not found')
  assert(type(e.inspect_ansible_playbook) == 'function', 'inspect_ansible_playbook not found')
  assert(type(e.validate_ansible_playbook) == 'function', 'validate_ansible_playbook not found')
  assert(type(e.parse_ansible_inventory) == 'function', 'parse_ansible_inventory not found')
  assert(type(e.generate_dap_config) == 'function', 'generate_dap_config not found')
  -- Story 13.1: Consolidated UI picker and action functions
  assert(type(e.select_bean) == 'function', 'select_bean not found')
  assert(type(e.select_endpoint) == 'function', 'select_endpoint not found')
  assert(type(e.optimize_imports_buffer) == 'function', 'optimize_imports_buffer not found')
  assert(type(e.validate_k8s_manifest_buffer) == 'function', 'validate_k8s_manifest_buffer not found')
  assert(type(e.validate_manifest) == 'function', 'validate_manifest not found')
  assert(type(e.validate_migrations_action) == 'function', 'validate_migrations_action not found')
  assert(type(e.resolve_git_conflicts) == 'function', 'resolve_git_conflicts not found')
  assert(type(e.resolve_conflicts) == 'function', 'resolve_conflicts not found')
  assert(type(e.view_coverage) == 'function', 'view_coverage not found')
  assert(type(e.search_indexed_logs) == 'function', 'search_indexed_logs not found')

  -- Story 13.2: Universal UI Terminal & Notification Bridge Standardization
  assert(type(e.notify) == 'function', 'notify not found')
  assert(type(e.notify_info) == 'function', 'notify_info not found')
  assert(type(e.notify_warn) == 'function', 'notify_warn not found')
  assert(type(e.notify_err) == 'function', 'notify_err not found')
  assert(type(e.run_term) == 'function', 'run_term not found')

  -- Test headless invocation of notification and terminal helpers
  e.notify('test notify', vim.log.levels.INFO, 'Test Title')
  e.notify_info('test info', 'Test Title')
  e.notify_warn('test warn', 'Test Title')
  e.notify_err('test err', 'Test Title')

  -- Verify purged stub and wrapper files do not exist
  assert(not pcall(require, 'cumulus.util.rust'), 'rust.lua must be purged')
  assert(not pcall(require, 'cumulus.util.beans'), 'beans.lua must be purged')
  assert(not pcall(require, 'cumulus.util.endpoints'), 'endpoints.lua must be purged')
  assert(not pcall(require, 'cumulus.util.import-optimizer'), 'import-optimizer.lua must be purged')
  assert(not pcall(require, 'cumulus.util.k8s-validator'), 'k8s-validator.lua must be purged')
  assert(not pcall(require, 'cumulus.util.migrations'), 'migrations.lua must be purged')
  assert(not pcall(require, 'cumulus.util.conflicts'), 'conflicts.lua must be purged')
  assert(not pcall(require, 'cumulus.util.coverage'), 'coverage.lua must be purged')
  assert(not pcall(require, 'cumulus.util.log-indexer'), 'log-indexer.lua must be purged')
  local devops = require('cumulus.util.devops')
  assert(type(devops.cfn_validate) == 'function')
  assert(type(devops.sam_local_invoke) == 'function')
  assert(type(devops.ansible_syntax_check) == 'function')
  assert(type(devops.ansible_lint) == 'function')
  assert(type(devops.ansible_dry_run) == 'function')
  assert(type(devops.ansible_run_playbook) == 'function')
  assert(type(devops.ansible_inventory_graph) == 'function')
  assert(type(devops.ansible_doc_lookup) == 'function')
  assert(type(devops.ansible_vault_action) == 'function')

  -- Story 9.3: Universal Keymap Registration & WhichKey Scope Configuration
  assert(type(devops.setup_keymaps) == 'function', 'setup_keymaps not found')
  assert(type(devops.whichkey_spec) == 'function', 'whichkey_spec not found')
  local wk_spec = devops.whichkey_spec()
  local groups = {}
  for _, item in ipairs(wk_spec) do
    groups[item[1]] = item.group
  end
  assert(groups['<leader>o'] == 'devops/infra', '<leader>o missing in devops whichkey_spec')
  assert(groups['<leader>ot'] == 'terraform/opentofu', '<leader>ot missing in devops whichkey_spec')
  assert(groups['<leader>oc'] == 'cloudformation/sam', '<leader>oc missing in devops whichkey_spec')
  assert(groups['<leader>oy'] == 'ansible', '<leader>oy missing in devops whichkey_spec')
  assert(groups['<leader>od'] == 'docker', '<leader>od missing in devops whichkey_spec')
  assert(groups['<leader>ok'] == 'helm/k8s', '<leader>ok missing in devops whichkey_spec')

  -- Check global availability of all 26 DevOps keymaps across any buffer
  -- Story 9.3: Keymaps are now GLOBALLY registered (not buffer-scoped)
  local global_maps = vim.api.nvim_get_keymap('n')
  local expected_subkeys = {
    -- Terraform (<leader>ot)
    'oti', 'otv', 'otp', 'ota', 'otf', 'otl', 'ots', 'oto',
    -- AWS CloudFormation & SAM (<leader>oc)
    'ocv', 'ocl', 'ocV', 'ocb', 'oci', 'ocr', 'ocg',
    -- Ansible (<leader>oy)
    'oys', 'oyl', 'oyc', 'oyr', 'oyi', 'oyd', 'oyv',
    -- Docker (<leader>od)
    'odb', 'odl',
    -- Helm (<leader>ok)
    'okl', 'okt',
  }
  local found_keymaps = 0
  for _, key in ipairs(expected_subkeys) do
    local found = false
    for _, m in ipairs(global_maps) do
      if m.lhs == '\''<leader>\'' .. key or m.lhs == '\''<Space>\'' .. key or m.lhs == '\'' '\'' .. key then
        found = true
        found_keymaps = found_keymaps + 1
        break
      end
    end
    assert(found, '\''leader '\' .. key .. '\'' missing from global keymaps\'')
  end
  assert(found_keymaps >= 26, '\''DevOps keymap count mismatch: found '\'' .. found_keymaps .. '\'' of 26\'')

  -- Robust Mason spec lookup
  local mason_plugins = require('cumulus.plugins.tools-mason')
  local ensure_installed = nil
  for _, plugin in ipairs(mason_plugins) do
    if plugin.opts and plugin.opts.ensure_installed then
      ensure_installed = plugin.opts.ensure_installed
      break
    end
  end
  assert(ensure_installed, 'mason ensure_installed not found')
  local ensure_set = {}
  for _, pkg in ipairs(ensure_installed) do
    ensure_set[pkg] = true
  end
  for _, req in ipairs({'terraform-ls', 'tflint', 'cfn-lint', 'ansible-language-server', 'ansible-lint', 'yaml-language-server'}) do
    assert(ensure_set[req], 'Missing Mason package: ' .. req)
  end

  -- Robust Conform spec lookup
  local conform_plugins = require('cumulus.plugins.tools-formatting')
  local formatters_by_ft = nil
  for _, plugin in ipairs(conform_plugins) do
    if plugin.opts and plugin.opts.formatters_by_ft then
      formatters_by_ft = plugin.opts.formatters_by_ft
      break
    end
  end
  assert(formatters_by_ft, 'conform formatters_by_ft not found')
  assert(formatters_by_ft.terraform[1] == 'terraform_fmt', 'terraform_fmt not mapped')

  -- Story 9.1: DevOps Root & Workspace Discovery Engine
  assert(type(devops.resolve_search_dir) == 'function', 'resolve_search_dir not found')
  assert(type(devops.find_tf_root) == 'function', 'find_tf_root not found')
  assert(type(devops.find_cfn_root) == 'function', 'find_cfn_root not found')
  assert(type(devops.find_ansible_root) == 'function', 'find_ansible_root not found')
  assert(type(devops.find_docker_root) == 'function', 'find_docker_root not found')
  assert(type(devops.find_helm_root) == 'function', 'find_helm_root not found')

  -- Mock workspace test setup
  local tmp_root = vim.fs.normalize(vim.fn.tempname())
  vim.fn.mkdir(tmp_root, 'p')

  -- 1. Terraform discovery
  local tf_proj = tmp_root .. '/tf_proj'
  local tf_mod = tf_proj .. '/infra/terraform/modules/vpc'
  vim.fn.mkdir(tf_mod, 'p')
  vim.fn.writefile({'resource "vpc" {}'}, tf_mod .. '/vpc.tf')
  vim.fn.writefile({'terraform {}'}, tf_proj .. '/infra/terraform/main.tf')
  local tf_from_file = devops.find_tf_root(tf_mod .. '/vpc.tf')
  assert(tf_from_file == tf_mod, 'Terraform root from child buffer mismatch: ' .. tostring(tf_from_file))
  local tf_from_root = devops.find_tf_root(tf_proj)
  assert(tf_from_root == tf_proj .. '/infra/terraform', 'Terraform root convention scan mismatch: ' .. tostring(tf_from_root))

  -- 2. CloudFormation / SAM discovery
  local cfn_proj = tmp_root .. '/cfn_proj'
  vim.fn.mkdir(cfn_proj .. '/src', 'p')
  vim.fn.writefile({'AWSTemplateFormatVersion: 2010-09-09'}, cfn_proj .. '/template.yaml')
  vim.fn.writefile({'version = 0.1'}, cfn_proj .. '/samconfig.toml')
  local cfn_from_root = devops.find_cfn_root(cfn_proj)
  assert(cfn_from_root == cfn_proj, 'CFN root mismatch: ' .. tostring(cfn_from_root))
  local cfn_from_child = devops.find_cfn_root(cfn_proj .. '/src/app.py')
  assert(cfn_from_child == cfn_proj, 'CFN root from child mismatch: ' .. tostring(cfn_from_child))

  -- 3. Ansible discovery
  local ans_proj = tmp_root .. '/ans_proj'
  local ans_pb = ans_proj .. '/playbooks'
  vim.fn.mkdir(ans_pb, 'p')
  vim.fn.writefile({'[defaults]'}, ans_proj .. '/ansible.cfg')
  vim.fn.writefile({'- hosts: all'}, ans_pb .. '/site.yml')
  local ans_from_root = devops.find_ansible_root(ans_proj)
  assert(ans_from_root == ans_proj, 'Ansible root mismatch: ' .. tostring(ans_from_root))
  local ans_from_pb = devops.find_ansible_root(ans_pb .. '/site.yml')
  assert(ans_from_pb == ans_pb, 'Ansible root from playbook mismatch: ' .. tostring(ans_from_pb))

  -- 4. Docker discovery
  local doc_proj = tmp_root .. '/doc_proj'
  vim.fn.mkdir(doc_proj .. '/src', 'p')
  vim.fn.writefile({'FROM alpine'}, doc_proj .. '/Dockerfile')
  vim.fn.writefile({'services: {}'}, doc_proj .. '/docker-compose.yml')
  local doc_from_root = devops.find_docker_root(doc_proj)
  assert(doc_from_root == doc_proj, 'Docker root mismatch: ' .. tostring(doc_from_root))
  local doc_from_child = devops.find_docker_root(doc_proj .. '/src/main.go')
  assert(doc_from_child == doc_proj, 'Docker root from child mismatch: ' .. tostring(doc_from_child))

  -- 5. Helm discovery
  local helm_proj = tmp_root .. '/helm_proj'
  local helm_chart = helm_proj .. '/charts/web-service'
  vim.fn.mkdir(helm_chart .. '/templates', 'p')
  vim.fn.writefile({'apiVersion: v2', 'name: web-service'}, helm_chart .. '/Chart.yaml')
  vim.fn.writefile({'replicaCount: 1'}, helm_chart .. '/values.yaml')
  local helm_from_root = devops.find_helm_root(helm_proj)
  assert(helm_from_root == helm_chart, 'Helm root from workspace mismatch: ' .. tostring(helm_from_root))
  local helm_from_tpl = devops.find_helm_root(helm_chart .. '/templates/deployment.yaml')
  assert(helm_from_tpl == helm_chart, 'Helm root from template mismatch: ' .. tostring(helm_from_tpl))

  -- 6. Non-DevOps workspace & fallback
  local empty_proj = tmp_root .. '/empty_proj'
  vim.fn.mkdir(empty_proj, 'p')
  vim.fn.writefile({'# Readme'}, empty_proj .. '/README.md')
  assert(devops.find_tf_root(empty_proj) == nil, 'find_tf_root should be nil for empty workspace')
  assert(devops.find_cfn_root(empty_proj) == nil, 'find_cfn_root should be nil for empty workspace')
  assert(devops.find_ansible_root(empty_proj) == nil, 'find_ansible_root should be nil for empty workspace')
  assert(devops.find_docker_root(empty_proj) == nil, 'find_docker_root should be nil for empty workspace')
  assert(devops.find_helm_root(empty_proj) == nil, 'find_helm_root should be nil for empty workspace')
  assert(devops.find_tf_root(999999) == nil or type(devops.find_tf_root(999999)) == 'string', 'find_tf_root invalid buffer must not error')

  -- Story 11.3: Workspace Classification & DevOps Roots Engine Integration
  local jvm = require('cumulus.util.jvm')
  local maven_util = require('cumulus.util.maven')
  local gradle_util = require('cumulus.util.gradle')
  assert(type(jvm.is_jvm_project) == 'function', 'jvm.is_jvm_project not found')

  if e.is_available() then
    local ws_topo = e.classify_workspace(tf_proj)
    assert(type(ws_topo) == 'table', 'classify_workspace must return table topology')
    assert(ws_topo.root ~= nil, 'classify_workspace root must not be nil')
    assert(ws_topo.primary_type ~= nil, 'classify_workspace primary_type must not be nil')

    local ws_devops = e.discover_devops_roots(tf_proj)
    assert(type(ws_devops) == 'table', 'discover_devops_roots must return table')
    assert(type(ws_devops.terraform) == 'table', 'discover_devops_roots terraform must be list')
    assert(#ws_devops.terraform >= 1, 'discover_devops_roots should detect tf roots')
  end

  local jvm_proj = tmp_root .. '/jvm_proj'
  vim.fn.mkdir(jvm_proj, 'p')
  vim.fn.writefile({'<project></project>'}, jvm_proj .. '/pom.xml')
  assert(jvm.is_jvm_project(jvm_proj) == true, 'is_jvm_project should return true for maven repo')
  assert(maven_util.find_pom(jvm_proj) == true, 'find_pom should return true for maven repo')
  assert(gradle_util.find_gradle(jvm_proj) == false, 'find_gradle should return false for maven repo')
  assert(jvm.is_jvm_project(empty_proj) == false, 'is_jvm_project should return false for empty repo')

  -- 7. Verify Root Adoption in Runner Execution & Warning Toasts
  local last_executed_cmd, last_executed_opts = nil, nil
  local orig_run_term = devops.run_term
  devops.run_term = function(cmd, opts)
    last_executed_cmd = cmd
    last_executed_opts = opts
  end

  local last_notify_msg, last_notify_level = nil, nil
  local orig_notify = vim.notify
  vim.notify = function(msg, level, opts)
    last_notify_msg = msg
    last_notify_level = level
  end

  -- Test Terraform adoption & missing notification
  local orig_get_tf_cmd = devops.get_tf_cmd
  devops.get_tf_cmd = function() return 'tofu' end
  local orig_find_tf_root = devops.find_tf_root
  devops.find_tf_root = function() return '/mock/tf/root' end
  devops.terraform_init()
  assert(last_executed_cmd == 'tofu init', 'terraform_init command mismatch')
  assert(last_executed_opts and last_executed_opts.cwd == '/mock/tf/root', 'terraform_init cwd not adopted: ' .. vim.inspect(last_executed_opts))

  -- Test Missing TF workspace warning
  devops.find_tf_root = function() return nil end
  last_executed_cmd = nil
  last_notify_msg = nil
  devops.terraform_plan()
  assert(last_executed_cmd == nil, 'terraform_plan should not execute when no root is found')
  assert(last_notify_msg:match('No Terraform/OpenTofu configuration found in workspace'), 'Missing TF warning toast: ' .. tostring(last_notify_msg))

  -- Test Ansible adoption & missing notification
  local orig_find_ansible_root = devops.find_ansible_root
  devops.find_ansible_root = function() return '/mock/ansible/root' end
  devops.ansible_inventory_graph()
  assert(last_executed_cmd == 'ansible-inventory --graph', 'ansible_inventory_graph command mismatch')
  assert(last_executed_opts and last_executed_opts.cwd == '/mock/ansible/root', 'ansible_inventory_graph cwd not adopted: ' .. vim.inspect(last_executed_opts))

  devops.find_ansible_root = function() return nil end
  last_executed_cmd = nil
  last_notify_msg = nil
  devops.ansible_syntax_check()
  assert(last_executed_cmd == nil, 'ansible_syntax_check should not execute when no root is found')
  assert(last_notify_msg:match('No Ansible configuration found in workspace'), 'Missing Ansible warning toast: ' .. tostring(last_notify_msg))

  -- Test Docker adoption & missing notification
  local orig_find_docker_root = devops.find_docker_root
  devops.find_docker_root = function() return '/mock/docker/my-app' end
  devops.docker_build()
  assert(last_executed_cmd:match('docker build %-t %'my%-app%' %.'), 'docker_build command mismatch: ' .. tostring(last_executed_cmd))
  assert(last_executed_opts and last_executed_opts.cwd == '/mock/docker/my-app', 'docker_build cwd not adopted')

  devops.find_docker_root = function() return nil end
  last_executed_cmd = nil
  last_notify_msg = nil
  devops.docker_build()
  assert(last_executed_cmd == nil, 'docker_build should not execute when no root is found')
  assert(last_notify_msg:match('No Docker configuration found in workspace'), 'Missing Docker warning toast: ' .. tostring(last_notify_msg))

  -- Test Helm adoption & missing notification
  local orig_find_helm_root = devops.find_helm_root
  devops.find_helm_root = function() return '/mock/helm/my-chart' end
  devops.helm_lint()
  assert(last_executed_cmd == 'helm lint .', 'helm_lint command mismatch: ' .. tostring(last_executed_cmd))
  assert(last_executed_opts and last_executed_opts.cwd == '/mock/helm/my-chart', 'helm_lint cwd not adopted')

  devops.find_helm_root = function() return nil end
  last_executed_cmd = nil
  last_notify_msg = nil
  devops.helm_lint()
  assert(last_executed_cmd == nil, 'helm_lint should not execute when no root is found')
  assert(last_notify_msg:match('No Helm configuration found in workspace'), 'Missing Helm warning toast: ' .. tostring(last_notify_msg))

  -- Test CloudFormation / SAM missing notification
  local orig_find_cfn_root = devops.find_cfn_root
  devops.find_cfn_root = function() return nil end
  last_executed_cmd = nil
  last_notify_msg = nil
  devops.sam_validate()
  assert(last_executed_cmd == nil, 'sam_validate should not execute when no root is found')
  assert(last_notify_msg:match('No CloudFormation/SAM configuration found in workspace'), 'Missing CFN warning toast: ' .. tostring(last_notify_msg))

  -- Restore mocks
  devops.run_term = orig_run_term
  vim.notify = orig_notify
  devops.get_tf_cmd = orig_get_tf_cmd
  devops.find_tf_root = orig_find_tf_root
  devops.find_ansible_root = orig_find_ansible_root
  devops.find_docker_root = orig_find_docker_root
  devops.find_helm_root = orig_find_helm_root
  devops.find_cfn_root = orig_find_cfn_root

  -- Cleanup mock workspace
  vim.fn.delete(tmp_root, 'rf')

  local plat = e.detect_platform() or 'unknown'
  assert(vim.fn.exists(':CumulusInstallEngine') == 2, ':CumulusInstallEngine not registered')
  print('✔ Engine bridge, DevOps WhichKey, scoped buffers, Mason tooling, Workspace Root Discovery & Global Root Execution with Warning Toasts verified (' .. plat .. ')')

" +qa; then
  echo "✔ Engine bridge & DevOps suite PASSED."
else
  echo "✖ Engine bridge & DevOps suite FAILED."
  exit 1
fi

echo "=========================================="
echo " ALL VALIDATIONS PASSED SUCCESSFULLY!"
echo "=========================================="

