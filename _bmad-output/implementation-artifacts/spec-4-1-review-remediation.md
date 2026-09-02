---
title: 'SPEC-4.1 Review Remediation: Git Guard & Conflict-Keymap Hardening'
type: 'bugfix'
created: '2026-09-01'
status: 'done'
review_loop_iteration: 1
baseline_commit: '87951caafd152d60d416afe0655dfe1ce083bd5f'
context: ['/home/petrolal/tetravim.nvim/_bmad-output/implementation-artifacts/epic-4-context.md', '/home/petrolal/tetravim.nvim/_bmad-output/implementation-artifacts/spec-4-1-advanced-git-conflict-resolution.md']
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** A multi-lens bmad-review of the shipped Story 4.1 implementation (commit `87951ca`) found the work-tree guard blocks the UI thread on a synchronous `git rev-parse` (violates epic-4's "no editor freeze" NFR), probes Neovim's process CWD instead of the current buffer's repo (wrong-repo / false-negative in multi-project sessions), and collapses every failure mode into "Not a git repository". Several in-view guards (`if_view_open`, `history_target_ok`) can still let a raw diffview stacktrace through — a SPEC-4.1 frozen-boundary violation; the file-panel conflict picks and the `<leader>gX` whole-file cluster escape the `<leader>gx*` git mnemonic (unmapped / undiscoverable); whole-file resolution is a silent one-keystroke destructive action; and `:checkhealth tetravim` cannot distinguish "diffview not installed" from "not yet lazy-loaded" and never checks plenary.

**Approach:** Replace the subprocess work-tree check with a pure-Lua `vim.fs.root` walk and add a shared buffer-scoped `repo_root` resolver (the one Story 4.2 reuses); make every guard message name its actual condition and never surface a raw stacktrace; retire the diffview `file_panel` default picks and register `<leader>gx`/`<leader>gX` buffer-locally; gate whole-file / delete-region resolution behind a confirm; and split the `:checkhealth` diffview states, adding a plenary probe.

## Boundaries & Constraints

**Always:**
- The shared guard (`in_worktree` / `guard`, run by every `<leader>gc*` press) is pure-Lua: `vim.fs.root(start, ".git")`, matching both a `.git` dir and a `.git` gitfile — no subprocess, no `:wait()`. `history_target_ok` (the `<leader>gch` / `gcH`-only precheck) may make **at most two** short, timeout-bounded `git` calls and `:checkhealth` one, since history and health are deliberate git operations, not a per-keystroke guard. (Renegotiated 2026-09-01 — see Spec Change Log: no single `git` call distinguishes unborn-HEAD from an untracked file while keeping both distinct warnings the I/O matrix mandates.)
- The repo root is resolved from the current buffer's path, falling back to Neovim's cwd for unnamed buffers, via a new shared `tetravim.util.git.repo_root(bufnr)`. Diffview entry points operate on that repo, not the process cwd.
- Every `notify_err` / `notify_warn` states the real condition — missing `git`, not a work tree, no commits yet, untracked file, unwritten buffer — never a blanket "Not a git repository".
- All SPEC-4.1 frozen boundaries still hold: every resolution path under `<leader>gx*` / `<leader>gX*` (view **and** file panel), never a raw diffview stacktrace, views in real splits, `<leader>g`-only keymaps, `gitsigns` keeps blame, `merge_tool.layout` stays `"diff4_mixed"`.
- `<leader>gx` / `<leader>gX` which-key groups register buffer-locally for diffview buffers only, following `lua/tetravim/core/lang-keymaps.lua`'s `buffer = true` pattern; `<leader>gc` stays a global group.
- Static shape stays in `git_conflict_spec.lua` and must not `require("diffview")` (per `AGENTS.md`). Tests must not pin a value the spec leaves free (`diff3_mixed` vs `diff4_mixed`).

**Ask First:**
- If diffview cannot be pointed at a repo other than the process cwd without custom window/tabpage code, stop and ask.

**Never:**
- Do not touch `scripts/validate-4-1.sh`, `scripts/validate.sh`, or `spec-4-1-advanced-git-conflict-resolution.md` — both are split into their own `deferred-work.md` follow-ups.
- Do not add `neogit` / `vim-fugitive` / `git-conflict.nvim`, hand-write conflict-marker parsing, or a custom diff/window UI.
- Do not hand-edit `lazy-lock.json`; do not touch `tetravim.util.engine`'s legacy conflict stub; no keymap outside `<leader>g`.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| git slow / hung / absent | any `<leader>gc*` press | shared guard runs no subprocess; editor does not block | `notify_err` names missing git vs non-repo |
| CWD ≠ buffer's repo | `<leader>gco` in a buffer under a different repo than cwd | diffview opens against the buffer's repo | N/A |
| Fresh repo, no commits (unborn HEAD) | `<leader>gch` / `<leader>gcH` | `notify_warn` "repository has no commits yet"; nothing opens | guarded, no stacktrace |
| Unnamed/scratch or `&modified` buffer | `<leader>gch` / `<leader>gcH` | `notify_warn`; no tabpage, no diffview error | guarded |
| Untracked file | `<leader>gch` | `notify_warn` "file is not tracked by git"; nothing opens | guarded (one bounded probe) |
| No view open | `<leader>gcq` / `<leader>gcf` | silent no-op — no notification, no thrown error | `pcall` wraps `get_current_view()` too |
| Whole-file / delete-region resolve | `<leader>gX1/2/3/a/0`, `<leader>gx0` | confirm prompt; "No" → buffer unchanged | N/A |
| File-panel conflict pick | `cO` / `cT` / `cB` / `cA` / `dX` in the diffview file panel | unmapped; only `<leader>gX*` resolves from the panel | N/A |
| diffview not installed | `:checkhealth tetravim` | `vim.health.error` "not installed — run :Lazy install", distinct from the not-yet-loaded warn | N/A |
| plenary.nvim missing | `:checkhealth tetravim` | `vim.health.warn` naming diffview's hard dependency | N/A |

</frozen-after-approval>

## Code Map

- `lua/tetravim/util/git.lua` -- ENTIRE FILE rewritten. `M.in_worktree()` (:27-40): drop the `vim.system(...):wait()` probe, return `M.repo_root() ~= nil`. NEW `M.repo_root(bufnr)`: see Design Notes. NEW `M.has_commits(root)`: bounded `git -C <root> rev-parse --verify HEAD` (history path only). `M.guard()` (:45-60): ONE `executable("git")` check (remove the duplicate at :28), then branch the message — install hint / "not a git repository" / "repository has no commits yet". `vim.fs.dirname` precedent: `lua/tetravim/plugins/lsp-java.lua:45`.
- `lua/tetravim/plugins/tools-diffview.lua` -- `guard()` (:16-18) and every `<leader>gc*` callback (:57-110) resolve `repo_root` and scope `:Diffview*` to it (window-local `vim.fn.chdir` / `:lcd` around the call, or diffview's `-C <path>` arg if the installed version takes it — else Ask First). `history_target_ok()` (:23-32): `vim.fn.expand("%:p")` for both the emptiness check and `filereadable`; reject `vim.bo.modified`; reject an untracked file (one bounded `git -C <root> ls-files --error-unmatch`); reject `not has_commits`. `<leader>gch` (:82) and `<leader>gcH` range (:95-99): pass `vim.fn.fnameescape(vim.fn.expand("%:p"))`, not bare `%`. `if_view_open()` (:37-42): `local ok, view = pcall(function() return require("diffview.lib").get_current_view() end); if ok and view then vim.cmd(cmd) end`. `opts.keymaps` (:127-183): add a `file_panel` block mirroring the `view` disable+rebind (`cO/cT/cB/cA/dX` → `false`; `<leader>gX1/2/3/a/0` → `actions.conflict_choose_all(...)`). Wrap the `conflict_choose_all(...)` callbacks and `conflict_choose("none")` in `vim.fn.confirm(...)`. In `config` (:186-188): create a diffview `FileType` / `BufWinEnter` autocmd that registers the buffer-local `<leader>gx` / `<leader>gX` which-key groups (`require("which-key").add({ ..., buffer = 0 })`).
- `lua/tetravim/health.lua:245-283` -- diffview branch (:276-283): `local lz = require("lazy.core.config").plugins["diffview.nvim"]`; `not lz` → `vim.health.error("diffview.nvim: not installed -- run :Lazy install")`; `lz and not package.loaded.diffview` → keep the current `warn` (not yet lazy-loaded); else `ok`. Add `pcall(require, "plenary")` → `ok` / `warn("plenary.nvim: not resolvable -- diffview's hard dependency")`. The git `--version` parse (:247-269) is unchanged and exempt from the no-subprocess rule (health only).
- `lua/tetravim/plugins/ui-whichkey.lua:29-30` -- DELETE the global `{ "<leader>gx", group = "conflict: pick side", ... }` line (now buffer-local, from tools-diffview). KEEP the global `{ "<leader>gc", group = "conflict/compare", ... }` line — its `gco/gcq/gch/gcH/gcf` children are global. Pattern reference: `ui-whichkey.lua:38-44` + `lua/tetravim/core/lang-keymaps.lua:140-155`.
- `lua/tetravim/tests/git_conflict_spec.lua` -- loosen the layout assertion to `src:match('layout%s*=%s*"diff[34]_mixed"')`; assert `git.repo_root` and `git.has_commits` are functions; assert `tools-diffview.lua` source contains a `file_panel` keymaps block, `vim.fn.confirm`, and `<leader>gX`; assert `ui-whichkey.lua` source no longer registers a global `"<leader>gx"` group. Still MUST NOT `require("diffview")`.
- Read-only references: `lua/tetravim/util/http.lua:26-42` (`notify_err` install-hint phrasing to match), `lua/tetravim/util/db.lua:25-81` (`[".git"]` marker walk), `AGENTS.md:29-35` (static-spec / `validate-*.sh` split).

## Tasks & Acceptance

**Execution:**
- [x] `lua/tetravim/util/git.lua` -- rewrite: pure-Lua `in_worktree` via `vim.fs.root`; new `repo_root(bufnr)` and `has_commits(root)`; single git-executable check; condition-specific `guard()` messages.
- [x] `lua/tetravim/plugins/tools-diffview.lua` -- scope every entry point to the buffer's `repo_root`; harden `history_target_ok` (abs path, `&modified`, untracked, unborn HEAD, `fnameescape`); `pcall` the `get_current_view()` call; add the `file_panel` disable+rebind block; `vim.fn.confirm` on whole-file / delete-region resolution; register buffer-local `<leader>gx` / `<leader>gX` which-key groups.
- [x] `lua/tetravim/health.lua` -- split diffview installed / not-loaded / ok states; add the plenary probe.
- [x] `lua/tetravim/plugins/ui-whichkey.lua` -- delete the global `<leader>gx` group entry; keep `<leader>gc`.
- [x] `lua/tetravim/tests/git_conflict_spec.lua` -- loosen the layout literal; cover the new symbols and source-shape assertions.

**Acceptance Criteria:**
- Given `git` is on `$PATH` but pathologically slow, when the user presses `<leader>gco`, then the shared guard runs no synchronous subprocess and the editor does not block.
- Given a buffer whose file lives in repo A while Neovim's cwd is repo B, when the user opens the merge tool or file history, then it operates on repo A.
- Given the user presses `<leader>gcq` or `<leader>gcf` with no diffview view open, then nothing is notified and no error is raised.
- Given a conflicted merge buffer, when the user presses a file-panel default pick (`cO`/`cT`/`dX`), then it is unmapped; `<leader>gX*` still resolves from the panel.
- Given diffview is not installed at all, when `:checkhealth tetravim` runs, then it reports an error telling the user to run `:Lazy install`, distinct from the not-yet-loaded warning; plenary absence is also reported.
- Given `stylua --check lua/ ftplugin/ init.lua`, then the changed files pass unchanged.
- Given `nvim --headless -u init.lua -c "PlenaryBustedDirectory lua/tetravim/tests/" -c "qa"`, then `git_conflict_spec.lua` passes and still never loads `diffview`.

## Spec Change Log

- **2026-09-01 — implementation (step-03).** Making `M.in_worktree()` pure-Lua moved the `git`-binary dependency out of `in_worktree` and into `M.guard()`. This broke `scripts/validate-4-1.sh` stage `[4/7]`, whose assertion `git.in_worktree() == false` (under a monkey-patched-absent `git`, cwd inside a real repo) pinned the old coupling. Per the user's "amend spec, leave script to follow-up" decision: the frozen **Never** (do not touch `validate-4-1.sh`) stands; the non-frozen Verification note now records stage 4 as expected-fail; and the deferred `validate-4-1.sh` hardening entry in `deferred-work.md` was expanded to name the stage-4 assertion fix (`in_worktree() == false` → `guard() == false`). No production-code change resulted — `guard()` and `health.lua` already report the missing binary correctly. KEEP: `in_worktree` staying pure-Lua is the whole point of the NFR fix; do not re-add a `git` call to it to appease the stale assertion.

- **2026-09-01 — review (step-04), loop iteration 1.** The multi-lens review found a contradiction *inside* the frozen boundary: **Always** capped `history_target_ok` at "one short, timeout-bounded `git` call", but the frozen I/O matrix mandates two distinct git-derived warnings — unborn HEAD → "repository has no commits yet" *and* untracked file → "file is not tracked by git". No single `git` invocation distinguishes unborn-HEAD, untracked, and tracked-with-history while producing both exact messages, so the step-03 implementation runs two sequential bounded `vim.system():wait()` calls (`rev-parse --verify HEAD`, then `ls-files --error-unmatch`, 2000 ms each). Triggering finding: blind-hunter + edge-case-hunter "≤4 s stall if `git` hangs on `<leader>gch`; exceeds the one-call cap". **Resolution (human, intent_gap):** renegotiate the frozen **Always** boundary to "at most two" bounded `git` calls for `history_target_ok`; ratify the existing two-call implementation unchanged. **Known-bad state avoided:** collapsing to one `git rev-list --max-count=1 HEAD -- <path>` call, which cannot tell an untracked file from a staged-but-never-committed one and would have merged or dropped one of the two frozen warnings. **KEEP:** the two-call `history_target_ok` (unborn-HEAD via `M.has_commits`, then the `ls-files --error-unmatch` untracked probe) with its three distinct `notify_warn` messages; `M.has_commits(root)` stays a public `tetravim.util.git` symbol for Story 4.2. No production-code change resulted from this iteration.

## Design Notes

Repo-root resolver (shared with Story 4.2), buffer-first:

```lua
function M.repo_root(bufnr)
  local name = vim.api.nvim_buf_get_name(bufnr or 0)
  local start = (name ~= "" and vim.fs.dirname(name)) or vim.fn.getcwd()
  return vim.fs.root(start, ".git") -- matches a .git dir OR a .git gitfile; nil if none
end
```

`guard()` order: `executable("git")` → `repo_root()` → (history path only) `has_commits`. Pointing diffview at a non-cwd repo: prefer a window-local `vim.fn.chdir` / `:lcd` to `repo_root` scoped around the `:Diffview*` call; if that needs custom tabpage code → Ask First.

Runtime verification of the new paths lands with the deferred `scripts/validate-4-1.sh` follow-up; this spec adds the static-shape assertions to `git_conflict_spec.lua` now.

**Matrix Test Audit note (step-03):** by design and per `AGENTS.md`, diffview runtime behavior cannot run in the plenary harness — it lives in `validate-*.sh`. So of the I/O matrix, `git_conflict_spec.lua` gives static/wiring coverage for the guard/resolver symbols (row 1), the `file_panel` retire + `vim.fn.confirm` wiring (rows 7–8), and the health section shape (rows 9–10); the behavioral rows (2 buffer-scoped repo, 3 unborn HEAD, 4 scratch/`&modified`, 5 untracked, 6 no-view no-op) were verified ad-hoc at implementation time and get their standing regression stages in the deferred `validate-4-1.sh` hardening entry, which enumerates exactly those scenarios. Accepted as a consequence of the user-approved split, not an open gap.

## Verification

**Commands:**
- `stylua --check lua/ ftplugin/ init.lua` -- expected: exit 0.
- `nvim --headless -u init.lua -c "PlenaryBustedDirectory lua/tetravim/tests/" -c "qa"` -- expected: `git_conflict_spec.lua` green.
- `bash scripts/validate-4-1.sh` -- expected: stage `[4/7]` now FAILS (exit 1) on a stale white-box assertion `git.in_worktree() == false` that pinned the old `in_worktree`-shells-out-to-git coupling this spec deliberately moved into `guard()`. `guard()` still returns `false` with the install hint and `health.lua` still reports git missing, so the user-facing behavior of that stage is intact. Fixing the assertion (→ `git.guard() == false`) is folded into the deferred `scripts/validate-4-1.sh` hardening follow-up (`deferred-work.md`); the script is frozen-off-limits here.
- `nvim --headless -u init.lua -c "checkhealth tetravim" -c "qa"` -- expected: no Lua errors; the Story 4.1 section renders.

**Manual checks:**
- `:checkhealth tetravim` with diffview uninstalled vs installed-not-loaded vs loaded shows three distinct messages; plenary absence is called out.
- In a buffer under a repo different from `:pwd`, `<leader>gco` opens diffview on the buffer's repo.

## Suggested Review Order

**Pure-Lua work-tree guard (epic-4 "no editor freeze" NFR)**

- Start here -- the buffer-first `vim.fs.root` walk every other entry point builds on; no subprocess.
  [`git.lua:33`](../../lua/tetravim/util/git.lua#L33)

- `in_worktree` is now just `repo_root() ~= nil` -- the `git rev-parse` `:wait()` is gone.
  [`git.lua:43`](../../lua/tetravim/util/git.lua#L43)

- `guard()` names its actual condition -- missing `git` vs. not under a work tree -- never a blanket message.
  [`git.lua:75`](../../lua/tetravim/util/git.lua#L75)

- `has_commits` is the one bounded subprocess left, reachable only from the deliberate history precheck.
  [`git.lua:54`](../../lua/tetravim/util/git.lua#L54)

**Buffer-scoped diffview entry points (multi-project correctness)**

- `scope_flag()` turns the buffer's repo root into a `-C<root>` token so `:Diffview*` ignores the process cwd.
  [`tools-diffview.lua:32`](../../lua/tetravim/plugins/tools-diffview.lua#L32)

- `<leader>gco` opens the merge tool / working-tree diff against the buffer's repo.
  [`tools-diffview.lua:118`](../../lua/tetravim/plugins/tools-diffview.lua#L118)

- `<leader>gch` / `<leader>gcH` pass an absolute, `fnameescape`d path plus the scope flag.
  [`tools-diffview.lua:135`](../../lua/tetravim/plugins/tools-diffview.lua#L135)

**History precheck hardening**

- `history_target_ok` rejects unwritten / `&modified` / unborn-HEAD / untracked targets with distinct warnings (the two bounded `git` calls the renegotiated boundary now permits).
  [`tools-diffview.lua:43`](../../lua/tetravim/plugins/tools-diffview.lua#L43)

- `if_view_open` pcall-wraps `get_current_view()` so `<leader>gcq` / `<leader>gcf` are a silent no-op, never a stacktrace.
  [`tools-diffview.lua:87`](../../lua/tetravim/plugins/tools-diffview.lua#L87)

**Conflict-keymap rework + destructive-action confirm**

- `confirm_then` gates every whole-file pick and the delete-region pick behind a yes/no prompt.
  [`tools-diffview.lua:176`](../../lua/tetravim/plugins/tools-diffview.lua#L176)

- `conflict_binds` retires diffview's default `cO/cT/cB/cA/dX` picks and rebinds `<leader>gx*` / `<leader>gX*`; shared by both keymap tables.
  [`tools-diffview.lua:195`](../../lua/tetravim/plugins/tools-diffview.lua#L195)

- `file_panel = conflict_binds(false)` -- the panel can now only resolve whole files through the confirmed `<leader>gX*`.
  [`tools-diffview.lua:276`](../../lua/tetravim/plugins/tools-diffview.lua#L276)

**which-key group scoping**

- `register_groups` registers `<leader>gx` / `<leader>gX` buffer-locally on diffview buffers only.
  [`tools-diffview.lua:288`](../../lua/tetravim/plugins/tools-diffview.lua#L288)

- The global `<leader>gx` group is removed; global `<leader>gc` stays (its `gco/gcq/gch/gcH/gcf` children are global).
  [`ui-whichkey.lua:32`](../../lua/tetravim/plugins/ui-whichkey.lua#L32)

**:checkhealth diffview states**

- Splits not-installed (`error`) / installed-not-loaded (`warn`) / loaded (`ok`) via `lazy.core.config`, plus a plenary probe.
  [`health.lua:279`](../../lua/tetravim/health.lua#L279)

**Tests (peripheral)**

- New assertions for the `repo_root` / `has_commits` symbols.
  [`git_conflict_spec.lua:73`](../../lua/tetravim/tests/git_conflict_spec.lua#L73)

- Source-shape assertions for the `file_panel` block, `vim.fn.confirm`, and `<leader>gX`.
  [`git_conflict_spec.lua:82`](../../lua/tetravim/tests/git_conflict_spec.lua#L82)

- Asserts the global `<leader>gx` group is gone; layout literal loosened to `diff[34]_mixed`.
  [`git_conflict_spec.lua:92`](../../lua/tetravim/tests/git_conflict_spec.lua#L92)
