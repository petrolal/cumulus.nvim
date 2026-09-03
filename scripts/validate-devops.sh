#!/usr/bin/env bash
# devops.lua declaration-order regression guard -- behavioral smoke test
#
# Mirrors validate-refactor.sh / validate-dap-jvm.sh: uses `vim.cmd('cquit
# 1')` on assertion failure so pass/fail is trustworthy, unlike
# scripts/validate.sh's `+lua assert(...)` pattern which never propagates a
# non-zero exit code.
#
# Context: `create_root_finder` closes over `resolve_search_dir`, and
# `M.find_tf_root`/`M.find_cfn_root`/`M.find_ansible_root`/`M.find_docker_root`/
# `M.find_helm_root` are each built by calling `create_root_finder(...)` at
# module-load time. A prior version of lua/tetravim/core/devops.lua declared
# these `M.find_*_root` assignments BEFORE `resolve_search_dir`/
# `create_root_finder` existed, which is a silent trap in Lua: the
# assignments themselves don't error at load time (forward local references
# just capture nil), but every actual CALL to any `M.find_*_root` function
# afterwards throws "attempt to call a nil value" the moment it reaches the
# missing helper. That code fix already shipped (the file now declares
# `resolve_search_dir` and `create_root_finder` before any `M.find_*_root`
# assignment) -- this script is the missing regression test guarding it:
# every finder must be CALLABLE without erroring.

set -e

echo "=== DevOps Root-Finder Declaration-Order Regression Guard ==="

echo "[1/2] Static: devops.lua declares resolve_search_dir and create_root_finder BEFORE any M.find_*_root assignment..."
nvim -u init.lua --headless -c "lua
local ok, err = pcall(function()
  local src = io.open('lua/tetravim/core/devops.lua', 'r'):read('*a')

  local resolve_pos = src:find('local function resolve_search_dir')
  local factory_pos = src:find('local function create_root_finder')
  local first_assign_pos = src:find('M%.find_tf_root%s*=%s*create_root_finder')

  assert(resolve_pos, 'resolve_search_dir declaration not found')
  assert(factory_pos, 'create_root_finder declaration not found')
  assert(first_assign_pos, 'M.find_tf_root assignment not found')

  assert(
    resolve_pos < first_assign_pos,
    'resolve_search_dir must be declared BEFORE the first M.find_*_root assignment'
  )
  assert(
    factory_pos < first_assign_pos,
    'create_root_finder must be declared BEFORE the first M.find_*_root assignment'
  )
end)
if not ok then
  io.stderr:write('FAIL: ' .. tostring(err) .. '\n')
  vim.cmd('cquit 1')
else
  print('OK: declaration order is correct in the source')
end
" -c "qa!"

echo "[2/2] Behavioral: every M.find_*_root function is REAL-CALLABLE without erroring, using native vim.fs marker discovery..."
nvim -u init.lua --headless -c "lua
local ok, err = pcall(function()
  local devops = require('tetravim.core.devops')

  local finders = {
    'find_tf_root',
    'find_cfn_root',
    'find_ansible_root',
    'find_docker_root',
    'find_helm_root',
  }

  vim.cmd('enew')
  local bufnr = vim.api.nvim_get_current_buf()

  -- Suppress the graceful-degradation notify a finder may emit when no
  -- matching marker exists in this environment -- this script only cares
  -- that the CALL ITSELF doesn't throw a Lua error (the declaration-order
  -- bug), not whether a root is actually found.
  local orig_notify = vim.notify
  vim.notify = function() end

  for _, name in ipairs(finders) do
    assert(type(devops[name]) == 'function', name .. ' is not a function on tetravim.core.devops')
    local call_ok, call_err = pcall(devops[name], bufnr)
    if not call_ok then
      vim.notify = orig_notify
      error(name .. '(bufnr) raised an error -- declaration-order regression: ' .. tostring(call_err), 0)
    end
    -- Also exercise the string-path argument form (resolve_search_dir's
    -- other branch) for full coverage of the same call path.
    local call_ok2, call_err2 = pcall(devops[name], vim.fn.getcwd())
    if not call_ok2 then
      vim.notify = orig_notify
      error(name .. '(path) raised an error -- declaration-order regression: ' .. tostring(call_err2), 0)
    end
  end

  vim.notify = orig_notify
end)
if not ok then
  io.stderr:write('FAIL: ' .. tostring(err) .. '\n')
  vim.cmd('cquit 1')
else
  print('OK: all 5 root finders are callable (buffer and path forms) without erroring')
end
" -c "qa!"

echo ""
echo "✔ DevOps root-finder declaration-order regression guard PASSED."
