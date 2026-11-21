#!/usr/bin/env bash
# Downloads the Java runtime FerroLoom embeds (development only; nothing here ships in the .hmod).
set -euo pipefail
REPO="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$REPO/java/.runtime"
FABRIC="$OUT/fabric"
MC="$OUT/minecraft"
mkdir -p "$FABRIC" "$MC"

get() { # url dest
  local url="$1" dest="$2"
  if [ -s "$dest" ]; then echo "have $(basename "$dest")"; return; fi
  echo "fetch $(basename "$dest")"
  curl -fsSL --retry 3 -o "$dest.part" "$url"
  mv "$dest.part" "$dest"
}

M=https://maven.fabricmc.net
get "$M/net/fabricmc/fabric-loader/0.20.1/fabric-loader-0.20.1.jar" "$FABRIC/fabric-loader-0.20.1.jar"
get "$M/net/fabricmc/sponge-mixin/0.17.5+mixin.0.8.7/sponge-mixin-0.17.5+mixin.0.8.7.jar" "$FABRIC/sponge-mixin.jar"
get "$M/io/github/llamalad7/mixinextras-fabric/0.5.5/mixinextras-fabric-0.5.5.jar" "$FABRIC/mixinextras.jar"
for a in asm asm-analysis asm-commons asm-tree asm-util; do
  get "$M/org/ow2/asm/$a/9.10.1/$a-9.10.1.jar" "$FABRIC/$a.jar"
done
get "$M/net/fabricmc/tiny-remapper/0.15.0/tiny-remapper-0.15.0.jar" "$FABRIC/tiny-remapper.jar"
get "$M/net/fabricmc/tiny-mappings-parser/0.3.0+build.17/tiny-mappings-parser-0.3.0+build.17.jar" "$FABRIC/tiny-mappings-parser.jar"
# Brigadier is what mods compile against for commands; the 26.3 game lists 1.3.11 (MIT).
get "https://libraries.minecraft.net/com/mojang/brigadier/1.3.11/brigadier-1.3.11.jar" "$FABRIC/brigadier.jar"

# Minecraft 26.3 is unobfuscated; the client jar is API metadata for the shim, never code.
if [ ! -s "$MC/version.json" ]; then
  curl -fsSL --retry 3 "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json" -o "$MC/manifest.json"
  URL=$(python3 - "$MC/manifest.json" <<'PY'
import json,sys
d=json.load(open(sys.argv[1]))
print(next(v["url"] for v in d["versions"] if v["id"]=="26.3"))
PY
)
  curl -fsSL --retry 3 "$URL" -o "$MC/version.json.part" && mv "$MC/version.json.part" "$MC/version.json"
fi
if [ ! -s "$MC/client.jar" ]; then
  URL=$(python3 - "$MC/version.json" <<'PY'
import json,sys
print(json.load(open(sys.argv[1]))["downloads"]["client"]["url"])
PY
)
  curl -fsSL --retry 3 "$URL" -o "$MC/client.jar.part" && mv "$MC/client.jar.part" "$MC/client.jar"
fi
echo "runtime ready at $OUT"
