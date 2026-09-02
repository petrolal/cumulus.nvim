---
title: 'In-Editor Code Reviews (GitHub/GitLab)'
type: 'feature'
created: '2026-09-02'
status: 'done'
review_loop_iteration: 1
baseline_commit: 'dc5e00683e7def11abec00b1b12e13f19b05d649'
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
- [x] `lua/tetravim/util/forge.lua` -- create engine for async `gh`/`glab` commands and UI management -- must handle fetching PRs, diffing via diffview, checking out PRs, and posting comments. Must not modify `EPICS_AND_STORIES.md` or `devops.lua`.
- [x] `lua/tetravim/plugins/tools-review.lua` -- create lazy spec merging into `diffview.nvim` -- registers `<leader>gr*` keymaps and wires them to `forge.lua`.
- [x] `lua/tetravim/plugins/ui-whichkey.lua` -- add `<leader>gr` group -- ensures discoverability of review features.
- [x] `lua/tetravim/health.lua` -- append health section -- checks `gh` and `glab` executables.
- [x] `lua/tetravim/tests/forge_review_spec.lua` -- write static spec -- verifies plugin loading shape and which-key registration.
- [x] `scripts/validate-4-2.sh` -- write runtime smoke test -- verifies keymaps load, healthcheck detects binaries, and `gh`/`glab` commands are properly escaped.

**Acceptance Criteria:**
- Given a git repo, when the user triggers the PR list keymap, then a picker shows open PRs and selecting one opens a diffview and a comment thread split.
- Given an active PR review, when the user triggers the comment keymap on a diff line, they can submit a comment asynchronously to GitHub/GitLab.
- Given the CWD is not a git work tree or CLIs are missing, when review keymaps are pressed, a clear error notification appears.
- Given a PR checkout request, when the user selects a PR, then the corresponding branch is checked out locally without blocking the editor.

## Spec Change Log
- **Finding:** `add_comment` is context-blind (relies on detached HEAD) and only supports single-line input. Validation gap in tests. Scope creep in planning docs.
  **Amended:** Tasks & Acceptance, Design Notes, Verification.
  **Avoids:** Broken `add_comment` functionality during remote reviews, missing multi-line comments, and undetected regression gaps.
  **KEEP:** `tools-review.lua` lazy merging structure, `scripts/validate-4-2.sh` basic structure, UI split implementation for thread loading.

## Design Notes

- **CLI detection:** `util/forge.lua` should determine the forge dynamically without hardcoding `origin`.
- **Keymaps:** 
  - `<leader>grp` - List and Review PRs
  - `<leader>grc` - Checkout PR branch
  - `<leader>grC` - Add comment
- **Async Execution:** Always pipe output from `vim.system({ "gh", ... })` directly into the UI state. Disable ANSI colors via `NO_COLOR=1` env or parse JSON.
- **Review State:** `forge.lua` MUST store the active PR number in a local variable when `<leader>grp` is run, so that `add_comment` knows which PR to comment on without relying on `git branch --show-current`.
- **Multi-line Comments:** `add_comment` MUST use a temporary scratch buffer or similar multi-line input method for comments instead of `vim.ui.input`.
- **Code Reuse:** Extract the PR fetching and `snacks.picker.select` logic into a shared `select_pr` helper to keep the code DRY.

## Verification

**Commands:**
- `stylua --check lua/ ftplugin/ init.lua` -- expected: pass clean.
- `bash scripts/validate-4-2.sh` -- expected: exits 0, proving smoke test passes. **Must** include a `trap` for cleanup of mock scripts. **Must** mock and assert `vim.system` correctly captures `list_and_review_prs` and `checkout_pr` operations, not just comments.
- `nvim --headless -u init.lua -c "PlenaryBustedDirectory lua/tetravim/tests/" -c "qa"` -- expected: `forge_review_spec.lua` passes. **Must** verify that the `<leader>gr` group is actually added to the `ui-whichkey.lua` spec (via parsing or `require("tetravim.plugins.ui-whichkey")`).

**Manual checks:**
- `:checkhealth tetravim` displays the "Code Reviews (GitHub/GitLab)" section evaluating `gh` and `glab` presence.

## Suggested Review Order

**Forge engine — the core PR integration**

- Repo-scoped session state and CLI detection via `git remote -v`
  [`forge.lua:5`](../../lua/tetravim/util/forge.lua#L5)

- Shared `select_pr` helper: fetches PRs, presents picker, stores active session
  [`forge.lua:43`](../../lua/tetravim/util/forge.lua#L43)

- Review flow: fetches both base and head branches, opens `DiffviewOpen` dynamically, loads comment threads with `NO_COLOR=1`
  [`forge.lua:117`](../../lua/tetravim/util/forge.lua#L117)

- Checkout flow: delegates to `gh pr checkout` / `glab mr checkout`
  [`forge.lua:165`](../../lua/tetravim/util/forge.lua#L165)

- Comment flow: `acwrite` scratch buffer with `BufWriteCmd` autocmd, buffer preserved on API failure
  [`forge.lua:187`](../../lua/tetravim/util/forge.lua#L187)

**Plugin wiring and discoverability**

- Lazy spec extending `diffview.nvim` with `<leader>gr*` keymaps
  [`tools-review.lua:1`](../../lua/tetravim/plugins/tools-review.lua#L1)

- Which-Key group registration for `<leader>gr`
  [`ui-whichkey.lua:33`](../../lua/tetravim/plugins/ui-whichkey.lua#L33)

- Healthcheck section verifying `gh` and `glab` presence
  [`health.lua:305`](../../lua/tetravim/health.lua#L305)

**Tests and validation**

- Static busted spec: plugin shape + which-key group assertion
  [`forge_review_spec.lua:1`](../../lua/tetravim/tests/forge_review_spec.lua#L1)

- Runtime smoke test: mocked `vim.system` assertions for list/checkout/comment + healthcheck
  [`validate-4-2.sh:1`](../../scripts/validate-4-2.sh#L1)
