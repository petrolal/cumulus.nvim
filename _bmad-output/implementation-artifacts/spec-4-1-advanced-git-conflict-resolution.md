---
title: 'Advanced Git Conflict Resolution'
type: 'feature'
created: '2026-09-01'
status: 'done'
review_loop_iteration: 0
context: ['/home/petrolal/cumulus.nvim/_bmad-output/implementation-artifacts/epic-4-context.md']
baseline_commit: '798b9a4f0384c81776ca780e337a34eca45fa061'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** cumulus.nvim has no visual 3-way merge-conflict resolver and no way to explore history for a specific code block or line range. A mid-merge developer must hand-edit `<<<<<<<`/`=======`/`>>>>>>>` markers, and `gitsigns` only offers whole-file blame/diff. This is the first gap Epic 4 ("workflow parity") targets.

**Approach:** Add `diffview.nvim` as the shared diff/merge engine (Story 4.2 will reuse it for PR diffs) and wire a conflict/compare keymap cluster under the existing `<leader>g` group: open the 3-way OURS/BASE/THEIRS merge tool, resolve conflict regions, and open file/line-range history in diffview's own tabpage. Line blame stays with `gitsigns`.

## Boundaries & Constraints

**Always:**
- `diffview.nvim` is the 3-way merge tool and file-history engine. Never hand-write conflict-marker parsing or a custom diff/window UI.
- New plugin lives in its own `lua/cumulus/plugins/tools-diffview.lua`, `cmd`/`keys`-lazy-loaded, following `tools-http.lua`'s isolation shape.
- Every new global keymap sits under `<leader>g` ("git control") in a `gc` conflict/compare sub-group. Rebind diffview's in-view conflict-resolution mappings under the same git mnemonic — never under `<leader>c` (code/lsp).
- Conflict, history, and diff views render in diffview's own persistent tabpage/splits — never floating windows.
- Guard every entry point: not inside a git work tree, or `git` not executable → clear message via `cumulus.util.ui.notify_err` and stop. Never a raw stacktrace.
- Reuse `gitsigns`' `<leader>gb` / `<leader>gB` for line blame; add no second blame path.
- Register a `:checkhealth cumulus` section for git presence/version and `diffview.nvim` loadability, following the existing `health.lua` section shape.
- Add a `lua/cumulus/tests/*_spec.lua` (static shape only — must NOT `require` the lazy plugin) plus `scripts/validate-4-1.sh` runtime smoke test, per `AGENTS.md`.

**Ask First:**
- If line-range history needs more than `:DiffviewFileHistory` with a range argument (a custom picker or extra plugin), stop and ask.
- If diffview's merge-tool layout cannot be set to 3-way OURS/BASE/THEIRS through its `opts`, stop and ask before writing custom window code.

**Never:**
- Do not touch the legacy `cumulus.util.engine` `parse_git_conflicts` / `resolve_git_conflicts` stub (out of scope).
- Do not add PR/forge features — that is Story 4.2.
- Do not add `neogit`, `vim-fugitive`, or `git-conflict.nvim`. diffview only.
- Do not bind any new keymap outside `<leader>g`. Do not hand-edit `lazy-lock.json`.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Behavior | Error Handling |
|----------|--------------|-------------------|----------------|
| In-progress merge with conflicts | `<leader>gco` | diffview opens a 3-way merge tool (OURS / BASE / THEIRS); conflict-nav (`]x`/`[x`) + resolution keymaps active under `<leader>g` | N/A |
| Resolve a conflict region | choose OURS / THEIRS / BASE / ALL / NONE | chosen content written to the working-tree buffer; remaining-conflict count drops | N/A |
| Clean tree, no merge | `<leader>gco` | diffview opens the working-tree diff; diffview's own empty state, no crash | N/A |
| Tracked file, file history | `<leader>gch` | `:DiffviewFileHistory` for the file opens in its own tabpage | N/A |
| Visual line range | lines selected, `<leader>gcH` | `:'<,'>DiffviewFileHistory` opens history scoped to those lines | N/A |
| CWD not a git work tree | any Story 4.1 keymap | `notify_err` "not a git repository"; nothing opens | guarded, no stacktrace |
| `git` binary missing | keymap / `:checkhealth cumulus` | keymap: `notify_err` + hint; health: `vim.health.error` | guarded |

</frozen-after-approval>

## Code Map

- `lua/cumulus/plugins/tools-http.lua` -- isolation reference: single-feature plugin file, `cmd`/`keys` lazy-load, `opts` + `config`, keymap cluster. Copy this shape for `tools-diffview.lua`.
- `lua/cumulus/plugins/tools-gitsigns.lua:16-55` -- existing `<leader>g` keymaps: `gb`/`gB` blame (reuse), `gd` diffthis, `gh*` hunks, `]c`/`[c` nav. Keep clear of these sub-keys.
- `lua/cumulus/plugins/editor-snacks.lua:311-338` -- other `<leader>g` bindings (`gg gl gL gs gS`); `gc` and `gx` sub-keys are free.
- `lua/cumulus/plugins/ui-whichkey.lua:21-35` -- `vim.list_extend(opts.spec, { … })` block where `<leader>` groups register (`g` = "git control" ~:28). Add the `<leader>gc` group entry here only.
- `lua/cumulus/core/keymaps.lua:148-241` -- precedent for a feature keymap cluster (`<leader>H*`). Prefer declaring new keys in the diffview spec's `keys`.
- `lua/cumulus/util/ui.lua:13-42` -- `notify_info/notify_warn/notify_err` (title "Cumulus"). Use these, never raw `vim.notify`.
- `lua/cumulus/util/http.lua:44` -- guard + `vim.system` style to model `util/git.lua` on.
- `lua/cumulus/health.lua:39` (`required_binaries = { rg, git }`), section list ends ~:245 -- add the new `vim.health.start(...)` after the HTTP client section; `scripts/validate.sh:24-30` loads this module.
- `lua/cumulus/util/engine.lua:664,1208-1232` -- legacy conflict stub. Read-only: leave untouched, do not call.
- `lua/cumulus/tests/profiling_spec.lua` -- busted static-spec shape for `git_conflict_spec.lua`. `scripts/validate-http.sh` -- smoke-test model (`cquit 1` on failure). `stylua.toml` -- 2-space, 120 col.

## Tasks & Acceptance

**Execution:**
- [x] `lua/cumulus/util/git.lua` (NEW) -- `M.in_worktree()` (via `git rev-parse --is-inside-work-tree`) and `M.guard()` returning `false` + `notify_err` when `git` is not executable or CWD is not a work tree, else `true`. Model on `util/http.lua`'s guard. Reused by Story 4.2.
- [x] `lua/cumulus/plugins/tools-diffview.lua` (NEW) -- `sindrets/diffview.nvim` spec: `dependencies = { "nvim-lua/plenary.nvim" }`, lazy on `cmd = { "DiffviewOpen", "DiffviewClose", "DiffviewFileHistory", "DiffviewToggleFiles", "DiffviewFocusFiles" }` + a `keys` cluster (Design Notes); `opts` sets a 3-way merge layout (`view.merge_tool.layout`, e.g. `"diff3_mixed"`) and rebinds conflict-resolution actions via `opts.keymaps` under `<leader>g`; each keymap callback runs `require("cumulus.util.git").guard()` first.
- [x] `lua/cumulus/plugins/ui-whichkey.lua` -- add `{ "<leader>gc", group = "conflict/compare", icon = "" }` to the `vim.list_extend(opts.spec, { … })` block (:21-35); leave every other group unchanged.
- [x] `lua/cumulus/health.lua` -- add a `vim.health.start("Cumulus Advanced Git Conflict Resolution")` section (after the HTTP client section): `vim.fn.executable("git")`, `git --version` >= 2.30, `pcall(require, "diffview")`; existing `ok/warn/error` shape.
- [x] `lua/cumulus/tests/git_conflict_spec.lua` (NEW) -- busted static spec: `tools-diffview.lua` first spec is `"sindrets/diffview.nvim"` and declares `cmd` + `keys`; `cumulus.util.git` exposes `in_worktree` and `guard`; which-key spec contains a `<leader>gc` group; `health.lua` source registers the new section. Must NOT `require("diffview")`.
- [x] `scripts/validate-4-1.sh` (NEW) -- fresh `nvim --headless -u init.lua`: `Lazy! load diffview.nvim`; assert `pcall(require, "diffview")`, `:DiffviewOpen` exists, the `<leader>gco`/`<leader>gch` mappings resolve, `:checkhealth cumulus` output contains the new section; in a `mktemp` repo with a real merge conflict, `:DiffviewOpen` → assert a diffview tabpage opened → `:DiffviewClose`; `cquit 1` on any failure.

**Acceptance Criteria:**
- Given a repo mid-merge with conflicts, when the user triggers the conflict keymap, then diffview opens a 3-way OURS/BASE/THEIRS view with conflict navigation and resolution keymaps active, all under `<leader>g`.
- Given a conflict region in diffview, when the user chooses ours/theirs/base/all/none, then that content is written into the working-tree buffer and the remaining-conflict count decreases.
- Given a tracked file, when the user triggers file history, then diffview's history panel opens in its own tabpage, not a floating window.
- Given a visual line selection, when the user triggers range history, then diffview shows history scoped to those lines.
- Given the CWD is not a git work tree, when any Story 4.1 keymap is pressed, then a clear error notification appears and no stacktrace is raised.
- Given `:checkhealth cumulus` is run, then it reports an "Advanced Git Conflict Resolution" section covering git and `diffview.nvim`.
- Given `scripts/validate-4-1.sh` runs, then it exits 0 on success and non-zero when an assertion is deliberately broken.
- Given `stylua --check lua/ ftplugin/ init.lua` runs, then the new files pass unchanged.

## Spec Change Log

## Design Notes

Golden keymap set (global, in the diffview spec's `keys`):

```
<leader>gco  DiffviewOpen               -- 3-way merge tool mid-merge, else working-tree diff
<leader>gcq  DiffviewClose              -- close the diffview tabpage
<leader>gch  DiffviewFileHistory %      -- history for the current file
<leader>gcH  :'<,'>DiffviewFileHistory  -- history for the selected line range (visual)
<leader>gcf  DiffviewToggleFiles        -- toggle the file panel
```

In-view conflict resolution (buffer-local, via `opts.keymaps.view`), rebound off diffview's default `<leader>c*`:

```
<leader>gx1 / gx2 / gx3   choose OURS / BASE / THEIRS for the current region
<leader>gxa  choose ALL    <leader>gx0  choose NONE (delete region)
]x / [x                    next / previous conflict (diffview defaults — keep)
```

Rationale: `<leader>c` is the LSP/code group; diffview's out-of-box `<leader>co`/`<leader>ct` picks would fracture the mnemonic hierarchy Epic 4 mandates, so they move under `<leader>gx*`. `diff3_mixed` gives OURS | BASE(over) | THEIRS and satisfies "3-way"; `diff4_mixed` is an acceptable alternative if BASE must stay always-visible.

## Verification

**Commands:**
- `stylua --check lua/ ftplugin/ init.lua` -- expected: exit 0.
- `nvim --headless -u init.lua -c "Lazy! load diffview.nvim" -c "lua assert(pcall(require,'diffview')); assert(vim.fn.exists(':DiffviewOpen')==2)" -c "qa"` -- expected: no error output, clean exit.
- `bash scripts/validate-4-1.sh` -- expected: exit 0; non-zero on a deliberately broken assertion.
- `nvim --headless -u init.lua -c "PlenaryBustedDirectory lua/cumulus/tests/" -c "qa"` -- expected: `git_conflict_spec.lua` passes with the suite.

**Manual checks:**
- `:checkhealth cumulus` shows the "Advanced Git Conflict Resolution" section: OK when `git` and `diffview.nvim` are present, error/warn otherwise.

## Suggested Review Order

**Feature wiring (start here)**

- Entry point: diffview.nvim is the sole engine, lazy-loaded on its `:Diffview*` commands and the `<leader>gc*` cluster.
  [`tools-diffview.lua:46`](../../lua/cumulus/plugins/tools-diffview.lua#L46)

- The golden keymap set — every lhs under `<leader>g`; `gco` opens the merge tool / working-tree diff.
  [`tools-diffview.lua:56`](../../lua/cumulus/plugins/tools-diffview.lua#L56)

- 3-way layout: `diff4_mixed` gives a distinct always-visible BASE pane (OURS / BASE / THEIRS).
  [`tools-diffview.lua:123`](../../lua/cumulus/plugins/tools-diffview.lua#L123)

**In-view conflict resolution rebind**

- diffview's default `<leader>c*` and `dx`/`dX` picks disabled, then rebound under `<leader>gx*` / `<leader>gX*`.
  [`tools-diffview.lua:127`](../../lua/cumulus/plugins/tools-diffview.lua#L127)

**Safe-entry guards (frozen boundary: never a raw stacktrace)**

- Shared git work-tree guard — `git` missing or non-repo CWD → one clear `notify_err`, timeout-bounded probe.
  [`git.lua:45`](../../lua/cumulus/util/git.lua#L45)

- History keymaps additionally bail (warn, not error) on unnamed / non-file buffers.
  [`tools-diffview.lua:23`](../../lua/cumulus/plugins/tools-diffview.lua#L23)

- Close / toggle-files act only on an already-open view — silent no-op otherwise, not an entry point.
  [`tools-diffview.lua:37`](../../lua/cumulus/plugins/tools-diffview.lua#L37)

**Discoverability & health**

- which-key groups for `<leader>gc` (conflict/compare) and `<leader>gx` (conflict: pick side).
  [`ui-whichkey.lua:29`](../../lua/cumulus/plugins/ui-whichkey.lua#L29)

- `:checkhealth cumulus` section: git presence + advisory `>= 2.30` version, diffview.nvim resolvability; honest warn on a failed probe.
  [`health.lua:245`](../../lua/cumulus/health.lua#L245)

**Tests**

- Static shape spec — declares diffview lazily, must never eagerly `require("diffview")`; repo-root-relative file reads.
  [`git_conflict_spec.lua:23`](../../lua/cumulus/tests/git_conflict_spec.lua#L23)

- Runtime smoke test — 7 stages driving the real `<leader>gc*` / buffer-local `<leader>gx*` callbacks; leaves `lazy-lock.json` byte-identical.
  [`validate-4-1.sh:352`](../../scripts/validate-4-1.sh#L352)
