#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
ST_SERVER_JAR="$HYTALE_SERVER_JAR" python3 tools/build.py
ST_SERVER_JAR="$HYTALE_SERVER_JAR" python3 tools/check.py
