# Cumulus Neovim Installation Guide

## Quick Start (One Shot Install)

### 1. Clone the Repository
```bash
git clone https://github.com/petrolal/cumulus.nvim.git
cd cumulus.nvim
```

### 2. Run Bootstrap
```bash
bash bootstrap.sh
```

This creates the `cn` command launcher and updates your shell PATH.

### 3. Run Setup
```bash
cn setup
```

This single command will:
- ✔ Install system dependencies (git, nvim, java, ripgrep)
- ✔ Build or download the cumulus-engine binary
- ✔ Setup Neovim configuration at `~/.config/nvim`
- ✔ Sync all plugins
- ✔ Verify health and setup

### 4. Done!
```bash
nvim
```

Plain `nvim` command now launches Cumulus. That's it!

---

## Installation Flow Diagram

```
User clones repo
        ↓
bash bootstrap.sh
  • Creates ~/.local/bin/cn
  • Updates shell PATH
        ↓
cn setup
  • Checks/installs deps (git, nvim, java, ripgrep)
  • Builds/downloads cumulus-engine
  • Links ~/.config/nvim → repo
  • Syncs plugins with Lazy
  • Runs health check
        ↓
nvim  (just works!)
```

---

## The `cn` Command

After installation, use the `cn` command for:

```bash
# Setup or update Cumulus
cn setup

# Check installation status
cn status

# Launch Cumulus Neovim
cn

# Pass args to nvim
cn -u ~/.config/nvim/init.lua
cn --noplugin

# Get help
cn --help
cn --version
```

---

## What Gets Installed

### System Dependencies
- **git** - Version control
- **neovim** - Editor (>= 0.10 recommended)
- **java** - For the Scala engine (21+)
- **ripgrep** - For fast file searching

### Cumulus Components
- **cumulus-engine** - Scala-based JVM intelligence engine
- **~/.config/nvim** - Symlinked to repository
- **Lazy plugins** - All plugins from lua/cumulus/plugins/

---

## Configuration

The configuration is a **direct symlink** to the repository:
```
~/.config/nvim → /path/to/cumulus.nvim
```

This means:
- Updates to the repo appear immediately
- You can modify configs and test locally
- Backup your own configs before installing

If you had a previous nvim config, it's backed up to:
```
~/.config/nvim.backup.{timestamp}
```

---

## Engine Installation

The cumulus-engine (Scala/JVM intelligence) is installed in:
```
~/.local/share/nvim/cumulus/bin/cumulus-engine
```

The installer will:
1. Try to build locally if `sbt` is available
2. Download pre-built binary for your platform
3. Fall back to manual install (`:CumulusInstallEngine` in nvim)

---

## Troubleshooting

### "cn command not found"
Your shell needs to reload PATH. Run:
```bash
source ~/.bashrc  # or ~/.zshrc
# or restart the terminal
```

### "Neovim: nvim not found"
Install Neovim:
```bash
# macOS
brew install neovim

# Ubuntu/Debian
sudo apt install neovim

# Arch
sudo pacman -S neovim

# Fedora
sudo dnf install neovim
```

### "cumulus-engine not available"
This is non-critical. The engine will be downloaded or built on first use.
Run `:CumulusInstallEngine` inside nvim.

### Plugin sync failed
Run inside Neovim:
```vim
:Lazy sync
```

### Health check warnings
Run inside Neovim:
```vim
:checkhealth cumulus
```

---

## Uninstall

To remove Cumulus:

```bash
# Remove configuration symlink
rm ~/.config/nvim

# Restore backup if you had previous config
mv ~/.config/nvim.backup.* ~/.config/nvim

# Optionally remove engine
rm ~/.local/share/nvim/cumulus/bin/cumulus-engine

# Optionally remove cn command
rm ~/.local/bin/cn
```

---

## Manual Installation (Advanced)

If you prefer manual setup:

```bash
# Clone
git clone https://github.com/petrolal/cumulus.nvim.git ~/.config/nvim

# Install deps manually
# (see your package manager for git, nvim, java 21+, ripgrep)

# Build engine (optional)
cd ~/.config/nvim/engine
sbt nativeImage

# Sync plugins
nvim --headless "+Lazy! sync" +qa
```

---

## Getting Help

Inside Neovim:
```vim
:help cumulus
:checkhealth cumulus
```

Online:
- GitHub Issues: https://github.com/petrolal/cumulus.nvim/issues
- Documentation: https://github.com/petrolal/cumulus.nvim
