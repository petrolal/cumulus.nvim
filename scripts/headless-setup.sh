#!/usr/bin/env bash
set -euo pipefail

# Enterprise headless bootstrap for TetraVim
# Set environment variable to indicate headless mode
export TETRAVIM_HEADLESS=1

# Run Neovim headlessly to sync plugins without UI
nvim --headless -u init.lua "+Lazy sync" "+qa"
