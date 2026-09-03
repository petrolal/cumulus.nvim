#!/usr/bin/env bash
# TetraVim Neovim: Development Initialization
# Lightweight setup for developers - just config + plugins, no deps/validation

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

echo "=================================================="
echo "   TetraVim Neovim: Developer Setup               "
echo "=================================================="
echo ""

# ============================================================================
# Step 1: Check if nvim is available
# ============================================================================
if ! command -v nvim >/dev/null 2>&1; then
  echo "✖ Neovim not found. Please install Neovim >= 0.10"
  echo "  macOS:  brew install neovim"
  echo "  Ubuntu: sudo apt install neovim"
  echo "  Arch:   sudo pacman -S neovim"
  exit 1
fi

echo "✔ Neovim: $(nvim --version | head -n 1)"
echo ""

# ============================================================================
# Step 2: Link configuration
# ============================================================================
echo "Setting up configuration..."

NVIM_CONFIG="${XDG_CONFIG_HOME:-$HOME/.config}/nvim"

# Backup existing config if needed
if [ -e "$NVIM_CONFIG" ] && [ ! -L "$NVIM_CONFIG" ]; then
  BACKUP="$NVIM_CONFIG.backup.$(date +%s)"
  echo "  ⚠ Backing up existing nvim config to $BACKUP"
  mv "$NVIM_CONFIG" "$BACKUP"
fi

# Remove old symlink if pointing elsewhere
if [ -L "$NVIM_CONFIG" ]; then
  rm "$NVIM_CONFIG"
fi

# Create symlink
mkdir -p "$(dirname "$NVIM_CONFIG")"
ln -sf "$REPO_DIR" "$NVIM_CONFIG"
echo "  ✔ Config linked: $NVIM_CONFIG → $REPO_DIR"

# ============================================================================
# Step 3: Sync plugins
# ============================================================================
echo ""
echo "Syncing plugins..."

if nvim --headless "+Lazy! sync" +qa 2>/dev/null; then
  echo "  ✔ Plugins synced"
else
  echo "  ⚠ Plugin sync had warnings (run :Lazy in nvim to check)"
fi

# ============================================================================
# Done
# ============================================================================
echo ""
echo "=================================================="
echo "  ✨ Ready for development!                      "
echo "=================================================="
echo ""
echo "Next steps:"
echo "  • nvim                     # Launch TetraVim"
echo "  • :checkhealth tetravim     # Verify setup"
echo "  • :help tetravim            # Read docs"
echo ""
echo "Development notes:"
echo "  • Config is symlinked from: $REPO_DIR"
echo "  • Changes to lua/ appear immediately"
echo "  • Edit and reload: :source $NVIM_CONFIG/init.lua"
echo ""
