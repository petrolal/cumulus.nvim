#!/usr/bin/env bash
set -e

echo "=== TetraVim Story 4.2 Smoke Test ==="

# Mock gh and glab commands
mkdir -p .mock-bin
cat << 'EOF' > .mock-bin/gh
#!/bin/bash
exit 0
EOF
chmod +x .mock-bin/gh

cat << 'EOF' > .mock-bin/glab
#!/bin/bash
exit 0
EOF
chmod +x .mock-bin/glab

export PATH="$(pwd)/.mock-bin:$PATH"
trap "rm -rf .mock-bin" EXIT

echo "[1/3] Verifying Static Specs..."
nvim --headless -u init.lua -c "lua require('plenary.busted')" -c "PlenaryBustedDirectory lua/tetravim/tests/forge_review_spec.lua" -c "qa"

echo "[2/3] Verifying vim.system usage in forge.lua..."
nvim --headless -u init.lua -c "lua
local ok, err = pcall(function()
  local forge = require('tetravim.util.forge')
  local system_calls = {}
  local orig_system = vim.system
  vim.system = function(cmd, opts, cb)
    table.insert(system_calls, cmd)
    if cb then
       if cmd[1] == 'git' and cmd[2] == 'fetch' then
         cb({code = 0, stdout = ''})
       else
         cb({code = 0, stdout = '#123 mock\tmock-ref\tmock-base\n'})
       end
    end
    return {wait = function() return {code = 0, stdout = 'github'} end}
  end
  
  vim.schedule_wrap = function(cb) return cb end

  package.loaded['snacks'] = {
    picker = {
      select = function(items, opts, cb)
         if items and #items > 0 then
           cb(items[1])
         else
           cb({number = '123', ref = 'mock-ref', base = 'mock-base'})
         end
      end
    }
  }

  package.loaded['tetravim.util.ui'] = {
    notify_info = function() end,
    notify_err = function() end,
    notify_warn = function() end,
  }

  local git = require('tetravim.util.git')
  git.guard = function() return true end
  git.repo_root = function() return '/mock' end

  local orig_cmd = vim.cmd
  vim.cmd = function(cmd) end
  
  -- Mock buffer APIs to test comment logic
  vim.api.nvim_buf_get_lines = function() return { '<!-- Enter comment. Save (:w) to submit. -->', 'Test comment body' } end
  vim.api.nvim_buf_set_lines = function() end
  vim.api.nvim_win_set_cursor = function() end
  vim.api.nvim_buf_set_name = function() end
  vim.api.nvim_buf_delete = function() end

  forge.list_and_review_prs()
  forge.checkout_pr()
  
  forge.add_comment()
  vim.api.nvim_exec_autocmds('BufWriteCmd', { buffer = vim.api.nvim_get_current_buf() })
  
  local has_pr_list, has_pr_checkout, has_pr_comment, has_pr_view = false, false, false, false
  for _, cmd in ipairs(system_calls) do
    local c = table.concat(cmd, ' ')
    if c:match('gh pr list') then has_pr_list = true end
    if c:match('gh pr checkout 123') then has_pr_checkout = true end
    if c:match('gh pr comment 123 %-%-body Test comment body') then has_pr_comment = true end
    if c:match('gh pr view 123') then has_pr_view = true end
  end
  
  assert(has_pr_list, 'Missing gh pr list')
  assert(has_pr_checkout, 'Missing gh pr checkout')
  assert(has_pr_comment, 'Missing gh pr comment')
  assert(has_pr_view, 'Missing gh pr view')
  print('✔ vim.system mocked and validated successfully')
end)
if not ok then
  io.stderr:write('FAIL: ' .. tostring(err) .. '\n')
  vim.cmd('cquit 1')
end
" -c "qa!"

echo "[3/3] Verifying Healthcheck Section..."
nvim --headless -u init.lua -c "lua
local ok, err = pcall(function()
  require('tetravim.health').check()
  print('✔ Healthcheck executes.')
end)
if not ok then
  io.stderr:write('FAIL: ' .. tostring(err) .. '\n')
  vim.cmd('cquit 1')
end
" -c "qa!"

echo "=========================================="
echo " ALL 4.2 VALIDATIONS PASSED SUCCESSFULLY!"
echo "=========================================="
