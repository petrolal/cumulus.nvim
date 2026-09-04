#!/usr/bin/env bash
set -euo pipefail

# ---------------------------------------------------------------------------
# TetraVim enterprise headless setup (Epic 5, Story 5.2)
#
# A non-interactive, CI-friendly install: it syncs plugins, installs the
# Mason tool-chain and the Tree-sitter parsers TetraVim relies on, then
# prints a machine-readable health snapshot. No UI is ever opened.
#
# `set -euo pipefail` aborts on a hard failure (plugin sync, Neovim missing)
# with a non-zero exit; the best-effort steps (Mason tools, parsers) log a
# warning and continue so a transient registry hiccup does not fail the whole
# provisioning run. When any best-effort step is skipped the run still exits 0
# but ends with an explicit "DEGRADED" summary naming what did not install.
# ---------------------------------------------------------------------------

# Resolve through symlinks so invoking the script via a symlinked bin dir
# still finds the real repo root.
SOURCE="${BASH_SOURCE[0]}"
if command -v readlink >/dev/null 2>&1; then
	RESOLVED="$(readlink -f "$SOURCE" 2>/dev/null || true)"
	[ -n "$RESOLVED" ] && SOURCE="$RESOLVED"
fi
SCRIPT_DIR="$(cd "$(dirname "$SOURCE")/.." && pwd)"
INIT_LUA="$SCRIPT_DIR/init.lua"

export TETRAVIM_HEADLESS=1

# Best-effort steps that logged a WARNING rather than aborting are tallied
# here so the run ends with an honest degraded-vs-clean summary.
DEGRADED=()

log() { printf '[headless-setup] %s\n' "$*"; }

if ! command -v nvim >/dev/null 2>&1; then
	log "ERROR: 'nvim' not found on \$PATH."
	exit 1
fi

log "Neovim:  $(nvim --version | head -n 1)"
log "Config:  $INIT_LUA"

log "1/4  Syncing plugins (Lazy)..."
nvim --headless -u "$INIT_LUA" "+Lazy! sync" "+qa"

log "2/4  Installing Mason tool-chain..."
if nvim --headless -u "$INIT_LUA" "+MasonToolsInstall" "+qa!"; then
	log "     Mason tools installed."
else
	log "     WARNING: MasonToolsInstall reported errors -- run ':MasonToolsInstall' inside nvim to inspect."
	DEGRADED+=("Mason tool-chain")
fi

log "3/4  Installing Tree-sitter parsers..."
if nvim --headless -u "$INIT_LUA" \
	-c "Lazy! load nvim-treesitter" \
	-c "lua require('nvim-treesitter.install').install({ 'java', 'kotlin', 'scala', 'lua', 'regex' }):wait(300000)" \
	-c "qa!"; then
	log "     Parsers installed."
else
	log "     WARNING: parser install reported errors -- run ':TSInstall java kotlin scala' inside nvim."
	DEGRADED+=("Tree-sitter parsers")
fi

log "4/4  Health snapshot (machine-readable JSON):"
nvim --headless -u "$INIT_LUA" \
	-c "lua io.write(require('tetravim.core.health').json() .. '\n')" \
	-c "qa!"

if [ "${#DEGRADED[@]}" -gt 0 ]; then
	log "Headless setup complete (DEGRADED): best-effort step(s) failed -- ${DEGRADED[*]}."
	log "Plugins synced and a health snapshot was produced; re-run the step(s) above or inspect interactively."
else
	log "Headless setup complete (all steps clean). Verify interactively with:  nvim +checkhealth"
fi
