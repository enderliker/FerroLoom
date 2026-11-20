# FerroLoom — Plan 3: a third-party zoom mod, unmodified

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Load a real 26.3 Fabric zoom mod from Modrinth with its jar untouched, run its client entrypoint, apply its `Camera.calculateFov` mixin through the `compute_fov_modifier` call site, and show its zoom key changing the FOV in game.

**Architecture:** The Plan 2 host already starts Fabric Loader, discovers mods, runs entrypoints and synthesises the FOV call site. Plan 3 (a) moves client entrypoints into the Registration phase so their key registrations reach the Registrar, (b) models the small `net.minecraft.*` surface the chosen mod touches (a `KeyMapping`, `Options.sensitivity`/`OptionInstance`, `Gui.screen`, `Mth.clamp`) in the shim, (c) adds a minimal `fabric-api` builtin mod (`KeyMappingHelper`, `Event`, `ClientTickEvents`) as a sibling project, and (d) makes `Camera.tickFov` call `Camera.calculateFov`, so a mixin injected at that method's RETURN is on the live FOV path.

**Tech Stack:** Rust edition 2024 (SDK pin `5ef7734`), hand-rolled JNI, JDK 27 (release 25 for Java sources), Gradle 9.9.2, Fabric Loader 0.20.1, Mixin 0.17.5 (`+mixin.0.8.7`, supports `JAVA_25`), JUnit 5, `horizon-mod-api` (`ClientHost::is_down`/`consume_click`, `Registrar::register_key_mapping`).

**Spec:** `docs/superpowers/specs/2026-11-19-ferroloom-design.md` (§6.4, §6.5, §6.8, §8.1, D4, D5)

## Target selection (evidence, not preference)

The design named **Logical Zoom** and fell back to **Just Zoom**. Neither is usable for Plan 3 as-is:

| Candidate | 26.3 jar | What its zoom does | Verdict |
|---|---|---|---|
| Logical Zoom 0.0.37 | no 26.3 build | mixin rewrites the `Matrix4f` in `GameRenderer.renderLevel` | **Rejected**: Horizon renders in Rust; there is no Java projection matrix |
| Just Zoom 3.1.0 | yes | mixin `Camera.tickFov/calculateFov` | **Rejected**: depends on `fabric-api` **and** `konkrete` (config/UI) |
| Cinematic Zoom 1.4 | yes | 7 mixins: `Camera.calculateFov` (`@WrapMethod`), `Gui` (`@ModifyVariable` at `STORE`), `Minecraft` (`@Inject TAIL` on `tick`), `MouseHandler` (HEAD/TAIL), `Options` (`@Inject` on `load` and `<init> TAIL`), `AbstractClientPlayer` (`@ModifyExpressionValue` at an `INVOKE` inside `getFieldOfViewModifier`), `SpyglassOverlay` | **Rejected**: its key is modelable, but the `ModifyVariable`/`ModifyExpressionValue`/`tick` injections need call sites the engine does not have; declaring them "optional" would silently discard mixins |
| **ZoomX 26.3** (`zoomx-26.3.jar`, MIT, 28 KB) | yes | `@Inject(RETURN, cancellable)` on `Camera.calculateFov(F)F`; `ItemInHandRenderer.submitHandsWithItems` (HEAD); `MouseHandler.onScroll(JDD)V` (HEAD) | **Chosen**: the FOV mixin is exactly the live call site; its key is `KeyMappingHelper.registerKeyMapping`; its full `net.minecraft` surface is 9 members |

ZoomX's `fabric.mod.json` depends on `fabricloader >=0.19.5`, `fabric-api *`, `minecraft ~26.3`, `java >=25`; it is `environment: client`; mixin config `zoomx.mixins.json` (`compatibilityLevel: JAVA_25`, `defaultRequire: 1`), no refmap needed (26.3 is named).

Every `net.minecraft`/Fabric member ZoomX references:

```
field  InputConstants$Type.KEYBOARD
field  KeyMapping$Category.MISC                 (a record's static field: null in the shim, tolerated)
field  ClientTickEvents.END_CLIENT_TICK
method KeyMappingHelper.registerKeyMapping(KeyMapping)KeyMapping
method Event.register(Object)V
method KeyMapping.<init>(String, InputConstants$Type, int, KeyMapping$Category)V
method KeyMapping.isDown()Z
method Gui.screen()Lnet/minecraft/client/gui/screens/Screen;
method Minecraft.getInstance()LMinecraft;
field  Minecraft.player, Minecraft.gui, Minecraft.options
method Options.sensitivity()LOptionInstance;
method OptionInstance.get()Ljava/lang/Object;
method OptionInstance.set(Ljava/lang/Object;)V
method Mth.clamp(DDD)D
```

`InputConstants` in 26.3 uses SDL/HID scancodes (`KEY_C = 6`, `KEY_V = 25`); ZoomX's default is `25` → `key.keyboard.v`.

## Global Constraints

- Rust edition 2024; toolchain 1.95. **No `rust-toolchain.toml` in the package.**
- **No JDK in the `.hmod` build.** `cargo horizon-mod check` must never run Java.
- The `.hmod` package may contain only `horizon-mod.toml`, `Cargo.toml`, `Cargo.lock`, `build.rs`, `.rs`/`.toml` under `src/` and root `README*`/`LICENSE*`/`NOTICE*`. **No jars inside it.**
- Do **not** run `horizon-launcher native-lock --update-lock`; the game's `Cargo.lock` stays.
- **No Mojang code or assets.** The 26.3 jar is read for names and descriptors only; shim bodies are FerroLoom's own or `throw FerroUnimplementedException`.
- Docs, code comments and commit messages in **English**; the licence is **MIT**.
- v0.1 and this plan are **client only**; all JNI stays on the bridge thread, engine reads happen on the game thread and are pushed before the tick.
- Execute on the existing master checkout (`/home/ender/projects/Ferro loom/FerroLoom`) — **no worktree**: the launcher and the Horizon instance are bound to this path.
- A mod failure never takes the game down; it is logged with the mod id and loading continues.
- The mod jar is never modified. Anything copied (the named-jar cache) is FerroLoom's own artefact.
- Platform: Linux x86-64.

## Review Focus

1. **A mod that registers its key only in `onInitializeClient`** must still reach the Registrar. Covered by Task 3 (`ClientEntrypointTest`).
2. **A mixin injecting at `RETURN` into a shim method whose body delegates** must apply and change the value the engine sees. Covered by Task 5 (`FovCalculateMixinTest`).
3. **A dotted translation key and an HID scancode** must produce a Registrar-legal id and a real Horizon key name (`key.zoomx.zoom`, `25` → `key.keyboard.v`, `6` → `key.keyboard.c`). Covered by Task 2.
4. **A mod declaring `depends: fabric-api`** must resolve against the builtin `fabric-api` `26.3-ferro.1` and find its classes through Knot. Covered by Task 4.
5. **An engine read or write from the new tick path** must never run outside a callback. The API's tick listener runs inside `GameBridge.onTick`; no new out-of-callback read is opened. Covered by the existing `ShimSmokeTest.a_read_outside_a_callback_is_refused` plus Task 3.

---

### Task 1: Constructor bodies in the shim generator

**Files:**
- Modify: `java/shim-generator/src/main/java/dev/ferroloom/shimgen/ShimGenerator.java` (`convert`)
- Test: `java/shim-generator/src/test/java/dev/ferroloom/shimgen/ShimGeneratorTest.java`, `Fixtures.java`

**Interfaces:**
- Consumes: the existing `ImplTables` row format (`class#name(desc)` → `owner.method`).
- Produces: an `<init>` with an impl row is emitted as `super.<init>()V` (no-arg) **then** the `invokestatic` delegate with `this` and the constructor arguments. Non-constructor methods are unchanged.

- [ ] **Step 1: Write the failing test**

```java
@Test
void a_constructor_with_an_impl_row_calls_super_then_the_delegate() throws Exception {
    byte[] in = Fixtures.clazz("example/Target", "java/lang/Object", null,
            Fixtures.methods(new Fixtures.Method("<init>", "(I)V")));
    ImplTables tables = ImplTables.of(Map.of(
            "example/Target#<init>(I)V", new ImplTables.Impl("example/Calls", "ctor")));
    byte[] out = ShimGenerator.generateClass(in, tables);
    ClassLoader loader = Fixtures.loader(out, Fixtures.ctorCalls("example/Calls", "example/Target"));
    Object target = loader.loadClass("example.Target").getDeclaredConstructor(int.class).newInstance(7);
    assertNotNull(target);
    assertEquals(7, Fixtures.lastCtorArg());
}
```

- [ ] **Step 2: Run it and watch it fail**

Run: `cd java && ./gradlew :shim-generator:test --tests '*ShimGeneratorTest*'`
Expected: FAIL with `VerifyError`/`NoSuchMethodException` (the delegate currently receives an uninitialised `this`).

- [ ] **Step 3: Emit the super call before the delegate**

In `convert`, when `m.name.equals("<init>")` and an impl row exists, visit
`ALOAD 0; INVOKESPECIAL owner.superName.<init>()V` before the existing argument loads. Keep every other path unchanged; document that this assumes the superclass has a no-arg constructor (true for the tables this plan adds: `KeyMapping extends Object`).

- [ ] **Step 4: Run the test to verify it passes**

Run: `cd java && ./gradlew :shim-generator:test`
Expected: PASS (all existing generator tests plus the new one).

- [ ] **Step 5: Commit**

```bash
git add java/shim-generator
git commit -m "feat(shim): give constructor impl rows a super call"
```

---

### Task 2: Shim members ZoomX touches

**Files:**
- Modify: `java/shim/impl/client.json`
- Modify: `java/ferroloom-shim/src/main/java/dev/ferroloom/shim/ShimImpl.java`
- Modify: `java/ferroloom-shim/src/main/java/dev/ferroloom/shim/ShimRuntime.java`
- Modify: `java/ferroloom-shim/src/main/java/dev/ferroloom/shim/GameBridge.java`
- Modify: `java/ferroloom-runtime/src/main/java/dev/ferroloom/api/FerroRegistrations.java`
- Test: `java/ferroloom-shim/src/test/java/dev/ferroloom/shim/ShimSmokeTest.java`, `java/ferroloom-runtime/src/test/java/dev/ferroloom/runtime/FerroRuntimeTest.java`

**Interfaces:**
- Produces:
  - `ShimRuntime.setKey(String modId, String keyId, boolean down)`, `ShimRuntime.keyDown(String translationKey)`, `ShimRuntime.keyName(int scancode)`, `ShimRuntime.keyModId(String translationKey)`, `ShimRuntime.keyId(String translationKey)`.
  - `ShimImpl.keyMappingInit(KeyMapping, String, InputConstants.Type, int, KeyMapping.Category)`, `keyMappingDown(KeyMapping)`, `ensureRegistered(KeyMapping)`, `sensitivity()`, `optionGet(OptionInstance)`, `optionSet(OptionInstance, Object)`, `clamp(double value, double min, double max)`, `screen()`.
  - `GameBridge.installClientNow()`; `GameBridge.onKey` also calls `ShimRuntime.setKey`.
  - `FerroRegistrations.key` is idempotent per `(modId, keyId)`.
- Impl rows (exact descriptors):

```json
"constructible": [ ...existing..., "net/minecraft/client/Options",
                   "net/minecraft/client/OptionInstance", "net/minecraft/client/gui/Gui" ],
"methods": {
  "net/minecraft/client/KeyMapping#<init>(Ljava/lang/String;Lcom/mojang/blaze3d/platform/InputConstants$Type;ILnet/minecraft/client/KeyMapping$Category;)V":
      {"impl": "dev/ferroloom/shim/ShimImpl.keyMappingInit"},
  "net/minecraft/client/KeyMapping#isDown()Z": {"impl": "dev/ferroloom/shim/ShimImpl.keyMappingDown"},
  "net/minecraft/client/Options#sensitivity()Lnet/minecraft/client/OptionInstance;": {"impl": "dev/ferroloom/shim/ShimImpl.sensitivity"},
  "net/minecraft/client/OptionInstance#get()Ljava/lang/Object;": {"impl": "dev/ferroloom/shim/ShimImpl.optionGet"},
  "net/minecraft/client/OptionInstance#set(Ljava/lang/Object;)V": {"impl": "dev/ferroloom/shim/ShimImpl.optionSet"},
  "net/minecraft/util/Mth#clamp(DDD)D": {"impl": "dev/ferroloom/shim/ShimImpl.clamp"},
  "net/minecraft/client/gui/Gui#screen()Lnet/minecraft/client/gui/screens/Screen;": {"impl": "dev/ferroloom/shim/ShimImpl.screen"}
}
```

- Translation key mapping: `key.zoomx.zoom` → mod `zoomx`, key `zoom`; state id `zoomx_zoom` (the same string the Registrar gets). Letters `4..29` → `a..z`, digits `30..39` → `0..9` (HID order), fallback `key.keyboard.unknown`.

- [ ] **Step 1: Write the failing tests**

```java
@Test
void a_key_mapping_registers_its_engine_key_and_reads_the_pushed_state() {
    FerroRegistrations.clear();
    KeyMapping zoom = new KeyMapping("key.testmod.zoom",
            InputConstants.Type.KEYBOARD, 25, null);
    assertTrue(FerroRegistrations.report().contains("KEY\ttestmod\tzoom\tkey.keyboard.v"));
    assertFalse(zoom.isDown());
    ShimRuntime.setKey("testmod", "zoom", true);
    assertTrue(zoom.isDown());
    ShimRuntime.setKey("testmod", "zoom", false);
    assertFalse(zoom.isDown());
}

@Test
void zoomx_surface_answers_without_engine_reads() {
    assertEquals(50.0, Mth.clamp(100.0, 1.0, 50.0));
    assertEquals(0.5, Options.class.cast(new Options()).sensitivity().get());
    assertNull(new net.minecraft.client.gui.Gui().screen());
    GameBridge.installClientNow();
    assertNotNull(Minecraft.getInstance());
    assertNotNull(Minecraft.getInstance().player);
    assertNotNull(Minecraft.getInstance().options);
    assertNotNull(Minecraft.getInstance().gui);
}
```

- [ ] **Step 2: Run them and watch them fail**

Run: `cd java && ./gradlew :ferroloom-shim:test`
Expected: FAIL — `FerroUnimplementedException` from `KeyMapping#<init>` or `Mth#clamp`.

- [ ] **Step 3: Implement the rows and bodies**

`ShimRuntime` keeps a `ConcurrentHashMap<String, Boolean>`; `keyName(25)` returns `key.keyboard.v`. `ShimImpl.keyMappingInit` stores `self.name` (a public shim field), calls `ensureRegistered`, and ignores a null category. `ensureRegistered` derives `(modId, keyId)` and calls `FerroRegistrations.key(modId, keyId, ShimRuntime.keyName(code))` — but the constructor is the only place with the code, so it registers there and `ensureRegistered` is the idempotent re-entry used by `KeyMappingHelper`. `sensitivity()` returns a lazily created `OptionInstance` whose public `value` field is `Double.valueOf(0.5)`. `tickFov` becomes `self.fov = self.calculateFov(ShimRuntime.fovBase()); self.hudFov = self.fov;`. `GameBridge.installClient` also assigns `minecraft.gui = new Gui()` and `minecraft.options = new Options()`.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `cd java && ./gradlew :ferroloom-shim:test :ferroloom-runtime:test`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add java/shim/impl/client.json java/ferroloom-shim java/ferroloom-runtime
git commit -m "feat(shim): model the key mapping, options and gui surface a zoom mod uses"
```

---

### Task 3: Client entrypoints run during registration

**Files:**
- Modify: `java/ferroloom-loader/src/main/java/dev/ferroloom/loader/FerroBootstrap.java`
- Modify: `java/ferroloom-loader/src/test/java/dev/ferroloom/loader/TestMods.java`
- Create: `java/ferroloom-loader/src/test/java/dev/ferroloom/loader/ClientEntrypointTest.java`

**Interfaces:**
- `FerroBootstrap.start()` installs the synthetic client, runs `main`, then `client` entrypoints, and returns one report containing both phases' `KEY`/`CMD`/`HUD`/`LOG` lines plus `FAILED` lines from either phase.
- `FerroBootstrap.onClientSetup()` no longer invokes entrypoints; it logs `client setup: entrypoints ran at registration` and keeps the client installed.
- Ruling: the engine only accepts key/capability declarations during Registration (`&mut dyn Registrar`), so client entrypoints move there. Documented in the ledger and `docs/ferrofabric-deviations.md`.

- [ ] **Step 1: Write the failing test**

```java
@Test
void a_key_registered_in_the_client_entrypoint_reaches_the_report() throws Exception {
    Path tmp = Files.createTempDirectory("fl-clientkey");
    Path mods = Files.createDirectories(tmp.resolve("mods"));
    TestMods.clientKeyJar(mods); // client entrypoint: FerroApi.registerKey("late", "zoom", "key.keyboard.v")
    System.setProperty("ferro.modsDir", mods.toString());
    System.setProperty("ferro.cacheDir", Files.createDirectories(tmp.resolve("cache")).toString());
    System.setProperty("ferro.shimDir", TestMods.stageShim(Files.createDirectories(tmp.resolve("shims"))).toString());
    System.setProperty("ferro.gameDir", tmp.toString());

    String report = FerroBootstrap.start();

    assertTrue(report.contains("KEY\tlate\tzoom\tkey.keyboard.v"), report);
    assertEquals("1", System.getProperty("test.clientkey.runs"));
    FerroBootstrap.onClientSetup();
    assertEquals("1", System.getProperty("test.clientkey.runs"), "entrypoints run once");
}
```

- [ ] **Step 2: Run it and watch it fail**

Run: `cd java && ./gradlew :ferroloom-loader:test --tests '*ClientEntrypointTest*'`
Expected: FAIL — the report has no `KEY` line.

- [ ] **Step 3: Run both phases in `start()`**

Replace the single `invokeEntrypoints("main", ...)` block with two guarded invocations (main, then client), each catching `Throwable` and appending `FAILED\t<describe>`. Call `GameBridge.installClientNow()` (reflectively through `gameLoader`) before the first phase. `onClientSetup` calls the same install and logs the marker.

- [ ] **Step 4: Run the test to verify it passes**

Run: `cd java && ./gradlew :ferroloom-loader:test`
Expected: PASS (including `TickTest`, `EntryFailureTest`, `FovMixinTest`).

- [ ] **Step 5: Commit**

```bash
git add java/ferroloom-loader
git commit -m "feat(loader): run client entrypoints at registration so keys reach the registrar"
```

---

### Task 4: The minimal `fabric-api` builtin

**Files:**
- Create: `../FerroFabric-API/settings.gradle.kts`, `../FerroFabric-API/build.gradle.kts`, `../FerroFabric-API/build.sh`
- Create: `../FerroFabric-API/src/main/java/net/fabricmc/fabric/api/event/Event.java`
- Create: `../FerroFabric-API/src/main/java/net/fabricmc/fabric/api/client/keymapping/v1/KeyMappingHelper.java`
- Create: `../FerroFabric-API/src/main/java/net/fabricmc/fabric/api/client/event/lifecycle/v1/ClientTickEvents.java`
- Create: `../FerroFabric-API/src/main/java/dev/ferroloom/fabric/impl/FerroFabricApi.java`
- Create: `../FerroFabric-API/src/main/resources/fabric.mod.json` (id `fabric-api`, version `26.3-ferro.1`)
- Create: `../FerroFabric-API/src/test/java/dev/ferroloom/fabric/FabricApiTest.java`
- Modify: `java/ferroloom-loader/src/main/java/dev/ferroloom/loader/HorizonGameProvider.java`
- Modify: `src/horizon.rs` (pass `-Dferro.apiJar=<lib>/ferrofabric-api.jar`)
- Modify: `tools/stage.sh` (build the sibling, copy its jar to `$LIB/ferrofabric-api.jar`)
- Create: `java/ferroloom-loader/src/test/java/dev/ferroloom/loader/FabricApiTest.java`

**Interfaces:**
- `Event<T>`: abstract class, `public void register(T listener)`, `public final void dispatch(java.util.function.Consumer<T> call)`; listeners copied before dispatch, so a listener can unregister/throw without aborting the others (a throwing listener is logged by its caller).
- `ClientTickEvents.END_CLIENT_TICK: Event<EndTick>`, `interface EndTick { void onEndTick(Minecraft client); }`, `public static void fireEnd()`.
- `KeyMappingHelper.registerKeyMapping(KeyMapping)` returns the mapping after `ShimImpl.ensureRegistered`.
- `FerroFabricApi.init()` registers one `FerroEvents.onClientTick` listener that calls `ClientTickEvents.fireEnd()`; it is invoked reflectively from `FerroBootstrap.start` after Knot init.
- `HorizonGameProvider`: when `ferro.apiJar` names a regular file, exclude it from the valid parent classpath, `launcher.addToClassPath(apiJar)`, and add `BuiltinMod(new BuiltinModMetadata.Builder("fabric-api", "26.3-ferro.1")... , List.of(apiJar))`.

- [ ] **Step 1: Write the failing sibling tests**

```java
@Test
void an_event_dispatches_to_every_registered_listener() {
    Event<ClientTickEvents.EndTick> event = new Event<>() {};
    List<String> seen = new ArrayList<>();
    event.register(client -> seen.add("a"));
    event.register(client -> seen.add("b"));
    event.dispatch(client -> assertEquals("client", client));
    event.dispatch(listener -> listener.onEndTick(null));
    assertEquals(List.of("a", "b"), seen);
}

@Test
void key_mapping_helper_registers_the_mapping_once() {
    FerroRegistrations.clear();
    KeyMapping zoom = new KeyMapping("key.testmod.zoom", InputConstants.Type.KEYBOARD, 6, null);
    assertSame(zoom, KeyMappingHelper.registerKeyMapping(zoom));
    assertEquals(1, FerroRegistrations.report().lines().filter(l -> l.startsWith("KEY\t")).count());
}
```

(Resolve `Minecraft` and `KeyMapping` from the built shim jar on the test classpath.)

- [ ] **Step 2: Run them and watch them fail**

Run: `../FerroFabric-API/build.sh test`
Expected: FAIL — the classes do not exist yet.

- [ ] **Step 3: Implement the API and the provider wiring**

`Event` holds a `CopyOnWriteArrayList`; `ClientTickEvents.fireEnd()` dispatches `Minecraft.getInstance()`. `FerroFabricApi.init()` is idempotent. The provider adds the jar to Knot and to the builtin mod list. `Rust` sets `-Dferro.apiJar` to `<lib>/ferrofabric-api.jar` (absent is fine). `stage.sh` runs `bash "$REPO/../FerroFabric-API/build.sh"` and copies the jar.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `../FerroFabric-API/build.sh test`
Run: `cd java && ./gradlew :ferroloom-loader:test` with `-Dferro.apiJar=<sibling jar>` for the integration test `FabricApiTest` (skipped via `Assumptions` when the property is absent).
Expected: PASS; the integration test shows a mod whose `depends` includes `fabric-api` loading and registering a key.

- [ ] **Step 5: Commit**

```bash
git add java/ferroloom-loader src/horizon.rs tools/stage.sh
git commit -m "feat(api): add the minimal fabric-api builtin a zoom mod needs"
```

(The sibling project is outside the FerroLoom repository; it is built and staged, not committed here. Reference it from the plan/ledger.)

---

### Task 5: Make `Camera.calculateFov` the live FOV path, with evidence

**Files:**
- Modify: `src/horizon.rs` (log a changed FOV modifier; log registered keys)
- Modify: `src/bridge/registrations.rs` (test for a dotted key id)
- Modify: `java/ferroloom-loader/src/test/java/dev/ferroloom/loader/TestMods.java`
- Modify: `java/ferroloom-loader/src/test/java/dev/ferroloom/loader/FovMixinTest.java`

**Interfaces:**
- `ShimImpl.tickFov` (Task 2) calls `self.calculateFov(base)`; `GameBridge.computeFovModifier` still returns `camera.getFov()`.
- Rust logs `fov: modifier <value> (base <base>)` only when the value changes, so the log carries evidence without a per-tick flood.
- Rust logs `key: <mod_id>/<key_id> -> <default>` for every `KEY` line it applies.

- [ ] **Step 1: Write the failing test**

```java
@Test
void a_mixin_on_camera_calculate_fov_changes_the_modifier_the_engine_sees() throws Exception {
    Path tmp = Files.createTempDirectory("fl-fovcalc");
    Path mods = Files.createDirectories(tmp.resolve("mods"));
    TestMods.fovCalculateMixinJar(mods); // @Inject(RETURN) on calculateFov: returnValue / 3.0f
    System.setProperty("ferro.modsDir", mods.toString());
    System.setProperty("ferro.cacheDir", Files.createDirectories(tmp.resolve("cache")).toString());
    System.setProperty("ferro.shimDir", TestMods.stageShim(Files.createDirectories(tmp.resolve("shims"))).toString());
    System.setProperty("ferro.gameDir", tmp.toString());
    FerroBootstrap.start();
    assertEquals(1.0f / 3.0f, FerroBootstrap.computeFovModifier(1.0f, 0.0f, false), 0.0001f);
}
```

```rust
#[test]
fn a_dotted_key_id_is_parsed() {
    let pending = PendingRegistrations::parse("KEY\tzoomx\tzoom\tkey.keyboard.v\n");
    assert_eq!(pending.keys[0].mod_id, "zoomx");
    assert_eq!(pending.keys[0].key_id, "zoom");
    assert_eq!(pending.keys[0].default_key, "key.keyboard.v");
}
```

- [ ] **Step 2: Run them and watch them fail**

Run: `cd java && ./gradlew :ferroloom-loader:test --tests '*FovMixinTest*'`
Run: `cargo test --lib a_dotted_key_id_is_parsed`
Expected: the Java test FAILS (1.0, not 0.333) until Task 2's `tickFov` change lands; the Rust test passes already (regression pin).

- [ ] **Step 3: Add the log evidence**

In `compute_fov_modifier`, compare the new modifier with a `Mutex<Option<f32>>`/`AtomicU32` last value and log on change. In `register`, log each `KEY` line before registering it.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `cargo test` and `cd java && ./gradlew test`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src java/ferroloom-loader
git commit -m "feat(fov): run zoom mixins through the live calculateFov path and log changes"
```

---

### Task 6: In-game proof with the unmodified ZoomX jar

**Files:**
- Modify: `docs/smoke.md` (Plan 3 section)
- Modify: `README.md` (what Plan 3 proves)
- Modify: `.superpowers/sdd/2026-11-20-ferroloom-fabric-loader/progress.md` (rulings)
- Create: `docs/ferrofabric-deviations.md` (client entrypoints at registration; API modules present)

- [ ] **Step 1: Build and stage**

```bash
cd "/home/ender/projects/Ferro loom/FerroLoom" && ./tools/stage.sh
cp /tmp/plan3/zoomx/zoomx-26.3.jar "$HOME/.local/share/horizon/ferroloom-mods/"
```

- [ ] **Step 2: Build the native mod and start the game**

```bash
cd "/home/ender/projects/Ferro loom/FerroLoom" && ~/Downloads/horizon-launcher-linux-x86_64/horizon-launcher native-build . --instance Horizon --trust-native-code
```
Then via MCP: `play` (instance Horizon, world `FerroLoomSmoke`), `game_wait_ready`.

- [ ] **Step 3: Prove the discovery, entrypoint and mixin**

Read `~/.local/share/horizon/ferroloom.log` and require, by literal text:
`register()` lines show `ferrofabric-api.jar` staged; `JVM ready; loader started; 1 key(s)`; `key: zoomx/zoom -> key.keyboard.v`; `client setup` marker; the loader's mod list contains `zoomx`; the API's `fabric-api 26.3-ferro.1` is loaded.

- [ ] **Step 4: Press the zoom key and record the FOV change**

Via MCP: `game_state` (baseline), `game_key v down`, wait ~10 ticks, `game_screenshot`, `game_key v up`, `game_screenshot`, `game_state`. Require a log line `fov: modifier 0.333... (base 1.0)` while held and `fov: modifier 1.0 (base 1.0)` after release, and a visibly zoomed screenshot.

- [ ] **Step 5: Run every suite and the package check**

```bash
cargo test && cargo horizon-mod check
cd java && ./gradlew test
cd "../FerroFabric-API" && ./build.sh test
```
Expected: all green. Record the counts in the ledger.

- [ ] **Step 6: Commit and push**

```bash
git add docs README.md .superpowers
git commit -m "docs: plan 3 evidence, fabric-api deviation and in-game zoom smoke"
git push origin master
```

## Self-review notes

- **Coverage:** target selection with jar evidence (§ above); constructor bodies (T1); every ZoomX `net.minecraft` member (T2); client-entrypoint keys (T3); `fabric-api` builtin + provider (T4); `calculateFov` call site, logging and mixin test (T5); in-game proof, docs, suites, push (T6).
- **Consistency:** `ShimRuntime.setKey(modId,keyId,down)` stores `modId_keyId`; `KeyMapping.isDown` derives the same id from its translation key; the Registrar id Rust builds is the same `modId_keyId`; `keyName(25) == "key.keyboard.v"` everywhere.
- **Review Focus:** each line points at a test in a named task; no line is left to "handle edge cases".
- **Proportion:** the plan names signatures, descriptors and tests; bodies stay in the implementation.