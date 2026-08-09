# Specification: SPEC-005 - JDTLS Project Sync Health Check

## Metadata
- **Spec ID**: SPEC-005
- **Title**: JDTLS Project Sync Health Check
- **Status**: BACKLOG
- **Author**: AI Systems Architect
- **Target Files/Paths**:
  - `lua/cumulus/util/jdtls-sync.lua` (new)
  - `lua/cumulus/core/keymaps.lua` (extends)
  - `lua/cumulus/core/autocmds.lua` (extends)

---

## Goal & Intent

JDTLS maintains an internal classpath cache based on `pom.xml`/`build.gradle` state at startup. When a developer adds or updates a Maven/Gradle dependency, JDTLS continues to use the stale classpath until manually restarted. This causes autocomplete to miss new imports, type inference to fail, and diagnostics to reference outdated APIs—all without visual indication that JDTLS is out of sync.

This spec implements **automatic sync detection**: on buffer enter in a Java project, check if `pom.xml`/`build.gradle` has been modified since JDTLS started. If yes, display a statusline badge (`🔄 Resync needed`) and provide a one-keypress restart via `<leader>cjs` (jvm: sync). This closes the "invisible staleness" gap and aligns with IntelliJ's transparency about classpath state.

---

## Scope Boundaries

**In scope:**
- Detect `pom.xml`/`build.gradle` modification time vs. JDTLS session start time
- Show statusline indicator when sync is needed
- Add keymap `<leader>cjs` to run Maven/Gradle dependency resolution + JDTLS restart
- Clear indicator on successful sync

**Out of scope:**
- Auto-restart JDTLS without user prompt (requires explicit action for safety)
- Modify frozen DevOps files
- Handle Gradle build cache invalidation beyond `--refresh-dependencies`
- Track individual dependency updates; check only filesystem mtime

---

## Prerequisite Analysis

- JDTLS is launched via `ftplugin/java.lua` on first Java buffer entry; start time can be captured via `os.time()` in that file
- Statusline: snacks.nvim already provides a way to extend statusline components
- `<leader>cjs` can be added to the Java `lang_keymaps` stack in `lua/cumulus/core/keymaps.lua`
- Maven sync: `mvn dependency:resolve -q` is already used in `maven.lua`; integrate output parsing
- Gradle sync: `./gradlew dependencies` or `./gradlew --refresh-dependencies`

---

## Constraints & Guardrails

1. **DevOps Immutability Guardrail**: No frozen files modified
2. **Zero Free Files Policy**: All code in `lua/cumulus/util/` and keymaps in `lua/cumulus/core/`
3. **Performance**: Mtime check is <1ms; sync is user-triggered, not automatic
4. **Startup Budget**: No impact; run on buffer enter only

---

## Execution Checklist

- [ ] Create `lua/cumulus/util/jdtls-sync.lua`:
  - [ ] Implement `is_sync_needed()` → returns bool by comparing pom.xml/build.gradle mtime vs. jdtls.start_time (stored globally)
  - [ ] Implement `sync_and_restart()` → run Maven/Gradle sync, wait, restart JDTLS
  - [ ] Implement `statusline_component()` → returns statusline widget text/highlight
- [ ] Extend `ftplugin/java.lua`: Store JDTLS start time in a module-level variable
- [ ] Extend `lua/cumulus/core/keymaps.lua`: Add `<leader>cjs` to Java lang_keymaps stack
- [ ] Extend `lua/cumulus/core/autocmds.lua`: Wire statusline update on BufEnter for Java files
- [ ] Test: Add dependency to pom.xml, verify badge appears + `<leader>cjs` syncs

---

## Verification Commands

```bash
bash scripts/validate.sh
luac -p lua/cumulus/util/jdtls-sync.lua lua/cumulus/core/keymaps.lua lua/cumulus/core/autocmds.lua
nvim --headless "+Lazy! sync" +qa
nvim --headless --startuptime /tmp/nvim-startuptime.log +qa && grep "NVIM STARTED" /tmp/nvim-startuptime.log
```

### Acceptance Criteria
- [ ] Statusline badge appears when pom.xml/build.gradle is modified
- [ ] `<leader>cjs` runs Maven/Gradle sync and restarts JDTLS
- [ ] Badge clears after successful sync
- [ ] No startup time impact (<50ms maintained)

---

## Summary

Eliminates the "invisible classpath staleness" trap by providing visual feedback and one-keypress sync. Critical for smooth iterative development in multi-module projects.
