# Cumulus Neovim: Command Structure

## Clear Separation of Concerns

The Cumulus Neovim system uses **two separate commands** for different purposes:

### 1. `cumulus` - CLI for Management
**Purpose:** Initialize, update, and manage Cumulus Neovim  
**Behavior:** CLI tool with subcommands  
**No file operations**

```bash
cumulus nvim setup       # Initialize or update
cumulus nvim sync        # Sync plugins & components
cumulus nvim status      # Check installation
cumulus nvim --help      # Get help
cumulus --version        # Show version
```

### 2. `cn` - Simple Launcher for Files
**Purpose:** Open files and launch Neovim  
**Behavior:** Lightweight wrapper around `nvim`  
**No CLI logic, no conflicts**

```bash
cn                       # Launch Neovim
cn file.txt              # Open file
cn -u custom-init.lua    # Custom init
cn --noplugin            # Without plugins
cn -c "set number"       # With command
```

---

## Why Separate?

### Problem (Old Design)
```bash
cn install          # Could try to open file named "install"
cn setup            # Could try to open file named "setup"
cn file.txt         # Open file (but conflicts with above)
```

**Result:** Confusing command that does two incompatible things

### Solution (New Design)
```bash
cumulus nvim setup  # Clear: "cumulus" → CLI, "nvim" → subcommand, "setup" → action
cn file.txt         # Clear: "cn" → launcher, "file.txt" → open
cn                  # Clear: just launch nvim
```

**Result:** Each command has one clear purpose

---

## Command Details

### `cumulus` - Management CLI

#### `cumulus nvim setup`
**What it does:**
1. Checks/installs system dependencies
2. Builds or downloads the engine
3. Links `~/.config/nvim` → cumulus.nvim
4. Syncs all plugins
5. Verifies installation

**When to use:** First-time setup, updating, rebuilding

**Time:** 5-15 min (first) or 1-2 min (updates)

```bash
cumulus nvim setup
# Output:
# [1/5] Checking dependencies...
# [2/5] Installing engine...
# [3/5] Setting up config...
# [4/5] Syncing plugins...
# [5/5] Setting up launcher...
# 🎉 Cumulus Neovim Ready!
```

#### `cumulus nvim sync`
**Alias for `cumulus nvim setup`** - Emphasizes syncing plugins

```bash
cumulus nvim sync
```

#### `cumulus nvim status`
**What it shows:**
- Installation location
- Engine binary status
- Configuration status

```bash
cumulus nvim status
# Installation found: /home/user/cumulus.nvim
# ✓ Engine binary: present
```

#### `cumulus nvim [args]`
**Launch Neovim with arguments**

```bash
cumulus nvim                    # Launch
cumulus nvim -u custom.lua      # Custom init
cumulus nvim file.txt           # Open file
cumulus nvim +100 file.txt      # Jump to line
```

#### `cumulus --help` / `cumulus --version`
**Show help or version**

```bash
cumulus --help
cumulus --version
```

---

### `cn` - File Launcher

#### `cn`
**Just launch Neovim**

```bash
cn
# Equivalent to: nvim
```

#### `cn [files]`
**Open files in Neovim**

```bash
cn file.txt                    # Open file
cn file1.txt file2.txt         # Multiple files
cn *.rs                        # Glob patterns
cn +10 file.txt                # Jump to line 10
cn +/search file.txt           # Jump to search result
```

#### `cn [nvim-args]`
**Pass arguments to Neovim**

```bash
cn -u custom-init.lua         # Custom init
cn --noplugin                 # Without plugins
cn -c "set number"            # With command
cn -R file.txt                # Read-only mode
cn --diff file1 file2         # Diff mode
cn -                           # Read from stdin
```

---

## Installation & Setup

### For Users

**First time:**
```bash
git clone https://github.com/petrolal/cumulus.nvim ~/.config/nvim
cumulus nvim setup
cumulus nvim
```

**Daily use:**
```bash
cn                       # Open files
cn file.txt             # Open specific file
cumulus nvim            # Launch with full setup
```

**Updates:**
```bash
cumulus nvim sync       # Update plugins
cumulus nvim setup      # Full rebuild
```

### For Developers

**Development setup:**
```bash
bash scripts/dev-init.sh
nvim                    # Edit code directly
```

**Full setup:**
```bash
cumulus nvim setup
cumulus nvim
```

---

## Command Naming Philosophy

| Command | Type | Pattern | Examples |
|---------|------|---------|----------|
| `cumulus` | CLI Tool | Package manager | npm, pip, docker, cargo |
| `cumulus nvim` | Subcommand | Specific tool | npm install, pip install |
| `cumulus nvim setup` | Action | Verb-based | git clone, docker build |
| `cn` | Launcher | Shorthand | vim, emacs, code |

---

## No Conflicts

### Old Problem
```bash
cn install      # Is this: (a) CLI install, or (b) open file "install"?
cn setup        # Is this: (a) CLI setup, or (b) open file "setup"?
```

### New Solution
```bash
cumulus nvim setup      # ALWAYS: CLI setup (clear subcommand structure)
cn setup                # If needed: open file "setup" (but unusual)
cn file.txt             # ALWAYS: open file (no ambiguity)
```

---

## Argument Passing

### `cumulus` - Controlled Parsing
```bash
cumulus nvim setup          # Matched command (not passed to nvim)
cumulus nvim --help         # Matched flag (not passed to nvim)
cumulus nvim -u custom.lua  # Passed to nvim (not matched)
cumulus nvim file.txt       # Passed to nvim (not matched)
```

### `cn` - Direct Pass-Through
```bash
cn                      # Just launch nvim
cn file.txt             # Open file (passed to nvim)
cn -u custom.lua        # Custom init (passed to nvim)
cn -c "set number"      # Execute command (passed to nvim)
cn --all-args-passthrough  # All args pass through
```

---

## Exit Codes

```
0   Success
1   Error (setup failed, not installed, etc.)
```

---

## Environment Variables

Both commands respect standard Neovim env vars:
```bash
XDG_CONFIG_HOME         # Config directory (default: ~/.config)
NVIM_APPNAME            # Not needed (using standard ~/.config/nvim)
EDITOR                  # Fallback editor (not affected)
```

---

## Examples by Use Case

### Use Case: First-Time Setup
```bash
git clone https://github.com/petrolal/cumulus.nvim ~/.config/nvim
cumulus nvim setup      # Initialize everything
cumulus nvim            # Launch and start using
```

### Use Case: Daily Development
```bash
cn *.rs                 # Open all Rust files
cn file.txt +10         # Open and jump to line 10
cn -R readonly.txt      # Open read-only
```

### Use Case: Update Plugins
```bash
cumulus nvim sync       # Sync plugins
cumulus nvim            # Verify everything works
```

### Use Case: Troubleshooting
```bash
cumulus nvim status     # Check installation
cumulus nvim setup      # Rebuild if needed
cn                      # Test launch
:checkhealth cumulus    # Inside nvim
```

### Use Case: Custom Configuration
```bash
cn -u my-custom-init.lua file.txt   # Use custom init
cumulus nvim -u my-custom-init.lua  # Use custom init with setup
```

---

## Migration from Old Commands

| Old | New | Type |
|-----|-----|------|
| `cn setup` | `cumulus nvim setup` | CLI management |
| `cn sync` | `cumulus nvim sync` | CLI management |
| `cn status` | `cumulus nvim status` | CLI management |
| `cn` | `cn` | Launcher (unchanged) |
| `cn file.txt` | `cn file.txt` | Launcher (unchanged) |

---

## Troubleshooting

### "cumulus: command not found"
```bash
# Reload PATH
source ~/.bashrc
# Or restart shell
```

### "cn: command not found"
```bash
# If not in PATH:
~/.local/bin/cn file.txt
# Or add to PATH:
export PATH="$HOME/.local/bin:$PATH"
```

### Conflicting with existing `cumulus`
If you already have a `cumulus` command (from cumulus.dotfiles):
```bash
# They work together fine!
cumulus nvim setup              # Manage Neovim
cumulus install                 # Manage other tools (different)
```

---

## Future Commands (Roadmap)

Potential future `cumulus nvim` subcommands:
- `cumulus nvim doctor` - Health diagnostics
- `cumulus nvim plugins list` - List plugins
- `cumulus nvim config` - Configuration helper
- `cumulus nvim lsp` - LSP management

Potential future `cn` aliases:
- `nvim-quick` - Alternative name
- Shell function shortcuts - Custom aliases

---

## Summary

```
TWO CLEAR, SEPARATE COMMANDS:

┌─────────────────────────────────────────┐
│  cumulus nvim setup/sync/status/help    │ ← CLI for management
│  (Subcommand structure, no conflicts)   │
└─────────────────────────────────────────┘

┌─────────────────────────────────────────┐
│  cn [files or nvim args]                │ ← Simple launcher
│  (Pass-through, just opens files)       │
└─────────────────────────────────────────┘

Result: Zero confusion, clear intent, no conflicts
```

---

**Last updated:** 2026-08-24
**Command Structure:** ✅ Finalized
**Status:** ✅ Production-ready
