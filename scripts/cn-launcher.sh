#!/usr/bin/env bash
# cn - Simple Cumulus Neovim Launcher
# Just opens files and launches Neovim - no CLI logic
# Usage: cn [files or nvim args]

exec nvim "$@"
