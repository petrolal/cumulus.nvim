#!/usr/bin/env bash
# Cumulus Neovim Bootstrap
# Initializes the Neovim environment and installs basic dependencies

set -euo pipefail

echo "=================================================="
echo "          Cumulus Neovim Bootstrap                "
echo "=================================================="
echo ""

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Ensure Neovim is available
if ! command -v nvim >/dev/null 2>&1; then
  echo "✖ Neovim is not installed or not in PATH."
  echo "Please install Neovim >= 0.10.0"
  exit 1
fi

echo "→ Syncing Neovim Plugins via Lazy.nvim..."
nvim --headless "+Lazy! sync" +qa

echo "✔ Setup complete! You can now run 'nvim'."
