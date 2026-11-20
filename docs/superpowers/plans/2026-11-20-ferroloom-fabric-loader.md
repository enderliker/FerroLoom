# FerroLoom — Plan 2: Fabric Loader, remapper and shim

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Load a real Fabric mod in Horizon: discover the jar, verify its namespace, start its entrypoints with the embedded Fabric Loader and dispatch an engine event to it, proven in the game log.

**Architecture:** The `.hmod` starts the JVM with a class path made of Fabric Loader 0.20.1, its libraries, the generated shim and `ferroloom-loader`. `FerroBootstrap` verifies `mods/` into a cache, initialises `Knot` with a `GameProvider` of its own (`horizon`) that never launches a game, invokes the `main`/`client` entrypoints and exposes static methods Rust calls through JNI on each hook. The `net.minecraft.*` shim is generated with ASM from the official 26.3 jar's metadata (names and descriptors, never bodies) and delegates to `ShimImpl`/`ShimRuntime`.

**Tech Stack:** Rust edition 2024 (the launcher's toolchain), hand-rolled JNI (vendored jni-sys), Gradle 8/9, JDK 25/27, Fabric Loader 0.20.1 (Apache-2.0), Mixin 0.17.5, ASM 9.10.1, JUnit 5, `horizon-mod-api` 5ef7734.

**Spec:** `docs/superpowers/specs/2026-11-19-ferroloom-design.md` (§6.1, §6.2, §6.5, §6.7)

## Verified facts that adjust the plan (they do not reopen the design)

1. **26.3 is unobfuscated and there is nothing to remap.** Mojang's 26.3 version JSON has no
   `client_mappings`, Fabric publishes `intermediary:0.0.0` (identity) for 26.3 and Yarn stopped
   at `1.21.11+build.6`. A 26.3 jar (Just Zoom 3.1.0) references
   `net/minecraft/client/KeyMapping`. Horizon has no pre-26.3 versions, so the
   intermediary→named remapper is dropped: the cache verifies the namespace and copies the jar.
   The shim is generated from the **official 26.3 jar's metadata** (names, hierarchy,
   descriptors; never bodies).
2. **The shim is emitted as ASM bytecode, not Java sources.** 10,737 classes with bridges,
   records and sealed hierarchies cannot be reliably round-tripped through javac; ASM reproduces
   the hierarchy and descriptors exactly. The design's intent (every class exists, anything
   unimplemented fails loudly) still holds.
3. **`impl` bodies live in `ShimImpl`** (hand-written Java with the exact descriptors) and the
   generator emits `invokestatic` calls to those methods. No arbitrary Java expressions.
4. **Implementation tables are JSON**, not TOML: Gson is already on the loader's class path and
   Java ships no TOML parser. Same content, different format.
5. Fabric Loader 0.20.1 supports 26.3 (`https://meta.fabricmc.net/v2/versions/loader/26.3`) and
   provides `fabric.skipMcProvider`, `fabric.modsFolder`, `fabric.gameVersion`,
   `fabric.gameJarPath.client`, `fabric.runtimeMappingNamespace` and
   `fabric.defaultModDistributionNamespace`.
6. **26.3 has no `GameRenderer.getFov`.** The FOV lives in `net.minecraft.client.Camera`
   (`tickFov`/`calculateFov`/`getFov`), which is exactly what Just Zoom 3.1.0 mixes into
   (checked with javap). The `compute_fov_modifier` call site synthesises `Camera.tickFov()`
   then `Camera.getFov()`.

## Global Constraints

- Rust edition 2024; toolchain 1.95. No `rust-toolchain.toml` inside the package.
- **No JDK in the `.hmod` build.** The Java build is a separate development step; `cargo horizon-mod check` never runs it.
- The `.hmod` package may contain only `horizon-mod.toml`, `Cargo.toml`, `Cargo.lock`, `build.rs`, `.rs`/`.toml` under `src/` and root `README*`/`LICENSE*`/`NOTICE*` texts. **No jars** inside the package.
- Do not run `horizon-launcher native-lock --update-lock`. The game's `Cargo.lock` is kept.
- No Mojang code or assets. The 26.3 jar is only read for names and descriptors; shim bodies are FerroLoom's or `throw`.
- Licences: FerroLoom is MIT; Fabric Loader (Apache-2.0), Mixin and MixinExtras (MIT) and ASM (BSD-3) are cited in NOTICE.
- v0.1 and this plan are **client only**. Java 25+ at runtime (26.3 declares `java-runtime-epsilon`, major 25).
- All JNI stays on the bridge thread (Plan 1 constraint). Engine reads happen on the game thread and are pushed to Java before the tick.
- A mod failure never takes the game down: it is logged with the mod id and loading continues.
- Platform: Linux x86-64.

## Review Focus

1. **A jar with `class_1234` references** (intermediary from an older version): it must be refused with a typed error naming the jar, never loaded halfway. Covered by Task 3.
2. **A jar without `fabric.mod.json`** in `mods/`: it is ignored and logged, without stopping the others. Covered by Task 3.
3. **A mixin pointing at a member the shim does not model**: Mixin must fail loudly when applying (defaultRequire 1), never be dropped silently. Covered by Tasks 4/8.
4. **A mod that throws in its entrypoint or tick listener**: FerroLoom marks it failed, removes it from dispatch and the game keeps running. Covered by Task 7.
5. **An engine read from a Java thread**: it still returns `FerroNotInCallbackException`; the new shim opens no out-of-callback read path. Covered by Task 6.

---

### Task 1: Java workspace and runtime staging

**Files:**
- Modify: `FerroLoom/java/settings.gradle.kts`
- Create: `FerroLoom/java/build.gradle.kts`
- Create: `FerroLoom/java/ferroloom-loader/build.gradle.kts`
- Create: `FerroLoom/java/ferroloom-loader/src/main/java/dev/ferroloom/loader/RuntimeLayout.java`
- Create: `FerroLoom/java/ferroloom-loader/src/test/java/dev/ferroloom/loader/RuntimeLayoutTest.java`
- Create: `FerroLoom/tools/fetch-runtime.sh`
- Modify: `FerroLoom/.gitignore` (`java/.runtime/`, `examples/**/build/`)

**Interfaces:**
- Produces: `RuntimeLayout.libDir()`, `RuntimeLayout.libJars()`; `java/.runtime` holds the loader, its libraries and the 26.3 client jar.

- [x] Steps: failing `RuntimeLayoutTest`; Gradle modules; `tools/fetch-runtime.sh` (loader 0.20.1, Mixin, MixinExtras, ASM, client jar and version JSON 26.3); pass; commit.

### Task 2: `horizon` GameProvider and loader bootstrap

**Files:**
- `HorizonGameProvider.java`, `FerroBootstrap.java`, `FerroGameMain.java`, `META-INF/services/...`, `BootstrapTest`, `TestMods`.

**Interfaces:**
- `FerroBootstrap.start()` reads `ferro.modsDir`, `ferro.cacheDir`, `ferro.shimDir`, `ferro.gameDir`; caches the mods, initialises `Knot`, invokes `main`; returns the registration report.
- `HorizonGameProvider` reports id `minecraft`, version `26.3`, runtime namespace `named`; adds the game jars to Knot in `initialize` and restricts the parent class path.

- [x] Steps: a Fabric mod in the mods folder runs its main entrypoint (real Fabric Loader 0.20.1 logged `Loading Horizon 26.3`); commit.

### Task 3: Namespace verification and named-jar cache

**Files:** `remap/Namespace.java`, `remap/JarNamespace.java`, `remap/RemapCache.java`, `remap/RemapError.java`, `RemapTest`, `JarFixtures`.

**Interfaces:**
- `JarNamespace.detect(Path) -> Namespace`; `RemapCache.ensureNamed(modsDir, cacheDir) -> List<Path>`; an intermediary jar raises `RemapError(jar, reason)`.

- [x] Steps: named jar cached by sha, original untouched; `class_*` jar refused by name; jar without metadata ignored; stale cache entries pruned (the loader reads the whole folder); commit.

### Task 4: Shim generator (ASM) and implementation tables

**Files:** `shim-generator/` (`ShimGenerator`, `ImplTables`, `ImplTableJson`, `ShimGeneratorMain`), `java/shim/impl/*.json`, `ShimGeneratorTest`, `Fixtures`.

**Interfaces:**
- `ShimGenerator.generateClass(byte[], ImplTables)`, `ShimGenerator.generate(clientJar, implDir, outJar) -> Summary`; impl rows `{"constructible": [...], "methods": {"class#method(desc)": {"impl": "owner.method"}}}`.

- [x] Steps: unimplemented methods throw with `class#member`; impl rows emit an `invokestatic`; hierarchy/fields/constructibility kept; enums keep constants and `values()`/`valueOf`; interfaces get default throwing bodies; generation follows the superclass chain; commit.

### Task 5: Full 26.3 shim and its runtime support

**Files:** `ferroloom-shim/` (`GameBridge`, `ShimRuntime`, `ShimImpl`, `FerroUnimplementedException`), `java/shim/impl/client.json`, `ShimSmokeTest`.

**Interfaces:**
- 11,383 classes / 77,780 methods. `ShimRuntime` holds the pushed player position and FOV base; `ShimImpl` implements `Minecraft.getInstance`, `Camera.tickFov/calculateFov/getFov` and `Entity.getX/Y/Z`.

- [x] Steps: generated jar has the 26.3 client classes; position reads come from pushed values; the FOV path passes through the shim; commit.

### Task 6: Rust — runtime class path, loader start and registration report

**Files:** `src/settings.rs`, `src/horizon.rs`, `src/bridge/registrations.rs`, tests.

**Interfaces:**
- `Settings { mods_dir, cache_dir }`; `PendingRegistrations::parse`; `classpath(lib_dir)`; `FerroBootstrap.start` called by JNI, keys registered with `Registrar`.

- [x] Steps: report parsing; class path sorted; key requests applied; `cargo test` 25/25; commit.

### Task 7: Tick to mods, position and chat

**Files:** `dev/ferroloom/api` (`FerroApi`, `FerroEvents`, `FerroRegistrations`, `Errors`), `GameBridge.onTick/onClientSetup`, `WriteOp::Chat`, `TickHost` sink, `TickTest`.

**Interfaces:**
- `FerroEvents.onClientTick/onKey/onCommand`; `FerroApi.log/sendChat/registerKey/registerCommand/setHudLine`; `WriteOp::Chat`.

- [x] Steps: a tick reaches every listener with the pushed position; a throwing listener is dropped and the others run; startup logs travel in the report (`LOG` lines) so they reach `ferroloom.log`; chat and log keep their order; commit.

### Task 8: `compute_fov_modifier` → `Camera.tickFov/getFov` with a real mixin

**Files:** `FerroBootstrap.computeFovModifier`, `FovBaseTest`, `FovMixinTest`, Rust `compute_fov_modifier` hook, `call_sites.json`.

**Interfaces:**
- `FerroBootstrap.computeFovModifier(float base, float tickDelta, boolean changingFov)`; Rust sets `event.modifier`.

- [x] Steps: a mod's Mixin on `Camera.getFov` doubles the modifier the engine sees (2.0 from a 1.0 base); Rust hook applies it; commit.

### Task 9: Key, command and HUD

**Files:** `FerroRegistrations`, `GameBridge.onKey/onCommand/hudLine`, Rust `command`, `register_gui_layers`, `gui_layer_draw`, `dispatch_keys`.

**Interfaces:**
- Report lines `KEY`/`CMD`/`HUD`/`LOG`; `FerroBootstrap.onKey/onCommand/hudLine`; the HUD layer `ferroloom:hud` drawn with `DrawContext::text`.

- [x] Steps: keys/commands/HUD reported; commands handled; key press/release dispatched; HUD line read; commit.

### Task 10: Example mod, staging and the in-game check

**Files:** `examples/example-mod/` (`fabric.mod.json`, `ExampleMod.java`, `build.sh`), `tools/stage.sh`, `docs/smoke.md`, `README.md`, `NOTICE`, `LICENSE`.

**Interfaces:**
- The example runs `main` (key `example_key` on G, command `ferro-hola`, HUD flag) and `client` (tick listener updating HUD/log, key and command listeners sending chat).

- [x] Steps: build and stage; `native-build`; in game the log shows both entrypoints, ticks with the position, the HUD line; G sends `hello from FerroLoom`; `/ferro-hola` answers `ferro-hola from the example`; docs and MIT licence; commit.

## Self-review notes

- Coverage: real loader (Tasks 1-2), discovery and namespace verification (Task 3), generator and shim (Tasks 4-5), FOV call site (Task 8), example mod with entrypoint, key, command, HUD and position (Tasks 9-10), Rust/Java tests and `cargo horizon-mod check` (Tasks 6-9).
- Consistency: `FerroBootstrap` exposes exactly the statics Rust calls (`start`, `onClientSetup`, `onTick`, `computeFovModifier`, `onCommand`, `onKey`, `hudLine`); the `KEY/CMD/HUD/LOG` report is produced in Task 7/9 and parsed in Task 6.
- Review Focus: every line has its test in the task that owns it (3, 4/8, 7, 6).
