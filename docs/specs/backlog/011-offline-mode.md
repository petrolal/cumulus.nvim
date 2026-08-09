# Specification: SPEC-011 - Gradle/Maven Offline Mode Detection

## Metadata
- **Spec ID**: SPEC-011
- **Title**: Gradle/Maven Offline Mode Detection
- **Status**: BACKLOG
- **Author**: AI Systems Architect
- **Target Files/Paths**:
  - `lua/cumulus/util/maven.lua` (extends)
  - `lua/cumulus/util/gradle.lua` (extends)

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

- [ ] Extend `lua/cumulus/util/maven.lua`:
  - [ ] Implement `check_network()` → returns bool (network reachable)
  - [ ] On build start, check network
  - [ ] If unreachable, suggest offline mode in notification
  - [ ] Auto-add `-o` flag to Maven command
- [ ] Extend `lua/cumulus/util/gradle.lua`: Same for `--offline`
- [ ] Add statusline component showing offline status
- [ ] Add `<leader>cjo` keymap to toggle offline mode setting

---

## Verification Commands

```bash
bash scripts/validate.sh
luac -p lua/cumulus/util/maven.lua lua/cumulus/util/gradle.lua
nvim --headless --startuptime /tmp/nvim-startuptime.log +qa && grep "NVIM STARTED" /tmp/nvim-startuptime.log
```

### Acceptance Criteria
- [ ] Network check works (quick test on offline network)
- [ ] Offline badge appears when network unavailable
- [ ] `<leader>cjo` toggles offline mode
- [ ] Build uses `-o` or `--offline` when offline mode is enabled

---

## Summary

Save hours of debugging build hangs on restricted networks by detecting and suggesting offline mode automatically.
