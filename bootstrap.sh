#!/usr/bin/env bash
# Cumulus Neovim Bootstrap
# Smart entry point: offers dev-init for contributors, or full installation for users

set -euo pipefail

echo "=================================================="
echo "          Cumulus Neovim Bootstrap                "
echo "=================================================="
echo ""

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Detect if running in dev mode (repo cloned locally)
if [ -d "$SCRIPT_DIR/.git" ]; then
  echo "Repository detected. Choose your setup:"
  echo ""
  echo "  1) Development (recommended for contributors)"
  echo "     bash scripts/dev-init.sh"
  echo "     • Just symlinks config + syncs plugins"
  echo "     • Quick & lightweight"
  echo "     • Perfect for editing lua/"
  echo ""
  echo "  2) Full Installation (for end users)"
  echo "     bash bootstrap.sh"
  echo "     then: cn install"
  echo "     • Installs dependencies"
  echo "     • Builds/downloads engine"
  echo "     • Full verification"
  echo ""
  echo "For development, run:"
  echo "  bash scripts/dev-init.sh"
  echo ""
  exit 0
fi

# ============================================================================
# Full Installation Path (for users, not in repo)
# ============================================================================

LOCAL_BIN="${HOME}/.local/bin"
mkdir -p "$LOCAL_BIN"

CN_LAUNCHER="$LOCAL_BIN/cn"

# Check if Coursier is available
if command -v cs >/dev/null 2>&1; then
  echo "→ Installing cn via Coursier..."

  # Try to install from local repo if available
  if [ -d "$SCRIPT_DIR/engine" ] && [ -f "$SCRIPT_DIR/engine/build.sbt" ]; then
    # Build and install locally
    cd "$SCRIPT_DIR/engine"
    sbt "cli/assembly" >/dev/null 2>&1 || true

    if [ -f "$SCRIPT_DIR/engine/cumulus-cli/target/scala-3.5.2/cumulus-cli.jar" ]; then
      cs install \
        --force \
        --directory "$LOCAL_BIN" \
        file://"$SCRIPT_DIR/engine/cumulus-cli/target/scala-3.5.2/cumulus-cli.jar" \
        --main-class cumulus.cli.CumulusCli \
        --output cn 2>/dev/null || true

      if [ -f "$CN_LAUNCHER" ] && [ -x "$CN_LAUNCHER" ]; then
        echo "✔ Installed 'cn' via Coursier"
      fi
    fi
  fi
else
  # Fallback: create shell script launcher
  echo "→ Creating shell script launcher..."
  cat << 'EOF' > "$CN_LAUNCHER"
#!/usr/bin/env bash
# Cumulus Neovim Launcher
# Usage: cn [install|update|command...]

REPO_DIR=""
if [ -f "${XDG_CONFIG_HOME:-$HOME/.config}/nvim/init.lua" ]; then
  REPO_DIR="$(readlink -f "${XDG_CONFIG_HOME:-$HOME/.config}/nvim")"
fi

case "${1:-}" in
  install|update)
    if [ -z "$REPO_DIR" ]; then
      echo "✖ Cumulus Neovim not found at ~/.config/nvim"
      echo "Clone and try again: git clone https://github.com/petrolal/cumulus.nvim ~/.config/nvim"
      exit 1
    fi
    exec bash "$REPO_DIR/scripts/install-cn.sh"
    ;;
  *)
    # Just launch nvim
    exec nvim "$@"
    ;;
esac
EOF
  chmod +x "$CN_LAUNCHER"
  echo "✔ Created 'cn' launcher (shell script)"
fi

echo "✔ 'cn' command installed at: $CN_LAUNCHER"
echo ""

# Add to PATH if needed
if ! echo "$PATH" | grep -q "${LOCAL_BIN//\//\\/}"; then
  echo "→ Adding $LOCAL_BIN to PATH..."
  for RC in "$HOME/.bashrc" "$HOME/.zshrc" "$HOME/.bash_profile"; do
    if [ -f "$RC" ]; then
      if ! grep -q "\\.local/bin" "$RC" 2>/dev/null; then
        {
          echo ""
          echo "# Cumulus: Add ~/.local/bin to PATH"
          echo "export PATH=\"\$HOME/.local/bin:\$PATH\""
        } >> "$RC"
      fi
    fi
  done
  echo "✔ PATH updated in shell configs"
  echo ""
  echo "⚠ Restart your shell or run: export PATH=\"\$HOME/.local/bin:\$PATH\""
fi

echo ""
echo "=================================================="
echo "  Next steps:"
echo "=================================================="
echo ""
echo "To install Cumulus Neovim:"
echo "  1. cumulus nvim setup     # Initialize everything"
echo "  2. cumulus nvim           # Launch Cumulus IDE"
echo ""
echo "Or use the simple launcher:"
echo "  1. cn [files]             # Just opens files in nvim"
echo "  2. nvim                   # Direct Neovim"
echo ""
