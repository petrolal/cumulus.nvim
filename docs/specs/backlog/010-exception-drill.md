# Specification: SPEC-010 - Runtime Exception Drill-Down

## Metadata
- **Spec ID**: SPEC-010
- **Title**: Runtime Exception Drill-Down
- **Status**: BACKLOG
- **Author**: AI Systems Architect
- **Target Files/Paths**:
  - `lua/cumulus/plugins/tools-dap-ui.lua` (extends)

---

## Goal & Intent

When a SpringBoot app crashes at runtime, the stack trace appears in the DAP console. Currently, developers must manually navigate to each file:line referenced in the trace. This spec makes stack trace lines clickable hyperlinks that jump directly to the source code.

---

## Scope Boundaries

**In scope:**
- Parse Java stack traces in DAP REPL/console output
- Extract file:line references
- Make lines clickable (open file + go to line)
- Support both full paths and relative paths

**Out of scope:**
- Kotlin or Groovy traces (for now; extend later)
- Remote stack traces (local only)

---

## Prerequisite Analysis

- DAP UI already captures console output
- Neovim can register keybindings on virtual text

---

## Execution Checklist

- [ ] Extend `lua/cumulus/plugins/tools-dap-ui.lua`:
  - [ ] Hook into DAP console/REPL output capture
  - [ ] Implement regex parser for Java stack trace lines: `at com.example.Class.method(File.java:123)`
  - [ ] Create virtual text or buffer keymaps to jump on click/gf
  - [ ] Use `vim.api.nvim_buf_set_keymap()` for line numbers in DAP console

---

## Verification Commands

```bash
bash scripts/validate.sh
luac -p lua/cumulus/plugins/tools-dap-ui.lua
nvim --headless --startuptime /tmp/nvim-startuptime.log +qa && grep "NVIM STARTED" /tmp/nvim-startuptime.log
```

### Acceptance Criteria
- [ ] Stack trace lines in DAP console are clickable
- [ ] Clicking jumps to file:line
- [ ] Works with `gf` (goto file) keybinding

---

## Summary

Dramatically faster stack trace navigation during debugging.
