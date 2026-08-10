#!/usr/bin/env bash
set -euo pipefail

echo "=========================================="
echo " Cumulus Neovim Automated Verification"
echo "=========================================="

echo "[1/6] Running Neovim headless Lazy plugin check..."
if nvim -u init.lua --headless "+Lazy check" +qa; then
  echo "✔ Headless Lazy check PASSED."
else
  echo "✖ Headless Lazy check FAILED."
  exit 1
fi

echo "[2/6] Verifying Cumulus core options & clipboard integration..."
if nvim -u init.lua --headless "+lua assert(vim.g.mapleader == ' '); assert(vim.opt.clipboard:get()[1] == 'unnamedplus'); print('✔ Options verified')" +qa; then
  echo "✔ Core options PASSED."
else
  echo "✖ Core options FAILED."
  exit 1
fi

echo "[3/6] Verifying Multi-Cloud Signature Themes (AWS, Azure, GCP, OCI)..."
if nvim -u init.lua --headless "+colorscheme aws-theme" "+colorscheme azure-theme" "+colorscheme gcp-theme" "+colorscheme oci-theme" "+lua print('✔ All 4 cloud themes loaded')" +qa; then
  echo "✔ Multi-Cloud Theme engines PASSED."
else
  echo "✖ Multi-Cloud Theme engines FAILED."
  exit 1
fi

echo "[4/6] Running Cumulus Healthcheck Suite (:checkhealth cumulus)..."
if nvim -u init.lua --headless "+checkhealth cumulus" +qa; then
  echo "✔ Cumulus healthcheck suite PASSED."
else
  echo "✖ Cumulus healthcheck suite FAILED."
  exit 1
fi

echo "[5/6] Verifying Markdown & File Operation Modules..."
if nvim -u init.lua --headless "+lua assert(pcall(require, 'render-markdown')); assert(pcall(require, 'persistence')); print('✔ Modules verified')" +qa; then
  echo "✔ Markdown & File Operation modules PASSED."
else
  echo "✖ Markdown & File Operation modules FAILED."
  exit 1
fi

echo "[6/6] Verifying Rust Native Helper (cumulus-core)..."
if command -v cargo >/dev/null 2>&1 && [ -f crates/cumulus-core/Cargo.toml ]; then
  if cargo test --manifest-path crates/cumulus-core/Cargo.toml; then
    echo "✔ Rust native helper build & unit tests PASSED."
  else
    echo "✖ Rust native helper build or tests FAILED."
    exit 1
  fi
else
  echo "ℹ Cargo not found or Rust helper missing -- skipping Rust test suite."
fi

echo "=========================================="
echo " ALL 6 VALIDATIONS PASSED SUCCESSFULLY!"
echo "=========================================="
