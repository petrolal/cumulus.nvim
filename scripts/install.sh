#!/usr/bin/env bash
set -euo pipefail

echo "=================================================="
echo "      Cumulus Neovim Distribution Installer      "
echo "=================================================="

# 1. Dependency checks
echo "[1/4] Checking required system dependencies..."

has_cmd() {
  command -v "$1" >/dev/null 2>&1
}

MISSING=0

if has_cmd nvim; then
  NVIM_VER=$(nvim --version | head -n 1)
  echo "  ✔ Neovim detected: $NVIM_VER"
else
  echo "  ✖ Neovim is not installed or not in PATH."
  MISSING=1
fi

if has_cmd sbt; then
  SBT_VER=$(sbt --version 2>&1 | head -n 1 || echo "sbt detected")
  echo "  ✔ SBT / Scala toolchain detected: $SBT_VER"
else
  echo "  ℹ sbt is not installed in PATH (engine can be pre-built or downloaded via :CumulusInstallEngine)."
fi

if has_cmd git; then
  echo "  ✔ git detected"
else
  echo "  ✖ git is missing (recommended)."
fi

if has_cmd rg; then
  echo "  ✔ ripgrep (rg) detected"
else
  echo "  ⚠ ripgrep (rg) missing — Telescope/Snacks live grep will be limited."
fi

if [ "$MISSING" -eq 1 ]; then
  echo "Please install missing required dependencies before continuing."
  exit 1
fi

# 2. Build Scala Native Core Engine (if sbt is available)
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

echo "[2/4] Building Scala native engine (cumulus-engine)..."
if has_cmd sbt && [ -d "$REPO_DIR/engine" ]; then
  if (cd "$REPO_DIR/engine" && sbt test); then
    echo "  ✔ cumulus-engine verified with unit test suite!"
  else
    echo "  ⚠ Failed to run engine tests via sbt."
  fi
else
  echo "  ℹ Skipping native engine local compilation (run :CumulusInstallEngine inside Neovim to download pre-built binary)."
fi

# 3. Sync lazy.nvim Plugins
echo "[3/4] Initializing and syncing Neovim plugins..."
if nvim -u "$REPO_DIR/init.lua" --headless "+Lazy! sync" +qa; then
  echo "  ✔ Plugins synced successfully!"
else
  echo "  ⚠ Lazy sync encountered warnings or non-zero exit code."
fi

# 4. Verification
echo "[4/4] Running installation health check..."
if bash "$REPO_DIR/scripts/validate.sh"; then
  echo "=================================================="
  echo " 🎉 Cumulus Neovim installed and verified! 🎉 "
  echo "=================================================="
  echo ""
  echo "Quick Start:"
  echo "  1. Primary config: Launch 'nvim'"
  echo "  2. Custom appname: NVIM_APPNAME=cumulus nvim"
  echo "  3. In Neovim, run ':checkhealth cumulus' to verify health."
else
  echo "  ⚠ Health check reported warnings/failures. Please inspect output above."
fi
