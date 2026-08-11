# LSP Performance Profiling Guide

**Status**: Guidance for future investigation of large-file performance  
**Created**: 2026-08-10  
**Context**: Architecture Consideration from code audit

## Overview

This guide documents how to profile LSP performance in Cumulus Neovim, specifically for large files (>10MB auto-generated Java code). This is important for identifying potential regressions when LSP servers like `jdtls`, `superhtml`, or `lemminx` attach to very large buffers.

## Motivation

**Risk**: LSP clients (`nvim-lspconfig`) attach automatically on `FileType` events. For large files, attachment, diagnostics, and codelens computation can block the UI thread.

**Goal**: Detect performance cliffs and establish guardrails (e.g., disable codelens/diagnostics for files >X MB).

## Profiling Method 1: Neovim Built-In Profiling

### Setup

```bash
# Terminal 1: Start Neovim with profiling
nvim --startuptime /tmp/nvim-profile.log

# Inside Neovim:
:profile start /tmp/lsp-profile.log
:profile func *
:profile file *

# Open a large Java file
:e /path/to/large/Generated.java
```

### Analyze

```bash
# View profile results
tail -100 /tmp/lsp-profile.log

# Look for:
# - lsp* functions taking >100ms
# - nvim_buf_* operations blocking
# - nvim-treesitter parsing times
```

### What to Look For

| Function Pattern | Expected | Issue If |
|-----------------|----------|-----------|
| `lsp_attach` | <50ms | >100ms (LSP initialization overhead) |
| `treesitter_parse` | <200ms | >500ms (syntax tree building) |
| `diagnostic_set` | <100ms | >300ms (diagnostic collection/rendering) |
| `codelens_refresh` | <200ms | >400ms (codelens computation) |

## Profiling Method 2: Manual Timing with Vim Commands

### In Neovim

```vim
" Measure buffer attachment time
:let start = reltime()
:edit /path/to/large/file.java
:echo reltimestr(reltime(start)) " Time to open and attach"

" Measure codelens computation
:let start = reltime()
:LspRestart
:echo reltimestr(reltime(start)) " Time to restart LSP"

" Measure diagnostic update
:let start = reltime()
:write
:echo reltimestr(reltime(start)) " Time to write and refresh diagnostics"
```

## Profiling Method 3: External Monitoring

### Using `htop` or `time`

```bash
# Measure memory and CPU during large file operations
time nvim /path/to/large/Generated.java -c "sleep 2 | qa"

# Look for:
# - Peak memory usage
# - CPU spikes (LSP server CPU %)
# - Time to launch + attach
```

### Using `perf` (Linux)

```bash
# Profile Neovim process
sudo perf record -g nvim /path/to/large/file.java
# ... perform operations (open, write, etc.)
# Exit Neovim
sudo perf report
```

## Test Files

### Generate a Large Test File

```bash
# Create a 10MB Java file with many empty methods
cat > /tmp/LargeGenerated.java <<'EOF'
public class LargeGenerated {
    // Auto-generated 10MB class
EOF

for i in {1..10000}; do
  echo "    public void method$i() { }" >> /tmp/LargeGenerated.java
done

echo "}" >> /tmp/LargeGenerated.java
```

### File Size Examples

| Size | Use Case | Expected Behavior |
|------|----------|-------------------|
| 1MB | Normal Java source | Instant (<100ms attach) |
| 5MB | Large auto-generated class | ~200ms attach, diagnostics visible |
| 10MB+ | Very large auto-gen (Spring Boot beans registry) | Monitor for UI lag, codelens delay |
| 50MB+ | Extreme case (generated test mock objects) | Should gracefully degrade |

## Red Flags & Remediation

### Red Flag #1: Codelens Computation > 500ms

**Symptom**: Delays when opening large files, "loading" spinner in codelens.

**Investigation**:
```vim
" Check codelens enable status per buffer
:lua print(vim.b.codelens_enabled or "nil")

" Disable codelens for this buffer
:lua vim.b.codelens_enabled = false
```

**Fix**: Add file-size threshold to disable codelens in `lua/cumulus/plugins/lsp-java.lua`:
```lua
local line_count = vim.api.nvim_buf_line_count(bufnr)
if line_count > 100000 then  -- >10K lines
  vim.b.codelens_enabled = false
end
```

### Red Flag #2: Diagnostics Lag > 300ms

**Symptom**: Status line shows "diagnsotic: updating..." that persists.

**Investigation**:
```vim
" Check diagnostic state
:lua print(vim.diagnostic.get(0))

" Disable diagnostics temporarily
:lua vim.diagnostic.disable(0)
```

**Fix**: Add thresholded diagnostics in `lua/cumulus/core/autocmds.lua`:
```lua
local bufsize = vim.fn.getfsize(filename)
if bufsize > 10 * 1024 * 1024 then  -- 10MB
  vim.diagnostic.disable(bufnr)
end
```

### Red Flag #3: LSP Attach > 2 seconds

**Symptom**: Long delay after opening file, JDTLS using 100% CPU.

**Investigation**:
```bash
# Monitor JDTLS startup
jps | grep JDTLS  # Check if process is running
ps aux | grep java | grep jdt  # Check memory/CPU
```

**Fix**: Consider lazy-loading JDTLS via custom command or conditional attach:
```lua
-- Only attach for files in src/main, not src/test
local path = vim.fn.expand("%:p")
if path:match("src/test") then
  return
end
```

## Healthy Baseline

### Ideal Metrics (i7-8700K, 16GB RAM, SSD)

```
File Size | Attach Time | Codelens | Diagnostic Update | Notes
----------|------------|----------|-------------------|-------
1MB       | <50ms      | <100ms   | <50ms             | Excellent
5MB       | 100-200ms  | 200-300ms| 100-150ms         | Good
10MB      | 200-400ms  | 400-500ms| 200-300ms         | Acceptable
50MB+     | >1s        | Manual   | Disabled          | Manual management
```

## Monitoring in Production

### Add to Your Nvimrc

```lua
-- Enable for profiling, disable when done
local PROFILE_LSP = false

if PROFILE_LSP then
  vim.cmd("profile start /tmp/lsp-profile.log")
  vim.cmd("profile func *")
end
```

### Key Metrics to Track

1. **Startup Time**: `nvim --startuptime /tmp/startup.log`
2. **Buffer Attach**: Time from buffer open to "LSP ready"
3. **Memory**: Peak memory after attaching to large file
4. **CPU**: CPU usage during diagnostic/codelens computation

## Related Issues

- **SPEC-006**: SpringBoot Debug Configuration & Hotswap (may impact performance)
- **SPEC-027**: Instant Java & Kotlin CodeLens Engine (performance-critical)
- LSP diagnostics on large files can be aggressive; consider `vim.diagnostic.config({ max_cached_items = 100 })`

## Future Work

- [ ] Profile JDTLS on files >50MB
- [ ] Establish file-size threshold for auto-disabling codelens
- [ ] Profile treesitter parsing on deeply nested Java files
- [ ] Benchmark `jdtls` memory usage with cached symbol tables
- [ ] Consider implementing a "large-file mode" buffer flag

## Commands for Quick Testing

```bash
# Generate 5MB test file
python3 -c "
code = 'public class Test {'
for i in range(1000):
    code += f'public void m{i}() {{}}'
code += '}'
print(code)
" > /tmp/Test.java && wc -c /tmp/Test.java

# Profile opening the file
time nvim /tmp/Test.java -c "qa"

# Check if diagnostics slow down the system
time nvim /tmp/Test.java -c "100000" -c "qa"
```

## References

- Neovim Profiling: `:help :profile`
- JDTLS Performance: https://github.com/eclipse/eclipse.jdt.ls/wiki/Performance
- LSP Spec Diagnostics: https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_publishDiagnostics
