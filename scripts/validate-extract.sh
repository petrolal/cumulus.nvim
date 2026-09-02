#!/usr/bin/env bash
# SPEC-2.2: Intelligent Extraction -- behavioral smoke test
#
# Mirrors validate-refactor.sh's mocked-LSP-seam pattern: uses
# `vim.cmd('cquit 1')` on assertion failure so pass/fail is trustworthy,
# unlike scripts/validate.sh's `+lua assert(...)` pattern which never
# propagates a non-zero exit code.
#
# A real JDTLS/Kotlin LS process is not available in every environment this
# runs in, so textDocument/codeAction (and, where exercised,
# codeAction/resolve) responses are mocked at the vim.lsp.get_clients /
# vim.lsp.buf_request_all seam -- everything downstream of that seam
# (matching kind/title, disambiguating multiple candidates via
# vim.ui.select, building the quickfix preview, confirming, and applying the
# WorkspaceEdit via vim.lsp.util.apply_workspace_edit) is exercised for real
# against real files/buffers.
#
# (This file replaces a previously-corrupted ~80MB/2.3M-line version of
# itself with no useful content beyond its first ~130 lines.)

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

echo "[1/7] Static: extract.lua / action-lock.lua module shape; ftplugin/java.lua and lsp-kotlin.lua wire up all 5 actions..."
nvim -u init.lua --headless -c "lua
local ok, err = pcall(function()
  local extract = require('cumulus.util.extract')
  assert(type(extract.extract_interface) == 'function', 'extract_interface missing')
  assert(type(extract.inline) == 'function', 'inline missing')
  assert(type(extract.extract_method) == 'function', 'extract_method missing')
  assert(type(extract.extract_variable) == 'function', 'extract_variable missing')
  assert(type(extract.extract_constant) == 'function', 'extract_constant missing')
  assert(type(extract.ACTION_TIMEOUT_MS) == 'number', 'ACTION_TIMEOUT_MS missing')

  local action_lock = require('cumulus.util.action-lock')
  assert(type(action_lock.is_busy) == 'function', 'action-lock.is_busy missing')
  assert(type(action_lock.acquire) == 'function', 'action-lock.acquire missing')
  assert(type(action_lock.release) == 'function', 'action-lock.release missing')

  local java_src = io.open('ftplugin/java.lua', 'r'):read('*a')
  for _, name in ipairs({ 'extract_interface', 'inline', 'extract_method', 'extract_variable', 'extract_constant' }) do
    assert(java_src:match(name), 'ftplugin/java.lua missing ' .. name)
  end

  local kotlin_src = io.open('lua/cumulus/plugins/lsp-kotlin.lua', 'r'):read('*a')
  for _, name in ipairs({ 'extract_interface', 'inline', 'extract_method', 'extract_variable', 'extract_constant' }) do
    assert(kotlin_src:match(name), 'lsp-kotlin.lua missing ' .. name)
  end
end)
if not ok then
  io.stderr:write('FAIL: ' .. tostring(err) .. '\n')
  vim.cmd('cquit 1')
else
  print('OK: extract module shape, action-lock shape, and buffer-local wiring verified')
end
" -c "qa!"

echo "[2/7] Behavioral: extract_method happy path (single matching action) -- Apply splices the mocked WorkspaceEdit into the real file..."
nvim -u init.lua --headless -c "lua
local ok, err = pcall(function()
  local fixture = '$FIXTURE_DIR'
  local java_file = fixture .. '/src/main/java/com/example/Foo.java'

  local fake_client = {
    id = 9101,
    name = 'jdtls',
    offset_encoding = 'utf-16',
    server_capabilities = {},
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
            title = 'Extract to method',
            kind = 'refactor.extract',
            edit = {
              changes = {
                ['file://' .. java_file] = {
                  {
                    range = { start = { line = 4, character = 8 }, ['end'] = { line = 5, character = 30 } },
                    newText = 'extracted();',
                  },
                },
              },
            },
          },
        },
      },
    })
  end
  vim.ui.select = function(_, _, on_choice) on_choice('Apply') end

  vim.cmd('noautocmd edit ' .. vim.fn.fnameescape(java_file))
  local extract = require('cumulus.util.extract')
  extract.extract_method()

  local action_lock = require('cumulus.util.action-lock')
  vim.wait(5000, function() return not action_lock.is_busy() end, 50)

  local saved_ei = vim.o.eventignore
  vim.o.eventignore = 'all'
  local bufnr = vim.fn.bufadd(java_file)
  vim.fn.bufload(bufnr)
  vim.o.eventignore = saved_ei
  local content = table.concat(vim.api.nvim_buf_get_lines(bufnr, 0, -1, false), '\n')

  assert(content:find('extracted();', 1, true), 'Extract Method edit not applied, got:\n' .. content)
  assert(not action_lock.is_busy(), 'action-lock must be released after Apply')

  vim.lsp.get_clients = orig_get_clients
  vim.lsp.buf_request_all = orig_buf_request_all
end)
if not ok then
  io.stderr:write('FAIL: ' .. tostring(err) .. '\n')
  vim.cmd('cquit 1')
else
  print('OK: single-match Extract Method action applied correctly, lock released')
end
" -c "qa!"

echo "[3/7] Behavioral: cancelling at the confirm prompt leaves the file unmodified and releases the lock..."
nvim -u init.lua --headless -c "lua
local ok, err = pcall(function()
  local fixture = '$FIXTURE_DIR'
  local java_file = fixture .. '/src/main/java/com/example/Foo.java'
  local before = table.concat(vim.fn.readfile(java_file), '\n')

  local fake_client = { id = 9102, name = 'jdtls', offset_encoding = 'utf-16', server_capabilities = {} }
  local orig_get_clients = vim.lsp.get_clients
  local orig_buf_request_all = vim.lsp.buf_request_all
  vim.lsp.get_clients = function(_) return { fake_client } end
  vim.lsp.buf_request_all = function(_, _, _, handler)
    handler({
      [fake_client.id] = {
        result = {
          {
            title = 'Extract to method',
            kind = 'refactor.extract',
            edit = {
              changes = {
                ['file://' .. java_file] = {
                  { range = { start = { line = 4, character = 8 }, ['end'] = { line = 5, character = 30 } }, newText = 'ShouldNotApply();' },
                },
              },
            },
          },
        },
      },
    })
  end
  vim.ui.select = function(_, _, on_choice) on_choice('Cancel') end

  vim.cmd('noautocmd edit ' .. vim.fn.fnameescape(java_file))
  local extract = require('cumulus.util.extract')
  extract.extract_method()

  local action_lock = require('cumulus.util.action-lock')
  vim.wait(5000, function() return not action_lock.is_busy() end, 50)

  local after = table.concat(vim.fn.readfile(java_file), '\n')
  assert(before == after, 'Cancel must leave the file byte-for-byte unmodified')
  assert(not action_lock.is_busy(), 'action-lock must be released after Cancel')

  vim.lsp.get_clients = orig_get_clients
  vim.lsp.buf_request_all = orig_buf_request_all
end)
if not ok then
  io.stderr:write('FAIL: ' .. tostring(err) .. '\n')
  vim.cmd('cquit 1')
else
  print('OK: Cancel applied no changes, lock released')
end
" -c "qa!"

echo "[4/7] Behavioral: AMBIGUOUS multiple matching actions -> vim.ui.select prompts for a choice BEFORE any preview/apply; the chosen action's edit is the one applied..."
nvim -u init.lua --headless -c "lua
local ok, err = pcall(function()
  local fixture = '$FIXTURE_DIR'
  local java_file = fixture .. '/src/main/java/com/example/Foo.java'

  local fake_client = { id = 9103, name = 'jdtls', offset_encoding = 'utf-16', server_capabilities = {} }
  local orig_get_clients = vim.lsp.get_clients
  local orig_buf_request_all = vim.lsp.buf_request_all
  vim.lsp.get_clients = function(_) return { fake_client } end
  vim.lsp.buf_request_all = function(_, _, _, handler)
    handler({
      [fake_client.id] = {
        result = {
          {
            title = 'Extract to method (outer block)',
            kind = 'refactor.extract',
            edit = { changes = { ['file://' .. java_file] = {
              { range = { start = { line = 4, character = 8 }, ['end'] = { line = 5, character = 30 } }, newText = 'wrongChoice();' },
            } } },
          },
          {
            title = 'Extract to method (inner statement)',
            kind = 'refactor.extract',
            edit = { changes = { ['file://' .. java_file] = {
              { range = { start = { line = 4, character = 8 }, ['end'] = { line = 5, character = 30 } }, newText = 'correctChoice();' },
            } } },
          },
        },
      },
    })
  end

  local select_calls = {}
  vim.ui.select = function(items, opts, on_choice)
    table.insert(select_calls, { items = items, prompt = opts and opts.prompt })
    if #select_calls == 1 then
      -- Disambiguation prompt: titles list, pick the 2nd ('inner statement').
      assert(#items == 2, 'expected 2 candidate titles, got ' .. #items)
      on_choice(items[2], 2)
    else
      -- Confirm prompt from the resulting preview.
      on_choice('Apply')
    end
  end

  vim.cmd('noautocmd edit ' .. vim.fn.fnameescape(java_file))
  local extract = require('cumulus.util.extract')
  extract.extract_method()

  local action_lock = require('cumulus.util.action-lock')
  vim.wait(5000, function() return not action_lock.is_busy() end, 50)

  assert(#select_calls == 2, 'expected disambiguation THEN confirm (2 vim.ui.select calls), got ' .. #select_calls)

  local saved_ei = vim.o.eventignore
  vim.o.eventignore = 'all'
  local bufnr = vim.fn.bufadd(java_file)
  vim.fn.bufload(bufnr)
  vim.o.eventignore = saved_ei
  local content = table.concat(vim.api.nvim_buf_get_lines(bufnr, 0, -1, false), '\n')

  assert(content:find('correctChoice();', 1, true), 'the chosen action edit must be applied, got:\n' .. content)
  assert(not content:find('wrongChoice();', 1, true), 'the NOT-chosen action edit must never be applied')

  vim.lsp.get_clients = orig_get_clients
  vim.lsp.buf_request_all = orig_buf_request_all
end)
if not ok then
  io.stderr:write('FAIL: ' .. tostring(err) .. '\n')
  vim.cmd('cquit 1')
else
  print('OK: ambiguous multi-action response prompted a choice before applying, and applied only the chosen edit')
end
" -c "qa!"

echo "[5/7] Behavioral: cancelling the DISAMBIGUATION prompt (no action chosen) applies nothing and releases the lock..."
nvim -u init.lua --headless -c "lua
local ok, err = pcall(function()
  local fixture = '$FIXTURE_DIR'
  local java_file = fixture .. '/src/main/java/com/example/Foo.java'
  local before = table.concat(vim.fn.readfile(java_file), '\n')

  local fake_client = { id = 9104, name = 'jdtls', offset_encoding = 'utf-16', server_capabilities = {} }
  local orig_get_clients = vim.lsp.get_clients
  local orig_buf_request_all = vim.lsp.buf_request_all
  vim.lsp.get_clients = function(_) return { fake_client } end
  vim.lsp.buf_request_all = function(_, _, _, handler)
    handler({
      [fake_client.id] = {
        result = {
          { title = 'Extract to method (a)', kind = 'refactor.extract', edit = { changes = {} } },
          { title = 'Extract to method (b)', kind = 'refactor.extract', edit = { changes = {} } },
        },
      },
    })
  end

  local confirm_select_called = false
  vim.ui.select = function(items, _, on_choice)
    if #items == 2 and items[1]:match('Extract to method') then
      on_choice(nil, nil) -- Escape/cancel the disambiguation prompt.
    else
      confirm_select_called = true
      on_choice('Apply')
    end
  end

  local notified = {}
  local orig_notify = vim.notify
  vim.notify = function(msg, level) table.insert(notified, { msg = msg, level = level }) end

  vim.cmd('noautocmd edit ' .. vim.fn.fnameescape(java_file))
  local extract = require('cumulus.util.extract')
  extract.extract_method()

  local action_lock = require('cumulus.util.action-lock')
  vim.wait(5000, function() return not action_lock.is_busy() end, 50)
  vim.notify = orig_notify

  assert(not confirm_select_called, 'the confirm (Apply/Cancel) prompt must never be reached when disambiguation is cancelled')
  assert(not action_lock.is_busy(), 'action-lock must be released after a cancelled disambiguation')
  local after = table.concat(vim.fn.readfile(java_file), '\n')
  assert(before == after, 'a cancelled disambiguation must not touch any file')

  local saw_info = false
  for _, n in ipairs(notified) do
    if n.msg:match('cancelled') then saw_info = true end
  end
  assert(saw_info, 'expected a cancellation notification')

  vim.lsp.get_clients = orig_get_clients
  vim.lsp.buf_request_all = orig_buf_request_all
end)
if not ok then
  io.stderr:write('FAIL: ' .. tostring(err) .. '\n')
  vim.cmd('cquit 1')
else
  print('OK: cancelling the disambiguation prompt short-circuits before any preview/apply, lock released')
end
" -c "qa!"

echo "[6/7] Behavioral: no applicable code action -> WARN, no crash, lock released; a second action while one is in flight is rejected via the SHARED action-lock..."
nvim -u init.lua --headless -c "lua
local ok, err = pcall(function()
  local fixture = '$FIXTURE_DIR'
  local java_file = fixture .. '/src/main/java/com/example/Foo.java'

  local fake_client = { id = 9105, name = 'jdtls', offset_encoding = 'utf-16', server_capabilities = {} }
  local orig_get_clients = vim.lsp.get_clients
  local orig_buf_request_all = vim.lsp.buf_request_all
  vim.lsp.get_clients = function(_) return { fake_client } end
  vim.lsp.buf_request_all = function(_, _, _, handler)
    handler({ [fake_client.id] = { result = {} } })
  end

  local notified = {}
  local orig_notify = vim.notify
  vim.notify = function(msg, level) table.insert(notified, { msg = msg, level = level }) end

  vim.cmd('noautocmd edit ' .. vim.fn.fnameescape(java_file))
  local extract = require('cumulus.util.extract')
  extract.extract_method()

  local action_lock = require('cumulus.util.action-lock')
  vim.wait(5000, function() return not action_lock.is_busy() end, 50)
  vim.notify = orig_notify

  assert(not action_lock.is_busy(), 'action-lock must be released when no applicable action is found')
  local saw_warn = false
  for _, n in ipairs(notified) do
    if n.level == vim.log.levels.WARN and tostring(n.msg):match('No applicable') then saw_warn = true end
  end
  assert(saw_warn, 'expected a WARN for no applicable code action')

  vim.lsp.get_clients = orig_get_clients
  vim.lsp.buf_request_all = orig_buf_request_all

  -- Shared action-lock: refactor.lua and extract.lua reject a concurrent
  -- action through the SAME lock (action-lock.lua), not independent
  -- private M._busy flags.
  local refactor = require('cumulus.util.refactor')
  action_lock.acquire()
  local notified2 = {}
  vim.notify = function(msg, level) table.insert(notified2, { msg = msg, level = level }) end
  extract.extract_interface()
  refactor.project_rename('Whatever')
  vim.notify = orig_notify
  action_lock.release()

  assert(#notified2 == 2, 'expected both extract.extract_interface() and refactor.project_rename() to be rejected')
  for _, n in ipairs(notified2) do
    assert(n.msg:match('already in progress'), 'expected an \"already in progress\" WARN, got: ' .. n.msg)
  end
end)
if not ok then
  io.stderr:write('FAIL: ' .. tostring(err) .. '\n')
  vim.cmd('cquit 1')
else
  print('OK: no-applicable-action path warns cleanly; the shared action-lock rejects concurrent actions across BOTH modules')
end
" -c "qa!"

echo "[7/7] Behavioral: visual-mode byte columns are converted to LSP CHARACTER offsets (not passed through raw) for a line with multi-byte UTF-8 text before the selection..."
nvim -u init.lua --headless -c 'lua
local ok, err = pcall(function()
  vim.cmd("enew")
  local bufnr = vim.api.nvim_get_current_buf()
  -- "é" is 1 UTF-16 code unit but 2 UTF-8 BYTES -- a naive byte-column
  -- pass-through would be off by one for anything after it on this line.
  local line = "System.out.println(\"héllo\");"
  vim.api.nvim_buf_set_lines(bufnr, 0, 1, false, { line })

  local e_start, e_end = line:find("é")
  assert(e_start, "fixture line must contain the multi-byte character used by this test")
  -- 0-based byte column of the first byte AFTER "é" (Lua 1-based e_end ==
  -- the 0-based index of the next byte).
  local after_e_byte_col = e_end
  local end_byte_col = #line - 2 -- a byte column near the end of the line, still after "é"

  vim.api.nvim_buf_set_mark(bufnr, "<", 1, after_e_byte_col, {})
  vim.api.nvim_buf_set_mark(bufnr, ">", 1, end_byte_col, {})

  local fake_client = { id = 9106, name = "jdtls", offset_encoding = "utf-16", server_capabilities = {} }
  local orig_get_clients = vim.lsp.get_clients
  local orig_buf_request_all = vim.lsp.buf_request_all
  vim.lsp.get_clients = function(_) return { fake_client } end

  local captured_range = nil
  vim.lsp.buf_request_all = function(_, _, params, _)
    captured_range = params.range
    -- Never invoke the handler -- this test only inspects the OUTGOING
    -- params, so leave the request "pending" rather than modeling a response.
  end

  local extract = require("cumulus.util.extract")
  extract.extract_variable(true) -- is_visual = true

  vim.lsp.get_clients = orig_get_clients
  vim.lsp.buf_request_all = orig_buf_request_all
  -- Manually release the lock this call acquired (its request never got a
  -- response in this test, by design -- see above).
  require("cumulus.util.action-lock").release()

  assert(captured_range, "do_action never reached vim.lsp.buf_request_all")

  local expected_start_char = vim.lsp.util.character_offset(bufnr, 0, after_e_byte_col, "utf-16")
  local expected_end_char_raw = vim.lsp.util.character_offset(bufnr, 0, end_byte_col, "utf-16")

  -- Sanity: this fixture line MUST actually differ between byte and utf-16
  -- indexing after the 2-byte "é", or the test below would pass vacuously
  -- even with the old, unconverted byte-column bug. Checked BEFORE the
  -- end-exclusive +1 adjustment below, which would otherwise mask a
  -- 1-byte divergence by coincidentally re-adding it back.
  assert(expected_end_char_raw < end_byte_col, "fixture did not actually exercise a byte-vs-utf16 divergence")

  local expected_end_char = expected_end_char_raw
  if vim.o.selection ~= "exclusive" then
    expected_end_char = expected_end_char + 1
  end

  assert(
    captured_range.start.character == expected_start_char,
    string.format("start.character: expected %d (utf-16), got %d (looks byte-based)", expected_start_char, captured_range.start.character)
  )
  assert(
    captured_range["end"].character == expected_end_char,
    string.format("end.character: expected %d (utf-16), got %d (looks byte-based)", expected_end_char, captured_range["end"].character)
  )
end)
if not ok then
  io.stderr:write("FAIL: " .. tostring(err) .. "\n")
  vim.cmd("cquit 1")
else
  print("OK: visual-mode selection converted via vim.lsp.util.character_offset, not raw byte columns")
end
' -c "qa!"

echo ""
echo "✔ Intelligent Extraction (SPEC-2.2) smoke test PASSED."
echo ""
echo "NOT covered by this script (requires a real JDTLS/Kotlin LS process attached"
echo "to a real project, unavailable in this sandbox) -- verify manually per"
echo "spec-2-2's Verification section:"
echo "  - A real textDocument/codeAction round-trip against JDTLS/Kotlin LS for"
echo "    each of extract_interface/inline/extract_method/extract_variable/extract_constant"
echo "  - The command-without-edit + codeAction/resolve fallback path against a"
echo "    real server that actually exercises it"
echo "  - The quickfix preview's on-screen contents (:copen) for a human reviewer"
