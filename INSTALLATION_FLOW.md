# Cumulus Neovim - Installation Architecture

## New Flow (One-Shot Install)

### User Experience
```
$ bash bootstrap.sh
✔ Created 'cn' launcher

$ cn install
[1/5] Checking system dependencies...
[2/5] Installing cumulus-engine...
[3/5] Setting up Neovim configuration...
[4/5] Syncing Neovim plugins...
[5/5] Setting up 'cn' command...
🎉 Cumulus Neovim Ready!

$ nvim
✨ Opens Cumulus with full JVM IDE features
```

---

## File Organization

```
cumulus.nvim/
├── bootstrap.sh              ← User runs this first (quick setup)
├── scripts/
│   ├── install-cn.sh         ← Main unified installer (does everything)
│   ├── install.sh            ← Legacy wrapper → calls install-cn.sh
│   └── validate.sh           ← Health verification
├── INSTALL.md                ← User-facing installation guide
└── INSTALLATION_FLOW.md      ← This file
```

---

## Installation Stages

### Stage 1: `bootstrap.sh` (Quick, <1 second)
**Purpose:** Kickstart the installation process
**What it does:**
- Creates `~/.local/bin/cn` launcher script
- Updates shell PATH to include `~/.local/bin`
- Tells user to run `cn install`

**Why separate:**
- Minimal setup required
- User can interrupt here if needed
- cn launcher becomes available immediately

### Stage 2: `cn install` (Full installation, 5-15 minutes)
**Purpose:** Complete system setup in one command
**What it does:**
1. **Check Dependencies** - Detect and install system packages
   - git, neovim, java (21+), ripgrep
   - Per OS: pacman, apt, dnf, yum, brew

2. **Install Engine** - Get Scala/JVM intelligence
   - Try to build locally if sbt is available
   - Download pre-built binary for platform
   - Fallback: User can install manually

3. **Setup Config** - Link Neovim to Cumulus
   - Symlink `~/.config/nvim` → repo directory
   - Backup any existing config to `.backup`
   - **Key difference from old flow:** Direct nvim config, not ~/.config/cumulus

4. **Sync Plugins** - Install all dependencies
   - Run Lazy plugin manager
   - Install LSP servers, completion, UI plugins

5. **Launcher Setup** - Create cn command
   - Verify `~/.local/bin/cn` exists
   - Ensure PATH is properly configured

---

## Key Improvements Over Old Flow

| Aspect | Old | New |
|--------|-----|-----|
| Entry point | bootstrap.sh (full install) | bootstrap.sh (5 sec kickstart) |
| Complete install | scripts/install.sh | cn install |
| Config location | ~/.config/cumulus | ~/.config/nvim |
| Usage | `NVIM_APPNAME=cumulus nvim` or `cn` | Plain `nvim` just works |
| One-shot install | ❌ Required 2 commands | ✅ Two commands, clearly ordered |
| User confusion | High (why ~/.config/cumulus?) | Low (standard ~/.config/nvim) |

---

## cn Command Behavior

After `cn install` succeeds:

```bash
cn                    # Launch nvim normally
cn install            # Re-run full installation
cn update             # Same as install (update everything)
cn -u <init>          # Pass args to nvim
cn --noplugin         # Any nvim flag works
```

Implementation (in `~/.local/bin/cn`):
```bash
case "${1:-}" in
  install|update)
    # Detect cumulus repo location
    # Run full install-cn.sh
    ;;
  *)
    # Just pass through to nvim
    ;;
esac
```

---

## Why This Design

### Problem with Old Flow
1. Config lived at `~/.config/cumulus` (non-standard)
2. Required users to understand `NVIM_APPNAME` environment variable
3. Plain `nvim` didn't work - confusing for new users
4. Installation required understanding 2 scripts with different responsibilities

### Solution
1. **Standard location:** `~/.config/nvim` is where nvim looks by default
2. **One command:** `cn install` does everything, clearly ordered
3. **Just works:** Plain `nvim` launches Cumulus immediately
4. **Clear responsibility:**
   - `bootstrap.sh` = quick kickstart
   - `cn install` = full installation
   - `install-cn.sh` = implementation details

---

## Migration Guide (For Users of Old Flow)

If you used `bootstrap.sh` before:

```bash
# Old setup still works, but do this once:
rm ~/.config/nvim                    # Remove old symlink (if exists)
rm ~/.config/cumulus/init.lua        # Clean up old location
ln -sf ~/cumulus.nvim ~/.config/nvim # Create proper symlink

# Now everything works with plain nvim
nvim
```

Or just run the new installer:
```bash
cn install  # This does it all automatically
```

---

## Backwards Compatibility

✅ **Old scripts still work:**
- `bash bootstrap.sh` → works, same behavior
- `bash scripts/install.sh` → delegates to install-cn.sh

✅ **Old cn launcher still works:**
- It falls back to plain `nvim` if not installed
- `cn install` still available

✅ **Config migration:**
- Symlink automatically backed up
- Previous configs preserved as `.backup`

---

## Architecture Benefits

### For Users
- Single, clear, ordered flow
- Standard config location (~/.config/nvim)
- Works with `nvim` immediately after install
- `cn` command available for updates

### For Developers
- Modular: bootstrap and install are separate concerns
- Easy to debug: clear 5-step process
- Easy to extend: add steps to install-cn.sh
- Easy to test: each stage can run independently

### For Maintenance
- No special env vars needed after setup
- Standard Neovim conventions
- Matches cumulus.dotfiles expectations
- Easier for newcomers to understand

---

## Testing the Flow

To verify the new installation works:

```bash
# Fresh setup
bash bootstrap.sh
cn install

# Verify it works
nvim --version
nvim -c ':checkhealth cumulus'

# Test updates
cn update
nvim -c ':Lazy sync'
```

---

## Troubleshooting Guide

### cn: command not found
```bash
# Reload shell
source ~/.bashrc  # or ~/.zshrc
# Or restart terminal
```

### Can't find cumulus-engine
```bash
# Non-critical. Install manually:
nvim -c ':CumulusInstallEngine'
# Or rebuild:
cd ~/.config/nvim/engine && sbt nativeImage
```

### Health check has warnings
```bash
# Inside nvim
:checkhealth cumulus
# Address specific warnings
```

---

## Future Improvements

Possible enhancements to install-cn.sh:
- [ ] Parallel dependency installation
- [ ] Resume capability (if interrupted)
- [ ] Dry-run mode (--dry-run)
- [ ] Custom config location (--config-dir)
- [ ] Container/Docker support (--docker)
