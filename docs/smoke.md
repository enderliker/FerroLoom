# In-game smoke — FerroLoom Plan 2

Checks that an unmodified Fabric mod is discovered, runs its entrypoints and answers the
engine's events inside Horizon.

## Requirements

- The `Horizon` instance with nightly `nightly-2026-11-19-5ef7734` (the mod's pin).
- The Java runtime: `./tools/fetch-runtime.sh` (Fabric Loader 0.20.1, Mixin, ASM and the
  official 26.3 jar under `java/.runtime/`).
- A JDK 21+: `JAVA_HOME` or the `/usr/lib/jvm/*` fallback.
- `horizon-launcher` (for example in `~/Downloads/horizon-launcher-linux-x86_64/`).

## Steps

```bash
cd FerroLoom
./tools/stage.sh
horizon-launcher native-build . --instance Horizon --trust-native-code
# with the Horizon MCP: play (instance Horizon, world FerroLoomSmoke)
```

`stage.sh` builds the Java jars, copies the runtime into
`~/.local/share/horizon/ferroloom-lib/` and the example mod into
`~/.local/share/horizon/ferroloom-mods/`.

## Proof of success

```bash
grep -E 'example: main|loader started|hud layer|client setup|example tick=' \
  ~/.local/share/horizon/ferroloom.log | tail
```

It must show both entrypoints, the loader start, the HUD layer and the ticks:

```
example: main entrypoint
JVM ready; loader started; 1 key(s), 1 command(s), hud=true
hud layer registered
client setup: client entrypoints ran
example tick=200 pos=-31.33,70.62,0.33
```

In the game:

- Type `/ferro-hola`: chat answers `ferro-hola from the example`.
- Hold `G`: chat receives `hello from FerroLoom`.
- The top-left corner shows the HUD line with `tick` and `pos`.

## Known failures

| Symptom | Cause | Fix |
|---|---|---|
| `... not found; set FERROLOOM_LIB_DIR ...` | A runtime jar is missing | `./tools/stage.sh` |
| `no JDK found` | No `JAVA_HOME` and no JDK in `/usr/lib/jvm` | Install a JDK 21+ |
| `intermediary names (class_*) are not loadable` | Jar built for a pre-26.3 version | Use the 26.3 jar |
| `duplicate or invalid key mapping` | Key id contains `:` | `Registrar` ids use letters, digits, `_`, `.` and `-` |
| `Class ... cannot be cast to ... ModInitializer` | The shim leaked onto the parent class path | The provider only exposes jars holding `net/minecraft` to Knot |
| `tick N failed: ...` | The bridge returned an error | Read `ferroloom.log`: the message names the cause |

## Notes

- The game and the mods are defined by the `KnotClassLoader`; the loader, its libraries and
  FerroLoom stay on the parent class path (`setValidParentClassPath`). Only the shim jar is
  game code.
- The `client` entrypoint runs at `client_setup`; `main` runs at registration. Key and command
  requests happen in `main` because FerroLoom reads the report right after it; Plan 4 moves
  that to the Fabric API.
- Every mod write (chat, log) is applied at the end of the hook, with the live host.
