# Changelog

All notable changes to FerroLoom. The version is the native mod's version in
`horizon-mod.toml`; the game version it targets is Minecraft 26.3.

## [Unreleased]

## [0.1.0] — 2026-11-20

The first working host: an embedded JVM, the real Fabric Loader, the generated shim and a real
third-party Fabric mod, through Plans 1–3 of the design.

### Added

- **Plan 1 — the JVM host.** JDK discovery, `libjvm` loading, one JVM per game process, a bridge
  thread for all JNI, the handle arena, the read window and the write queue, with actionable
  errors for a missing JDK, a missing `libjvm` and a failed VM creation.
- **Plan 2 — Fabric Loader and the shim.** Fabric Loader 0.20.1 embedded with a `horizon`
  `GameProvider`; jars discovered in `ferroloom-mods/`, namespace-verified and cached; a
  `net.minecraft.*` shim generated with ASM from the official 26.3 jar's metadata (11,383
  classes); ticks, keys, client commands and a HUD layer reaching a mod; the
  `compute_fov_modifier` call site synthesising `Camera.tickFov/getFov`; an example mod and an
  in-game smoke test.
- **Plan 3 — a third-party zoom mod.** Client entrypoints run during Registration so their keys
  reach the `Registrar`; the shim models `KeyMapping`, `Options.sensitivity`/`OptionInstance`,
  `Gui.screen` and `Mth.clamp`; `Camera.tickFov` calls `Camera.calculateFov`, so a zoom mixin at
  its `RETURN` is on the live FOV path; a minimal FerroFabric-API builtin (`fabric-api`
  `26.3-ferro.1`) with `Event`, `KeyMappingHelper` and `ClientTickEvents`; ZoomX 26.3 runs from its
  untouched jar and its key changes `fov_modifier` from `1.0` to `0.3333` in game.
- Tests: 29 Rust, 45 Java, 4 for the sibling FerroFabric-API; `cargo horizon-mod check` green.
- Documentation: the design and the three plans, `docs/smoke.md`, `docs/ferrofabric-deviations.md`,
  MIT licence and NOTICE.

### Changed

- `client` entrypoints moved from `client_setup` to Registration (see
  `docs/ferrofabric-deviations.md`); `client_setup` records readiness only.
- `KeyMapping` construction registers the key with the engine; `KeyMappingHelper` is idempotent.

### Known limitations

- Client only; dedicated servers and worker hooks are not dispatched.
- The shim is a model, not the game: members outside the implemented surface throw
  `FerroUnimplementedException` naming the member.
- FerroFabric-API is partial (three modules); the full fork is Plan 4.
- No Java rendering: screens, world render events and most HUD APIs are absent.