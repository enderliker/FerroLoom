# FerroFabric-API deviations (Plan 3)

FerroFabric-API is FerroLoom's counterpart of Sinytra Connector's Forgified Fabric API: the API a
Fabric mod links against, implemented over the FerroLoom shim and the engine bridge. It lives
beside the repository (`../FerroFabric-API`) and is built into
`ferrofabric-api-26.3-ferro.1.jar`, which `tools/stage.sh` copies to
`~/.local/share/horizon/ferroloom-lib/ferrofabric-api.jar`.

This file records what differs from upstream Fabric API in the modules present. Modules are added
as a target mod needs them and must compile against the shim (design §6.1 and §6.8).

## Modules present

- **`fabric-event`** (`net.fabricmc.fabric.api.event.Event`): `register(T)` as upstream, plus a
  `dispatch(Consumer<T>)` the owning module fires. Upstream's `invoker()` factory is not present.
  A failing listener is logged and the others still run, so one broken mod cannot stop the rest.
- **`fabric-key-binding-api-v1`** (`KeyMappingHelper.registerKeyMapping`): registers the mapping
  with the engine's `Registrar`. Upstream keeps it in a static list; here the mapping's
  translation key (`key.<mod>.<key>`) names the mod and key, and the 26.3 key code becomes a
  Horizon key name. 26.3 uses SDL/HID scancodes, so `6` is `key.keyboard.c` and `25` is
  `key.keyboard.v` (not GLFW's 67/86). The key appears in Options > Controls and can be rebound;
  the engine polls the player's binding.
- **`fabric-lifecycle-events-v1`** (`ClientTickEvents.END_CLIENT_TICK`): fired from the engine's
  client tick, after the pushed key state, through `FerroEvents`. `START_CLIENT_TICK`, world load
  and unload events, and the server side are not present yet.

## Deviations that affect mods

1. **Client entrypoints run during Registration, not at `client_setup`.** Horizon accepts key and
   capability declarations only through `&mut dyn Registrar` in the Registration phase, so a mod
   that registers its key in `onInitializeClient` — the normal Fabric pattern — needs its client
   entrypoint to run there. `FerroBootstrap.start` invokes `main` and then `client` entrypoints
   before returning the registration report; `client_setup` only records that the client half is
   ready.
2. **Key registration is idempotent per (mod, key).** Constructing a `KeyMapping` registers it,
   and `KeyMappingHelper.registerKeyMapping` confirms the same binding, so either pattern works
   and the first binding (with its real default key) wins.
3. **The builtin mod id is `fabric-api`, version `26.3-ferro.1`.** The provider adds the jar to
   Knot and to the mod list as a `BuiltinMod` (`-Dferro.apiJar`), so a mod's
   `depends: fabric-api` resolves without the jar being copied into the mods folder.
4. **No classpath mod discovery is relied on.** The API jar is not in the mods folder; it is
   compiled and staged separately, which keeps Fabric Loader's mod list deterministic.

## Known inert mixins

A mixin only runs when the engine calls the method it targets. ZoomX 26.3's
`MouseHandler.onScroll(JDD)V` and `ItemInHandRenderer.submitHandsWithItems` mixins apply to shim
methods the engine never calls, so scroll-to-zoom and hiding the hands do nothing; the key zoom
and the `Camera.calculateFov` mixin work. The general `FerroUninvokedMixinWarning` of design §6.5
and §8.1.6 is not implemented yet; until it is, this file names the known cases.

## The FOV upcall is a callback window

`GameBridge.computeFovModifier` enters the same read window as a tick, so a `calculateFov` mixin
may read the position pushed before the tick. Outside the hook (and outside a tick), engine reads
still fail with `FerroNotInCallbackException`.

## What is not here yet

Screen API, rendering and HUD modules, item/registry modules, the resource loader, networking
events and every server-side event. `docs/superpowers/specs/2026-11-19-ferroloom-design.md`
§6.1/§6.8 lists the order; the full fork is Plan 4. A module whose upstream source does not
compile against the shim does not enter, and the reason is recorded here when it is attempted.
