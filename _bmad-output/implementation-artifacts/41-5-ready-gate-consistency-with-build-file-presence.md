---
baseline_commit: ab34e5e9880e821890c0b14f87aa17b9a671c5fd
---

# Story 41.5: Ready-Gate Consistency with Build File Presence

Status: review

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As a JVM developer,
I want `<leader>cj`/`<leader>cx` to require both a matching filetype AND an actual `pom.xml`/`build.gradle(.kts)` in the project,
so that these Maven/Gradle-only build commands don't appear in every `.java`/`.kotlin`/`.groovy`/`.xml` buffer regardless of whether the project is a Maven/Gradle project at all.

## Acceptance Criteria

1. **Given** a `java`/`kotlin`/`groovy`/`xml` buffer opened in a project with **no** `pom.xml` and **no** `build.gradle(.kts)` anywhere in its search path, **when** keymaps are applied, **then** `<leader>cj`/`<leader>cx` do **not** appear for that buffer.
2. **Given** the same buffer, **when** a `pom.xml` or `build.gradle(.kts)` is later added to the project (or the buffer is re-applied after one is found), **then** `<leader>cj`/`<leader>cx` do appear, exactly as today.
3. **Given** any *other* registered stack in `lua/cumulus/core/keymaps.lua` that declares only `filetypes` with no `condition` (Terraform, Ansible, Docker, Helm, etc.), **when** this story's fix ships, **then** those stacks' visibility is completely unchanged — the fix must be additive/generic, not a hardcoded special case for the `<leader>cj`/`<leader>cx` stacks specifically.

## Root Cause (read this before touching anything)

In `lua/cumulus/core/lang-keymaps.lua`'s `apply()` (~line 61):

```lua
if matches_ft or matches_cond then
```

This is an **OR**. `matches_ft` becomes `true` whenever the buffer's filetype is in `stack.filetypes` — completely independent of `stack.condition`'s result. Because the `<leader>cj`/`<leader>cx` stacks in `keymaps.lua` (~line 74-207) declare **both** `filetypes = { "java", "kotlin", "groovy", "xml" }` (or `{"java","kotlin","groovy"}`) **and** a `condition` function that checks `maven.find_pom() or gradle.find_gradle()`, the `condition` is currently dead weight for gating purposes: any `java`/`kotlin`/`groovy`/`xml` buffer satisfies `matches_ft` on its own, so the keys register regardless of whether the project actually has a `pom.xml`/`build.gradle`. Combined with `ready_gate` becoming `true` almost immediately in any session (even the "nothing to sync" branch in `autocmds.lua` calls `mark_ready()`), the net effect today is: **`<leader>cj`/`<leader>cx` show up in any Java/Kotlin/Groovy/XML buffer in any project, Maven/Gradle or not.**

## Tasks / Subtasks

- [x] Task 1: Fix the matcher combinator generically in `apply()` (AC: #1, #2, #3)
  - [x] In `lua/cumulus/core/lang-keymaps.lua`'s `apply()`, replace the `if matches_ft or matches_cond then` line with logic that requires **both** to hold only when a stack declares **both** `filetypes` and `condition`; stacks with only one of the two keep today's behavior (effectively OR, since the missing side is always `false`). E.g.:
    ```lua
    local visible
    if stack.filetypes and stack.condition then
      visible = matches_ft and matches_cond
    else
      visible = matches_ft or matches_cond
    end
    if visible then
      -- existing vim.keymap.set loop
    end
    ```
  - [x] Do **not** hardcode this to the `<leader>cj`/`<leader>cx` groups by name/group-string — the fix must live in the generic `apply()` combinator so it applies to any current or future stack that declares both `filetypes` and `condition`.
  - [x] Confirm via `grep -n "condition = function" lua/cumulus/core/keymaps.lua` that, as of this writing, only the two `<leader>cj`/`<leader>cx` stacks (~line 77, ~line 212) declare `condition` — every other stack (Terraform `<leader>ct`, Ansible `<leader>cy`, Docker `<leader>cD`, Helm `<leader>ck`) declares only `filetypes`, so AC #3 is satisfied automatically by this generic fix, not by a separate code path.
- [x] Task 2: Verify
  - [x] Open a `.java` file in a directory tree with no `pom.xml`/`build.gradle` anywhere above it — confirm `<leader>cj`/`<leader>cx` are absent from the which-key popup and `vim.api.nvim_buf_get_keymap` for that buffer.
  - [x] Open a `.java` file in a project that does have a `pom.xml` — confirm `<leader>cj`/`<leader>cx` still appear once sync completes, exactly as before this story.
  - [x] Open a `.tf` (Terraform) file and confirm `<leader>ct` is unaffected (still shows purely by filetype, no regression from the combinator change).
  - [x] Run `stylua lua` and `nvim --headless "+Lazy check" +qa`.

## Dev Notes

- **This story is independent of Stories 41.1-41.4** — it does not touch `build-sync-state.lua`, `maven.lua`, or `gradle.lua` at all. It can be implemented in any order relative to those.
- **`ready_gate` itself is not being removed or changed** — it still correctly hides `<leader>cj`/`<leader>cx` until the first sync attempt completes. This story only fixes the *separate* `matches_ft`/`matches_cond` combinator bug that makes `condition` a no-op once `ready_gate` is satisfied. Do not conflate the two mechanisms; they compose via the outer `if not (stack.ready_gate and not sync_state.ready) then` guard (~line 42), which is unaffected by this story.
- **Relevant existing code (read before editing):**
  - `lua/cumulus/core/lang-keymaps.lua:32-69` — `apply()` in full, including the `ready_gate` guard and the `matches_ft`/`matches_cond` block being fixed here.
  - `lua/cumulus/core/keymaps.lua:74-259` — both `<leader>cj` and `<leader>cx` `lang_keymaps.register({...})` calls, to confirm both declare `filetypes` and `condition`.
  - `lua/cumulus/core/keymaps.lua:261-300` — the other stacks (`<leader>ct`, `<leader>cy`, `<leader>cD`, `<leader>ck`), to confirm none declare `condition`.

### Project Structure Notes

- No new files. Single edit: `lua/cumulus/core/lang-keymaps.lua`'s `apply()`.
- 2-space indent, 120-column width per `stylua.toml`.

### References

- [Source: _bmad-output/planning-artifacts/epics.md#Epic 41: Maven/Gradle Sync Lifecycle Hardening / Story 41.5]
- [Source: lua/cumulus/core/lang-keymaps.lua]
- [Source: lua/cumulus/core/keymaps.lua]

## Git Intelligence Summary

- Commit `4137109` ("show java keybindgs after sync") is the commit that added `ready_gate` alongside the pre-existing `condition` functions on the `<leader>cj`/`<leader>cx` stacks — the OR-combinator bug predates that commit (the `condition` functions were already effectively dead-weight before `ready_gate` existed, for the same filetype-match reason), but `ready_gate`'s addition is what makes the practical impact worse (the group now reliably shows almost immediately in any session instead of only after a real sync).

## Dev Agent Record

### Agent Model Used

Claude Sonnet 5 (claude-sonnet-5), via the `dev-story` workflow.

### Debug Log References

- `grep -n "condition = function" lua/cumulus/core/keymaps.lua` — confirmed only the `<leader>cj` (~line 77) and `<leader>cx` (~line 221) stacks declare `condition`; Terraform/Ansible/Docker/Helm declare only `filetypes`, so AC #3 is satisfied by the generic fix with no special-casing.
- `luac -p lua/cumulus/core/lang-keymaps.lua` — syntax OK.
- `npx --yes @johnnymorganz/stylua-bin --check lua/cumulus/core/lang-keymaps.lua` (`stylua` itself isn't installed in this sandbox; `stylua-bin` via `npx` is the same formatter) — clean.
- `nvim --headless "+Lazy check" +qa` — exit 0.
- Headless integration probe (temp scratch script, not committed): loaded the real `cumulus.core.keymaps` + `cumulus.core.lang-keymaps` modules, called `sync_state.mark_ready()` to satisfy `ready_gate`, then opened buffers via `doautocmd FileType` against fake project directories and inspected `vim.api.nvim_buf_get_keymap`:
  - `.java` buffer in a directory with no `pom.xml`/`build.gradle` anywhere → `<leader>cjm` and `<leader>cxv` both **absent** → AC #1.
  - `.java` buffer in a directory with a `pom.xml` → both **present** → AC #2.
  - `.tf` buffer (Terraform stack, `filetypes`-only, no `condition`) in the no-build-file directory → `<leader>ctv` still **present**, confirming the AND-gating only applies to stacks declaring both `filetypes` and `condition` → AC #3.
  - Re-ran the identical probe against the pre-fix `git stash`-restored file to confirm it reproduces the bug (`<leader>cjm`/`<leader>cxv` incorrectly present with no build file) — establishes the test is meaningful, not just passing by construction.

### Completion Notes List

- Replaced the `if matches_ft or matches_cond then` combinator in `lang-keymaps.lua`'s `apply()` with a generic rule: stacks declaring **both** `filetypes` and `condition` require both to match (AND); stacks declaring only one of the two keep the prior OR-equivalent behavior (the missing side is always `false`). No stack is special-cased by name/group string.
- Verified against the real `<leader>cj`/`<leader>cx` registrations in `keymaps.lua` (both declare `filetypes` + `condition`) and the filetypes-only stacks (`<leader>ct`, `<leader>cy`, `<leader>cD`, `<leader>ck`), via headless integration probes since no unit-test framework exists for Lua in this repo (same approach used for Story 41.4).
- `ready_gate` itself is untouched — it still independently hides `<leader>cj`/`<leader>cx` until the first sync attempt completes; this story only fixes the separate matcher bug that made `condition` a no-op once `ready_gate` was satisfied.

### File List

- `lua/cumulus/core/lang-keymaps.lua` (modified — `apply()`'s `matches_ft or matches_cond` combinator replaced with a generic AND-when-both-declared / OR-otherwise rule)

## Change Log

- 2026-08-08: Implemented Story 41.5 (Tasks 1-2 complete) — fixed the `apply()` matcher combinator in `lang-keymaps.lua` so a stack declaring both `filetypes` and `condition` requires both to match, closing the gap where `<leader>cj`/`<leader>cx` appeared in any java/kotlin/groovy/xml buffer regardless of whether the project actually had a `pom.xml`/`build.gradle`. Verified via headless integration probes (including a reproduction of the pre-fix bug for confidence) plus `stylua` and `Lazy check`. Status → review.
- 2026-08-08: Post-review cleanup from `/code-review` (no correctness bugs found; 1 of 3 efficiency/cleanup findings applied here, 1 shared finding applied in `maven.lua`/`gradle.lua`, 1 declined): `lang-keymaps.lua`'s `apply()` now skips evaluating `stack.condition` when a stack declares both `filetypes` and `condition` and `matches_ft` is already `false` — previously `maven.find_pom()`/`gradle.find_gradle()` (filesystem searches) ran on every `FileType`/`BufEnter` for every buffer, including completely unrelated filetypes like Terraform, even though `visible` could never be `true` without a filetype match. Verified via a headless probe that stubs `find_pom`/`find_gradle` to count calls: 0 calls on an unrelated Terraform buffer, all 3 ACs still hold. Declined finding: extracting a shared `maven.lua`/`gradle.lua` timer helper module — a real duplication observation, but a structural refactor beyond this story's scope; flagged to the user rather than applied unprompted.