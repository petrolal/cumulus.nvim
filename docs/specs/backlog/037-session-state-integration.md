# Specification: SPEC-037 - Session State Window Cleanup Integration

## Metadata
- **Spec ID**: SPEC-037
- **Title**: Session State Window Cleanup Integration (Lua Loop → Rust)
- **Status**: BACKLOG
- **Author**: AI Systems Architect
- **Target Files/Paths**:
  - `crates/cumulus-core/src/session_cleaner.rs` (reuse from SPEC-030)
  - `lua/cumulus/util/session.lua` (extends)
  - `lua/cumulus/core/autocmds.lua` (integrates)

- **Implementation**: Rust (SPEC-030 prerequisite) + Lua bridge
- **Prerequisite**: SPEC-031 (error standardization), SPEC-030 (session-sanitize command)

---

## Architecture

**Integrate SPEC-030's Rust session-sanitize command with session.lua's window cleanup loops. Replace Lua buffer inspection with Rust .vim script parsing.**

```
PersistenceSavePre Autocmd  →  Lua checks conditions  →  Rust sanitize-session  →  Cleaned .vim  →  persistence.nvim save
```

- **Rust Engine (`crates/cumulus-core`)**: `session-sanitize --file <path>` (SPEC-030) parses .vim session file and removes:
  1. `snacks_dashboard` buffers (ephemeral landing screen).
  2. `snacks_picker` floating windows (ephemeral explorer).
  3. Unnamed blank buffers (`[No Name]` with no content).
  4. Invalid `badd`/window creation commands.
  Outputs cleaned .vim file atomically.
- **Lua Bridge (`lua/cumulus/util/session.lua`)**: Replace `close_dashboard_windows()` and `close_blank_windows()` loops with call to `rust.sanitize_session(session_file)`. Simpler, faster, more reliable.
- **Autocmd Integration (`lua/cumulus/core/autocmds.lua`)**: Wire PersistenceSavePre to call sanitizer before session save.

---

## Goal & Intent

Current `session.lua` (139 LOC) manually iterates Neovim's window list at save time:
```lua
for _, win in ipairs(vim.api.nvim_list_wins()) do
  -- Inspect buffers, check filetype == "snacks_dashboard"
  -- Close if match
end
```

This approach has three problems:
1. **Racy**: Window state can change between inspection and close.
2. **Incomplete**: Handles only Neovim's current window list; misses buffers already serialized in .vim session by `mksession`.
3. **Brittle**: Snacks plugin changes (e.g., new ephemeral buffer type) require code updates.

SPEC-030 (Rust `session-sanitize`) instead parses the actual .vim session file **after** `mksession` creates it but **before** persistence.nvim saves it. This catches all ephemeral windows, not just those in current Neovim state.

SPEC-037 integrates SPEC-030's Rust sanitizer with session.lua's lifecycle, replacing the manual window loops entirely.

---

## Scope Boundaries

**In scope:**
- Replace `close_dashboard_windows()` + `close_blank_windows()` Lua loops with single `rust.sanitize_session()` call.
- Wire `PersistenceSavePre` autocmd to invoke Rust sanitizer on generated .vim file.
- Remove ~50 lines of buffer inspection logic from `session.lua`.
- Optional: Add config option to disable session sanitization (for debugging).

**Out of scope:**
- Modifying `persistence.nvim` core logic.
- Modifying frozen DevOps specs.
- SPEC-030's Rust implementation (session_cleaner.rs is SPEC-030 scope).
- Snacks plugin customization (we just detect and clean its buffers).

---

## Prerequisite Analysis

- `crates/cumulus-core/src/session_cleaner.rs` (SPEC-030) must exist with `session-sanitize` subcommand before this spec can integrate.
- `lua/cumulus/util/session.lua` currently has manual `close_dashboard_windows()` (lines 27–34) and `close_blank_windows()` (lines 47–63). These ~40 lines become 2–3 lines post-SPEC-037.
- `lua/cumulus/core/autocmds.lua` already handles project-wide autocmds; this spec extends it.
- SPEC-031 (error standardization) ensures Rust errors are visible to Lua.

---

## Constraints & Guardrails

1. **Rust-First Directive**: Window cleanup logic MUST be in Rust (SPEC-030's session_cleaner.rs). Lua only calls `rust.sanitize_session()` and handles errors.
2. **DevOps Guardrail**: Never touch frozen specs.
3. **Session File Safety**: Rust sanitizer must write atomically (temp file + rename) to prevent session corruption.
4. **Backward Compatibility**: If Rust binary missing or sanitizer fails, gracefully skip (warn in vim.notify, do not block save).
5. **Zero Free Files**: All Rust code in `crates/cumulus-core/src/session_cleaner.rs` (SPEC-030).

---

## Execution Checklist

- [ ] **Task 1: Confirm SPEC-030 Implementation**
  - Verify `crates/cumulus-core/src/session_cleaner.rs` exists with `session-sanitize` subcommand.
  - Verify `cumulus-core session-sanitize --file /path/to/session.vim` works end-to-end.
  - Verify output is valid .vim syntax.

- [ ] **Task 2: Add Lua Bridge (`lua/cumulus/util/rust.lua`)**
  - Add IPC wrapper:
    ```lua
    function M.sanitize_session(session_file)
      return call_rust({ "session-sanitize", "--file", session_file })
    end
    ```

- [ ] **Task 3: Refactor `lua/cumulus/util/session.lua`**
  - Remove `close_dashboard_windows()` function (lines 27–34).
  - Remove `close_blank_windows()` function (lines 47–63).
  - Remove `is_blank_buf()` helper (lines 39–45).
  - Add new function:
    ```lua
    local function sanitize_session_file()
      local session_file = vim.fn.stdpath("state") .. "/cumulus_session.vim"
      if vim.fn.filereadable(session_file) == 0 then
        return
      end
      local rust = require("cumulus.util.rust")
      local ok = rust.sanitize_session(session_file)
      if not ok then
        vim.notify("[cumulus] Session sanitization failed; saving as-is", vim.log.levels.WARN)
      end
    end
    ```
  - Keep `remember_explorer_state()` and session marker logic (unchanged).
  - Update `M.setup()` or setup autocmd to call `sanitize_session_file()` on PersistenceSavePre.

- [ ] **Task 4: Wire Autocmd in `lua/cumulus/core/autocmds.lua`**
  - Add autocmd for `PersistenceSavePre`:
    ```lua
    vim.api.nvim_create_autocmd("User", {
      pattern = "PersistenceSavePre",
      callback = function()
        require("cumulus.util.session").sanitize_session_file()
      end,
    })
    ```

- [ ] **Task 5: Add Config Option**
  - Add optional config flag in `lua/cumulus/core/config.lua` or `health.lua`:
    ```lua
    local cumulus_session_sanitize = vim.g.cumulus_session_sanitize ~= false  -- default: true
    ```
  - Respect flag in `sanitize_session_file()`:
    ```lua
    if not cumulus_session_sanitize then
      return
    end
    ```

- [ ] **Task 6: Test & Verify**
  - Open Neovim, create a multi-tab session with Snacks dashboard.
  - Trigger session save (via persistence.nvim or manual `:mksession`).
  - Verify Snacks dashboard buffer not present in reloaded session.
  - Verify blank `[No Name]` tabs not persisted.
  - Verify error logging if Rust binary missing.

---

## Verification Commands & Acceptance Criteria

```bash
# Verify SPEC-030 Rust implementation
cargo test --manifest-path crates/cumulus-core/Cargo.toml --lib session_cleaner
cargo build --manifest-path crates/cumulus-core/Cargo.toml --release

# Verify Lua syntax
luac -p lua/cumulus/util/session.lua lua/cumulus/core/autocmds.lua

# Headless verification
nvim --headless "+checkhealth cumulus" +qa

# Full validation
bash scripts/validate.sh
```

### Acceptance Criteria
- [ ] `cargo test` passes for session_cleaner (SPEC-030).
- [ ] `session-sanitize --file /tmp/test.vim` removes `snacks_dashboard` buffers from .vim file.
- [ ] `session-sanitize` removes unnamed blank buffers (`[No Name]`).
- [ ] `session-sanitize` preserves all non-ephemeral buffers and window layouts.
- [ ] Lua `sanitize_session_file()` calls Rust binary and handles errors gracefully.
- [ ] Session cleanup occurs on PersistenceSavePre automatically (no manual invocation needed).
- [ ] ~50 lines of Lua buffer inspection code removed from `session.lua`.
- [ ] Disabling via `vim.g.cumulus_session_sanitize = false` skips sanitization.
- [ ] Session restore works without stale Snacks dashboard or blank tabs.
- [ ] Zero regression in non-session functionality (Snacks explorer, dashboard still work when used).
