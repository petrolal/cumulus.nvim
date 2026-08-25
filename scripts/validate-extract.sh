#!/usr/bin/env bash
# SPEC-2.2: Intelligent Extraction -- behavioral smoke test

set -e

echo "=== Cumulus Intelligent Extraction (SPEC-2.2) Smoke Test ==="

FIXTURE_DIR="$(mktemp -d)"
trap 'rm -rf "$FIXTURE_DIR"' EXIT

mkdir -p "$FIXTURE_DIR/src/main/java/com/example"

cat > "$FIXTURE_DIR/src/main/java/com/example/Foo.java" <<'JAVA'
package com.example;

public class Foo {
    public void bar() {
        int x = 42;
        System.out.println(x);
    }
}
JAVA

echo "[1/7] Static: extract.lua module shape..."
nvim -u init.lua --headless -c "lua
local ok, err = pcall(function()
  local extract = require('cumulus.util.extract')
  assert(type(extract.extract_interface) == 'function', 'extract_interface missing')
  assert(type(extract.inline) == 'function', 'inline missing')

  local java_src = io.open('ftplugin/java.lua', 'r'):read('*a')
  assert(java_src:match('extract_interface'), 'ftplugin/java.lua missing extract_interface')
  assert(java_src:match('inline'), 'ftplugin/java.lua missing inline')

  local kotlin_src = io.open('lua/cumulus/plugins/lsp-kotlin.lua', 'r'):read('*a')
  assert(kotlin_src:match('extract_interface'), 'lsp-kotlin.lua missing extract_interface')
  assert(kotlin_src:match('inline'), 'lsp-kotlin.lua missing inline')
end)
if not ok then
  io.stderr:write('FAIL: ' .. tostring(err) .. '\n')
  vim.cmd('cquit 1')
else
  print('OK: extract module shape and buffer-local wiring verified')
end
" -c "qa!"

echo "[2/7] Behavioral: Extract Interface happy path..."
nvim -u init.lua --headless -c "lua
local ok, err = pcall(function()
  local fixture = '$FIXTURE_DIR'
  local java_file = fixture .. '/src/main/java/com/example/Foo.java'
  local new_file = fixture .. '/src/main/java/com/example/IFoo.java'

  local fake_client = {
    id = 9001,
    name = 'jdtls',
    offset_encoding = 'utf-16',
    config = { root_dir = fixture },
    server_capabilities = { codeActionProvider = { resolveProvider = false } }
  }

  local orig_get_clients = vim.lsp.get_clients
  local orig_buf_request_all = vim.lsp.buf_request_all
  vim.lsp.get_clients = function(_) return { fake_client } end
  vim.lsp.buf_request_all = function(_, method, _, handler)
    assert(method == 'textDocument/codeAction', 'unexpected method: ' .. tostring(method))
    handler({
      [fake_client.id] = {
        result = {
          {
            title = 'Extract Interface',
            kind = 'refactor.extract.interface',
            edit = {
              changes = {
                ['file://' .. java_file] = {
                  { range = { start = { line = 2, character = 13 }, ['end'] = { line = 2, character = 16 } }, newText = 'Foo implements IFoo' }
                }
              }
            }
          }
        }
      }
    })
  end

  vim.ui.select = function(_, _, on_choice) on_choice('Apply') end

  local function buf_content(file)
    local bufnr = vim.fn.bufadd(file)
    vim.fn.bufload(bufnr)
    return table.concat(vim.api.nvim_buf_get_lines(bufnr, 0, -1, false), '\n')
  end

  vim.cmd('noautocmd edit ' .. vim.fn.fnameescape(java_file))
  vim.api.nvim_win_set_cursor(0, { 3, 13 })
  
  local extract = require('cumulus.util.extract')
  extract.extract_interface()

  vim.wait(1000, function() return false end, 100)

  local content = buf_content(java_file)
  assert(content:find('Foo implements IFoo', 1, true), 'Extract Interface edit not applied')

  vim.lsp.get_clients = orig_get_clients
  vim.lsp.buf_request_all = orig_buf_request_all
end)
if not ok then
  io.stderr:write('FAIL: ' .. tostring(err) .. '\n')
  vim.cmd('cquit 1')
else
  print('OK: Extract Interface applied correctly')
end
" -c "qa!"

echo "[3/7] Behavioral: Inline cancel path..."
nvim -u init.lua --headless -c "lua
local ok, err = pcall(function()
  local fixture = '$FIXTURE_DIR'
  local java_file = fixture .. '/src/main/java/com/example/Foo.java'
  
  local fake_client = {
    id = 9002,
    name = 'jdtls',
    offset_encoding = 'utf-16',
    config = { root_dir = fixture },
    server_capabilities = {}
  }
  
  local orig_get_clients = vim.lsp.get_clients
  local orig_buf_request_all = vim.lsp.buf_request_all
  vim.lsp.get_clients = function(_) return { fake_client } end
  vim.lsp.buf_request_all = function(_, method, _, handler)
    handler({
      [fake_client.id] = {
        result = {
          {
            title = 'Inline',
            kind = 'refactor.inline',
            edit = {
              changes = {
                ['file://' .. java_file] = {
                  { range = { start = { line = 4, character = 8 }, ['end'] = { line = 4, character = 22 } }, newText = 'System.out.println(42);' }
                }
              }
            }
          }
        }
      }
    })
  end

  vim.ui.select = function(_, _, on_choice) on_choice('Cancel') end

  local function buf_content(file)
    local bufnr = vim.fn.bufadd(file)
    vim.fn.bufload(bufnr)
    return table.concat(vim.api.nvim_buf_get_lines(bufnr, 0, -1, false), '\n')
  end

  vim.cmd('noautocmd edit ' .. vim.fn.fnameescape(java_file))
  vim.api.nvim_win_set_cursor(0, { 5, 8 })

  local before = buf_content(java_file)
  
  local extract = require('cumulus.util.extract')
  extract.inline()

  vim.wait(1000, function() return false end, 100)

  local after = buf_content(java_file)
  assert(before == after, 'Cancel must leave the file byte-for-byte unmodified')

  vim.lsp.get_clients = orig_get_clients
  vim.lsp.buf_request_all = orig_buf_request_all
end)
if not ok then
  io.stderr:write('FAIL: ' .. tostring(err) .. '\n')
  vim.cmd('cquit 1')
else
  print('OK: Inline cancel path applied no changes')
end
" -c "qa!"

echo "[4/7] Behavioral: No applicable action..."
nvim -u init.lua --headless -c "lua
local ok, err = pcall(function()
  local fixture = '$FIXTURE_DIR'
  local java_file = fixture .. '/src/main/java/com/example/Foo.java'
  
  local fake_client = {
    id = 9003,
    name = 'jdtls',
    offset_encoding = 'utf-16',
    config = { root_dir = fixture },
    server_capabilities = {}
  }
  
  local orig_get_clients = vim.lsp.get_clients
  local orig_buf_request_all = vim.lsp.buf_request_all
  vim.lsp.get_clients = function(_) return { fake_client } end
  vim.lsp.buf_request_all = function(_, method, _, handler)
    handler({ [fake_client.id] = { result = {} } })
  end

  local select_called = false
  vim.ui.select = function() select_called = true end

  local notified = {}
  local orig_notify = vim.notify
  vim.notify = function(msg, level, opts)
    table.insert(notified, { msg = msg, level = level })
    orig_notify(msg, level, opts)
  end

  vim.cmd('noautocmd edit ' .. vim.fn.fnameescape(java_file))
  
  local extract = require('cumulus.util.extract')
  extract.inline()

  vim.wait(1000, function() return false end, 100)
  vim.notify = orig_notify

  assert(not select_called, 'quickfix confirm must never be shown for no-action')
  
  local saw_warn = false
  for _, n in ipairs(notified) do
    if n.level == vim.log.levels.WARN and string.find(n.msg, 'No applicable Inline') then 
       saw_warn = true 
    end
  end
  assert(saw_warn, 'a visible vim.notify WARN is required')

  vim.lsp.get_clients = orig_get_clients
  vim.lsp.buf_request_all = orig_buf_request_all
end)
if not ok then
  io.stderr:write('FAIL: ' .. tostring(err) .. '\n')
  vim.cmd('cquit 1')
else
  print('OK: No applicable action aborted gracefully')
end
" -c "qa!"

echo "[5/7] Behavioral: Inline happy path..."
nvim -u init.lua --headless -c "lua
local ok, err = pcall(function()
  local fixture = '$FIXTURE_DIR'
  local java_file = fixture .. '/src/main/java/com/example/Foo.java'
  
  local fake_client = {
    id = 9004,
    name = 'jdtls',
    offset_encoding = 'utf-16',
    config = { root_dir = fixture },
    server_capabilities = { codeActionProvider = { resolveProvider = false } }
  }
  
  local orig_get_clients = vim.lsp.get_clients
  local orig_buf_request_all = vim.lsp.buf_request_all
  vim.lsp.get_clients = function(_) return { fake_client } end
  vim.lsp.buf_request_all = function(_, method, _, handler)
    handler({
      [fake_client.id] = {
        result = {
          {
            title = 'Inline',
            kind = 'refactor.inline',
            edit = {
              changes = {
                ['file://' .. java_file] = {
                  { range = { start = { line = 4, character = 8 }, ['end'] = { line = 4, character = 22 } }, newText = 'System.out.println(42);' }
                }
              }
            }
          }
        }
      }
    })
  end

  vim.ui.select = function(_, _, on_choice) on_choice('Apply') end

  local function buf_content(file)
    local bufnr = vim.fn.bufadd(file)
    vim.fn.bufload(bufnr)
    return table.concat(vim.api.nvim_buf_get_lines(bufnr, 0, -1, false), '\n')
  end

  vim.cmd('noautocmd edit ' .. vim.fn.fnameescape(java_file))
  vim.api.nvim_win_set_cursor(0, { 5, 8 })
  
  local extract = require('cumulus.util.extract')
  extract.inline()

  vim.wait(1000, function() return false end, 100)

  local content = buf_content(java_file)
  assert(content:find('System.out.println%(42%);', 1, false), 'Inline edit not applied')

  vim.lsp.get_clients = orig_get_clients
  vim.lsp.buf_request_all = orig_buf_request_all
end)
if not ok then
  io.stderr:write('FAIL: ' .. tostring(err) .. '\n')
  vim.cmd('cquit 1')
else
  print('OK: Inline happy path applied correctly')
end
" -c "qa!"

echo "[6/7] Behavioral: No JVM LSP attached..."
nvim -u init.lua --headless -c "lua
local ok, err = pcall(function()
  local fixture = '$FIXTURE_DIR'
  local java_file = fixture .. '/src/main/java/com/example/Foo.java'
  
  local orig_get_clients = vim.lsp.get_clients
  vim.lsp.get_clients = function(_) return {} end -- No clients

  local notified = {}
  local orig_notify = vim.notify
  vim.notify = function(msg, level, opts)
    table.insert(notified, { msg = msg, level = level })
    orig_notify(msg, level, opts)
  end

  vim.cmd('noautocmd edit ' .. vim.fn.fnameescape(java_file))
  
  local extract = require('cumulus.util.extract')
  extract.inline()

  vim.wait(1000, function() return false end, 100)
  vim.notify = orig_notify

  local saw_warn = false
  for _, n in ipairs(notified) do
    if n.level == vim.log.levels.WARN and string.find(n.msg, 'no JDTLS/Kotlin LS client attached') then 
       saw_warn = true 
    end
  end
  assert(saw_warn, 'a visible vim.notify WARN is required for no LSP')

  vim.lsp.get_clients = orig_get_clients
end)
if not ok then
  io.stderr:write('FAIL: ' .. tostring(err) .. '\n')
  vim.cmd('cquit 1')
else
  print('OK: No JVM LSP attached aborted gracefully')
end
" -c "qa!"

echo "[7/7] Behavioral: Concurrent invocation..."
nvim -u init.lua --headless -c "lua
local ok, err = pcall(function()
  local fixture = '$FIXTURE_DIR'
  local java_file = fixture .. '/src/main/java/com/example/Foo.java'
  
  local fake_client = {
    id = 9005,
    name = 'jdtls',
    offset_encoding = 'utf-16',
    config = { root_dir = fixture },
    server_capabilities = {}
  }
  
  local orig_get_clients = vim.lsp.get_clients
  local orig_buf_request_all = vim.lsp.buf_request_all
  vim.lsp.get_clients = function(_) return { fake_client } end
  vim.lsp.buf_request_all = function(_, method, _, handler)
    -- Do not respond, keep it hanging to simulate concurrent execution
  end

  local notified = {}
  local orig_notify = vim.notify
  vim.notify = function(msg, level, opts)
    table.insert(notified, { msg = msg, level = level })
    orig_notify(msg, level, opts)
  end

  vim.cmd('noautocmd edit ' .. vim.fn.fnameescape(java_file))
  
  local extract = require('cumulus.util.extract')
  
  extract.inline() -- First call
  
  extract.inline() -- Second call immediately

  vim.notify = orig_notify

  local saw_warn = false
  for _, n in ipairs(notified) do
    if n.level == vim.log.levels.WARN and string.find(n.msg, 'already in progress') then 
       saw_warn = true 
    end
  end
  assert(saw_warn, 'a visible vim.notify WARN is required for concurrent invocation')

  vim.lsp.get_clients = orig_get_clients
  vim.lsp.buf_request_all = orig_buf_request_all
  
  -- Reset busy flag
  extract._busy = false
end)
if not ok then
  io.stderr:write('FAIL: ' .. tostring(err) .. '\n')
  vim.cmd('cquit 1')
else
  print('OK: Concurrent invocation rejected correctly')
end
" -c "qa!"

echo ""
echo "✔ Intelligent Extraction (SPEC-2.2) smoke test PASSED."
