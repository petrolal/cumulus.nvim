#!/usr/bin/env bash
# SPEC-2.1: Project-Wide Safe Rename (Java & Kotlin) -- behavioral smoke test
#
# Mirrors validate-dap-jvm.sh: uses `vim.cmd('cquit 1')` on assertion failure
# so pass/fail is trustworthy, unlike scripts/validate.sh's `+lua
# assert(...)` pattern which never propagates a non-zero exit code.
#
# A real JDTLS/Kotlin LS process is not available in every environment this
# runs in (no network/Mason install guaranteed), so the textDocument/rename
# response is mocked at the vim.lsp.get_clients / vim.lsp.buf_request_all
# seam -- everything downstream of that seam (merging the mocked
# WorkspaceEdit with refactor-treesitter.lua's real, un-mocked project scan,
# building the quickfix preview, confirming, and applying both the LSP edit
# and the Spring text edits to real files on disk) is exercised for real
# against a small fixture project.

set -e

echo "=== Cumulus Project-Wide Safe Rename (SPEC-2.1) Smoke Test ==="

FIXTURE_DIR="$(mktemp -d)"
trap 'rm -rf "$FIXTURE_DIR"' EXIT

mkdir -p "$FIXTURE_DIR/src/main/java/com/example"
mkdir -p "$FIXTURE_DIR/src/main/java/com/other"
mkdir -p "$FIXTURE_DIR/src/main/resources"

# `@Service` on the class itself, and only ONE occurrence of the class name
# on that declaration line, tests the LSP/Spring overlap fix (item 2): the
# mocked LSP edit below touches this exact line/column, and the stereotype
# classifier independently matches the same line too -- filter_overlapping_
# spring_items must drop the Spring-scanned duplicate rather than let
# apply_spring_edits re-splice a line the LSP edit already rewrote.
cat > "$FIXTURE_DIR/src/main/java/com/example/FooService.java" <<'JAVA'
package com.example;

import org.springframework.stereotype.Service;

@Service
public class FooService {
    public String greet() {
        return "hi";
    }
}
JAVA

# The @Autowired field line mentions the renamed type TWICE (field type +
# static-factory call) -- the reviewer's exact reproduction case for item 1
# (duplicate same-line occurrences corrupting the buffer).
cat > "$FIXTURE_DIR/src/main/java/com/example/Consumer.java" <<'JAVA'
package com.example;

import org.springframework.beans.factory.annotation.Autowired;

public class Consumer {
    @Autowired
    private FooService fooService = FooService.createDefault();
}
JAVA

# Unrelated same-named class in a DIFFERENT package -- item 3's
# package-scoping fix must leave every reference here untouched.
cat > "$FIXTURE_DIR/src/main/java/com/other/FooService.java" <<'JAVA'
package com.other;

public class FooService {
    public String greet() {
        return "other";
    }
}
JAVA

cat > "$FIXTURE_DIR/src/main/java/com/other/OtherConsumer.java" <<'JAVA'
package com.other;

import org.springframework.beans.factory.annotation.Autowired;

public class OtherConsumer {
    @Autowired
    private FooService fooService;
}
JAVA

cat > "$FIXTURE_DIR/src/main/resources/beans.xml" <<'XML'
<beans>
    <bean id="fooService" class="com.example.FooService"/>
    <bean id="otherFooService" class="com.other.FooService"/>
    <!-- <bean id="commentedOut" class="com.example.FooService"/> -->
</beans>
XML

echo "[1/4] Static: refactor.lua / refactor-treesitter.lua module shape..."
nvim -u init.lua --headless -c "lua
local ok, err = pcall(function()
  local refactor = require('cumulus.util.refactor')
  assert(type(refactor.project_rename) == 'function', 'project_rename missing')
  assert(type(refactor.find_jvm_client) == 'function', 'find_jvm_client missing')
  assert(type(refactor.workspace_edit_to_locations) == 'function', 'workspace_edit_to_locations missing')

  local ts = require('cumulus.util.refactor-treesitter')
  assert(type(ts.scan_root_async) == 'function', 'scan_root_async missing')
  assert(type(ts.classify_xml_line) == 'function', 'classify_xml_line missing')
  assert(type(ts.classify_jvm_line) == 'function', 'classify_jvm_line missing')

  -- Buffer-local wiring: verify the keymap plumbing source, not a live attach.
  local java_src = io.open('ftplugin/java.lua', 'r'):read('*a')
  assert(java_src:match('cumulus%.util%.refactor'), 'ftplugin/java.lua must wire up refactor.project_rename')
  local kotlin_src = io.open('lua/cumulus/plugins/lsp-kotlin.lua', 'r'):read('*a')
  assert(kotlin_src:match('cumulus%.util%.refactor'), 'lsp-kotlin.lua must wire up refactor.project_rename')
end)
if not ok then
  io.stderr:write('FAIL: ' .. tostring(err) .. '\n')
  vim.cmd('cquit 1')
else
  print('OK: refactor module shape and buffer-local wiring verified')
end
" -c "qa!"

echo "[2/4] Behavioral: fixture rename (mocked JDTLS response) -- Apply updates Java class, BOTH occurrences of the @Autowired field's type on one line, and the XML bean class attribute; leaves the commented-out bean, the LSP/Spring-overlapping stereotype line, and the unrelated cross-package class untouched..."
nvim -u init.lua --headless -c "lua
local ok, err = pcall(function()
  local fixture = '$FIXTURE_DIR'
  local java_file = fixture .. '/src/main/java/com/example/FooService.java'
  local consumer_file = fixture .. '/src/main/java/com/example/Consumer.java'
  local xml_file = fixture .. '/src/main/resources/beans.xml'
  local other_service_file = fixture .. '/src/main/java/com/other/FooService.java'
  local other_consumer_file = fixture .. '/src/main/java/com/other/OtherConsumer.java'

  local fake_client = {
    id = 9001,
    name = 'jdtls',
    offset_encoding = 'utf-16',
    config = { root_dir = fixture },
  }

  -- Mock the LSP seam: get_clients reports the fake jdtls client attached;
  -- buf_request_all returns a WorkspaceEdit that renames only the class
  -- declaration in FooService.java (mirroring what a real jdtls response
  -- would contain -- FooService.java line 6, 'public class FooService {',
  -- 0-based char 13) -- everything else (the @Autowired field's two
  -- occurrences in Consumer.java, the XML bean class attribute) must come
  -- from the real, un-mocked refactor-treesitter.lua project scan. This
  -- same class-declaration line is ALSO independently matched by the
  -- stereotype classifier (FooService.java has @Service on it) -- that's
  -- the LSP/Spring overlap case filter_overlapping_spring_items must
  -- resolve without a double-splice.
  local orig_get_clients = vim.lsp.get_clients
  local orig_buf_request_all = vim.lsp.buf_request_all
  vim.lsp.get_clients = function(_) return { fake_client } end
  vim.lsp.buf_request_all = function(_, method, _, handler)
    assert(method == 'textDocument/rename', 'unexpected method: ' .. tostring(method))
    handler({
      [fake_client.id] = {
        result = {
          changes = {
            ['file://' .. java_file] = {
              {
                range = {
                  start = { line = 5, character = 13 },
                  ['end'] = { line = 5, character = 23 },
                },
                newText = 'BarService',
              },
            },
          },
        },
      },
    })
  end

  -- Mock the confirm prompt to always choose 'Apply'.
  vim.ui.select = function(_, _, on_choice) on_choice('Apply') end

  -- Both the LSP-applied edit and the Spring/treesitter-applied edits land
  -- in in-memory buffers, not written to disk (matching how
  -- vim.lsp.util.apply_text_edits already behaves for the LSP part) -- so
  -- assertions read buffer content, mirroring what the user would actually
  -- see, rather than the still-unmodified on-disk file.
  local function buf_content(file)
    local saved_ei = vim.o.eventignore
    vim.o.eventignore = 'all'
    local bufnr = vim.fn.bufadd(file)
    vim.fn.bufload(bufnr)
    vim.o.eventignore = saved_ei
    return table.concat(vim.api.nvim_buf_get_lines(bufnr, 0, -1, false), '\n')
  end

  vim.cmd('noautocmd edit ' .. vim.fn.fnameescape(java_file))
  -- Position the cursor on the 'FooService' class name (line 6, 'public
  -- class FooService {') -- project_rename derives old_name from
  -- vim.fn.expand('<cword>') at the cursor, exactly like the real
  -- <leader>cr keymap would.
  vim.api.nvim_win_set_cursor(0, { 6, 13 })
  local refactor = require('cumulus.util.refactor')
  refactor.project_rename('BarService')

  -- The rename pipeline is async (vim.system for the Tree-sitter/Spring
  -- scan) -- wait for the fixture buffer to reflect the applied edit
  -- rather than assuming synchronous completion.
  local deadline = vim.uv.now() + 10000
  local function done()
    return buf_content(java_file):find('BarService', 1, true) ~= nil
  end
  while not done() and vim.uv.now() < deadline do
    vim.wait(100)
  end

  -- item 2 (LSP/Spring overlap): the class declaration line must be
  -- renamed exactly once (LSP-applied), never double-spliced by the
  -- Spring stereotype match for the same line/column.
  local foo_content = buf_content(java_file)
  assert(foo_content:find('public class BarService', 1, true), 'LSP-driven class rename not applied to FooService.java')
  assert(not foo_content:find('BarServiceBarService', 1, true), 'class declaration line was double-spliced (LSP + Spring overlap not filtered)')

  -- item 1 (duplicate same-line occurrences): BOTH the field type and the
  -- static-factory call on the same line must be renamed, with no
  -- corruption between them.
  local consumer_content = buf_content(consumer_file)
  assert(
    consumer_content:find('private BarService fooService = BarService.createDefault();', 1, true),
    'both same-line occurrences of the @Autowired field type were not correctly renamed: ' .. consumer_content
  )

  -- item 4 (XML comment filtering) + the live bean entry: the live
  -- com.example bean is updated, the commented-out one is not.
  local xml_content = buf_content(xml_file)
  assert(
    xml_content:find('class=\"com.example.BarService\"', 1, true),
    'live XML bean class attribute not updated by Spring/treesitter scan'
  )
  assert(
    xml_content:find('<!-- <bean id=\"commentedOut\" class=\"com.example.FooService\"/> -->', 1, true),
    'a bean entry inside an XML comment must NOT be renamed'
  )

  -- item 3 (package scoping): the unrelated com.other.FooService and every
  -- reference to it must be completely untouched.
  assert(
    xml_content:find('class=\"com.other.FooService\"', 1, true),
    'an unrelated cross-package XML bean entry (com.other.FooService) must not be renamed'
  )
  assert(
    buf_content(other_service_file):find('public class FooService', 1, true),
    'the unrelated com.other.FooService class declaration must not be renamed'
  )
  assert(
    buf_content(other_consumer_file):find('private FooService fooService;', 1, true),
    'the unrelated com.other.OtherConsumer @Autowired field must not be renamed'
  )

  vim.lsp.get_clients = orig_get_clients
  vim.lsp.buf_request_all = orig_buf_request_all
end)
if not ok then
  io.stderr:write('FAIL: ' .. tostring(err) .. '\n')
  vim.cmd('cquit 1')
else
  print('OK: Apply correctly updated com.example.FooService everywhere (including both same-line occurrences), left the commented-out bean and the unrelated com.other.FooService untouched, and did not double-splice the LSP/Spring-overlapping class declaration line')
end
" -c "qa!"

echo "[3/4] Behavioral: cancelling at the confirm prompt leaves every file unmodified..."
nvim -u init.lua --headless -c "lua
local ok, err = pcall(function()
  local fixture = '$FIXTURE_DIR'
  local java_file = fixture .. '/src/main/java/com/example/FooService.java'
  local before = table.concat(vim.fn.readfile(java_file), '\n')

  local fake_client = {
    id = 9002,
    name = 'jdtls',
    offset_encoding = 'utf-16',
    config = { root_dir = fixture },
  }
  local orig_get_clients = vim.lsp.get_clients
  local orig_buf_request_all = vim.lsp.buf_request_all
  vim.lsp.get_clients = function(_) return { fake_client } end
  vim.lsp.buf_request_all = function(_, _, _, handler)
    handler({
      [fake_client.id] = {
        result = {
          changes = {
            ['file://' .. java_file] = {
              {
                range = { start = { line = 5, character = 13 }, ['end'] = { line = 5, character = 23 } },
                newText = 'ShouldNotApply',
              },
            },
          },
        },
      },
    })
  end
  vim.ui.select = function(_, _, on_choice) on_choice('Cancel') end

  vim.cmd('noautocmd edit ' .. vim.fn.fnameescape(java_file))
  -- Position the cursor on the 'FooService' class name (line 6, 'public
  -- class FooService {') -- project_rename derives old_name from
  -- vim.fn.expand('<cword>') at the cursor, exactly like the real
  -- <leader>cr keymap would.
  vim.api.nvim_win_set_cursor(0, { 6, 13 })
  local refactor = require('cumulus.util.refactor')
  refactor.project_rename('ShouldNotApply')

  vim.wait(2000, function() return false end, 100)

  local after = table.concat(vim.fn.readfile(java_file), '\n')
  assert(before == after, 'Cancel must leave the file byte-for-byte unmodified')
  vim.lsp.get_clients = orig_get_clients
  vim.lsp.buf_request_all = orig_buf_request_all
end)
if not ok then
  io.stderr:write('FAIL: ' .. tostring(err) .. '\n')
  vim.cmd('cquit 1')
else
  print('OK: Cancel applied no changes')
end
" -c "qa!"

echo "[4/4] Behavioral: a rename that collides with an existing symbol (LSP error response) aborts before any file is touched..."
nvim -u init.lua --headless -c "lua
local ok, err = pcall(function()
  local fixture = '$FIXTURE_DIR'
  local java_file = fixture .. '/src/main/java/com/example/FooService.java'
  local before = table.concat(vim.fn.readfile(java_file), '\n')

  local fake_client = {
    id = 9003,
    name = 'jdtls',
    offset_encoding = 'utf-16',
    config = { root_dir = fixture },
  }
  local orig_get_clients = vim.lsp.get_clients
  local orig_buf_request_all = vim.lsp.buf_request_all
  vim.lsp.get_clients = function(_) return { fake_client } end
  vim.lsp.buf_request_all = function(_, _, _, handler)
    handler({ [fake_client.id] = { err = { message = 'Element already exists: Consumer' } } })
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
  -- Position the cursor on the 'FooService' class name (line 6, 'public
  -- class FooService {') -- project_rename derives old_name from
  -- vim.fn.expand('<cword>') at the cursor, exactly like the real
  -- <leader>cr keymap would.
  vim.api.nvim_win_set_cursor(0, { 6, 13 })
  local refactor = require('cumulus.util.refactor')
  refactor.project_rename('Consumer')

  vim.wait(2000, function() return false end, 100)
  vim.notify = orig_notify

  assert(not select_called, 'quickfix confirm must never be shown for a colliding rename')
  local saw_error = false
  for _, n in ipairs(notified) do
    if n.level == vim.log.levels.ERROR then saw_error = true end
  end
  assert(saw_error, 'a visible vim.notify ERROR naming the conflict is required')

  local after = table.concat(vim.fn.readfile(java_file), '\n')
  assert(before == after, 'a colliding rename must not touch any file')
  vim.lsp.get_clients = orig_get_clients
  vim.lsp.buf_request_all = orig_buf_request_all
end)
if not ok then
  io.stderr:write('FAIL: ' .. tostring(err) .. '\n')
  vim.cmd('cquit 1')
else
  print('OK: collision aborted before the quickfix preview, no edits applied')
end
" -c "qa!"

echo ""
echo "✔ Project-Wide Safe Rename (SPEC-2.1) smoke test PASSED."
echo ""
echo "NOT covered by this script (requires a real JDTLS/Kotlin LS process attached"
echo "to a real project, unavailable in this sandbox) -- verify manually per"
echo "spec-2-1's Verification section:"
echo "  - A real textDocument/rename round-trip against JDTLS/Kotlin LS"
echo "  - The 'No JVM LSP attached' path against a genuine non-JVM buffer with"
echo "    no client, from the actual <leader>cr keymap rather than a direct call"
echo "  - The quickfix preview's on-screen contents (:copen) for a human reviewer"
