#!/usr/bin/env bash
# Builds the Java side and stages it where the game's fallback looks for it.
set -euo pipefail
REPO="$(cd "$(dirname "$0")/.." && pwd)"
LIB="${FERROLOOM_LIB_DIR:-$HOME/.local/share/horizon/ferroloom-lib}"
MODS="${FERROLOOM_MODS_DIR:-$HOME/.local/share/horizon/ferroloom-mods}"
FABRIC="$REPO/java/.runtime/fabric"

(cd "$REPO/java" && ./gradlew -q :ferroloom-runtime:jar :ferroloom-loader:jar :ferroloom-shim:jar)

mkdir -p "$LIB" "$MODS"
cp "$REPO/java/ferroloom-runtime/build/libs/ferroloom-runtime.jar" "$LIB/"
cp "$REPO/java/ferroloom-loader/build/libs/ferroloom-loader.jar" "$LIB/"
cp "$REPO/java/ferroloom-shim/build/libs/ferroloom-shim.jar" "$LIB/ferro-shim.jar"
cp "$FABRIC/fabric-loader-0.20.1.jar" "$FABRIC/sponge-mixin.jar" "$FABRIC/mixinextras.jar" "$LIB/"
cp "$FABRIC"/asm*.jar "$LIB/"

bash "$REPO/examples/example-mod/build.sh"
cp "$REPO/examples/example-mod/build/ferroloom-example-1.0.0.jar" "$MODS/"
echo "staged: $LIB and $MODS"
