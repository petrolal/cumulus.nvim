#!/usr/bin/env bash
# Legacy installer wrapper - delegates to install-cn.sh
# This exists for backwards compatibility

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
exec bash "$SCRIPT_DIR/install-cn.sh" "$@"
