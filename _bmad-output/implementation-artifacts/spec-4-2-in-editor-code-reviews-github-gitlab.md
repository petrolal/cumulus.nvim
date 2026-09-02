---
title: 'In-Editor Code Reviews (GitHub/GitLab)'
type: 'feature'
created: '2026-09-02'
status: 'draft'
review_loop_iteration: 0
context: ['/home/petrolal/tetravim.nvim/_bmad-output/implementation-artifacts/epic-4-context.md']
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Enterprise developers must leave the editor to use a browser or external IDE for code reviews. There is no integrated way to view Pull Request diffs, leave line-level comments, approve PRs, or check out PR branches for GitHub and GitLab without breaking flow state.

**Approach:** Introduce a stateless, headless forge integration (`util/forge.lua`) that shells out to `gh` and `glab` via `vim.system`. This component will fetch PR data asynchronously, render PR diffs natively through `diffview.nvim`, and display review threads in a persistent split. Keymaps will sit under a new `<leader>gr` (git review) group.

## Boundaries & Constraints

**Always:**
- Use `vim.system` for all `gh` and `glab` interactions to ensure strict asynchronous non-blocking behavior.
- Support both GitHub (`gh`) and GitLab (`glab`) dynamically by sniffing the git remote or relying on tool presence.
- Render diffs using `diffview.nvim`. Render comment threads in a persistent split or bottom drawer. Floating windows are only for ephemeral inputs (e.g., typing a comment or selecting a PR).
- Extend `sindrets/diffview.nvim` config in a new `lua/tetravim/plugins/tools-review.lua` instead of modifying global config.
- Validate safety first using `require("tetravim.util.git").guard()` for every command.
- Provide a `:checkhealth tetravim` section to verify the presence of `gh` and `glab`.

**Ask First:**
- If mapping a diffview buffer line number to a `gh`/`glab` line-level comment proves technically impossible without writing a full diff parser, HALT and ask for permission to simplify to PR-level comments.
- If `diffview.nvim` requires checking out a branch to diff it properly instead of doing remote diffs in memory, ask before changing the working tree unexpectedly.

**Never:**
- Do not use `octo.nvim` or `gitlab.nvim`. This must be a bespoke, minimal pure-Lua integration bridging to the CLIs.
- Do not introduce any Scala backend changes.
- Do not cache PR lists, comments, or repository topology across sessions.
- Do not use `:terminal` to shell out.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Missing CLIs | `<leader>grp` with no `gh` or `glab` installed | Graceful exit, clear `notify_err` instructing the user to install the appropriate CLI | Guarded, no stacktrace |
| Not in a git repo | `<leader>grp` | Blocked by `git.lua`'s guard | `notify_err`, no stacktrace |
| Select PR for review | `<leader>grp` -> pick PR via `snacks` picker | Fetches PR branch, opens `diffview.nvim` showing base vs PR, opens split with threads | N/A |
| Checkout PR branch | `<leader>grc` -> pick PR | Asynchronously checks out the PR branch locally and shows success toast | Error toast on failure |

</frozen-after-approval>

## Code Map

- `lua/tetravim/util/forge.lua` (NEW) -- The core stateless engine handling asynchronous `vim.system` calls to `gh` and `glab`, parsing results, and managing the UI split for threads.
- `lua/tetravim/plugins/tools-review.lua` (NEW) -- Lazy config extending `sindrets/diffview.nvim` with the `<leader>gr` keymaps and `cmd` lazy loads.
- `lua/tetravim/util/git.lua` -- Reference point; reuse `M.guard()` to validate worktree constraints.
- `lua/tetravim/plugins/ui-whichkey.lua` -- Add `<leader>gr` group (e.g. "git review") to the global which-key spec.
- `lua/tetravim/health.lua` -- Add a section checking for `gh` and `glab` binaries.
- `lua/tetravim/tests/forge_review_spec.lua` (NEW) -- Static shape busted spec ensuring lazy config doesn't require modules eagerly.
- `scripts/validate-4-2.sh` (NEW) -- Runtime smoke test for the new commands and healthcheck section.

## Tasks & Acceptance

**Execution:**
- [ ] `lua/tetravim/util/forge.lua` -- create engine for async `gh`/`glab` commands and UI management -- must handle fetching PRs, diffing via diffview, checking out PRs, and posting comments.
- [ ] `lua/tetravim/plugins/tools-review.lua` -- create lazy spec merging into `diffview.nvim` -- registers `<leader>gr*` keymaps and wires them to `forge.lua`.
- [ ] `lua/tetravim/plugins/ui-whichkey.lua` -- add `<leader>gr` group -- ensures discoverability of review features.
- [ ] `lua/tetravim/health.lua` -- append health section -- checks `gh` and `glab` executables.
- [ ] `lua/tetravim/tests/forge_review_spec.lua` -- write static spec -- verifies plugin loading shape and which-key registration.
- [ ] `scripts/validate-4-2.sh` -- write runtime smoke test -- verifies keymaps load, healthcheck detects binaries, and `gh`/`glab` commands are properly escaped.

**Acceptance Criteria:**
- Given a git repo, when the user triggers the PR list keymap, then a picker shows open PRs and selecting one opens a diffview and a comment thread split.
- Given an active PR review, when the user triggers the comment keymap on a diff line, they can submit a comment asynchronously to GitHub/GitLab.
- Given the CWD is not a git work tree or CLIs are missing, when review keymaps are pressed, a clear error notification appears.
- Given a PR checkout request, when the user selects a PR, then the corresponding branch is checked out locally without blocking the editor.

## Spec Change Log

## Design Notes

- **CLI detection:** `util/forge.lua` should determine the forge by checking `git remote -v` (e.g., `github.com` vs `gitlab.com` or custom domains) or fallback to checking `gh auth status` vs `glab auth status`.
- **Keymaps:** 
  - `<leader>grp` - List and Review PRs
  - `<leader>grc` - Checkout PR branch
  - `<leader>grC` - Add comment (active within diffview)
- **Async Execution:** Always pipe output from `vim.system({ "gh", ... })` directly into the UI state, never freezing Neovim.

## Verification

**Commands:**
- `stylua --check lua/ ftplugin/ init.lua` -- expected: pass clean.
- `bash scripts/validate-4-2.sh` -- expected: exits 0, proving smoke test passes.
- `nvim --headless -u init.lua -c "PlenaryBustedDirectory lua/tetravim/tests/" -c "qa"` -- expected: `forge_review_spec.lua` passes.

**Manual checks:**
- `:checkhealth tetravim` displays the "Code Reviews (GitHub/GitLab)" section evaluating `gh` and `glab` presence.
