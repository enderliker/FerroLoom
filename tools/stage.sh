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

# FerroFabric-API lives beside the repo; when present, build it and stage it as the builtin mod.
API_DIR="$REPO/../FerroFabric-API"
if [ -x "$API_DIR/build.sh" ]; then
    bash "$API_DIR/build.sh" jar
    # The binary jar, never the sources jar: `ls | head -1` under the C locale sorts
    # "-sources.jar" first, and an empty class path silently breaks the builtin mod.
    API_JAR=$(find "$API_DIR/build/libs" -maxdepth 1 -name 'ferrofabric-api-*.jar' \
        ! -name '*-sources.jar' 2>/dev/null | sort | tail -1)
    if [ -n "$API_JAR" ]; then
        cp "$API_JAR" "$LIB/ferrofabric-api.jar"
    fi
fi
echo "staged: $LIB and $MODS"
