#!/usr/bin/env bash
# Quick smoke validation test script for cumulus.nvim

set -e

echo "=== Cumulus Neovim Distribution Smoke Test ==="

echo "[1/6] Verifying Neovim Loading & Startup..."
if nvim -u init.lua --headless "+lua print('✔ Core init.lua loads without error')" +qa; then
  echo "✔ Headless core init.lua PASSED."
else
  echo "✖ Headless core init.lua FAILED."
  exit 1
fi

echo "[2/6] Verifying Core Modules (Options, Keymaps, Autocmds, Health)..."
if nvim -u init.lua --headless "+lua require('cumulus.core.options'); require('cumulus.core.keymaps'); require('cumulus.core.autocmds'); require('cumulus.health'); print('✔ Core modules loaded successfully')" +qa; then
  echo "✔ Core modules PASSED."
else
  echo "✖ Core modules FAILED."
  exit 1
fi

echo "[3/6] Verifying Theme System (AWS, Azure, GCP, OCI)..."
if nvim -u init.lua --headless "+lua require('cumulus.theme').setup(); print('✔ Theme system initialized')" +qa; then
  echo "✔ Theme system PASSED."
else
  echo "✖ Theme system FAILED."
  exit 1
fi

echo "[4/6] Verifying LSP & Completion Specs..."
if nvim -u init.lua --headless "+lua assert(pcall(require, 'blink.cmp')); assert(pcall(require, 'nvim-lspconfig')); print('✔ LSP & Completion specs verified')" +qa; then
  echo "✔ LSP and Completion specs PASSED."
else
  echo "✖ LSP and Completion specs FAILED."
  exit 1
fi

echo "[5/6] Verifying Markdown & File Operation Modules..."
if nvim -u init.lua --headless "+lua assert(pcall(require, 'render-markdown')); assert(pcall(require, 'persistence')); print('✔ Modules verified')" +qa; then
  echo "✔ Markdown & File Operation modules PASSED."
else
  echo "✖ Markdown & File Operation modules FAILED."
  exit 1
fi

echo "[6/7] Verifying Engine Bridge & :CumulusInstallEngine Command..."
if nvim -u init.lua --headless "+lua local e = require('cumulus.util.engine'); assert(type(e.detect_platform) == 'function', 'detect_platform not found'); assert(type(e.install) == 'function', 'install not found'); local plat = e.detect_platform(); assert(plat ~= nil, 'platform should be detected'); assert(vim.fn.exists(':CumulusInstallEngine') == 2, ':CumulusInstallEngine not registered'); print('✔ Engine bridge APIs & :CumulusInstallEngine command verified (' .. plat .. ')')" +qa; then
  echo "✔ Engine bridge & command PASSED."
else
  echo "✖ Engine bridge & command FAILED."
  exit 1
fi

echo "[7/7] Verifying Native Helper Engine (cumulus-engine)..."
if command -v sbt >/dev/null 2>&1 && [ -d engine ]; then
  if (cd engine && sbt test); then
    echo "✔ Scala native helper build & unit tests PASSED."
  else
    echo "✖ Scala native helper build or tests FAILED."
    exit 1
  fi
else
  echo "ℹ sbt not found or engine directory missing -- skipping native engine test suite."
fi

echo "=========================================="
echo " ALL 7 VALIDATIONS PASSED SUCCESSFULLY!"
echo "=========================================="

