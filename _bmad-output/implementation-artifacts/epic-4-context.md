# Epic 4 Context: Git, ALM, and Team Collaboration

<!-- Compiled from planning artifacts. Edit freely. Regenerate with compile-epic-context if planning docs change. -->

## Goal

Bring the version-control and pull-request workflow fully inside the editor so an enterprise developer never has to context-switch to a browser or a JetBrains IDE to merge branches or review code. This is the "workflow parity" epic: it delivers a 3-way visual merge-conflict resolver, inline blame and history exploration scoped to specific code blocks, and an in-editor code-review surface for GitHub and GitLab (fetch PR diffs, leave line-level comments, submit reviews, check out PR branches with one command). The intent is to keep a solo operator in flow state while participating in normal team processes — not to add real-time multi-user collaboration.

## Stories

- Story 4.1: Advanced Git Conflict Resolution
- Story 4.2: In-Editor Code Reviews (GitHub/GitLab)

## Requirements & Constraints

- Merge conflicts must be resolvable through a 3-way visual diff (base / ours / theirs) rendered in the editor, not by hand-editing conflict markers.
- Git blame must be available inline, and history must be explorable for a selected code block or line range, not just whole files.
- Pull request diffs must be fetchable from the remote forge and displayed in-editor.
- Reviewers must be able to add line-level comments and submit a full review (approve / request changes / comment) without leaving Neovim.
- Checking out a PR's branch must be a single command.
- Both GitHub and GitLab must be supported as review backends.
- All forge network calls and git operations must be strictly asynchronous — no editor freeze while fetching PRs, diffs, or comment threads (project-wide non-functional requirement).
- Deliver functionality via pure Lua and well-established Neovim plugins; add no new surface area to the legacy Scala `tetravim-engine`, and no external Bash/Python helper scripts.
- Scope is single-operator efficiency. Complex multi-user / collaborative-editing features are explicitly out of scope.

## Technical Decisions

- **Use `diffview.nvim`** as the diff/merge engine for both stories: it provides the 3-way conflict-resolution UI for Story 4.1 and the diff-rendering layer for PR review in Story 4.2. Build the diff/review surface once and share it.
- **Event-driven UI:** git and forge-API results must emit Neovim autocommands; UI components listen and render asynchronously via `vim.schedule`. Never update UI directly from a background callback.
- **Headless external execution:** shell out to `git` and forge CLIs/APIs (e.g. `gh`, `glab`) via `vim.system`, never `:terminal`. Pipe output into native UI (splits, quickfix, notifications).
- **Stateless project context:** do not cache PR lists, diffs, review threads, or repo topology across sessions; fetch on demand from git or the forge API.
- **Decentralized plugin config:** git and review plugin configuration is isolated by filetype/command under `lua/tetravim/plugins/`, never merged into a global config file.
- **Direct UI coupling:** call `snacks.nvim` and `telescope.nvim` primitives directly; do not build adapter/facade layers.
- **Display placement:** referenceable, long-lived output (PR diffs, review comment threads, blame history, conflict views) belongs in persistent splits / the bottom drawer. Floating windows are reserved for ephemeral pickers and momentary confirmations, and use rounded borders plus the active cloud colorscheme.

## UX & Interaction Patterns

- Keymaps slot into the existing mnemonic `<leader>` hierarchy (`<leader>c` Code, `<leader>j` Java/JVM, `<leader>o` DevOps, `<leader>d` Debug, `<leader>ct` Cloud Theme). Git has no reserved prefix yet — choose one consistent mnemonic and keep every command under it; do not introduce an ad-hoc scheme.
- List-style choices (pick a PR, pick a conflicted file, jump to a review thread) use the standard fuzzy-searchable Telescope / Snacks picker with preview enabled.
- In-flight operations show a spinner or Snacks notification; completion shows a toast. Success states stay quiet (a statusline flash / tick); errors must be visible, explicit, and never silently swallowed, and should say what happened and how to fix it.
- The Lualine statusline already shows the current git branch on the left; conflict / review state can surface there.
- Nerd Font git-status glyphs must render correctly (patched monospace font is a hard requirement).

## Cross-Story Dependencies

- Stories 4.1 and 4.2 share the `diffview.nvim`-based diff surface; sequence 4.1 first (local git only) so 4.2 can build PR diff rendering on the same layer.
- Story 4.2 additionally depends on forge CLIs/APIs (`gh` for GitHub, `glab` for GitLab) and their auth being present on the machine; Story 4.1 needs only a local git repository.
- Both stories reuse the project-wide headless executor utility and the async spinner/toast + quickfix notification patterns established for build and test output in earlier epics; neither depends on the feature content of Epics 1–3 or 5.
