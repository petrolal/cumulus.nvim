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

# Source SDKMAN if present and Java is not currently in PATH
if ! has_cmd java; then
  if [ -s "${SDKMAN_DIR:-$HOME/.sdkman}/bin/sdkman-init.sh" ]; then
    # shellcheck source=/dev/null
    source "${SDKMAN_DIR:-$HOME/.sdkman}/bin/sdkman-init.sh" 2>/dev/null || true
  elif [ -d "${SDKMAN_DIR:-$HOME/.sdkman}/candidates/java/current/bin" ]; then
    export PATH="${SDKMAN_DIR:-$HOME/.sdkman}/candidates/java/current/bin:$PATH"
    export JAVA_HOME="${SDKMAN_DIR:-$HOME/.sdkman}/candidates/java/current"
  fi
fi

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

# 2. Build or Download Scala Native Core Engine
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

echo "[2/4] Ensuring Scala native engine (cumulus-engine) is installed..."
DATA_BIN_DIR="${XDG_DATA_HOME:-$HOME/.local/share}/nvim/cumulus/bin"
DATA_BIN="$DATA_BIN_DIR/cumulus-engine"

if has_cmd cumulus-engine || [ -x "$DATA_BIN" ]; then
  echo "  ✔ cumulus-engine binary is already installed and ready."
elif has_cmd sbt && [ -d "$REPO_DIR/engine" ]; then
  echo "  → Compiling native engine locally via sbt..."
  if (cd "$REPO_DIR/engine" && sbt test); then
    echo "  ✔ cumulus-engine verified with unit test suite!"
  else
    echo "  ⚠ Failed to build via sbt, attempting pre-built binary download fallback..."
  fi
fi

# If binary is still not installed, download the pre-built binary
if ! has_cmd cumulus-engine && [ ! -x "$DATA_BIN" ]; then
  echo "  → Downloading pre-built cumulus-engine binary from GitHub Releases..."
  OS="$(uname -s)"
  ARCH="$(uname -m)"
  TARGET=""
  
  if [ "$OS" = "Linux" ]; then
    if [ "$ARCH" = "x86_64" ]; then
      TARGET="cumulus-engine-linux-x86_64"
    elif [ "$ARCH" = "aarch64" ] || [ "$ARCH" = "arm64" ]; then
      TARGET="cumulus-engine-linux-aarch64"
    fi
  elif [ "$OS" = "Darwin" ]; then
    if [ "$ARCH" = "arm64" ] || [ "$ARCH" = "aarch64" ]; then
      TARGET="cumulus-engine-darwin-arm64"
    fi
  fi

  if [ -n "$TARGET" ] && has_cmd curl; then
    mkdir -p "$DATA_BIN_DIR"
    TMP_DIR="$(mktemp -d)"
    BASE_URL="https://github.com/petrolal/cumulus.nvim/releases/latest/download"
    
    if curl -fsSL "$BASE_URL/$TARGET" -o "$TMP_DIR/$TARGET" 2>/dev/null; then
      mv "$TMP_DIR/$TARGET" "$DATA_BIN"
      chmod +x "$DATA_BIN"
      echo "  ✔ cumulus-engine downloaded and installed to $DATA_BIN"
    else
      echo "  ℹ Pre-built binary release download skipped or unavailable. You can compile via 'cd engine && sbt test' or run ':CumulusInstallEngine' inside Neovim."
    fi
    rm -rf "$TMP_DIR"
  else
    echo "  ℹ Automatic download skipped for platform ($OS $ARCH). Use ':CumulusInstallEngine' inside Neovim or build with sbt."
  fi
fi

# 3. Symlink configuration into ~/.config/cumulus
echo "[3/5] Linking Cumulus configuration into ${XDG_CONFIG_HOME:-$HOME/.config}/cumulus..."
CUMULUS_CONFIG_DIR="${XDG_CONFIG_HOME:-$HOME/.config}/cumulus"
mkdir -p "$CUMULUS_CONFIG_DIR"
ln -sf "$REPO_DIR/init.lua" "$CUMULUS_CONFIG_DIR/init.lua"
ln -sfn "$REPO_DIR/lua" "$CUMULUS_CONFIG_DIR/lua"
echo "  ✔ Linked init.lua and lua/ into $CUMULUS_CONFIG_DIR"

# 4. Sync lazy.nvim Plugins
echo "[4/5] Initializing and syncing Neovim plugins..."
if NVIM_APPNAME=cumulus nvim --headless "+Lazy! sync" +qa; then
  echo "  ✔ Plugins synced successfully!"
else
  echo "  ⚠ Lazy sync encountered warnings or non-zero exit code."
fi

# 5. Configure 'cn' Alias and Launcher
echo "[5/5] Setting up 'cn' command launcher and shell alias..."
LOCAL_BIN="${HOME}/.local/bin"
mkdir -p "$LOCAL_BIN"

CN_LAUNCHER="$LOCAL_BIN/cn"
cat << 'EOF' > "$CN_LAUNCHER"
#!/usr/bin/env bash
if [ -f "${XDG_CONFIG_HOME:-$HOME/.config}/cumulus/init.lua" ]; then
  exec env NVIM_APPNAME=cumulus nvim "$@"
elif [ -f "${XDG_CONFIG_HOME:-$HOME/.config}/nvim/init.lua" ]; then
  exec nvim "$@"
elif [ -f "$PWD/init.lua" ] && [ -d "$PWD/lua/cumulus" ]; then
  exec nvim -u "$PWD/init.lua" "$@"
else
  exec nvim "$@"
fi
EOF
chmod +x "$CN_LAUNCHER"
echo "  ✔ Created launcher script at $CN_LAUNCHER"

# Add alias to shell rc files if not already present
for RC in "$HOME/.bashrc" "$HOME/.zshrc"; do
  if [ -f "$RC" ]; then
    sed -i '/alias cn=/d' "$RC" 2>/dev/null || true
    echo "" >> "$RC"
    echo "# Cumulus Neovim alias" >> "$RC"
    echo "alias cn='NVIM_APPNAME=cumulus nvim'" >> "$RC"
    echo "  ✔ Configured 'cn' alias in $(basename "$RC")"
  fi
done

# 5. Verification
echo "[5/5] Running installation health check..."
if bash "$REPO_DIR/scripts/validate.sh"; then
  echo "=================================================="
  echo " 🎉 Cumulus Neovim installed and verified! 🎉 "
  echo "=================================================="
  echo ""
  echo "Quick Start:"
  echo "  1. Launcher command: 'cn' or 'nvim'"
  echo "  2. Custom appname  : NVIM_APPNAME=cumulus nvim"
  echo "  3. In Neovim, run  : ':checkhealth cumulus' to verify health."
else
  echo "  ⚠ Health check reported warnings/failures. Please inspect output above."
fi
