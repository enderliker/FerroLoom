#!/usr/bin/env bash
# Compiles the example mod against the staged shim and loader API (no Loom: 26.3 is named).
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
LIB="${FERROLOOM_LIB_DIR:-$HOME/.local/share/horizon/ferroloom-lib}"
rm -rf "$HERE/build/classes"
mkdir -p "$HERE/build/classes"
mapfile -d '' SOURCES < <(find "$HERE/src" -name '*.java' -print0)
javac --release 21 \
  -cp "$LIB/ferro-shim.jar:$LIB/ferroloom-runtime.jar:$LIB/fabric-loader-0.20.1.jar" \
  -d "$HERE/build/classes" \
  "${SOURCES[@]}"
jar --create --file "$HERE/build/ferroloom-example-1.0.0.jar" \
  -C "$HERE/build/classes" . -C "$HERE" fabric.mod.json
echo "built $HERE/build/ferroloom-example-1.0.0.jar"
