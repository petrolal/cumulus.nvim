# Cumulus Neovim CLI Commands

## Quick Reference

```bash
cn setup        # Initialize or update everything
cn sync         # Same as setup (sync plugins & components)
cn status       # Show installation status
cn              # Launch Cumulus Neovim
cn --help       # Show help
cn --version    # Show version
```

---

## Detailed Commands

### `cn setup`
**Purpose:** Initialize or update Cumulus Neovim

**What it does:**
1. Checks/installs system dependencies (git, nvim, java, ripgrep)
2. Builds or downloads the cumulus-engine binary
3. Links `~/.config/nvim` → cumulus.nvim repo
4. Syncs all plugins with Lazy
5. Runs health verification

**When to use:**
- First time setup
- Updating components
- Rebuilding after config changes

**Example:**
```bash
cn setup
# Output:
# [1/5] Checking dependencies...
# [2/5] Installing engine...
# [3/5] Setting up config...
# [4/5] Syncing plugins...
# [5/5] Setting up launcher...
# 🎉 Cumulus Neovim Ready!
```

**Time:** 5-15 minutes (first time) or 1-2 minutes (updates)

---

### `cn sync`
**Purpose:** Sync plugins and components (alias for `setup`)

**When to use:**
- Shorthand for quick updates
- Refreshing plugin cache
- Re-syncing after config changes

**Example:**
```bash
cn sync
# Same as: cn setup
```

---

### `cn status`
**Purpose:** Show installation status

**What it shows:**
- Installation location
- Engine binary presence
- Configuration status

**When to use:**
- Check if properly installed
- Verify before updating
- Troubleshooting

**Example:**
```bash
cn status
# Output:
# Installation found: /home/user/cumulus.nvim
# ✓ Engine binary: present
```

---

### `cn` (no args)
**Purpose:** Launch Cumulus Neovim

**Behavior:**
- Launches Neovim with Cumulus configuration
- Same as running `nvim` directly
- Detects `~/.config/nvim` configuration

**Examples:**
```bash
cn                          # Launch normally
cn -u custom-init.lua       # With custom init
cn --noplugin               # Without plugins
cn -c "set number"          # With command
cn --diff file1 file2       # Diff mode
cn -R                       # Read-only mode
```

---

### `cn --help`
**Purpose:** Show command help

**Output:**
```
Cumulus Neovim CLI

USAGE:
    cn [COMMAND] [ARGS]...

COMMANDS:
    setup                   Initialize or update Cumulus
    sync                    Sync plugins & components (same as setup)
    status                  Show installation status
    ...
```

---

### `cn --version`
**Purpose:** Show CLI version

**Output:**
```
cn version 0.1.0
```

---

## Command Naming Philosophy

The CLI uses **standard verb patterns** that are intuitive and follow Unix conventions:

| Command | Pattern | Examples |
|---------|---------|----------|
| `setup` | Initialize/configure system | `docker setup`, `npm init`, `git init` |
| `sync` | Synchronize components | `git sync`, `rsync`, `unison` |
| `status` | Show current state | `git status`, `systemctl status` |
| No args | Default action (launch) | `nvim`, `code`, `jupyter` |

---

## Common Workflows

### First-Time Setup
```bash
# Clone the repo
git clone https://github.com/petrolal/cumulus.nvim ~/.config/nvim

# Run setup
cn setup

# Launch
nvim
```

### Daily Usage
```bash
# Just launch
nvim

# Or check status first
cn status
nvim
```

### Update & Refresh
```bash
# Update everything
cn setup

# Or quick sync
cn sync

# Then launch
nvim
```

### Troubleshooting
```bash
# Check status
cn status

# Run setup again
cn setup

# Check inside Neovim
nvim
:checkhealth cumulus
```

---

## Argument Passing

All arguments after `cn` are passed to `nvim` (except for recognized `cn` commands):

```bash
cn --help               # cn help (not nvim help)
cn -h                   # cn help (short form)

cn --version            # cn version (not nvim version)
cn -v                   # cn version (short form)

cn status               # cn status command

cn -u init.lua          # nvim -u init.lua
cn -c "set number"      # nvim -c "set number"
cn file.txt             # nvim file.txt
cn +10 file.txt         # nvim +10 file.txt
```

---

## Exit Codes

```
0   Success (setup complete, nvim launched normally)
1   Error (setup failed, no Cumulus found, etc.)
```

---

## Configuration

The CLI automatically detects:
- **Config location:** `~/.config/nvim` (symlink to cumulus.nvim)
- **Installation scripts:** Located in repo's `scripts/` directory
- **Installation state:** Checks for init.lua in config directory

No configuration file needed - CLI is zero-config.

---

## Troubleshooting

### "cn: command not found"
```bash
# Reload PATH
source ~/.bashrc  # or ~/.zshrc

# Or restart shell
exec $SHELL
```

### "cn setup" fails
```bash
# Check status
cn status

# Verify Java is installed
java -version

# Run with verbose output
bash ~/.config/nvim/scripts/install-cn.sh
```

### "cn status" shows engine not found
```bash
# This is normal - will be built on first nvim launch
# Run setup again to build it
cn setup
```

---

## Migration from Old Commands

| Old | New | Notes |
|-----|-----|-------|
| `cn install` | `cn setup` | Same functionality |
| `cn update` | `cn setup` | Same functionality |
| `cn` | `cn` | Unchanged |

No breaking changes - `cn` still launches Neovim.

---

## Future Commands (Roadmap)

Potential future commands:
- `cn doctor` - Health diagnostics
- `cn plugins` - Plugin management
- `cn config` - Configuration helper
- `cn upgrade` - Check for updates
- `cn uninstall` - Remove Cumulus

---

## Design Rationale

### Why "setup" instead of "install"?

1. **Less conflicting** - "install" can conflict with file operations or shell semantics
2. **More accurate** - Also handles updates, not just first-time install
3. **Standard terminology** - Used by `docker setup`, `git init`, etc.
4. **Clearer intent** - "Setup" implies configuration, not just extraction

### Why "sync" as alias?

1. **Familiar** - Users know "sync" from `git sync`, `rsync`
2. **Shorter** - Faster to type when just updating
3. **Semantic** - "Sync" emphasizes synchronizing plugins/components

### Why "status" for verification?

1. **Standard** - Used by `git status`, `systemctl status`
2. **Idiomatic** - Users expect `status` to show current state
3. **Useful** - Quick way to diagnose setup issues

---

## Related Documentation

- [INSTALL.md](./INSTALL.md) - Installation guide
- [CLI_ARCHITECTURE.md](./CLI_ARCHITECTURE.md) - CLI design
- [COURSIER_CLI.md](./COURSIER_CLI.md) - Distribution details

---

**Last updated:** 2026-08-24
**CLI Version:** 0.1.0
**Status:** ✅ Stable
