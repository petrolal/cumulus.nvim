#!/usr/bin/env bash
# SPEC-4.1: Advanced Git Conflict Resolution -- runtime smoke test
#
# Mirrors validate-http.sh / validate-db.sh: every assertion runs inside a
# fresh `nvim --headless -u init.lua` and calls `vim.cmd('cquit 1')` on
# failure so the exit code is trustworthy (unlike scripts/validate.sh's
# `+lua assert(...)` pattern, which never propagates non-zero).
#
# Wherever practical the assertions drive the REAL <leader>gc* / <leader>gx*
# keymap callbacks (resolved from nvim_get_keymap / nvim_buf_get_keymap),
# not the bare :Diffview* commands or diffview.actions.* directly -- so the
# user-facing wiring, guards included, is what gets exercised.
#
# Stages:
#   [0/7] install diffview.nvim (install-only; lazy-lock.json restored after)
#   [1/7] plugin loads; :Diffview* commands + <leader>gc* mappings resolve
#   [2/7] :checkhealth tetravim carries the new section
#   [3/7] outside a git work tree: guard + <leader>gco callback -> error, nothing opens
#   [4/7] git binary absent ($PATH stripped): guard error mentions install/PATH; health reports "not found"
#   [5/7] mid-merge repo: <leader>gco callback opens a diffview tabpage (real
#         splits, diff4_mixed merge layout); <leader>gcq callback closes it
#   [6/7] <leader>gch (whole file) + <leader>gcH (visual range) callbacks each
#         open history in their own tabpage, never a float
#   [7/7] resolving conflict regions through the buffer-local <leader>gx3 /
#         <leader>gX1 keymaps removes the markers and writes the chosen side;
#         the merge buffer carries none of diffview's default <leader>c* / dx picks
#
# NOT covered here (on-screen only): the exact rendered history contents a
# human reviewer reads in the <leader>gcH range view -- see spec-4-1's Manual
# checks.

set -e

echo "=== TetraVim Advanced Git Conflict Resolution (SPEC-4.1) Smoke Test ==="

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT"

# Snapshot the lockfile BEFORE anything runs and arm a single cleanup trap
# immediately: `lazy` rewrites lazy-lock.json wholesale from on-disk plugin
# state as a side effect of any install, and this smoke test must leave the
# lockfile byte-identical. Every temp dir is torn down here too.
LOCK_SNAPSHOT="$(mktemp)"
cp lazy-lock.json "$LOCK_SNAPSHOT"
trap '
  if [ -n "${LOCK_SNAPSHOT:-}" ] && [ -f "$LOCK_SNAPSHOT" ]; then cp "$LOCK_SNAPSHOT" lazy-lock.json; fi
  for d in "${LOCK_SNAPSHOT:-}" "${NON_REPO:-}" "${MERGE_REPO:-}" "${RESOLVE_REPO:-}"; do
    [ -n "$d" ] && rm -rf "$d"
  done
  true
' EXIT

# -- [0/7] Ensure diffview.nvim is on disk (lazy.nvim does not auto-install
#          missing plugins in headless mode). Install-only -- never sync/update.
echo "[0/7] Installing diffview.nvim via lazy.nvim (if missing)..."
nvim --headless -u init.lua -c "lua require('lazy').install({ wait = true, plugins = { 'diffview.nvim' } })" -c "qa!" \
  >/dev/null 2>&1 || nvim --headless -u init.lua "+Lazy! install" "+qa!" >/dev/null 2>&1 || true
cp "$LOCK_SNAPSHOT" lazy-lock.json

# -- [1/7] Plugin loads; commands + <leader>gc keymaps resolve. -----------
echo "[1/7] Static+load: diffview.nvim resolves, :Diffview* commands exist, <leader>gc* mappings registered..."
nvim --headless -u init.lua -c "Lazy! load diffview.nvim" -c "lua
local ok, err = pcall(function()
  assert(pcall(require, 'diffview'), 'diffview not resolvable after Lazy! load')
  assert(vim.fn.exists(':DiffviewOpen') == 2, ':DiffviewOpen not registered')
  assert(vim.fn.exists(':DiffviewClose') == 2, ':DiffviewClose not registered')
  assert(vim.fn.exists(':DiffviewFileHistory') == 2, ':DiffviewFileHistory not registered')

  local function mapped(suffix, modes)
    for _, mode in ipairs(modes) do
      for _, m in ipairs(vim.api.nvim_get_keymap(mode)) do
        if m.lhs:gsub('<Space>', ' '):match(suffix .. '\$') then
          return true
        end
      end
    end
    return false
  end
  assert(mapped('gco', { 'n' }), '<leader>gco (DiffviewOpen) mapping missing')
  assert(mapped('gcq', { 'n' }), '<leader>gcq (DiffviewClose) mapping missing')
  assert(mapped('gch', { 'n' }), '<leader>gch (file history) mapping missing')
  assert(mapped('gcH', { 'x', 'v' }), '<leader>gcH (visual range history) mapping missing')
  assert(mapped('gcf', { 'n' }), '<leader>gcf (toggle files) mapping missing')

  -- Frozen boundary: no new global keymap outside <leader>g.
  local diffview_spec = require('tetravim.plugins.tools-diffview')[1]
  for _, k in ipairs(diffview_spec.keys) do
    assert(tostring(k[1]):match('^<leader>g'), k[1] .. ' escapes the <leader>g group')
  end
end)
if not ok then
  io.stderr:write('FAIL: ' .. tostring(err) .. '\n')
  vim.cmd('cquit 1')
else
  print('OK: diffview.nvim loads; commands + <leader>gc* keymaps resolve; all keys under <leader>g')
end
" -c "qa!"

# -- [2/7] :checkhealth tetravim reports the new section. -----------------
echo "[2/7] Health: :checkhealth tetravim output contains the Advanced Git Conflict Resolution section..."
nvim --headless -u init.lua -c "checkhealth tetravim" -c "lua
local ok, err = pcall(function()
  local out = table.concat(vim.api.nvim_buf_get_lines(0, 0, -1, false), '\n')
  assert(out:find('Advanced Git Conflict Resolution', 1, true), 'health section header missing from :checkhealth tetravim')
  assert(out:lower():find('diffview', 1, true), 'health section does not mention diffview.nvim')
  assert(out:lower():find('git:', 1, true), 'health section does not report on the git binary')
end)
if not ok then
  io.stderr:write('FAIL: ' .. tostring(err) .. '\n')
  vim.cmd('cquit 1')
else
  print('OK: :checkhealth tetravim reports the Advanced Git Conflict Resolution section')
end
" -c "qa!"

# -- [3/7] Not a git work tree -> guarded error, nothing opens. ---------
echo "[3/7] Guard: outside a git work tree, guard() + the <leader>gco callback both notify an error and open no tabpage..."
NON_REPO="$(mktemp -d)"
NON_REPO="$NON_REPO" nvim --headless -u init.lua -c "lua vim.fn.chdir(vim.env.NON_REPO)" -c "Lazy! load diffview.nvim" -c "lua
local ok, err = pcall(function()
  assert(vim.fn.getcwd():match('/tmp/'), 'test precondition: cwd should be the temp non-repo dir')

  local errors = {}
  local orig = vim.notify
  vim.notify = function(msg, level) table.insert(errors, { msg = msg, level = level }) end

  local git = require('tetravim.util.git')
  local tabs_before = #vim.api.nvim_list_tabpages()

  -- direct guard()
  assert(git.guard() == false, 'git.guard() must return false outside a git work tree')

  -- real <leader>gco keymap callback (resolved from the live keymap table)
  local gco
  for _, m in ipairs(vim.api.nvim_get_keymap('n')) do
    if m.lhs:gsub('<Space>', ' '):match('gco\$') then gco = m end
  end
  assert(gco and type(gco.callback) == 'function', '<leader>gco must have a function callback after load')
  gco.callback()

  vim.notify = orig

  local saw_err = false
  for _, n in ipairs(errors) do
    if n.level == vim.log.levels.ERROR and tostring(n.msg):lower():find('git') then saw_err = true end
  end
  assert(saw_err, 'expected an ERROR notification mentioning git outside a work tree')
  assert(#vim.api.nvim_list_tabpages() == tabs_before, 'nothing must open when the guard fails')
end)
if not ok then
  io.stderr:write('FAIL: ' .. tostring(err) .. '\n')
  vim.cmd('cquit 1')
else
  print('OK: outside a git work tree, guard() + the <leader>gco callback error out and open nothing')
end
" -c "qa!"

# -- [4/7] git binary missing -> guard + health both honest.
#          `vim.fn.executable` is monkey-patched to report git absent -- the
#          same technique validate-http.sh uses for its "jq not installed"
#          row. (A genuinely stripped $PATH also breaks unrelated plugins that
#          shell out to git on load, e.g. kulala's grammar bootstrap, which
#          would spew ENOENT tracebacks unrelated to this story.)
echo "[4/7] Guard: with git reported absent, guard() errors (install/PATH) and :checkhealth tetravim reports git NOT found..."
PATCH_EXECUTABLE="vim.fn.executable = (function(o) return function(n) if n == 'git' then return 0 end return o(n) end end)(vim.fn.executable)"

nvim --headless -u init.lua -c "lua $PATCH_EXECUTABLE" -c "lua
local ok, err = pcall(function()
  assert(vim.fn.executable('git') ~= 1, 'test precondition: git must report as not executable')

  local errors = {}
  local orig = vim.notify
  vim.notify = function(msg, level) table.insert(errors, { msg = msg, level = level }) end

  local git = require('tetravim.util.git')
  local proceed = git.guard()
  assert(git.in_worktree() == false, 'in_worktree() must be false when git is not executable')

  vim.notify = orig

  assert(proceed == false, 'guard() must return false when git is not executable')
  local hit = false
  for _, e in ipairs(errors) do
    local t = tostring(e.msg):lower()
    if e.level == vim.log.levels.ERROR and (t:find('install') or t:find('path')) then hit = true end
  end
  assert(hit, 'expected an ERROR notification mentioning install/PATH when git is missing')
end)
if not ok then
  io.stderr:write('FAIL: ' .. tostring(err) .. '\n')
  vim.cmd('cquit 1')
else
  print('OK: git-missing guard() returns false with an install/PATH ERROR')
end
" -c "qa!"

nvim --headless -u init.lua -c "lua $PATCH_EXECUTABLE" -c "checkhealth tetravim" -c "lua
local ok, err = pcall(function()
  local out = table.concat(vim.api.nvim_buf_get_lines(0, 0, -1, false), '\n'):lower()
  assert(out:find('advanced git conflict resolution', 1, true), 'health section missing')
  assert(out:match('git:[^\n]*not found'), 'health section must report git as NOT found when it is unavailable')
end)
if not ok then
  io.stderr:write('FAIL: ' .. tostring(err) .. '\n')
  vim.cmd('cquit 1')
else
  print('OK: :checkhealth tetravim reports git NOT found when it is unavailable')
end
" -c "qa!"

# -- [5/7] Real mid-merge repo -> <leader>gco callback opens the merge tool.
echo "[5/7] Runtime: in a repo with a live merge conflict, the <leader>gco callback opens a diff4_mixed merge tabpage; <leader>gcq closes it..."
MERGE_REPO="$(mktemp -d)"
git -C "$MERGE_REPO" init -q
git -C "$MERGE_REPO" config user.email tetravim-test@example.com
git -C "$MERGE_REPO" config user.name "TetraVim Test"
git -C "$MERGE_REPO" config commit.gpgsign false
printf 'alpha\nbeta\ngamma\n' > "$MERGE_REPO/file.txt"
git -C "$MERGE_REPO" add file.txt
git -C "$MERGE_REPO" commit -qm "base"
BASE_BRANCH="$(git -C "$MERGE_REPO" rev-parse --abbrev-ref HEAD)"
git -C "$MERGE_REPO" checkout -q -b feature
printf 'alpha\nFEATURE-CHANGE\ngamma\n' > "$MERGE_REPO/file.txt"
git -C "$MERGE_REPO" commit -qam "feature edit"
git -C "$MERGE_REPO" checkout -q "$BASE_BRANCH"
printf 'alpha\nBASE-CHANGE\ngamma\n' > "$MERGE_REPO/file.txt"
git -C "$MERGE_REPO" commit -qam "base edit"
git -C "$MERGE_REPO" merge feature -q || true
if ! grep -q '<<<<<<<' "$MERGE_REPO/file.txt"; then
  echo "FAIL: fixture setup produced no conflict markers"
  exit 1
fi

MERGE_REPO="$MERGE_REPO" nvim --headless -u init.lua -c "lua vim.fn.chdir(vim.env.MERGE_REPO)" -c "lua vim.cmd.edit('file.txt')" -c "Lazy! load diffview.nvim" -c "lua
local ok, err = pcall(function()
  local lib = require('diffview.lib')

  local gco, gcq
  for _, m in ipairs(vim.api.nvim_get_keymap('n')) do
    local l = m.lhs:gsub('<Space>', ' ')
    if l:match('gco\$') then gco = m end
    if l:match('gcq\$') then gcq = m end
  end
  assert(gco and type(gco.callback) == 'function', 'no <leader>gco callback')
  assert(gcq and type(gcq.callback) == 'function', 'no <leader>gcq callback')

  local tabs_before = #vim.api.nvim_list_tabpages()
  gco.callback()
  vim.wait(5000, function() return #vim.api.nvim_list_tabpages() > tabs_before end, 50)
  assert(#vim.api.nvim_list_tabpages() > tabs_before, 'the <leader>gco callback did not open its own tabpage')
  assert(lib.get_current_view() ~= nil, 'diffview.lib.get_current_view() is nil after the <leader>gco callback')

  -- No floating windows -- diffview renders in real splits (frozen boundary).
  for _, win in ipairs(vim.api.nvim_tabpage_list_wins(0)) do
    assert(vim.api.nvim_win_get_config(win).relative == '', 'diffview must render in real splits, found a float')
  end

  -- The merge tool is a true multi-pane 3-way layout.
  local cfg_ok, cfg = pcall(function() return require('diffview.config').get_config() end)
  if cfg_ok and cfg and cfg.view and cfg.view.merge_tool then
    assert(
      cfg.view.merge_tool.layout == 'diff4_mixed',
      'merge_tool.layout must be diff4_mixed, got ' .. tostring(cfg.view.merge_tool.layout)
    )
  else
    -- fallback: a mixed 3-way merge tabpage has a file panel + >= 3 diff panes
    assert(#vim.api.nvim_tabpage_list_wins(0) >= 4, 'expected >= 4 windows in the merge tabpage')
  end

  gcq.callback()
  vim.wait(3000, function() return #vim.api.nvim_list_tabpages() == tabs_before end, 50)
  assert(#vim.api.nvim_list_tabpages() == tabs_before, 'the <leader>gcq callback did not close the diffview tabpage')
end)
if not ok then
  io.stderr:write('FAIL: ' .. tostring(err) .. '\n')
  vim.cmd('cquit 1')
else
  print('OK: the <leader>gco callback opened a diff4_mixed merge tabpage (real splits); <leader>gcq closed it')
end
" -c "qa!"

# -- [6/7] File history (whole file + visual line range) via the real keymap
#          callbacks; each opens its own tabpage, never a float.
echo "[6/7] Runtime: <leader>gch (whole file) and <leader>gcH (visual range) callbacks each open history in their own tabpage..."
MERGE_REPO="$MERGE_REPO" nvim --headless -u init.lua -c "lua vim.fn.chdir(vim.env.MERGE_REPO)" -c "lua vim.cmd.edit('file.txt')" -c "Lazy! load diffview.nvim" -c "lua
local ok, err = pcall(function()
  vim.o.showmode = false -- stop :normal ggVG from echoing the visual-mode indicator
  vim.fn.system({ 'git', 'merge', '--abort' })
  vim.cmd('edit! file.txt')
  local lib = require('diffview.lib')

  -- diffview's file-history view schedules a file_safeguard callback in
  -- post_open() that indexes panel.entries; closing before its async git-log
  -- job has populated that table leaves the callback to raise against nil
  -- during :qa! -- a diffview headless quirk, not a Story 4.1 regression.
  -- Wait for the panel to fully populate, drop diffview's autocmds, then close.
  local function close_history_cleanly(tabs_before)
    vim.wait(8000, function()
      local v = lib.get_current_view()
      local p = v and v.panel
      return p ~= nil and type(p.entries) == 'table' and #p.entries > 0
    end, 50)
    pcall(vim.api.nvim_clear_autocmds, { group = 'diffview_nvim' })
    pcall(vim.cmd, 'DiffviewClose')
    vim.wait(2000, function() return #vim.api.nvim_list_tabpages() == tabs_before end, 50)
  end

  local function cb(suffix, mode)
    for _, m in ipairs(vim.api.nvim_get_keymap(mode)) do
      if m.lhs:gsub('<Space>', ' '):match(suffix .. '\$') then return m.callback end
    end
  end

  -- Whole-file history via the <leader>gch callback.
  local gch = cb('gch', 'n')
  assert(type(gch) == 'function', 'no <leader>gch callback')
  local tabs_before = #vim.api.nvim_list_tabpages()
  gch()
  vim.wait(5000, function() return #vim.api.nvim_list_tabpages() > tabs_before end, 50)
  assert(#vim.api.nvim_list_tabpages() > tabs_before, 'the <leader>gch callback did not open its own tabpage')
  for _, win in ipairs(vim.api.nvim_tabpage_list_wins(0)) do
    assert(vim.api.nvim_win_get_config(win).relative == '', 'file history must not render in a floating window')
  end
  close_history_cleanly(tabs_before)

  -- Visual line range via the <leader>gcH callback, from a real selection.
  local gcH = cb('gcH', 'x')
  assert(type(gcH) == 'function', 'no <leader>gcH visual-mode callback')
  vim.cmd('edit! file.txt')
  vim.cmd('silent! normal! ggVG')
  gcH()
  vim.cmd('silent! normal! \27')
  vim.wait(5000, function() return #vim.api.nvim_list_tabpages() > tabs_before end, 50)
  assert(#vim.api.nvim_list_tabpages() > tabs_before, 'the <leader>gcH callback did not open a range-scoped history tabpage')
  assert(lib.get_current_view() ~= nil, 'no diffview view after the <leader>gcH callback')
  for _, win in ipairs(vim.api.nvim_tabpage_list_wins(0)) do
    assert(vim.api.nvim_win_get_config(win).relative == '', 'range history must not render in a floating window')
  end
  close_history_cleanly(tabs_before)
end)
if not ok then
  io.stderr:write('FAIL: ' .. tostring(err) .. '\n')
  vim.cmd('cquit 1')
else
  print('OK: <leader>gch + <leader>gcH callbacks each opened history in their own tabpage, no floats')
end
" -c "qa!"

# -- [7/7] Real mid-merge repo -> resolving conflict regions through the
#          BUFFER-LOCAL <leader>gx3 / <leader>gX1 keymaps removes the markers
#          and writes the chosen side. (I/O matrix: "Resolve a conflict region".)
echo "[7/7] Runtime: buffer-local <leader>gx3 / <leader>gX1 keymaps resolve regions; merge buffer has no <leader>c* / dx picks..."
RESOLVE_REPO="$(mktemp -d)"
git -C "$RESOLVE_REPO" init -q
git -C "$RESOLVE_REPO" config user.email tetravim-test@example.com
git -C "$RESOLVE_REPO" config user.name "TetraVim Test"
git -C "$RESOLVE_REPO" config commit.gpgsign false
# The two edited lines sit far apart (8 unchanged lines between them) so git
# keeps them as two distinct conflict regions rather than coalescing one.
printf 'top\nA\nf1\nf2\nf3\nf4\nf5\nf6\nf7\nf8\nB\nbot\n' > "$RESOLVE_REPO/file.txt"
git -C "$RESOLVE_REPO" add file.txt
git -C "$RESOLVE_REPO" commit -qm "base"
RESOLVE_BASE="$(git -C "$RESOLVE_REPO" rev-parse --abbrev-ref HEAD)"
git -C "$RESOLVE_REPO" checkout -q -b feature
printf 'top\nTHEIRS-1\nf1\nf2\nf3\nf4\nf5\nf6\nf7\nf8\nTHEIRS-2\nbot\n' > "$RESOLVE_REPO/file.txt"
git -C "$RESOLVE_REPO" commit -qam "feature edit"
git -C "$RESOLVE_REPO" checkout -q "$RESOLVE_BASE"
printf 'top\nOURS-1\nf1\nf2\nf3\nf4\nf5\nf6\nf7\nf8\nOURS-2\nbot\n' > "$RESOLVE_REPO/file.txt"
git -C "$RESOLVE_REPO" commit -qam "base edit"
git -C "$RESOLVE_REPO" merge feature -q || true
if [ "$(grep -c '^<<<<<<<' "$RESOLVE_REPO/file.txt")" != "2" ]; then
  echo "FAIL: fixture setup did not produce the expected 2 conflict regions"
  exit 1
fi

RESOLVE_REPO="$RESOLVE_REPO" nvim --headless -u init.lua -c "lua vim.fn.chdir(vim.env.RESOLVE_REPO)" -c "lua vim.cmd.edit('file.txt')" -c "Lazy! load diffview.nvim" -c "lua
local ok, err = pcall(function()
  local lib = require('diffview.lib')

  local function count_markers(buf)
    local n = 0
    for _, l in ipairs(vim.api.nvim_buf_get_lines(buf, 0, -1, false)) do
      if l:match('^<<<<<<<') then n = n + 1 end
    end
    return n
  end
  -- normalized (leader stripped) buffer-local keymap lookup
  local function buf_maps(buf)
    local set = {}
    for _, m in ipairs(vim.api.nvim_buf_get_keymap(buf, 'n')) do
      set[m.lhs:gsub('<Space>', ' '):gsub('^ ', '')] = m
    end
    return set
  end

  -- Open via the real <leader>gco callback.
  local gco
  for _, m in ipairs(vim.api.nvim_get_keymap('n')) do
    if m.lhs:gsub('<Space>', ' '):match('gco\$') then gco = m end
  end
  assert(gco and type(gco.callback) == 'function', 'no <leader>gco callback')
  local tabs_before = #vim.api.nvim_list_tabpages()
  gco.callback()

  vim.wait(10000, function()
    local v = lib.get_current_view()
    return v ~= nil and v.files ~= nil and v.files.conflicting ~= nil and #v.files.conflicting > 0
  end, 50)
  local view = lib.get_current_view()
  assert(view ~= nil, 'no diffview view after the <leader>gco callback on a mid-merge repo')
  assert(view.files and #view.files.conflicting > 0, 'diffview listed no conflicting files')

  -- Focus the conflicted file in the merge tool + its result window (what
  -- picking it in the file panel does).
  view:set_file(view.files.conflicting[1], true, true)
  vim.wait(10000, function()
    local m = view.cur_layout and view.cur_layout.get_main_win and view.cur_layout:get_main_win()
    if not (m and m.file and m.file.bufnr and vim.api.nvim_buf_is_valid(m.file.bufnr)) then return false end
    if count_markers(m.file.bufnr) ~= 2 then return false end
    return buf_maps(m.file.bufnr)['gx3'] ~= nil
  end, 50)

  local main = view.cur_layout:get_main_win()
  assert(main ~= nil and main:is_valid(), 'merge-tool main (result) window is not available')
  local buf = main.file.bufnr
  assert(buf and vim.api.nvim_buf_is_valid(buf), 'merge-tool result buffer is invalid')
  vim.api.nvim_set_current_win(main.id)

  local maps = buf_maps(buf)
  -- Frozen boundary: none of diffview's default non-mnemonic conflict picks.
  for _, bad in ipairs({ 'co', 'ct', 'cb', 'ca', 'cO', 'cT', 'cB', 'cA', 'dx', 'dX' }) do
    assert(maps[bad] == nil, bad .. ' must NOT be mapped in the merge buffer (frozen boundary)')
  end
  -- The rebinds ARE present, buffer-local.
  for _, want in ipairs({ 'gx1', 'gx2', 'gx3', 'gxa', 'gx0', 'gX1', 'gXa' }) do
    assert(type(maps[want] and maps[want].callback) == 'function', want .. ' must be a buffer-local mapping in the merge buffer')
  end

  local before = count_markers(buf)
  assert(before == 2, 'expected 2 conflict regions in the result buffer, got ' .. tostring(before))

  -- Park the cursor inside the first region, then fire the buffer-local
  -- <leader>gx3 keymap (choose THEIRS for the region under the cursor).
  for i, l in ipairs(vim.api.nvim_buf_get_lines(buf, 0, -1, false)) do
    if l:match('^<<<<<<<') then
      vim.api.nvim_win_set_cursor(main.id, { i, 0 })
      break
    end
  end
  maps['gx3'].callback()
  vim.wait(3000, function() return count_markers(buf) < before end, 50)
  local mid = count_markers(buf)
  assert(mid == before - 1, '<leader>gx3 should drop the conflict count by 1 (got ' .. tostring(mid) .. ')')

  -- Fire the buffer-local <leader>gX1 keymap (choose OURS for every remaining region).
  maps['gX1'].callback()
  vim.wait(3000, function() return count_markers(buf) == 0 end, 50)
  assert(count_markers(buf) == 0, '<leader>gX1 should clear all remaining conflict markers')

  -- Let diffview's async choose_all + sync_scroll drain before teardown.
  vim.wait(1500)
  pcall(vim.api.nvim_clear_autocmds, { group = 'diffview_nvim' })

  -- Persist to the working tree (what the user does with :w).
  vim.api.nvim_buf_call(buf, function() vim.cmd('silent write') end)
  pcall(vim.cmd, 'DiffviewClose')
  vim.wait(2000, function() return #vim.api.nvim_list_tabpages() == tabs_before end, 50)
  vim.wait(500)

  local disk = table.concat(vim.fn.readfile('file.txt'), '\n')
  assert(not disk:find('<<<<<<<', 1, true), 'working-tree file still has <<<<<<< markers after resolution')
  assert(not disk:find('=======', 1, true), 'working-tree file still has ======= markers after resolution')
  assert(not disk:find('>>>>>>>', 1, true), 'working-tree file still has >>>>>>> markers after resolution')
  assert(disk:find('THEIRS-1', 1, true), 'region 1 should carry the chosen THEIRS content')
  assert(disk:find('OURS-2', 1, true), 'region 2 should carry the chosen OURS content')
  assert(not disk:find('OURS-1', 1, true), 'region 1 should NOT retain the OURS content')
  assert(not disk:find('THEIRS-2', 1, true), 'region 2 should NOT retain the THEIRS content')
end)
if not ok then
  io.stderr:write('FAIL: ' .. tostring(err) .. '\n')
  vim.cmd('cquit 1')
else
  print('OK: buffer-local <leader>gx3 / <leader>gX1 removed the markers and wrote the chosen side; no <leader>c*/dx picks in the merge buffer')
end
" -c "qa!"

echo ""
echo "PASS: Advanced Git Conflict Resolution (SPEC-4.1) smoke test."
echo ""
echo "NOT covered (on-screen only -- verify manually per spec-4-1):"
echo "  - the exact rendered history contents a human reads in the <leader>gcH"
echo "    range view"
