# Cumulus: Shared Configuration Architecture

## Overview

**cumulus.nvim** and **cumulus.dotfiles** share a unified configuration system. Changes made in one project are immediately reflected in the other - **single source of truth**.

```
~/.cumulus/                          ← Root configuration
├── config/nvim/                    ← Shared Neovim config
├── config/sway/                    ← Sway config (dotfiles)
├── config/themes/                  ← Shared themes
├── settings.yaml                   ← Global settings
└── ... (other configs)

cumulus.dotfiles/
└── → reads from ~/.cumulus/

cumulus.nvim/
└── → reads from ~/.cumulus/
```

---

## Architecture

### Config Hierarchy

```
~/.cumulus/                    (Root - shared by both projects)
├── config/
│   ├── nvim/                (Neovim configuration)
│   │   ├── init.lua         ← Shared init (loaded by both)
│   │   ├── lua/             ← Lua modules (shared)
│   │   └── plugin-config/   ← Plugin settings (shared)
│   ├── sway/                (Sway desktop config)
│   ├── themes/              ← Theme definitions
│   └── ...
├── settings.yaml            ← Global cumulus settings
└── secrets/                 ← Credentials (optional)
```

### Symlink Structure

```
cumulus.nvim/lua/
└── symlink → ~/.cumulus/config/nvim/lua/

~/.config/nvim/
└── symlink → ~/.cumulus/config/nvim/

cumulus.dotfiles/config/sway/
└── symlink → ~/.cumulus/config/sway/

~/.config/sway/
└── symlink → ~/.cumulus/config/sway/
```

### Flow Diagram

```
User edits ~/.cumulus/config/nvim/init.lua
        ↓
Changes visible immediately in:
    ├─ cumulus.nvim (loads from ~/.cumulus)
    ├─ cumulus.dotfiles (reads from ~/.cumulus)
    ├─ ~/.config/nvim → (symlink)
    └─ Reload in nvim: :source $MYVIMRC
```

---

## Installation Flow

### Step 1: Clone cumulus.dotfiles (Linux)

```bash
git clone https://github.com/petrolal/cumulus.dotfiles.git ~/cumulus.dotfiles
cd ~/cumulus.dotfiles
```

### Step 2: Clone cumulus.nvim

```bash
git clone https://github.com/petrolal/cumulus.nvim.git ~/cumulus.nvim
```

### Step 3: Create shared config root

```bash
mkdir -p ~/.cumulus/config/nvim
mkdir -p ~/.cumulus/config/sway
mkdir -p ~/.cumulus/config/themes
```

### Step 4: Initialize shared configuration

```bash
# Copy initial configs from cumulus.nvim
cp -r ~/cumulus.nvim/lua/cumulus/plugins ~/.cumulus/config/nvim/
cp ~/cumulus.nvim/init.lua ~/.cumulus/config/nvim/

# Copy initial configs from cumulus.dotfiles
cp -r ~/cumulus.dotfiles/config/sway ~/.cumulus/config/
cp -r ~/cumulus.dotfiles/config/themes ~/.cumulus/config/
```

### Step 5: Create symlinks

**From cumulus.nvim → shared config:**
```bash
cd ~/cumulus.nvim
rm -rf lua/cumulus/plugins init.lua
ln -sf ~/.cumulus/config/nvim/plugins lua/cumulus/
ln -sf ~/.cumulus/config/nvim/init.lua init.lua
```

**From cumulus.dotfiles → shared config:**
```bash
cd ~/cumulus.dotfiles
rm -rf config/sway
ln -sf ~/.cumulus/config/sway config/
```

**From ~/.config → shared config:**
```bash
rm -rf ~/.config/nvim ~/.config/sway
ln -sf ~/.cumulus/config/nvim ~/.config/nvim
ln -sf ~/.cumulus/config/sway ~/.config/sway
```

### Step 6: Install both systems

```bash
# Install cumulus.dotfiles (includes nvim setup)
cd ~/cumulus.dotfiles
cumulus install

# Or install just nvim
cumulus nvim setup
```

---

## Configuration Access

### From cumulus.nvim

```lua
-- init.lua (shared location: ~/.cumulus/config/nvim/init.lua)

-- Load plugins from shared location
require("cumulus.plugins")

-- Theme from shared location
vim.cmd.colorscheme(vim.env.CUMULUS_THEME or "default")

-- Settings from shared settings.yaml
local settings = require("cumulus.config.settings")
```

### From cumulus.dotfiles

```scala
// Main.scala (cumulus.dotfiles)

// Read shared config
val nvimConfig = os.read(os.home / ".cumulus" / "config" / "nvim" / "init.lua")

// Read global settings
val settings = os.read(os.home / ".cumulus" / "settings.yaml")

// Theme from shared location
val theme = os.read(os.home / ".cumulus" / "config" / "themes" / "active.yaml")
```

### Accessing from Shell

```bash
# Theme
cat ~/.cumulus/config/themes/active.yaml

# Nvim config
cat ~/.cumulus/config/nvim/init.lua

# Global settings
cat ~/.cumulus/settings.yaml

# Quick edit
$EDITOR ~/.cumulus/config/nvim/init.lua
$EDITOR ~/.cumulus/config/sway/config
```

---

## File Structure (Complete)

```
~/.cumulus/
├── config/
│   ├── nvim/                      ← Neovim (shared)
│   │   ├── init.lua               ← Main init
│   │   ├── lua/
│   │   │   ├── cumulus/
│   │   │   │   ├── plugins/       ← Plugin specs
│   │   │   │   ├── config/        ← Settings
│   │   │   │   └── util/          ← Utilities
│   │   │   └── user/              ← User customizations
│   │   ├── after/                 ← After configs
│   │   └── snippets/              ← Snippet files
│   │
│   ├── sway/                      ← Sway (Linux desktop)
│   │   ├── config                 ← Sway config
│   │   ├── waybar/                ← Status bar
│   │   └── scripts/               ← Helper scripts
│   │
│   ├── themes/                    ← Shared themes
│   │   ├── active.yaml            ← Currently active
│   │   ├── nord.yaml              ← Theme: Nord
│   │   ├── dracula.yaml           ← Theme: Dracula
│   │   └── ...
│   │
│   └── other/                     ← Other configs
│       ├── kitty/                 ← Terminal
│       ├── wofi/                  ← App launcher
│       └── ...
│
├── settings.yaml                  ← Global settings
│   # Example:
│   # theme: nord
│   # font: JetBrainsMono Nerd
│   # editor: nvim
│   # shell: zsh
│
├── secrets/                       ← (Optional) Secrets
│   └── .gitignore                ← Prevent committing
│
└── logs/                          ← Logs
    └── setup.log                  ← Installation log
```

---

## Making Changes

### Change Neovim Config

```bash
# Edit directly (affects both projects)
$EDITOR ~/.cumulus/config/nvim/init.lua

# Reload in Neovim
:source $MYVIMRC
:checkhealth cumulus

# Or reload Sway if needed
swaymsg reload
```

### Change Theme

```bash
# Update active theme
echo "nord" > ~/.cumulus/config/themes/active.yaml

# Apply to desktop
cumulus theme nord

# Apply to Neovim
:set background=light
:colorscheme nord
```

### Change Global Settings

```bash
# Edit global settings
$EDITOR ~/.cumulus/settings.yaml

# Reload cumulus (reapplies settings)
cumulus install --update-config
```

---

## Project Integration Points

### cumulus.dotfiles reads from ~/.cumulus/

```scala
// When user runs: cumulus install
// Reads configuration from ~/.cumulus/

val nvimConfig = os.read(os.home / ".cumulus" / "config" / "nvim" / "init.lua")
val theme = readTheme()
val settings = readSettings()

// Installs/configures nvim based on shared config
```

### cumulus.nvim reads from ~/.cumulus/

```lua
-- When nvim starts (init.lua)
-- Loads from ~/.cumulus/config/nvim/

require("cumulus.init")           -- Shared init
require("cumulus.plugins")        -- Shared plugins
require("cumulus.config")         -- Shared settings
```

### Both watch for changes

```bash
# Changes to ~/.cumulus are visible immediately
# No rebuild needed, just reload

# In nvim:
:source $MYVIMRC

# In sway:
swaymsg reload

# In cumulus:
cumulus refresh
```

---

## Synchronization

### Automatic (Symlinks)

Changes in shared location automatically visible to both projects:

```
Edit ~/.cumulus/config/nvim/init.lua
    ↓
Symlink: ~/.config/nvim/init.lua → (updated)
    ↓
Symlink: ~/cumulus.nvim/init.lua → (updated)
    ↓
Both projects see changes immediately
```

### Manual (if needed)

```bash
# Sync from cumulus.nvim → shared
cp -r ~/cumulus.nvim/lua/cumulus/*.  ~/.cumulus/config/nvim/lua/

# Sync from cumulus.dotfiles → shared
cp -r ~/cumulus.dotfiles/config/sway/*  ~/.cumulus/config/sway/

# Verify symlinks
ls -la ~/.config/nvim
ls -la ~/.config/sway
ls -la ~/cumulus.nvim/
```

---

## Environment Variables

Both projects use these shared env vars:

```bash
# Set in ~/.bashrc or ~/.zshrc
export CUMULUS_CONFIG=~/.cumulus/config
export CUMULUS_SETTINGS=~/.cumulus/settings.yaml
export CUMULUS_THEME=$(cat ~/.cumulus/config/themes/active.yaml)
export CUMULUS_HOME=$HOME/.cumulus
```

Projects access via:

```lua
-- Neovim
local config_dir = os.getenv("CUMULUS_CONFIG") or (os.getenv("HOME") .. "/.cumulus/config")

-- Scala
val cumulusConfig = sys.env.getOrElse("CUMULUS_CONFIG", os.home / ".cumulus" / "config")
```

---

## Workflow Examples

### Add a new Neovim plugin

```bash
# 1. Edit shared plugin config
$EDITOR ~/.cumulus/config/nvim/lua/cumulus/plugins.lua

# 2. Add plugin spec (lazy.nvim)
# plugins = {
#   { "user/plugin", ... }
# }

# 3. Reload in Neovim
:Lazy sync
:source $MYVIMRC

# 4. Automatic: cumulus.dotfiles sees updated config next time it runs
```

### Change desktop theme

```bash
# 1. Update active theme
echo "dracula" > ~/.cumulus/config/themes/active.yaml

# 2. Reload desktop
swaymsg reload

# 3. Reload Neovim
:set background=dark
:colorscheme dracula

# 4. Both are now using same theme
```

### Customize for your machine

```bash
# ~/.cumulus/config/nvim/lua/user/
mkdir -p ~/.cumulus/config/nvim/lua/user

# Create local overrides
cat > ~/.cumulus/config/nvim/lua/user/settings.lua << 'EOF'
-- Your local customizations
vim.opt.number = true
vim.opt.relativenumber = true
-- etc.
EOF

# Load in init.lua
require("user.settings")
```

---

## Important Notes

### Version Control

```bash
# .gitignore for shared config
cat > ~/.cumulus/.gitignore << 'EOF'
secrets/
logs/
*.swp
*.tmp
node_modules/
.LSP_STORAGE/
EOF

# Git tracking
cd ~/.cumulus
git init
git add config/ settings.yaml
git commit -m "Initial cumulus configuration"
```

### Backup

```bash
# Backup shared config
tar -czf ~/.cumulus-backup-$(date +%Y%m%d).tar.gz ~/.cumulus/

# Or use cumulus backup
cumulus backup
```

### Troubleshooting

```bash
# Check symlinks
ls -la ~/.config/nvim
ls -la ~/cumulus.nvim/

# Verify they point to ~/.cumulus/
readlink ~/.config/nvim

# If broken, fix:
rm ~/.config/nvim
ln -sf ~/.cumulus/config/nvim ~/.config/nvim
```

---

## Summary

| Aspect | How It Works |
|--------|-------------|
| **Config location** | `~/.cumulus/config/` (single source) |
| **Nvim config** | `~/.cumulus/config/nvim/` (shared) |
| **Desktop config** | `~/.cumulus/config/sway/` (shared) |
| **Themes** | `~/.cumulus/config/themes/` (shared) |
| **Settings** | `~/.cumulus/settings.yaml` (shared) |
| **Symlinks** | Both projects point to shared location |
| **Changes** | Immediate (no rebuild needed) |
| **Synchronization** | Automatic via symlinks |
| **Version control** | Single git repo for all config |

---

## Installation Checklist

- [ ] Clone both projects
- [ ] Create `~/.cumulus/config/` directory
- [ ] Copy configs from projects to `~/.cumulus/`
- [ ] Create symlinks from projects to `~/.cumulus/`
- [ ] Create symlinks from `~/.config/` to `~/.cumulus/`
- [ ] Set environment variables
- [ ] Run `cumulus install`
- [ ] Test: changes in `~/.cumulus/` affect both projects
- [ ] Verify with `ls -la ~/.config/nvim` (should show symlink)

---

**Status:** ✅ Shared configuration architecture ready  
**Last updated:** 2026-08-24
