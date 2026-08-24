#!/usr/bin/env bash
# Cumulus Neovim: Unified Installation Script
# Single entry point for all setup: cn install

set -euo pipefail

has_cmd() {
  command -v "$1" >/dev/null 2>&1
}

# Determine repository root
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

# ============================================================================
# PART 1: System Dependencies
# ============================================================================
check_and_install_deps() {
  echo "=================================================="
  echo "        Cumulus Neovim: Full Installation        "
  echo "=================================================="
  echo ""
  echo "[1/5] Checking system dependencies..."

  # Detect OS and package manager
  OS_TYPE="$(uname -s)"
  PKG_MGR=""

  case "$OS_TYPE" in
    Linux*)
      if has_cmd pacman; then PKG_MGR="pacman"
      elif has_cmd apt-get; then PKG_MGR="apt"
      elif has_cmd dnf; then PKG_MGR="dnf"
      elif has_cmd yum; then PKG_MGR="yum"
      fi
      ;;
    Darwin*)
      if has_cmd brew; then PKG_MGR="brew"; fi
      ;;
  esac

  echo "  OS: $OS_TYPE | Package Manager: ${PKG_MGR:-none}"

  # Source SDKMAN if Java is missing
  if ! has_cmd java; then
    if [ -s "${SDKMAN_DIR:-$HOME/.sdkman}/bin/sdkman-init.sh" ]; then
      source "${SDKMAN_DIR:-$HOME/.sdkman}/bin/sdkman-init.sh" 2>/dev/null || true
    elif [ -d "${SDKMAN_DIR:-$HOME/.sdkman}/candidates/java/current/bin" ]; then
      export PATH="${SDKMAN_DIR:-$HOME/.sdkman}/candidates/java/current/bin:$PATH"
      export JAVA_HOME="${SDKMAN_DIR:-$HOME/.sdkman}/candidates/java/current"
    fi
  fi

  # Check what's missing
  MISSING_PKGS=()
  REQUIRED_TOOLS=("git" "nvim" "rg" "java")

  for tool in "${REQUIRED_TOOLS[@]}"; do
    if ! has_cmd "$tool"; then
      case "$tool" in
        git) MISSING_PKGS+=("git") ;;
        nvim) MISSING_PKGS+=("neovim") ;;
        rg) MISSING_PKGS+=("ripgrep") ;;
        java)
          case "$PKG_MGR" in
            pacman) MISSING_PKGS+=("jdk21-openjdk") ;;
            apt) MISSING_PKGS+=("openjdk-21-jdk") ;;
            dnf|yum) MISSING_PKGS+=("java-21-openjdk-devel") ;;
            brew) MISSING_PKGS+=("openjdk@21") ;;
          esac
          ;;
      esac
    fi
  done

  # Install missing packages
  if [ ${#MISSING_PKGS[@]} -gt 0 ]; then
    echo "  → Installing missing packages: ${MISSING_PKGS[*]}"

    run_sudo() {
      if [ "$(id -u)" -eq 0 ]; then
        "$@"
      elif has_cmd sudo; then
        sudo "$@"
      else
        "$@"
      fi
    }

    case "$PKG_MGR" in
      pacman)
        run_sudo pacman -S --noconfirm --needed "${MISSING_PKGS[@]}" || true
        ;;
      apt)
        run_sudo apt-get update -y || true
        run_sudo apt-get install -y "${MISSING_PKGS[@]}" || true
        ;;
      dnf|yum)
        run_sudo "$PKG_MGR" install -y "${MISSING_PKGS[@]}" || true
        ;;
      brew)
        brew install "${MISSING_PKGS[@]}" || true
        ;;
    esac
  else
    echo "  ✔ All system dependencies installed"
  fi

  # Verify critical tools
  if ! has_cmd nvim; then
    echo "  ✖ Neovim is required but not installed. Please install Neovim >= 0.10"
    return 1
  fi

  echo "  ✔ Dependencies verified"
}

# ============================================================================
# PART 2: Cumulus Engine
# ============================================================================
install_engine() {
  echo "[2/5] Installing cumulus-engine..."

  DATA_BIN_DIR="${XDG_DATA_HOME:-$HOME/.local/share}/nvim/cumulus/bin"
  DATA_BIN="$DATA_BIN_DIR/cumulus-engine"

  if has_cmd cumulus-engine; then
    echo "  ✔ cumulus-engine already in PATH"
    return 0
  fi

  if [ -x "$DATA_BIN" ]; then
    echo "  ✔ cumulus-engine already installed at $DATA_BIN"
    return 0
  fi

  # Try to build locally if sbt is available
  if has_cmd sbt && [ -d "$REPO_DIR/engine" ]; then
    echo "  → Building locally with sbt..."
    if (cd "$REPO_DIR/engine" && sbt test >/dev/null 2>&1); then
      echo "  ✔ Engine built and tested locally"
      return 0
    fi
  fi

  # Download pre-built binary
  echo "  → Downloading pre-built binary..."
  OS="$(uname -s)"
  ARCH="$(uname -m)"
  TARGET=""

  case "$OS:$ARCH" in
    Linux:x86_64) TARGET="cumulus-engine-linux-x86_64" ;;
    Linux:aarch64|Linux:arm64) TARGET="cumulus-engine-linux-aarch64" ;;
    Darwin:arm64|Darwin:aarch64) TARGET="cumulus-engine-darwin-arm64" ;;
  esac

  if [ -n "$TARGET" ] && has_cmd curl; then
    mkdir -p "$DATA_BIN_DIR"
    TMP_DIR="$(mktemp -d)"
    BASE_URL="https://github.com/petrolal/cumulus.nvim/releases/latest/download"

    if curl -fsSL "$BASE_URL/$TARGET" -o "$TMP_DIR/$TARGET" 2>/dev/null; then
      mv "$TMP_DIR/$TARGET" "$DATA_BIN"
      chmod +x "$DATA_BIN"
      echo "  ✔ Engine installed to $DATA_BIN"
      rm -rf "$TMP_DIR"
      return 0
    fi
    rm -rf "$TMP_DIR"
  fi

  echo "  ⚠ Could not auto-install engine. Run inside Neovim: :CumulusInstallEngine"
}

# ============================================================================
# PART 3: Configuration
# ============================================================================
setup_config() {
  echo "[3/5] Setting up Neovim configuration..."

  NVIM_CONFIG="${XDG_CONFIG_HOME:-$HOME/.config}/nvim"

  # Backup existing config if not already a cumulus symlink
  if [ -e "$NVIM_CONFIG" ] && [ ! -L "$NVIM_CONFIG" ]; then
    BACKUP="$NVIM_CONFIG.backup.$(date +%s)"
    echo "  ⚠ Backing up existing nvim config to $BACKUP"
    mv "$NVIM_CONFIG" "$BACKUP"
  fi

  # Remove old symlink if pointing elsewhere
  if [ -L "$NVIM_CONFIG" ]; then
    rm "$NVIM_CONFIG"
  fi

  # Create symlink to cumulus repo
  mkdir -p "$(dirname "$NVIM_CONFIG")"
  ln -sf "$REPO_DIR" "$NVIM_CONFIG"
  echo "  ✔ Configuration linked: $NVIM_CONFIG → $REPO_DIR"
}

# ============================================================================
# PART 4: Plugins
# ============================================================================
sync_plugins() {
  echo "[4/5] Syncing Neovim plugins..."

  if nvim --headless "+Lazy! sync" +qa 2>/dev/null; then
    echo "  ✔ Plugins synced"
  else
    echo "  ⚠ Plugin sync encountered issues (non-critical, can retry in nvim)"
  fi
}

# ============================================================================
# PART 5: Launcher
# ============================================================================
setup_launcher() {
  echo "[5/5] Setting up 'cn' command..."

  LOCAL_BIN="${HOME}/.local/bin"
  mkdir -p "$LOCAL_BIN"

  CN_LAUNCHER="$LOCAL_BIN/cn"
  cat << 'EOF' > "$CN_LAUNCHER"
#!/usr/bin/env bash
# Cumulus Neovim Launcher
# Usage: cn [install|update|command...]

REPO_DIR=""
if [ -f "${XDG_CONFIG_HOME:-$HOME/.config}/nvim/init.lua" ]; then
  REPO_DIR="$(readlink -f "${XDG_CONFIG_HOME:-$HOME/.config}/nvim")"
fi

case "${1:-}" in
  setup|sync)
    if [ -z "$REPO_DIR" ]; then
      echo "✖ Cumulus Neovim not found. Clone the repo and run: bash bootstrap.sh"
      exit 1
    fi
    exec bash "$REPO_DIR/scripts/install-cn.sh"
    ;;
  *)
    # Just launch nvim with cumulus config
    exec nvim "$@"
    ;;
esac
EOF
  chmod +x "$CN_LAUNCHER"
  echo "  ✔ Command created: $CN_LAUNCHER"

  # Add to PATH if needed
  if ! echo "$PATH" | grep -q "$LOCAL_BIN"; then
    for RC in "$HOME/.bashrc" "$HOME/.zshrc" "$HOME/.fish/config.fish"; do
      if [ -f "$RC" ]; then
        if ! grep -q "export PATH.*\.local/bin" "$RC" 2>/dev/null; then
          {
            echo ""
            echo "# Add ~/.local/bin to PATH for Cumulus"
            echo "export PATH=\"\$HOME/.local/bin:\$PATH\""
          } >> "$RC"
        fi
      fi
    done
    echo "  ✔ Added ~/.local/bin to shell PATH"
  fi

  echo ""
  echo "  Quick start:"
  echo "    • Launch Cumulus: nvim"
  echo "    • Check status: cn status"
  echo "    • Update: cn setup"
  echo "    • Get help: cn --help"
}

# ============================================================================
# VERIFICATION
# ============================================================================
verify_install() {
  echo ""
  echo "=================================================="

  local HEALTH_SCRIPT="$REPO_DIR/scripts/validate.sh"
  if [ -f "$HEALTH_SCRIPT" ]; then
    if bash "$HEALTH_SCRIPT"; then
      echo "=================================================="
      echo "  🎉 Cumulus Neovim Ready! 🎉"
      echo "=================================================="
      echo ""
      echo "Next steps:"
      echo "  1. Launch Neovim: nvim"
      echo "  2. Check health: :checkhealth cumulus"
      echo "  3. Read docs: :help cumulus"
      return 0
    fi
  fi

  echo "=================================================="
  echo "  ⚠ Installation complete, but health check had issues"
  echo "  Run inside nvim: :checkhealth cumulus"
  echo "=================================================="
}

# ============================================================================
# MAIN
# ============================================================================
main() {
  check_and_install_deps || exit 1
  install_engine || true
  setup_config
  sync_plugins || true
  setup_launcher
  verify_install
}

main "$@"
