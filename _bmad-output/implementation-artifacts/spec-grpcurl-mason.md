---
title: 'Ensure grpcurl package is installed via Mason'
type: 'feature'
created: '2026-09-03'
status: 'draft'
review_loop_iteration: 0
context: []
---

<!-- Intent -->

**Problem:** The Neovim distribution fails to start because Mason cannot find the `grpcurl` package during automatic tool installation.

**Approach:** Update Mason configuration to ensure the registry is up‑to‑date before the installer runs and verify that `grpcurl` is installed on startup.

## Boundaries & Constraints

**Always:** Use only existing Mason and mason-tool-installer plugins; do not introduce external package managers.
**Ask First:** If the user wants to disable automatic registry updates.
**Never:** Add custom shell scripts outside the plugin system.

## I/O & Edge‑Case Matrix

| Scenario | Input / State | Expected Output | Error Handling |
|----------|--------------|----------------|----------------|
| HAPPY_PATH | Neovim starts with a fresh config | `grpcurl` binary appears in `~/.local/share/nvim/mason/bin/` and no error is thrown | N/A |
| REGISTRY_STALE | Registry is outdated | `MasonUpdate` runs automatically, then `grpcurl` installs | If update fails, notify user via `vim.notify`.
| INSTALL_FAIL | `grpcurl` fails to install (network error) | Notify user and leave fallback to manual `:MasonInstall grpcurl` | Show error message from Mason.

## Code Map

- `lua/tetravim/plugins/tools-mason.lua` – `ensure_installed` already includes `grpcurl`.
- `lua/tetravim/core/mason_init.lua` – new file to run `MasonUpdate` on VimEnter before mason‑tool‑installer triggers.

## Tasks & Acceptance

- [ ] Create `lua/tetravim/core/mason_init.lua` with an autocommand that schedules `MasonUpdate` on VimEnter.
- [ ] Ensure the new file is required in `lua/tetravim/core/init.lua`.
- [ ] Verify that after a fresh start, `:Mason` shows `grpcurl` installed.

## Acceptance Criteria

- Given a fresh clone and no `grpcurl` binary, when Neovim starts, then `grpcurl` is installed automatically and no startup error occurs.
- Given a network failure during registry update, then Vim notifies the user and does not crash.

## Spec Change Log

*(empty)*

## Design Notes

No additional design complexity required.

## Verification

- `Mason` UI shows `grpcurl` with a green check.
- Running `~/.local/share/nvim/mason/bin/grpcurl -version` prints a version.

**Commands:**
- `:MasonUpdate`
- `:MasonToolsInstall`
