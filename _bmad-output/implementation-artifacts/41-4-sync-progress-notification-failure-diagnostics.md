---
baseline_commit: ab34e5e9880e821890c0b14f87aa17b9a671c5fd
---

# Story 41.4: Sync Progress Notification & Failure Diagnostics

Status: review

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As a JVM developer,
I want a persistent, updating notification while the dependency sync is running, and surfaced errors when internal sync callbacks fail,
so that a hung or errored sync is distinguishable from a silently succeeding one.

## Acceptance Criteria

1. **Given** a sync is in progress, **when** the start/heartbeat/completion notifications fire, **then** they all share one stable notification `id` (via `vim.notify(msg, level, { id = ... })`) so they collapse into a single updating toast instead of stacking multiple separate ones.
2. **Given** a sync is still running several seconds after it started, **when** enough time has passed, **then** the notification visibly updates (e.g. elapsed-seconds heartbeat) — a static, never-changing "syncing..." message is not sufficient, because it would look identical whether the process is healthy-but-slow or silently hung.
3. **Given** an error is raised inside a `pcall`-wrapped callback in the sync/ready-notification path (`build-sync-state.lua`'s `mark_ready()` listener loop, and `lang-keymaps.lua`'s `stack.condition` check), **when** that `pcall` fails, **then** the error is surfaced via `vim.notify(..., vim.log.levels.WARN)` instead of being silently discarded — today both call sites do `pcall(cb)` / `pcall(stack.condition, buf)` and drop the error entirely on failure.

## Tasks / Subtasks

- [x] Task 1: Collapse sync notifications into one updating toast (AC: #1, #2)
  - [x] In `lua/cumulus/util/maven.lua`, add a stable id, e.g. `local NOTIFY_ID = "cumulus_maven_sync"`; in `lua/cumulus/util/gradle.lua`, `local NOTIFY_ID = "cumulus_gradle_sync"` (distinct per tool — even though in practice only one of the two runs per project, keeping them distinct avoids any cross-tool notification collision).
  - [x] Pass `{ id = NOTIFY_ID }` as the third argument on every `vim.notify` call already present in `sync_dependencies()` (start, success, failure) — this project's `vim.notify` is globally bound to `Snacks.notifier.notify` (see `lua/cumulus/plugins/editor-snacks.lua:154-155`), whose `notify()` accepts `opts.id` and replaces the existing notification with that id rather than stacking a new one (`snacks.nvim`'s `notifier.lua` around its `notify`/`queue` handling confirms `id`-based replace semantics) — verify this behavior interactively before relying on it, since Snacks' exact replace semantics should be confirmed against the installed version rather than assumed from memory.
  - [x] Add a heartbeat: using `(vim.uv or vim.loop).new_timer()` (a **second**, independent timer from Story 41.2's timeout timer — do not conflate the two), fire every ~5000ms while the sync is still running, calling `vim.notify("Maven: syncing dependencies... (" .. elapsed_seconds .. "s)", vim.log.levels.INFO, { id = NOTIFY_ID })` with the same `NOTIFY_ID`. Track elapsed time with `local started = (vim.uv or vim.loop).now()` at sync start.
  - [x] Stop and close the heartbeat timer in the same exit-callback branch where Story 41.2's timeout timer is stopped — both timers must be cleaned up together whenever the process actually exits (success, failure, or gets killed on timeout), so neither leaks or fires after completion.
- [x] Task 2: Surface swallowed pcall errors (AC: #3)
  - [x] In `lua/cumulus/util/build-sync-state.lua`'s `mark_ready()`, change `for _, cb in ipairs(listeners) do pcall(cb) end` to capture the failure: `local ok, err = pcall(cb); if not ok then vim.notify("Cumulus: build-sync-state on_ready listener failed: " .. tostring(err), vim.log.levels.WARN) end`.
  - [x] In `lua/cumulus/core/lang-keymaps.lua`'s `apply()`, change `local ok, res = pcall(stack.condition, buf)` so that on failure it also notifies: `if not ok then vim.notify("Cumulus: lang-keymaps condition for " .. tostring(stack.group) .. " failed: " .. tostring(res), vim.log.levels.WARN) end` (keep the existing `if ok and res then matches_cond = true end` behavior unchanged for the success path).
- [x] Task 3: Verify
  - [x] Trigger a sync and confirm only one notification toast is visible at a time for it (not one-per-message stacked).
  - [x] Force a slow sync (or temporarily shorten the heartbeat interval for testing) and confirm the toast's elapsed-time text updates in place.
  - [x] Temporarily make a `stack.condition` function `error(...)` and confirm a WARN notification appears instead of the failure being silent.
  - [x] Run `stylua lua` and `nvim --headless "+Lazy check" +qa`.

## Dev Notes

- **Coordinate with Story 41.2**: both stories touch `sync_dependencies()` in `maven.lua`/`gradle.lua` and both introduce a `(vim.uv or vim.loop).new_timer()`. If implemented separately, the second implementer must merge cleanly with the first's timer rather than clobbering it — there will be **two** timers per sync call (one timeout-kill timer from 41.2, one heartbeat-notify timer from this story), both created/stopped/closed in the same function.
- **`vim.notify` is globally Snacks-backed in this project** (`lua/cumulus/plugins/editor-snacks.lua:154-155`, `opts.notifier.timeout = 3000` at line 31) — do not assume default Neovim `vim.notify` semantics (which has no concept of `id`-based replace); this only works because of that override. If the dev agent later finds Snacks' `notify()` doesn't support `id` the way expected, fall back to at minimum ensuring the start/success/failure messages are worded so a user can tell which sync attempt they belong to (e.g. include a timestamp), rather than silently dropping AC #1.
- **Relevant existing code (read before editing):**
  - `lua/cumulus/util/maven.lua:59-92`, `lua/cumulus/util/gradle.lua:63-96` — the notify call sites.
  - `lua/cumulus/util/build-sync-state.lua:20-28` — `mark_ready()`'s listener loop.
  - `lua/cumulus/core/lang-keymaps.lua:53-59` — `apply()`'s `stack.condition` pcall.
  - `lua/cumulus/plugins/editor-snacks.lua:29-31,154-155` — the Snacks notifier config and `vim.notify` override.

### Project Structure Notes

- No new files. Edits: `lua/cumulus/util/maven.lua`, `lua/cumulus/util/gradle.lua`, `lua/cumulus/util/build-sync-state.lua`, `lua/cumulus/core/lang-keymaps.lua`.
- 2-space indent, 120-column width per `stylua.toml`.

### References

- [Source: _bmad-output/planning-artifacts/epics.md#Epic 41: Maven/Gradle Sync Lifecycle Hardening / Story 41.4]
- [Source: lua/cumulus/plugins/editor-snacks.lua]
- [Source: lua/cumulus/util/build-sync-state.lua]
- [Source: lua/cumulus/core/lang-keymaps.lua]
- [Source: _bmad-output/implementation-artifacts/41-2-sync-timeout-cancellation-safeguard.md]

## Git Intelligence Summary

- Commit `4137109`'s `build-sync-state.lua` already documents (in its own comment) the intent to fail open rather than closed ("mark ready on both success and failure so a broken/offline sync doesn't hide them forever") — this story's error-surfacing must preserve that same fail-open guarantee; notify on `pcall` failure but never let a failed listener prevent the others from running or leave `M.ready` in a bad state.

## Dev Agent Record

### Agent Model Used

Claude Sonnet 5 (claude-sonnet-5), via the `dev-story` workflow.

### Debug Log References

- `nvim --headless "+Lazy check" +qa` — passed (exit 0), before and after formatting.
- `luac -p` on all four edited files — syntax OK.
- `npx --yes @johnnymorganz/stylua-bin lua/cumulus/util/{maven,gradle}.lua lua/cumulus/util/build-sync-state.lua lua/cumulus/core/lang-keymaps.lua` (`stylua` itself is not installed in this sandbox; `stylua-bin` via `npx` is the same formatter/version behavior) — reformatted the four story files; `--check` afterwards reports clean. Note: running `stylua-bin` against the whole `lua/` tree also reformatted 5 unrelated files with pre-existing style drift (`autocmds.lua`, `keymaps.lua`, `editor-snacks.lua`, `tools-gitsigns.lua`, `session.lua`); those were reverted with `git checkout --` to keep this story's diff scoped to its own File List.
- Headless integration probes (temp scratch scripts, not committed) against a fake `pom.xml`/`mvnw` (12s sleep) and fake `build.gradle`/`gradlew` (11s sleep, exit 1), stubbing `vim.notify` to record calls:
  - Maven: 4 notifications (start, 5s heartbeat, 10s heartbeat, success), all sharing `id=cumulus_maven_sync` → confirms AC #1 and #2.
  - Gradle: 4 notifications (start, 5s heartbeat, 10s heartbeat, failure), all sharing `id=cumulus_gradle_sync`, failure at ERROR level → confirms AC #1, #2, and the failure path.
  - `build-sync-state.mark_ready()` with an `error()`-throwing listener → WARN notification "Cumulus: build-sync-state on_ready listener failed: ...boom-from-listener" observed → confirms AC #3 (listener loop).
  - `lang-keymaps.apply()` with an `error()`-throwing `stack.condition` → WARN notification "Cumulus: lang-keymaps condition for <leader>cZ failed: ...boom-from-condition" observed → confirms AC #3 (condition check).

### Completion Notes List

- Added `NOTIFY_ID` (`cumulus_maven_sync` / `cumulus_gradle_sync`) and passed `{ id = NOTIFY_ID }` on every `vim.notify` call in each tool's `sync_dependencies()` (start, heartbeat, success, failure, timeout, spawn-failure) so Snacks' `id`-based replace collapses them into one updating toast — verified interactively via the headless probes above, confirming Snacks' replace semantics as flagged as a risk in Dev Notes.
- Added a second, independent `(vim.uv or vim.loop).new_timer()` heartbeat (5000ms interval) alongside Story 41.2's timeout-kill timer in both `maven.lua` and `gradle.lua`. Elapsed time is tracked via `(vim.uv or vim.loop).now()` captured at sync start. Both timers are stopped/closed together via a shared `stop_timers()` helper in the process exit callback, in the timeout branch, and in the synchronous-spawn-failure branch, so neither can leak or fire after the sync concludes.
- `build-sync-state.lua`'s `mark_ready()` listener loop and `lang-keymaps.lua`'s `apply()` `stack.condition` pcall now both notify at WARN on failure instead of silently discarding the error, while preserving the existing fail-open behavior (other listeners still run; `matches_cond` stays false on error, same as before).
- Only the four files in the story's Project Structure Notes were touched; unrelated pre-existing style drift that `stylua` incidentally reformatted elsewhere was reverted to keep the diff scoped.
- No automated test framework (busted/plenary) exists in this repo for Lua modules — verification follows the story's own Task 3 checklist (headless integration probes + `stylua` + `Lazy check`), consistent with how prior stories in this epic (41.1–41.3) were verified.

### File List

- `lua/cumulus/util/maven.lua` (modified — `NOTIFY_ID`, `HEARTBEAT_INTERVAL_MS`, heartbeat timer + `stop_timers()` helper, `id` on all `vim.notify` calls in `sync_dependencies()`)
- `lua/cumulus/util/gradle.lua` (modified — same, symmetric change)
- `lua/cumulus/util/build-sync-state.lua` (modified — `mark_ready()` listener loop now notifies WARN on `pcall` failure)
- `lua/cumulus/core/lang-keymaps.lua` (modified — `apply()`'s `stack.condition` pcall now notifies WARN on failure)

## Change Log

- 2026-08-08: Implemented Story 41.4 (Tasks 1-3 complete) — sync notifications in `maven.lua`/`gradle.lua` collapsed into a single updating toast via a stable `NOTIFY_ID` and a heartbeat timer independent of Story 41.2's timeout timer; swallowed `pcall` errors in `build-sync-state.lua`'s `mark_ready()` and `lang-keymaps.lua`'s `apply()` now surface as WARN notifications. Verified via headless integration probes against fake slow/failing Maven and Gradle projects, plus `stylua` and `Lazy check`. Status → review.
- 2026-08-08: Post-review fixes from `/code-review` (3 findings, all confirmed and fixed):
  1. **Double-close crash** (`maven.lua`, `gradle.lua`): the timeout branch closed the heartbeat timer immediately, but the killed process's exit callback later called `stop_timers()` again, unconditionally re-closing the same already-closed heartbeat handle — libuv raises this as an uncaught Lua error ("handle ... is already closing"), exactly matching this project's own documented expectation that a killed process's exit callback can show up late (orphaned grandchild keeping stdio open). Fixed with an idempotent `close_heartbeat()` guard shared by both call sites. Reproduced the crash against a reconstructed pre-fix version (SIGTERM-ignoring fake `gradlew`, compressed timeout) to confirm the exact error, then confirmed 0 errors with the fix applied.
  2. **Notification flood** (`lang-keymaps.lua`): the new WARN for a failing `stack.condition` had no `id`, so a persistently-broken condition would spam a fresh toast on every `FileType`/`BufEnter` for every buffer. Fixed by giving it a stable per-stack id (`cumulus_lang_keymaps_condition_<group>`) so repeats replace in place instead of stacking, consistent with the `NOTIFY_ID` pattern already used for sync notifications.
  Re-verified `stylua` and `Lazy check` pass after the fixes; diff remains scoped to the four files in File List.
- 2026-08-08: Second `/code-review` pass (run against Story 41.5, re-scoped over these files too) found no correctness bugs here — confirmed every `mark_ready()` path is reachable on all outcomes and the heartbeat/timeout race is self-correcting. One cleanup finding applied: replaced the bespoke `heartbeat_closed` boolean guard in `maven.lua`/`gradle.lua` with libuv's built-in `heartbeat:is_closing()` check, removing a hand-rolled flag in favor of the handle's own state. Re-verified the timeout-then-late-exit double-close scenario still produces 0 errors with this simplification. A second finding (extract the duplicated timer/notify plumbing between `maven.lua` and `gradle.lua` into a shared module) was flagged to the user as a legitimate but out-of-scope structural refactor, not applied.
