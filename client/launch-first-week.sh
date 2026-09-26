#!/usr/bin/env bash
set -euo pipefail
PZ_HELPER_DIRECTORY="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
exec python3 "$PZ_HELPER_DIRECTORY/launch.py" "$@"
