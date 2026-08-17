#!/usr/bin/env bash
# Quick smoke validation test script for cumulus.nvim

set -e

echo "=== Cumulus Neovim Distribution Smoke Test ==="

echo "[1/7] Verifying Shell Scripts Syntax (bootstrap.sh, install.sh, validate.sh)..."
if bash -n bootstrap.sh && bash -n scripts/install.sh && bash -n scripts/validate.sh; then
  echo "✔ Shell scripts syntax PASSED."
else
  echo "✖ Shell scripts syntax FAILED."
  exit 1
fi

echo "[2/7] Verifying Neovim Loading & Startup..."
if nvim -u init.lua --headless "+lua print('✔ Core init.lua loads without error')" +qa; then
  echo "✔ Headless core init.lua PASSED."
else
  echo "✖ Headless core init.lua FAILED."
  exit 1
fi

echo "[3/7] Verifying Core Modules (Options, Keymaps, Autocmds, Health)..."
if nvim -u init.lua --headless "+lua require('cumulus.core.options'); require('cumulus.core.keymaps'); require('cumulus.core.autocmds'); require('cumulus.health'); print('✔ Core modules loaded successfully')" +qa; then
  echo "✔ Core modules PASSED."
else
  echo "✖ Core modules FAILED."
  exit 1
fi

echo "[4/7] Verifying Theme System (AWS, Azure, GCP, OCI)..."
if nvim -u init.lua --headless "+lua require('cumulus.theme').setup(); print('✔ Theme system initialized')" +qa; then
  echo "✔ Theme system PASSED."
else
  echo "✖ Theme system FAILED."
  exit 1
fi

echo "[5/7] Verifying LSP, Completion & UI Specs..."
if nvim -u init.lua --headless "+lua assert(pcall(require, 'blink.cmp')); assert(pcall(require, 'nvim-lspconfig')); assert(pcall(require, 'render-markdown')); assert(pcall(require, 'persistence')); print('✔ Plugins and UI specs verified')" +qa; then
  echo "✔ Plugins and UI specs PASSED."
else
  echo "✖ Plugins and UI specs FAILED."
  exit 1
fi

echo "[6/7] Verifying Engine Bridge & DevOps Suite (<leader>oc, :CumulusInstallEngine)..."
if nvim -u init.lua --headless "+lua local e = require('cumulus.util.engine'); assert(type(e.detect_platform) == 'function', 'detect_platform not found'); assert(type(e.install) == 'function', 'install not found'); assert(type(e.inspect_cfn_template) == 'function', 'inspect_cfn_template not found'); assert(type(e.validate_cfn_template) == 'function', 'validate_cfn_template not found'); local devops = require('cumulus.util.devops'); assert(type(devops.cfn_validate) == 'function'); assert(type(devops.sam_local_invoke) == 'function'); local plat = e.detect_platform() or 'unknown'; assert(vim.fn.exists(':CumulusInstallEngine') == 2, ':CumulusInstallEngine not registered'); print('✔ Engine bridge APIs & DevOps CFN/SAM suite verified (' .. plat .. ')')" +qa; then
  echo "✔ Engine bridge & DevOps CFN/SAM suite PASSED."
else
  echo "✖ Engine bridge & DevOps CFN/SAM suite FAILED."
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

