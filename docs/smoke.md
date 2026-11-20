# In-game smoke — FerroLoom Plans 2 and 3

Checks that an unmodified Fabric mod is discovered, runs its entrypoints and answers the
engine's events inside Horizon (Plan 2), and that a real third-party zoom mod loads with its jar
untouched and its `Camera.calculateFov` mixin changes the FOV (Plan 3).

## Requirements

- The `Horizon` instance with nightly `nightly-2026-11-19-5ef7734` (the mod's pin).
- The Java runtime: `./tools/fetch-runtime.sh` (Fabric Loader 0.20.1, Mixin, ASM and the
  official 26.3 jar under `java/.runtime/`).
- A JDK 21+: `JAVA_HOME` or the `/usr/lib/jvm/*` fallback.
- `horizon-launcher` (for example in `~/Downloads/horizon-launcher-linux-x86_64/`).
- For Plan 3, `../FerroFabric-API` beside this checkout (the minimal `fabric-api` builtin).

## Steps

```bash
cd FerroLoom
./tools/stage.sh
horizon-launcher native-build . --instance Horizon --trust-native-code
# with the Horizon MCP: play (instance Horizon, world FerroLoomSmoke)
```

`stage.sh` builds the Java jars, copies the runtime into
`~/.local/share/horizon/ferroloom-lib/`, builds `../FerroFabric-API` into
`ferrofabric-api.jar` there when the folder exists, and copies the example mod into
`~/.local/share/horizon/ferroloom-mods/`.

## Proof of success — Plan 2

```bash
grep -E 'example: main|loader started|hud layer|client setup|example tick=' \
  ~/.local/share/horizon/ferroloom.log | tail
```

It must show both entrypoints, the loader start, the HUD layer and the ticks:

```
example: main entrypoint
JVM ready; loader started; 1 key(s), 1 command(s), hud=true
hud layer registered
client setup: entrypoints ran at registration
example tick=200 pos=-31.33,70.62,0.33
```

In the game:

- Type `/ferro-hola`: chat answers `ferro-hola from the example`.
- Hold `G`: chat receives `hello from FerroLoom`.
- The top-left corner shows the HUD line with `tick` and `pos`.

## Plan 3 — an unmodified third-party zoom mod

Download `zoomx-26.3.jar` (ZoomX 26.3, MIT) from Modrinth and copy it into
`~/.local/share/horizon/ferroloom-mods/`. Nothing in the jar is edited; the cache keeps a
byte-identical copy.

```bash
cp zoomx-26.3.jar ~/.local/share/horizon/ferroloom-mods/
```

Start the game as above and read:

```bash
grep -E 'key: zoomx|fabric-api:|client setup|fov:' \
  ~/.local/share/horizon/ferroloom.log | tail
```

Expected, in this order:

```
key: zoomx/zoom -> key.keyboard.v
fabric-api: 26.3-ferro.1 initialized
JVM ready; loader started; 2 key(s), 1 command(s), hud=true
client setup: entrypoints ran at registration
fov: modifier 1.0000 (base 1.0000)
fov: modifier 0.3333 (base 1.0000)   # V held: ZoomX's mixin divided the FOV
fov: modifier 1.0000 (base 1.0000)   # V released
```

In the game, hold `V`: the world zooms in. With the Horizon MCP, `game_state` reports
`player.fov_modifier` `0.3333` while the key is held and `1.0` after release, and a screenshot
shows the narrower view. The key is listed in Options > Controls under the mod and can be
rebound (for example to mouse button 5); the engine polls the rebound key.

## Known failures

| Symptom | Cause | Fix |
|---|---|---|
| `... not found; set FERROLOOM_LIB_DIR ...` | A runtime jar is missing | `./tools/stage.sh` |
| `no JDK found` | No `JAVA_HOME` and no JDK in `/usr/lib/jvm` | Install a JDK 21+ |
| `intermediary names (class_*) are not loadable` | Jar built for a pre-26.3 version | Use the 26.3 jar |
| `duplicate or invalid key mapping` | Key id contains `:` | `Registrar` ids use letters, digits, `_`, `.` and `-` |
| `Class ... cannot be cast to ... ModInitializer` | The shim leaked onto the parent class path | The provider only exposes jars holding `net/minecraft` to Knot |
| `Mod ... requires fabric-api` | No `ferrofabric-api.jar` was staged | Build `../FerroFabric-API` with `./build.sh jar`, then re-run `stage.sh` |
| `tick N failed: ...` | The bridge returned an error | Read `ferroloom.log`: the message names the cause |

## Notes

- The game and the mods are defined by the `KnotClassLoader`; the loader, its libraries and
  FerroLoom stay on the parent class path (`setValidParentClassPath`). The shim and
  FerroFabric-API are game-facing code and are defined by Knot.
- `main` and `client` entrypoints both run at registration, because the engine only accepts key
  declarations through `Registrar` there; `client_setup` only records that the client half is
  ready. `docs/ferrofabric-deviations.md` explains the deviation.
- Every mod write (chat, log) is applied at the end of the hook, with the live host.