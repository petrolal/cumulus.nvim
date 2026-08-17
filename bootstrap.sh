#!/usr/bin/env bash
set -euo pipefail

echo "=================================================="
echo "          Cumulus Neovim System Bootstrap         "
echo "=================================================="

has_cmd() {
  command -v "$1" >/dev/null 2>&1
}

# 1. Detect OS and Package Manager
OS_TYPE="$(uname -s)"
PKG_MGR=""

case "$OS_TYPE" in
  Linux*)
    if has_cmd pacman; then
      PKG_MGR="pacman"
    elif has_cmd apt-get; then
      PKG_MGR="apt"
    elif has_cmd dnf; then
      PKG_MGR="dnf"
    elif has_cmd yum; then
      PKG_MGR="yum"
    fi
    ;;
  Darwin*)
    if has_cmd brew; then
      PKG_MGR="brew"
    else
      echo "ℹ macOS detected but Homebrew (brew) is not installed."
      echo "  Install Homebrew from https://brew.sh to enable automatic dependency installation."
    fi
    ;;
  *)
    echo "⚠ Unsupported operating system: $OS_TYPE"
    ;;
esac

echo "[1/4] Detected environment: $OS_TYPE (Package Manager: ${PKG_MGR:-none})"

# Helper for privileged commands
run_sudo() {
  if [ "$(id -u)" -eq 0 ]; then
    "$@"
  elif has_cmd sudo; then
    sudo "$@"
  else
    echo "⚠ 'sudo' not found. Attempting to run command without privileges: $*"
    "$@" || {
      echo "✖ Failed to execute: $* (requires root or sudo)"
      return 1
    }
  fi
}

# 2. Install System Packages
echo "[2/4] Ensuring core system tools are installed..."

install_packages() {
  case "$PKG_MGR" in
    pacman)
      echo "  → Installing dependencies via pacman..."
      run_sudo pacman -S --noconfirm --needed git curl ripgrep neovim jdk21-openjdk || {
        echo "  ⚠ Some packages could not be installed via pacman. Continuing..."
      }
      ;;
    apt)
      echo "  → Installing dependencies via apt-get..."
      run_sudo apt-get update -y || true
      run_sudo apt-get install -y git curl ripgrep neovim openjdk-21-jdk || {
        echo "  ⚠ Attempting fallback to default-jdk on older apt distributions..."
        run_sudo apt-get install -y git curl ripgrep neovim default-jdk || {
          echo "  ⚠ Some packages could not be installed via apt. Continuing..."
        }
      }
      ;;
    dnf|yum)
      echo "  → Installing dependencies via $PKG_MGR..."
      run_sudo "$PKG_MGR" install -y git curl ripgrep neovim java-21-openjdk-devel || {
        echo "  ⚠ Some packages could not be installed via $PKG_MGR. Continuing..."
      }
      ;;
    brew)
      echo "  → Installing dependencies via Homebrew..."
      brew install git curl ripgrep neovim openjdk@21 || {
        echo "  ⚠ Some packages could not be installed via brew. Continuing..."
      }
      ;;
    *)
      echo "  ℹ No supported package manager detected or skipped. Ensuring manual prerequisites."
      ;;
  esac
}

install_packages

# 3. Verify System Tools
echo "[3/4] Verifying installed toolchain..."

if has_cmd nvim; then
  echo "  ✔ Neovim: $(nvim --version | head -n 1)"
else
  echo "  ⚠ Neovim is not in PATH. Please install Neovim (>= 0.10 recommended)."
fi

if has_cmd git; then
  echo "  ✔ Git: $(git --version)"
else
  echo "  ⚠ Git is missing."
fi

if has_cmd rg; then
  echo "  ✔ Ripgrep: $(rg --version | head -n 1)"
else
  echo "  ℹ Ripgrep is not installed (recommended for search)."
fi

if has_cmd java; then
  echo "  ✔ Java: $(java -version 2>&1 | head -n 1)"
else
  echo "  ℹ Java is not in PATH (install Java 21+ for local engine development)."
fi

# 4. Invoke Neovim Distribution Installer
echo "[4/4] Executing Neovim distribution setup..."
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

if [ -f "$SCRIPT_DIR/scripts/install.sh" ]; then
  chmod +x "$SCRIPT_DIR/scripts/install.sh"
  bash "$SCRIPT_DIR/scripts/install.sh"
else
  echo "✖ Installer script not found at $SCRIPT_DIR/scripts/install.sh"
  exit 1
fi
