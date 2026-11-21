# FerroFabric-API base — implementation plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Turn FerroFabric-API from three ZoomX-shaped classes into the base of the Fabric API ecosystem: the real Event system, client lifecycle, complete key mappings, client messages, client commands, client interaction and a text/shape HUD, with module identities that satisfy `depends`.

**Architecture:** Every module mirrors upstream's public classes under its package. FerroLoom's engine hooks push state to Java and call the module's events inside a callback window; the Java side never reads the engine directly. The API jar declares `provides` for every module it implements, so Fabric Loader resolves a mod's `depends: fabric-lifecycle-events-v1` (and friends) against one builtin `fabric-api` mod. Modules we cannot support yet are **not provided**, so resolution fails with a module name instead of a `NoClassDefFoundError`.

**Tech Stack:** Java 25 bytecode, JUnit 5/6 (the sibling repo is on 6.1.3), Gradle 9.9.2, Fabric Loader 0.20.1, ASM-generated shim, Rust edition 2024, the pinned Horizon SDK (`5ef7734`), Brigadier 1.3.11 (MIT, from the official 26.3 library list).

**Spec:** `docs/superpowers/specs/2026-11-19-ferroloom-design.md` (§6.4, §6.5, §6.8, §8.1), and the evidence in this document's analysis.

---

## Analysis (what the base must be)

### Fabric API 26.3 is 44 modules

`fabric-api-0.166.2+26.3.jar` ships 44 nested module jars. The largest are networking (133 Java files), rendering (154), transfer (111), registry-sync (74), item (69), resource-loader-v1 (64), lifecycle (54), data-generation (68), data-attachment (53), command (25), screen (22), key-mapping (7), api-base (17). The identity and dependency graph are in `fabric.mod.json` per module; `fabric-api-base` is the root every other module depends on.

### Usage measured over 16 popular 26.3 Fabric mods

Sample: AppleSkin, Jade, Mouse Tweaks, Nemos Inventory Sorting, YACL, Mod Menu, JEI, Simple Voice Chat, VeinMiner, GeckoLib, Xaero's Minimap, Waystones, Shulker Box Tooltip, Durability Tooltip, and two mods with no API references. Method: download each 26.3 Fabric jar, extract every `net/fabricmc/fabric/api/**` class reference from the constant pools, map the class to its Fabric API module.

| Module | Mods (of 16) | Feasible with the current engine? |
|---|---|---|
| `fabric-api-base` | 11 | **Yes** — pure Java |
| `fabric-lifecycle-events-v1` | 7 | **Client half yes** — client tick, level load/unload, client started/stopping hooks exist |
| `fabric-networking-api-v1` | 7 | **No** — no mod channel in the engine; registration-only stubs would lie |
| `fabric-key-mapping-api-v1` | 6 | **Yes** — already half done (KeyMappingHelper) |
| `fabric-resource-loader-v1` | 5 | **No** — Horizon's resource system is Rust; no Java pack registration |
| `fabric-command-api-v2` | 4 | **Client half yes** — `register_client_commands` + `command` hooks; Brigadier is MIT |
| `fabric-rendering-v1` | 4 | **HUD only** — `gui_layer_draw`'s DrawContext has text, fill, gradient, quad, item model and sprites; no world render |
| `fabric-screen-api-v1` | 4 | **No** — no Java screens; Horizon's screens are Rust |
| `fabric-api-lookup`, `entity-events`, `item`, `transfer` | 1–2 each | **No** — content/registry/lookup surfaces |
| `fabric-data-generation` | 2 | Not runtime; datagen entrypoints only run in dev |

### The classes that actually matter (references across the sample)

`Event` (200), `EventFactory` (29), `ClientTickEvents` (9), `ClientLifecycleEvents` (8), `KeyMappingHelper` (7), `ClientPlayNetworking` (73, gated), `PayloadTypeRegistry` (14, gated), `ScreenEvents`/`ScreenMouseEvents`/`ScreenKeyboardEvents` (27+18+7, gated), `CommandRegistrationCallback`/`ClientCommandRegistrationCallback`/`FabricClientCommandSource` (6/4/4), `ClientReceiveMessageEvents` (3), `ClientSendMessageEvents`, `HudElement`/`HudElementRegistry`/`VanillaHudElements` (6/3/2), `AttackBlockCallback` (2), `UseBlockCallback`/`UseEntityCallback`, `ResourceLoader` (9, gated), `CommonLifecycleEvents` (6, server/tags).

### The engine hooks that exist

Verified in `horizon-ref/horizon/crates/horizon-mod-vocab/src/hooks`:

- `client_tick_pre`, `client_tick_post`, `client_setup`, `client_stopping`, `client_level_load`, `client_level_unload`.
- `client_chat` (outgoing message, mutable, cancel), `client_chat_received` (system/overlay/sender/chat type/message, cancel).
- `register_client_commands` (collects `Command { usage, level }`), `command` (dispatches a raw line).
- `right_click_block` (both sides), `use_item_on_block` (both), `entity_interact` (both); `left_click_block` and `attack_entity` are **server-side** and therefore gated for a client-only host.
- `register_gui_layers` (collects `GuiLayer { id, placement }`), `gui_layer_draw` (a `DrawContext` with `size`, `text`, `text_width`, `fill`, `fill_gradient`, `quad`, `item_model`, GUI sprites).
- `client_resources_reloaded`, `add_client_reload_listeners` exist, but Java mod resources are not wired; gated.

### The base tier

**In this plan:** `fabric-api-base`, client `fabric-lifecycle-events-v1`, `fabric-key-mapping-api-v1`, client `fabric-message-api-v1`, client `fabric-command-api-v2`, client `fabric-events-interaction-v0` (use/right-click subset) and the HUD part of `fabric-rendering-v1`.

**Gated, with the reason and the engine capability each needs:** screen API (a Java screen/widget route), networking (mod channels), resource loader (Java resource packs), item/registry/content/transfer/lookup (a Java content bridge), server events and server-side interaction (v0.2), world/entity/model rendering (a Java render route).

### Packaging decision

`BuiltinModMetadata.Builder` cannot declare `provides`. `ModMetadata` has `getProvides()`, so FerroLoom's provider wraps the builtin metadata in a small delegating implementation that returns the module ids from the API jar's `fabric.mod.json`. One builtin mod, many provided ids, no duplicate jar paths. The module list lives in the API repo (`fabric.mod.json`), the single source of truth.

---

## Global Constraints

- FerroLoom is MIT; FerroFabric-API is Apache-2.0; Brigadier is MIT (Microsoft) and is cited in both NOTICE files.
- No Mojang game code or assets. Brigadier is a separate MIT library from the official 26.3 library list, not game code; it is vendored like ASM.
- Client only (v0.1): server events and server-side hooks are out.
- Every engine read happens inside an engine callback, on the callback thread; Java never reads the engine outside one.
- A supported module's event fires **or** it is documented as unsupported; no silent inert event in a module we provide.
- A missing member throws `FerroUnimplementedException` naming the member; a missing module fails dependency resolution by name.
- TDD for every task; English docs, comments and commits.
- Module ids are provided only for modules whose public classes are present and whose supported events fire.

## Review Focus

1. **A mod that depends on a specific module id** (`fabric-lifecycle-events-v1`) must resolve against the builtin `fabric-api` and load. Covered by Task 1.
2. **A mod that creates its own event with `EventFactory.createArrayBacked` and registers in phases** must see the invoker call listeners in phase order. Covered by Task 2.
3. **A client entrypoint that registers a key, a command, a message listener and a HUD element** must still work when they all run during Registration, and the command must dispatch through Brigadier. Covered by Tasks 4/5/6/8.
4. **A HUD element that draws text and a rectangle** must reach Horizon's `DrawContext` without reading the engine, and a listener that throws must not abort the other elements. Covered by Task 8.
5. **A client-side `UseBlockCallback`/`UseEntityCallback`** must be able to cancel the engine action; a server-side `AttackBlockCallback` must not be advertised. Covered by Task 7 and the gates.

---

### Task 1: Module identity, Brigadier and the builtin `fabric-api`

**Files:**
- Modify: `FerroLoom/java/ferroloom-loader/src/main/java/dev/ferroloom/loader/HorizonGameProvider.java`
- Create: `FerroLoom/java/ferroloom-loader/src/main/java/dev/ferroloom/loader/ProvidedMetadata.java`
- Modify: `FerroLoom/tools/fetch-runtime.sh`, `stage.sh`
- Modify: `FerroFabric-API/src/main/resources/fabric.mod.json`, `build.gradle.kts`
- Test: `FerroLoom/java/ferroloom-loader/src/test/java/dev/ferroloom/loader/ModuleIdentityTest.java`, `FerroLoom/java/ferroloom-loader/src/test/java/dev/ferroloom/loader/TestMods.java`

**Interfaces:**
- `ProvidedMetadata implements ModMetadata` delegates every method to a `ModMetadata` and returns `Set<String>` from `getProvides()`.
- The provider reads `provides` from the API jar's `fabric.mod.json` and builds one `BuiltinMod` with that metadata.
- `fetch-runtime.sh` downloads `brigadier-1.3.11.jar` from `https://libraries.minecraft.net/com/mojang/brigadier/1.3.11/brigadier-1.3.11.jar` into `java/.runtime/fabric/`; `stage.sh` copies it to the lib dir.
- `fabric.mod.json` lists `provides: ["fabric-api-base", "fabric-lifecycle-events-v1", "fabric-key-mapping-api-v1", "fabric-message-api-v1", "fabric-command-api-v2", "fabric-events-interaction-v0", "fabric-rendering-v1"]` as each task lands; start with `fabric-api-base`.

- [ ] **Step 1: Write the failing test** — a mod whose metadata has `"depends":{"fabric-lifecycle-events-v1":"*"}` loads, and `FabricLoader.getInstance().isModLoaded("fabric-lifecycle-events-v1")` is true.

```java
@Test
void a_mod_can_depend_on_a_provided_module() throws Exception {
    Path tmp = Files.createTempDirectory("fl-provides");
    Path mods = Files.createDirectories(tmp.resolve("mods"));
    TestMods.jar(mods, "needsapi", "needsapi.Entry", A_MOD, Map.of("client", "needsapi.Entry"),
            null, "\"fabric-lifecycle-events-v1\":\"*\"");
    // ... properties, start()
    assertTrue(FabricLoader.getInstance().isModLoaded("fabric-lifecycle-events-v1"));
}
```

- [ ] **Step 2: Run it and watch it fail** — `./gradlew :ferroloom-loader:test --tests '*ModuleIdentityTest*'` fails with a dependency-resolution error naming the module.

- [ ] **Step 3: Implement** the metadata wrapper, read the API jar's `provides`, add Brigadier to the runtime and stage, and list the module ids.

- [ ] **Step 4: Run it and the loader suite** — PASS.

- [ ] **Step 5: Commit** in both repos (`feat: resolve provided fabric-api module ids`).

---

### Task 2: The real Event system (`fabric-api-base`)

**Files:**
- Modify: `FerroFabric-API/src/main/java/net/fabricmc/fabric/api/event/Event.java`
- Create: `EventFactory.java`, `ArrayBackedEvent.java`, `AutoInvokingEvent.java`
- Create: `net/fabricmc/fabric/api/util/{TriState,EventResult,BooleanFunction,NbtType}.java`
- Modify: `FerroFabric-API/src/main/java/net/fabricmc/fabric/api/client/event/lifecycle/v1/ClientTickEvents.java`
- Shim: `FerroLoom/java/shim/impl/client.json` + `ShimImpl` for `Identifier` (`fromNamespaceAndPath`, `parse`, `getNamespace`, `getPath`, `toString`, `equals`, `hashCode`).
- Test: `FerroFabric-API/src/test/java/dev/ferroloom/fabric/EventSystemTest.java`

**Interfaces:**
- `abstract class Event<T>` with `protected volatile T invoker`, `final T invoker()`, `abstract void register(T)`, `void register(Identifier, T)`, `void addPhaseOrdering(Identifier, Identifier)`, `static final Identifier DEFAULT_PHASE`.
- `EventFactory.createArrayBacked(Class<? super T>, Function<T[], T>)`, `createArrayBacked(Class<T>, T emptyInvoker, Function<T[], T>)`, `createWithPhases(Class, Function, Identifier...)`, `getHandlerName`, `isProfilingEnabled`, `invalidate`.
- `ArrayBackedEvent<T>` keeps phases, rebuilds the invoker on every registration, and runs phases in the declared order; a cycle logs and keeps a deterministic order.

- [ ] **Step 1: Write the failing tests** — create an event with `EventFactory.createArrayBacked`, register two listeners, assert the invoker calls them in order; create phases `A` and `B`, register B then A, assert A runs first; `ClientTickEvents.END_CLIENT_TICK.invoker().onEndTick(client)` calls a listener.

- [ ] **Step 2: Run and watch fail** — `EventFactory` does not exist (`Event` currently has our `dispatch` extension).

- [ ] **Step 3: Implement** the upstream shapes; delete `Event.dispatch`; migrate `ClientTickEvents` to `EventFactory`. Our tests/API use `invoker()` from now on.

- [ ] **Step 4: Run the API suite and FerroLoom's loader suite** (the tick forwarder changes from `dispatch` to `invoker`).

- [ ] **Step 5: Commit** (`feat: implement the Fabric API event system and phases`).

---

### Task 3: Client lifecycle events

**Files:**
- FerroFabric-API: `ClientTickEvents` (START/END tick, START/END level tick), `ClientLifecycleEvents` (CLIENT_STARTED/STOPPING), `ClientLevelEvents` (LOAD/UNLOAD).
- FerroLoom: `GameBridge.onTick` fires START, level start, level end, END; `onClientSetup` fires CLIENT_STARTED; new `onClientStopping`, `onClientLevelLoad(dimension)`, `onClientLevelUnload(dimension)`; Rust hooks `client_stopping`, `client_level_load`, `client_level_unload`.
- Shim: `net/minecraft/client/multiplayer/ClientLevel` constructible with a dimension `Identifier`; `ShimRuntime.installLevel/clearLevel`; `Minecraft.level` set to the level while loaded.

**Interfaces:**
- `ClientTickEvents.START_CLIENT_TICK/END_CLIENT_TICK`, `START_LEVEL_TICK/END_LEVEL_TICK`; listeners get `Minecraft` and `ClientLevel`.
- `ClientLifecycleEvents.CLIENT_STARTED/CLIENT_STOPPING`; `ClientLevelEvents.LOAD/UNLOAD` with `Minecraft` and `ClientLevel`.
- `GameBridge` exposes `onClientStopping()`, `onClientLevelLoad(String dimension)`, `onClientLevelUnload(String dimension)`; `FerroBootstrap` forwards them.
- Rust registers the three hooks and calls the forwards inside a write window.

- [ ] **Step 1: Write the failing test** — a client mod records the sequence `STARTED, START, END_LEVEL, END, LEVEL_LOAD(overworld), LEVEL_UNLOAD(overworld), STOPPING`; the bridge calls produce it in that order.

- [ ] **Step 2: Run and watch fail** — the events exist only as END_CLIENT_TICK.

- [ ] **Step 3: Implement** the events and the bridge calls; model `ClientLevel` and the dimension.

- [ ] **Step 4: Run the suite**; the existing ZoomX tick listener still fires.

- [ ] **Step 5: Commit** (`feat: client lifecycle events through the engine hooks`).

---

### Task 4: Complete key mapping

**Files:**
- FerroFabric-API: `KeyMappingHelper` (`registerKeyMapping`, `getBoundKeyOf`).
- Shim: `KeyMapping` (constructor stores name/default/category; helper registers), `InputConstants.Key` (`getName`, `getDisplayName`), `InputConstants.Type.getOrCreate`, `KeyMapping.Category.register`.
- FerroLoom: `ShimRuntime.keyBindingName(name)` from the Registrar's mapping; `ShimImpl.boundKey`.
- Test: `FerroFabric-API/src/test/java/dev/ferroloom/fabric/KeyMappingTest.java`, `ShimSmokeTest`.

**Interfaces:**
- Construction does not register (upstream semantics); `registerKeyMapping` registers and throws `IllegalArgumentException` on a duplicate id (mod + key).
- `getBoundKeyOf` returns an `InputConstants.Key` whose `getName()` is the Horizon key name (`key.keyboard.v`), including a player rebind if the engine reports one.
- A `KeyMapping` without registration still reads `isDown()` false and never reaches the Registrar.

- [ ] **Step 1: Write the failing tests** — a duplicate registration throws; `getBoundKeyOf` returns `key.keyboard.v` for a key created with scancode 25; constructing without the helper produces no `KEY` report line.
- [ ] **Step 2: Run and watch fail.**
- [ ] **Step 3: Implement**; move registration from `keyMappingInit` to the helper; update ZoomX's path (it calls the helper).
- [ ] **Step 4: Run the API and loader suites;** ZoomX still registers.
- [ ] **Step 5: Commit** (`feat: complete the key mapping module`).

---

### Task 5: Client messages

**Files:**
- FerroFabric-API: `ClientSendMessageEvents` (ALLOW_CHAT, MODIFY_CHAT, ALLOW_COMMAND, MODIFY_COMMAND), `ClientReceiveMessageEvents` (GAME, CHAT, ALLOW_GAME, ALLOW_CHAT; MODIFY_* plain text only).
- Shim: `net/minecraft/network/chat/Component`/`Text` (`literal`, `empty`, `getString`, `copy`, `append`), `PlayerChatMessage` minimal.
- FerroLoom: `GameBridge.onChatSend(String message)` returns a result (cancel/modified), called from the `client_chat` hook through the write window; `GameBridge.onChatReceived(system, overlay, sender, chatType, message)` returns cancel/modified text, called from `client_chat_received`.
- Rust: dispatch `client_chat` (mutate `message`, apply `cancel`) and `client_chat_received` (convert the engine `Text` to a string, apply the Java result back as plain text, apply cancel).

**Interfaces:**
- `ClientSendMessageEvents.ALLOW_CHAT` gets `(String message)` and returns boolean; `MODIFY_CHAT` returns the string to send.
- `ClientReceiveMessageEvents.GAME/CHAT` get a `Text` built from the engine string; `ALLOW_*` returns boolean; `MODIFY_*` returns a replacement string (styles are lost; document).
- Commands are separate events if the engine reports them as chat lines; the plan implements `MODIFY_COMMAND` only if a raw command reaches `client_chat`.

- [ ] **Step 1: Write the failing test** — a mod cancels `ALLOW_CHAT` and the bridge returns "cancelled"; a `MODIFY_CHAT` listener changes the text; a `GAME` listener sees `hello`.
- [ ] **Step 2: Run and watch fail.**
- [ ] **Step 3: Implement** the events, the `Text` shim and the bridge.
- [ ] **Step 4: Run the suite.**
- [ ] **Step 5: Commit** (`feat: client message events`).

---

### Task 6: Client commands (Brigadier)

**Files:**
- FerroLoom: `fetch-runtime.sh`/`stage.sh` Brigadier; `GameBridge.registerClientCommands()` returns `KEY`-like `CMD` usage strings; `GameBridge.runClientCommand(String)` returns handled.
- FerroFabric-API: `net/fabricmc/fabric/api/client/command/v2/ClientCommandRegistrationCallback`, `ClientCommands`, `FabricClientCommandSource`, `CommandBuildContext`.
- FerroLoom Rust: the `register_client_commands` hook collects usages; the `command` hook runs Brigadier first, then FerroLoom's dev command listeners.
- Test: `FerroFabric-API` unit + a loader mod registering `/base test`.

**Interfaces:**
- `ClientCommandRegistrationCallback.EVENT` is an `Event<ClientCommandRegistrationCallback>`; `register(CommandDispatcher<FabricClientCommandSource>, CommandBuildContext)`.
- `FabricClientCommandSource` implements Brigadier's `CommandSource`: `sendFeedback(Component)`, `sendError(Component)`, `getClient()`, `getWorld()`.
- The dispatcher is a real `com.mojang.brigadier.CommandDispatcher` from the vendored Brigadier jar.
- Usages are flattened to `Command { usage }` for the engine's completion/colouring; dispatch parses the raw line with the same dispatcher.

- [ ] **Step 1: Write the failing test** — a mod registers `literal("base").then(literal("test").executes(...))`; the collected usages contain `base test`; `runClientCommand("base test")` returns true and the source received feedback.
- [ ] **Step 2: Run and watch fail** (`ClientCommands` does not exist).
- [ ] **Step 3: Implement** Brigadier staging, the API classes and the bridge.
- [ ] **Step 4: Run the suite.**
- [ ] **Step 5: Commit** (`feat: client command API over Brigadier`).

---

### Task 7: Client interaction (use/right-click subset)

**Files:**
- FerroFabric-API: `UseBlockCallback`, `UseItemCallback`, `UseEntityCallback`; `InteractionResult`, `InteractionHand`.
- Shim: `BlockPos`, `Direction`, `BlockHitResult`, `Entity`, `Player`, `Level`, `ItemStack` minimal constructors/fields.
- FerroLoom: `GameBridge.onUseBlock(...)`, `onUseItem(...)`, `onUseEntity(...)` return `InteractionResult` (pass/success/consume/fail); Rust maps the hooks' `BlockClick`, hand, stack and entity id.
- Gate: `AttackBlockCallback`, `AttackEntityCallback`, `PlayerBlockBreakEvents` and `ClientPreAttackCallback` are server-side or need unavailable hooks; they stay out of the provided module surface and are documented in `docs/compatibility.md`.

**Interfaces:**
- `UseBlockCallback.interact(Player, Level, InteractionHand, BlockHitResult) -> InteractionResult`.
- `UseItemCallback.interact(Player, Level, InteractionHand) -> InteractionResult`.
- `UseEntityCallback.interact(Player, Level, InteractionHand, Entity, EntityHitResult) -> InteractionResult`.
- Rust applies the result: PASS continues, any other result cancels with that `InteractionResult`.

- [ ] **Step 1: Write the failing test** — a mod cancels a right-click with `InteractionResult.SUCCESS`; the bridge returns SUCCESS and the engine event's cancel is set; a PASS does not cancel.
- [ ] **Step 2: Run and watch fail.**
- [ ] **Step 3: Implement** the shim args, the events and the Rust mapping.
- [ ] **Step 4: Run the suite.**
- [ ] **Step 5: Commit** (`feat: client interaction callbacks`).

---

### Task 8: HUD text and shapes

**Files:**
- FerroFabric-API: `net/fabricmc/fabric/api/client/rendering/v1/hud/{HudElement,HudElementRegistry,VanillaHudElements}.java`.
- Shim: `GuiGraphicsExtractor` subset (`guiWidth`, `guiHeight`, `fill(IIIII)`, `fillGradient`, `text(Font,String,int,int,int[,boolean])`, `centeredText`, `blit` only if the sprite route is ready), `Font.width(String)` via the current draw context.
- FerroLoom: `WriteOp::{HudText,HudFill,HudFillGradient}`; `GuiLayerDrawEvent` drains the queue into `event.draw`; `register_gui_layers` adds `ferroloom:hud` when any HUD element exists; `GameBridge.drawHud(layer, width, height)` iterates elements.
- Test: `FerroLoom` Rust sink test + `FerroFabric-API` unit + in-game screenshot.

**Interfaces:**
- `HudElement.extractRenderState(GuiGraphicsExtractor, DeltaTracker)`.
- `HudElementRegistry.addFirst/addLast/attachElementBefore/attachElementAfter/removeElement/replaceElement` with `Identifier` ids.
- `VanillaHudElements` exposes the vanilla layer ids the engine knows (`hud`, `chat`, ...) as `Identifier`s.
- A `GuiGraphicsExtractor` method resolves the current drain queue and enqueues a draw op; no engine read.

- [ ] **Step 1: Write the failing test** — a mod adds a HUD element that calls `text` and `fill`; `GameBridge.drawHud` produces two queued ops; a Rust sink records `HudText` and `HudFill`.
- [ ] **Step 2: Run and watch fail.**
- [ ] **Step 3: Implement** the API registry, the shim subset, the draw ops and the drain.
- [ ] **Step 4: Run the suites; in game, the HUD element is visible.**
- [ ] **Step 5: Commit** (`feat: HUD elements with text and fills`).

---

### Task 9: Gates, compatibility matrix and a real-mod smoke

**Files:**
- `FerroFabric-API/docs/compatibility.md`: mark each module `base` / `gated` with the engine capability it needs.
- `FerroLoom/docs/ferrofabric-deviations.md`: the same from the host's side.
- `FerroLoom/docs/smoke.md`: the new log lines and the HUD element.
- `CHANGELOG.md` in both repos.
- A real mod that fits the base (one HUD/coordinates/lifecycle mod from Modrinth) plus the test mods.

**Gates (explicit, no silent stubs):**

| Module | Why not now | Needs |
|---|---|---|
| `fabric-screen-api-v1` | No Java screens; Horizon's screens are Rust | A Java screen/widget route or a translation layer |
| `fabric-networking-api-v1` | No mod channels | Engine networking + payload bridge |
| `fabric-resource-loader-v1` | Java packs not registered | Resource pack bridge |
| `fabric-item-api-v1`, `object-builder`, `registry-sync`, `transfer`, `lookup` | Content registration | Java content bridge to the engine registry |
| `fabric-lifecycle-events-v1` server half | Client only | v0.2 server hooks |
| `fabric-rendering-v1` world/entity/model half | No Java world render | A Java render route |
| `fabric-events-interaction-v0` attack half | `left_click_block`/`attack_entity` are server-side | v0.2 |

- [ ] **Step 1: Write the matrix tests** — the API's `compatibility` doc lists provided vs missing; a loader test asserts a mod depending on a gated module fails with the module name.
- [ ] **Step 2: Run the suites and the in-game smoke** — a real mod that only needs the base loads, its key/command/HUD work, and the log shows the module versions.
- [ ] **Step 3: Commit and push both repos.**

## Self-review notes

- **Coverage:** the data names the modules; Tasks 1–8 implement every base-tier module; Task 9 records the gates. Every module in the "provided" list has a class present and an event that fires.
- **Consistency:** `Event` moves to the upstream `invoker()` shape everywhere (Task 2); `KeyMapping` registration moves from the constructor to the helper (Task 4); Brigadier 1.3.11 is the same jar the 26.3 game lists (Task 1/6); the HUD draw ops use the same queue as chat/log (Task 8).
- **Proportion:** the plan names signatures and tests; bodies stay in the implementation.
- **Ordering risk:** Task 1's module list grows with each task; the final list must match the provided modules exactly. Task 6 depends on Task 1's Brigadier staging. Task 8 depends on Task 2's `Event` (the HUD registry can use plain lists until then; prefer Task 2 first).

## Analysis sources

- Fabric API 26.3 module list and dependencies: `refs/fabric-api` (local checkout) and `fabric-api-0.166.2+26.3.jar`.
- Usage sample: the 26.3 Fabric jars downloaded from Modrinth for the 16 mods above; references extracted from class constant pools and mapped to modules by exact class ownership.
- Engine hooks and the `DrawContext` surface: `horizon-ref/horizon/crates/horizon-mod-vocab/src/hooks` and `.../generated/mod_api_generated.rs`.
- Brigadier: `com.mojang:brigadier:1.3.11` in the official 26.3 version JSON; MIT licence (Microsoft).