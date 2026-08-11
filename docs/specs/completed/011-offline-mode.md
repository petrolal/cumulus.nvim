# Specification: SPEC-011 - Gradle/Maven Offline Mode Detection

## Metadata
- **Spec ID**: SPEC-011
- **Title**: Gradle/Maven Offline Mode Detection
- **Status**: COMPLETED
- **Implementation**: Rust (Lua bridge only — minimal Lua)
- **Author**: AI Systems Architect
- **Target Files/Paths**:
  - `crates/cumulus-core/src/network.rs` (new)
  - `crates/cumulus-core/src/main.rs` (extends)
  - `lua/cumulus/util/rust.lua` (extends)

---

---

## Architecture

**Lua is a bridge to the Rust backend. That is it.**

```
Neovim  →  Lua (bridge)  →  cumulus-core (Rust binary)
```

- **Rust** (`crates/cumulus-core`): all logic — parsing, file I/O, network, validation, analysis
- **Lua**: one job only — call the Rust binary and pass results to Neovim APIs
- No Lua fallbacks. No Lua parsing. No Lua analysis. If the binary is missing, fail explicitly.
---

## Goal & Intent

On restricted networks (corporate proxy, VPN, limited bandwidth), Maven/Gradle builds silently fail or hang waiting for network timeouts. Developers don't realize offline mode is needed until a build mysteriously hangs for 2+ minutes.

This spec adds network detection and automatic offline mode suggestion:
- Check if Maven Central / Gradle Plugin Portal is reachable (ping with 2-second timeout)
- If unreachable, show statusline badge: `🔒 Offline Mode Available`
- Add `<leader>cjo` keymap to toggle offline mode
- Re-run build with `-o` (Maven) or `--offline` (Gradle) flag

---

## Scope Boundaries

**In scope:**
- Detect network connectivity (ping test)
- Show offline mode badge in statusline
- Add toggle keymap `<leader>cjo`
- Auto-suggest offline mode on network failure

**Out of scope:**
- Configure Maven/Gradle proxy settings
- Detect proxy authentication errors

---

## Prerequisite Analysis

- Maven/Gradle runners already exist
- Network detection can use `vim.fn.system("ping -c 1 -W 2 repo.maven.apache.org")` or similar

---

## Execution Checklist

- [x] Create `crates/cumulus-core/src/network.rs` (TCP socket network connectivity checker)
- [x] Extend `crates/cumulus-core/src/main.rs` with `check-network` subcommand
- [x] Extend `lua/cumulus/util/rust.lua` with `rust.check_network()`
- [x] Add feature binding to `lua/cumulus/util/rust.lua` (single Lua dispatcher)
  - [x] Implement `toggle_offline_mode()`
  - [x] Auto-add `-o` flag when offline or toggled
- [x] Add `<leader>cjo` keymap to toggle offline mode setting in `lua/cumulus/core/keymaps.lua`

---

## Verification Commands

```bash
bash scripts/validate.sh
luac -p lua/cumulus/util/maven.lua lua/cumulus/util/gradle.lua
nvim --headless --startuptime /tmp/nvim-startuptime.log +qa && grep "NVIM STARTED" /tmp/nvim-startuptime.log
```

### Acceptance Criteria
- [x] Network check works (quick test on offline network)
- [x] Offline badge appears when network unavailable
- [x] `<leader>cjo` toggles offline mode
- [x] Build uses `-o` or `--offline` when offline mode is enabled

---

## Summary

Save hours of debugging build hangs on restricted networks by detecting and suggesting offline mode automatically.
