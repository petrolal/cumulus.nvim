---
title: 'Asynchronous LSP Operations Resilience'
type: 'feature'
created: '2026-09-03'
status: 'in-progress'
baseline_commit: '05e27f1a27fbd858c203caa22fce63ef6250ac1e'
review_loop_iteration: 0
context: []
---

<!-- Target: 900–1300 tokens. Above 1600 = high risk of context rot.
     Never over-specify "how" — use boundaries + examples.
     Cohesive cross-layer stories (DB+BE+UI) stay in ONE file.
     IMPORTANT: Remove all HTML comments when filling this template. -->

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

- `lua/tetravim/util/lsp_async.lua` -- core async wrappers for LSP requests
- `lua/tetravim/core/lsp.lua` -- integration point for async handling

## Tasks & Acceptance

- [ ] `lua/tetravim/util/lsp_async.lua` -- implement async request wrapper -- to enable non-blocking calls
- [ ] `lua/tetravim/core/lsp.lua` -- modify request handling to use async wrapper -- ensure backward compatibility

**Acceptance Criteria:**
- Given a heavy LSP request, when triggered, then the UI remains responsive and the request completes without blocking.

## Verification

- `:checkhealth tetravim` -- should report async LSP health check PASS
- Manual test: open a large Java file, trigger a refactor request, observe UI remains interactive.
