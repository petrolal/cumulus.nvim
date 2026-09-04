---
title: 'Asynchronous LSP Operations Resilience'
type: 'feature'
created: '2026-09-03'
status: 'review'
baseline_commit: '05e27f1a27fbd858c203caa22fce63ef6250ac1e'
review_loop_iteration: 0
context: []
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** The current LSP operations are synchronous and can block the editor, leading to degraded responsiveness during heavy load.

**Approach:** Introduce asynchronous handling for LSP requests, ensuring non-blocking UI updates and robust error handling.

## Boundaries & Constraints

**Always:** Preserve existing LSP functionality; maintain compatibility with current LSP servers.

**Ask First:** Choose between using native Neovim async APIs vs external async library.

**Never:** Replace LSP core protocol; avoid dropping support for any language server.

</frozen-after-approval>

## Code Map

- `lua/tetravim/util/lsp_async.lua` -- `request_all_async` fan-out (non-blocking, `vim.schedule` callback), `request_all_sync` retained for inline callers; `get_clients`/`get_active_clients` version fallback; empty-client and refused-request paths still fire the callback
- `lua/tetravim/util/lsp_resilience.lua` -- `apply_memory_limit` (pure: injects `-Xmx`/`-Xms`, `--jvm-arg=` form for the JDTLS launcher, raw form for a bare `java`), `note_exit`/`reset` (bounded restart budget: `MAX_RESTARTS` per `WINDOW_S`), `make_on_exit` (crash -> WARN + `restart_fn`, budget exhausted -> ERROR + give up), `health`
- `ftplugin/java.lua` -- applies `apply_memory_limit` to the JDTLS `cmd` (`-Xmx2g`/`-Xms512m`), wires `resilience.make_on_exit("jdtls", ...)` as `config.on_exit`, `resilience.reset("jdtls")` on clean attach
- `lua/tetravim/util/refactor.lua` -- project-wide refactor dispatch goes through `lsp_async`
- `lua/tetravim/health.lua` -- "TetraVim Asynchronous LSP & Resilience (Story 5.1)" section
- `lua/tetravim/tests/lsp_async_spec.lua`, `lua/tetravim/tests/lsp_resilience_spec.lua`, `scripts/validate-5.sh`

## Tasks & Acceptance

- [x] `util/lsp_async.lua` -- non-blocking `request_all_async` fan-out with a `vim.schedule` callback; version-tolerant client discovery; callback never stranded on no-client / refused-request
- [x] `util/lsp_resilience.lua` -- pure `apply_memory_limit` heap-flag injection + bounded `note_exit`/`reset`/`make_on_exit` auto-restart
- [x] `ftplugin/java.lua` -- bound the JDTLS JVM heap and auto-restart (bounded) on crash, resetting the window on a clean attach
- [x] `util/refactor.lua` -- dispatch the project-wide reference scan through the async wrapper
- [x] health section + `lsp_async_spec.lua` / `lsp_resilience_spec.lua` unit tests + `validate-5.sh` smoke test

**Acceptance Criteria:**
- Given a heavy LSP request, when triggered, then the UI remains responsive and the request completes without blocking.
- Given a language server (JDTLS) is launched, when it starts, then its JVM heap is capped (`-Xmx2g`) so indexing a large monorepo cannot OOM the host.
- Given an LSP server process crashes, when it exits non-zero/on a signal, then it is auto-restarted up to `MAX_RESTARTS` times within `WINDOW_S`, after which TetraVim stops and surfaces one error pointing at `:LspLog`.

## Verification

- `bash scripts/validate-5.sh` -- heap-flag injection, the restart budget, non-blocking/non-stranding fan-out, and the Story 5.1 health section
- `:checkhealth tetravim` -- "Asynchronous LSP & Resilience (Story 5.1)" section reports PASS
- Manual test: open a large Java file, trigger a project-wide rename, observe the UI stays interactive; `kill -9` the JDTLS process and confirm it auto-restarts (and that a 4th crash inside the window gives up with an error).
