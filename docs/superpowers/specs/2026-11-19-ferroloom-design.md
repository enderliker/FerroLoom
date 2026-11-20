# FerroLoom + FerroFabric-API — diseño

Fecha: 2026-11-19
Estado: propuesto, pendiente de aprobación.

## 1. Resumen

FerroLoom es un host de JVM para Horizon (Minecraft: Java Edition 26.3 reescrito en Rust) que carga
mods de Fabric sin modificar los jars y sin modificar Horizon. Se distribuye como un único mod nativo
`.hmod` de Horizon. Dentro del proceso del juego arranca una JVM, embebe Fabric Loader, remapea los mods
de Fabric al espacio de nombres del shim y ejecuta su código Java contra `FerroFabric-API`, un fork de
Fabric API cuyas llamadas al juego cruzan por JNI hacia el motor nativo.

FerroFabric-API es la cara Java que ve el mod: `net.minecraft.*` y los paquetes
`net.fabricmc.fabric.*`. FerroLoom es el runtime: descubrimiento, remapeo, arranque, hilos, puente y
diagnóstico.

La analogía con Sinytra Connector es deliberada, pero el reparto es distinto: Connector traduce bytecode
porque NeoForge y Fabric comparten JVM y clases de Minecraft. Horizon no tiene ni JVM ni clases Java de
Minecraft, así que FerroLoom no traduce bytecode: **provee las clases**. El shim Java es un modelo
generado desde Yarn 26.3 que delega en Rust.

## 2. Por qué

Objetivo del autor: llevar mods pequeños y populares de Fabric a Horizon sin esperar a que alguien los
reescriba en Rust, y sin mantener un fork de Horizon. Horizon ya tiene un ledger de porteo manual de
Fabric API (`docs/fabric-ledger.md` de Horizon), pero portar un mod a mano cuesta días y cada
actualización de Horizon lo rompe. FerroLoom convierte ese trabajo repetido en una infraestructura una
vez.

Éxito a ojos del autor: instalar el `.hmod`, poner `zoom.jar` en la carpeta de mods de FerroLoom,
abrir Horizon y que el zoom funcione con su tecla, su mixin y su configuración, sin tocar el jar.

## 3. Qué es FerroLoom y qué no es

Es:

- Un mod nativo `.hmod` de Horizon (ruta nativa, no wasm, no fork).
- Un anfitrión de JVM: localiza Java, carga `libjvm`, arranca una JVM en el proceso del juego.
- Un cargador: embebe Fabric Loader real, con un `GameProvider` propio para "horizon".
- Un traductor de nombres: remapea jars intermediary → Yarn named al instalarlos, y sus refmaps.
- Un puente JNI: clases Java de Minecraft que leen y escriben el motor Rust.
- Un proveedor de API: FerroFabric-API, fork de Fabric API 26.3, módulo a módulo.

No es:

- Un transpilador Java → Rust.
- Un fork de Horizon ni un parche al motor.
- Un entorno sandbox: el código Java corre con los permisos del jugador.
- Compatible con mods de rendimiento (Sodium, Iris, Lithium, FerriteCore) ni con mods que dependan del
  bytecode exacto de Minecraft.
- Un sustituto de Horizon ni un launcher.

## 4. Restricciones verificadas

Hechos comprobados en el checkout de Horizon (`horizon-ref/horizon`, commit `ded44b26`, 2026-11-19) y
en las referencias clonadas.

### 4.1 Horizon

- Horizon es Minecraft 26.3 reescrito en Rust, sin Java ni clases de Mojang. `docs/fabric-ledger.md`
  mapea Fabric API a hooks de Horizon, explícitamente **como guía de porteo manual, no como capa de
  compatibilidad**.
- El formato `.hnmod` está retirado. Hoy un solo `.hmod` sirve para contenido y código nativo
  (`kind = "native-mod"`); el launcher rechaza los `.hnmod` antiguos
  (`launcher/src/native_status.rs:252`, commit `02122e25`). La página playhorizon.dev sigue diciendo
  `.hnmod`: está desfasada.
- Un mod nativo es una crate Rust plana con `horizon-mod.toml`, `Cargo.toml`, `src/`, y
  `[native] game/horizon/mixins` con commits exactos. Admite `Cargo.lock` y dependencias de crates.io
  (`docs/modding/src/reference/manifest.md`, líneas 226-253).
- El paquete nativo solo admite, además del manifiesto: `Cargo.toml`, `Cargo.lock`, `build.rs`, `.rs` y
  `.toml` bajo `src/`, y textos raíz `README*`/`LICENSE*`/`NOTICE*`; hasta 511 ficheros y 64 MiB
  descomprimidos. **No puede contener un `.jar`.**
- La crate del mod depende solo de `horizon-mod-api` (nombre de librería `mod_api`). Su crate **no
  puede llamar funciones del juego**: solo los helpers de `[[native.mixin]]`, los slots de
  `[[native.loop]]` y los listeners de `[[inject]]` —compilados dentro de copias de funciones del juego
  — pueden tocar tipos del motor y llamar a la crate
  (`docs/modding/src/concepts/native-route/7-how-it-works.md`).
- Hooks disponibles para el host (verificados en `crates/horizon-mod-vocab/src/hooks/`):
  `client_setup`, `client_tick_pre`, `client_tick_post`, `render_frame_pre`, `render_frame_post`,
  `render_gui_pre`, `command`, `input_key`, `key_mapping_pre`,
  `interaction_key_mapping_triggered`, `health_hud`, `level_tick_pre`, `level_tick_post`,
  `player_tick_pre`, `player_tick_post`, `entity_tick_pre`, `entity_tick_post`, y otros.
- Fases de carga: `Packages → Registration → Setup → Config → World`. No hay `Host` hasta `World`; en
  `Registration` solo hay `RegistrationContext`.
- El FOV de Horizon es el hook `compute_fov_modifier` (cliente, una vez por tick, sin cancelación), no
  un método Java. Las teclas, capas y contenido solo se declaran con `&mut dyn Registrar` durante
  Registration (`Registrar::register_key_mapping`, `ClientHost::is_down(mod_id, id)`).
- El juego tiene `horizon-ctl` con socket de control (`launch`, `state`, `keys`, `command`,
  `screenshot`, `quit`), útil para pruebas de integración.

### 4.2 Fabric

- Un mod Fabric es un jar con `fabric.mod.json` (id, versión, `entrypoints` main/client/server,
  `mixins`, `accessWidener`, `depends`/`recommends`/`suggests`/`breaks`/`conflicts`, `provides`,
  `environment`, jars anidados).
- Los jars publicados en Modrinth están remapeados a **intermediary**, y sus refmaps también.
- Fabric Loader está diseñado para embeberse: `GameProvider` es un servicio cargado por
  `Knot`/`FabricLauncher`, con `getGameId`, `locateGame`, `initialize`, `launch`, `BuiltinMod`.
- Fabric Loader y Fabric API son Apache-2.0.
- Launchpad (Sinytra) es GPL-3.0-only con Classpath Exception; Connector también tiene licencia propia.
  **Solo se usan como referencia de arquitectura, sin copiar código.**
- Mixin solo puede transformar clases que existan en el classpath en el momento de carga. Con el shim,
  "existen" todas las clases que generemos, pero solo se comportan como el original los miembros que
  modelemos.

### 4.3 Consecuencias

1. FerroLoom debe traer su propia JVM: Horizon no envía Java y el launcher no necesita JDK para
   compilar el mod. El build del `.hmod` no debe requerir JDK.
2. El puente no puede ser "llamar a cualquier función del motor": tiene que pasar por hooks y helpers.
3. El shim es un modelo, no el juego real. La fidelidad se elige por mod, no se hereda.
4. Los jars (shim, loader, FerroFabric-API) no caben en el `.hmod`; hay que resolver su distribución.

## 5. Enfoques considerados

### 5.1 Qué hacer con un mod Fabric existente

| Enfoque | Qué implica | Veredicto |
|---|---|---|
| A. Porteo asistido a Rust | Generar un proyecto Rust desde el jar; el código se reescribe | Descartado: no ejecuta mods existentes |
| B. Transpilar bytecode a Rust | Compilador JVM→Rust + shim de Minecraft | Descartado: investigación de años |
| **C. JVM embebida + JNI** | El jar corre tal cual en una JVM; las clases de Minecraft son shims que delegan en Rust | **Elegido** |

C es el único que cumple "mods existentes, sin tocar el jar" con un alcance acotable.

### 5.2 Dónde vive FerroLoom

| Enfoque | Veredicto |
|---|---|
| **Mod nativo `.hmod`** | **Elegido**: sin fork, distribución normal, límite: solo toca el juego vía helpers/hooks |
| Fork de Horizon | Descartado: mantener un fork LGPL que cambia a diario |
| Proceso aparte | Descartado: sin JNI real, habría que serializar el mundo |

### 5.3 Piezas Java

| Enfoque | Veredicto |
|---|---|
| **Fabric Loader real + FerroFabric-API (fork de Fabric API)** | **Elegido**: compatibilidad de manifiestos y mixins sin reimplementar el loader |
| Fabric Loader real + Fabric API real | Descartado para v0.1: Fabric API toca miles de métodos; el shim crecería sin control |
| Loader propio | Descartado: reimplementar entrypoints, dependencias, remapping y Mixin |

### 5.4 Puente

| Enfoque | Veredicto |
|---|---|
| **Híbrido: lecturas directas, escrituras en cola** | **Elegido**: semántica casi inmediata sin reentrancia de `&mut Host` |
| Directo con ventana de llamada | Descartado como defecto: punteros vivos a través de código Java |
| Todo en cola | Descartado: HUD/render y tooltips necesitan respuestas síncronas |

### 5.5 Shim

| Enfoque | Veredicto |
|---|---|
| **Generador completo + implementación por prioridad de mod** | **Elegido**: mixins siempre encuentran su target; lo no implementado falla en voz alta |
| Subconjunto a mano | Descartado: no escala y rompe mixins sin aviso |
| Generador sin prioridad | Descartado: implementar el juego entero de golpe |

### 5.6 Mapeos

| Enfoque | Veredicto |
|---|---|
| **Shims en Yarn named + remapear al instalar** | **Elegido**: shims legibles; tiny-remapper ya resuelve el problema |
| Shims en intermediary | Descartado como principal: `class_1234`/`method_5678` ilegible |
| Ambos juegos de nombres | Aplazado: duplica la superficie de clase |

## 6. Arquitectura

```
┌──────────────────────────── proceso del juego Horizon (Rust) ────────────────────────────┐
│                                                                                          │
│  Motor Horizon ──hooks──▶ FerroLoom (crate Rust, mod nativo)                             │
│       ▲                    │  ├─ JvmHost: descubre Java, carga libjvm, crea la JVM       │
│       │                    │  ├─ HandleArena: objetos del motor como handles u64         │
│       │                    │  ├─ ReadWindow: host vivo durante un upcall                 │
│       │                    │  ├─ WriteQueue: escrituras diferidas al fin del hook        │
│       │                    │  └─ Upcalls: FerroRuntime.onTick/onFrame/onCommand/...      │
│       │                    │                                                             │
│       └──── helpers/mixins ┘                                                             │
│                                                                                          │
│  ┌──────────────────────────── JVM embebida ─────────────────────────────────────────┐   │
│  │  FerroBootstrap ─▶ Fabric Loader (fork Apache-2.0) ─▶ mods/*.jar (remap a named)   │   │
│  │        │                     │                                                     │   │
│  │        │                     └─ Mixin (fork de Fabric) ─▶ shim clases               │   │
│  │        │                                                                          │   │
│  │  FerroFabric-API (fork de Fabric API 26.3) ─▶ eventos, registros, recursos...      │   │
│  │        │                                                                          │   │
│  │  Shim `net.minecraft.*` (generado desde Yarn 26.3) ──JNI──▶ FerroNative (Rust)     │   │
│  └──────────────────────────────────────────────────────────────────────────────────┘   │
│                                                                                          │
└──────────────────────────────────────────────────────────────────────────────────────────┘
```

### 6.1 Componentes

**FerroLoom (Rust, crate del `.hmod`)**

| Módulo | Responsabilidad |
|---|---|
| `registry` | `Hooks::register`: lee ajustes, valida carpeta de mods, registra hooks |
| `jvm` | Descubrimiento de Java (runtime de Minecraft 26.3 → `JAVA_HOME` → error), `libloading` de `libjvm`, `JNI_CreateJavaVM`, opciones (`-Xmx`, `-Djava.awt.headless`), classpath |
| `host` | Upcalls tipadas a `FerroRuntime`; conversión de eventos de Horizon a eventos de Fabric |
| `handles` | Arena generacional: `Handle(u64)` → objeto del motor; validación de generación y ventana |
| `read_window` | Puntero al host vivo durante un upcall; lecturas directas |
| `write_queue` | Operaciones de escritura con destino y argumentos; drenaje al final de cada hook |
| `diag` | Errores accionables: "método X no implementado en el shim", "lectura fuera de callback" |

**FerroLoom Loader (Java, fork Apache-2.0 de Fabric Loader)**

| Módulo | Responsabilidad |
|---|---|
| `HorizonGameProvider` | `GameProvider` con id `minecraft`, versión `26.3`, `BuiltinMod`s del shim, entrypoint de arranque |
| `Bootstrap` | Secuencia de arranque sin `Knot.launch`: classloader, descubrimiento, resolución, Mixin, pre-launch, main, client/server |
| `ModDir` | `fabric.mod.json`, jars anidados, filtrado por `environment`, `provides` |
| `Remapper` | tiny-remapper intermediary → Yarn para jars y refmaps, con caché por sha256 |
| `AccessWidener` | Aplicación al modelo del shim (en v0.1 el shim genera todo público; AW se acepta y se registra) |
| `MixinBootstrap` | Fork de Fabric de Mixin sobre nuestro classloader |

**FerroFabric-API (Java, fork Apache-2.0 de Fabric API 26.3)**

Módulos de v0.1, en este orden:

1. `fabric-lifecycle-events-v1` (client/server tick, world load/unload)
2. `fabric-events-interaction-v0` (block break/use, entity interact)
3. `fabric-item-api-v1`, `fabric-item-group-api-v1`
4. `fabric-object-builder-api-v1`, `fabric-registry-sync-v0`
5. `fabric-resource-loader-v0`, `fabric-client-tags-api-v1`
6. `fabric-key-binding-api-v1`, `fabric-screen-api-v1`
7. `fabric-rendering-v1`, HUD (`fabric-hud` no existe; `HudRenderCallback` en `fabric-rendering-v1`)
8. `fabric-transfer-api-v1` (sobre capabilities de Horizon)

Cada módulo se parte del fuente real de Fabric API y se sustituyen las llamadas al interior de Minecraft
por llamadas al shim. Un módulo que no compila contra el shim no entra. En v0.1 solo se habilitan las
partes de cliente de cada módulo; las de servidor se compilan pero no se despachan hasta v0.2.

**Shim (Java, generado)**

- Entrada: mappings Yarn 26.3 (tiny v2) + tablas de implementación (`shim/impl/*.toml`).
- Salida: fuentes Java de `net.minecraft.*` y `com.mojang.*` mínimos.
- Cada miembro es `delegate:<op>`, `java:<cuerpo propio>` o `unimplemented`.
- Clases `client` separadas en un jar distinto para que el servidor no las cargue.
- **Sin código de Mojang**: los cuerpos `java:` se escriben desde el comportamiento observable, no se
  copian del jar descompilado. Es la misma regla que sigue Horizon.

### 6.2 Arranque

1. Horizon carga el `.hmod`; `Hooks::register` de FerroLoom registra hooks y lee ajustes
   (`ferroloom.toml`: carpeta de mods, ruta de Java, memoria, nivel de log).
2. En `Hooks::register` (fase Registration), antes de que exista ningún `Host`, FerroLoom:
   - localiza Java; si no, error con instrucciones y el mod queda desactivado;
   - carga `libjvm`, crea la JVM con classpath = `ferroloom-loader.jar`, `ferro-shim.jar`,
     `ferro-shim-client.jar`, `ferrofabric-api-*.jar`, `mods/*.jar`;
   - abre una barra con `RegistrationContext::progress("FerroLoom", …)` y llama a
     `FerroBootstrap.start(side, modsDir, cacheDir)`.

   La JVM arranca aquí, no en `client_setup`, porque Horizon solo admite declarar teclas, capas y
   contenido con `&mut dyn Registrar`, y ese registro solo existe en esta fase.
3. Java: descubre mods, remapea lo que falte (caché por sha256 en `<game>/ferroloom/cache`), resuelve
   dependencias con Fabric Loader, construye el classloader, arranca Mixin, ejecuta
   `PreLaunchEntrypoint`, luego `main` (`ModInitializer`).
   Durante `main`, las peticiones de registro (`KeyBindingHelper`, bloques e items, capas de GUI) no se
   aplican al vuelo: se acumulan y se devuelven a Rust al terminar.
4. Rust aplica esas peticiones con `Registrar` antes de salir de `register`
   (`register_key_mapping`, registro de bloques e items, `register_gui_layers`).
5. En `client_setup` se ejecutan los entrypoints `client`; en `client_tick_pre/post`, los eventos de
   tick. Un `main` que no termina bloquea el arranque: FerroLoom aplica un timeout (120 s por defecto),
   desactiva el mod culpable y continúa con el resto.
6. Si algo falla, FerroLoom captura la excepción Java, la registra con el nombre del mod y deja el mod
   desactivado; nunca tumba el juego.

### 6.3 Flujo de eventos

v0.1 es **solo cliente** (ver §6.10). Las filas marcadas v0.2 no se despachan todavía.

| Hook de Horizon | Evento de Fabric |
|---|---|
| `client_tick_pre` | `ClientTickEvents.START_CLIENT_TICK`, luego `FerroRuntime.flushWrites()` |
| `client_tick_post` | `ClientTickEvents.END_CLIENT_TICK` |
| `compute_fov_modifier` | punto de llamada motor→Java sobre `GameRenderer.getFov` (ver §6.5) |
| `render_gui_pre` | `HudRenderCallback` |
| `render_frame_pre/post` | `WorldRenderEvents` (v0.2) |
| `command` | `CommandRegistrationCallback` + despacho de Brigadier |
| `input_key`, `key_mapping_pre`, `interaction_key_mapping_triggered` | `KeyBinding` y `ClientTickEvents` |
| `health_hud` | HUD de vida (v0.2) |
| `level_tick_pre/post` | `ServerTickEvents.START/END_WORLD_TICK` (v0.2) |
| `player_tick_pre/post` | `ServerPlayerEvents` (v0.2) |

Regla: cada upcall termina con `flushWrites()`; las escrituras pendientes se aplican con el mismo host
antes de devolver el control a Horizon. Si el hook no admite escrituras, las operaciones se rechazan con
un error tipado.

### 6.4 Puente

**Handles.** Todo objeto del motor expuesto a Java es un `long` en una arena generacional. El objeto
Java (`Level`, `Player`, `ItemStack`...) guarda el handle y el tipo. Un handle caducado lanza
`FerroStaleHandleException` nombrando el objeto y la ventana en que se emitió.

**Lecturas.** Dentro de un upcall, el host de Horizon vive en un `ReadWindow`
(`NonNull<dyn Host>` en thread-local, restaurado al salir). Las lecturas van directas: posición, salud,
estado de bloque, inventario, tiempo. Fuera de un upcall:

- durante init o config, las lecturas de mundo lanzan `FerroNotInCallbackException`;
- lecturas de configuración y de recursos van a la caché de arranque.

**Escrituras.** `FerroNative.setBlock(...)`, `player.sendMessage(...)`, etc. construyen una `WriteOp`
con handle, operación y argumentos, y la encolan. `flushWrites()` las aplica en orden con el host vivo,
antes de devolver el hook. Una lectura posterior dentro del mismo callback ve el estado anterior: se
documenta y se expone `FerroRuntime.flushNow()` para el mod que lo necesite.

**Tipos.** Las firmas JNI se generan desde las tablas del shim, no se escriben a mano: cada `delegate:op`
produce un método `native` estático en `FerroNative` y su `extern "system"` en Rust.

**Hilos.** v0.1 es solo cliente: se adjunta un único hilo Java, el del juego del cliente (el que
tickea la ventana y dibuja). Los hooks de servidor y los de worker (mesher, worldgen) no se despachan;
`ServerTickEvents` y los eventos de servidor llegan en v0.2. Un upcall ocurre solo en ese hilo. Los
hilos que creen los mods no se adjuntan: pueden hacer trabajo Java puro, pero una llamada al motor
desde ellos lanza `FerroNotInCallbackException`.

### 6.5 Puntos de llamada motor → Java

Un mixin transforma el cuerpo de un método, y solo tiene efecto si algo llama a ese método. El motor
de Horizon no llama métodos Java, así que FerroLoom **sintetiza la llamada vanilla** en el hook
equivalente. La tabla `call_sites` (`shim/call_sites.toml`) dice, por hook, qué método del shim se
invoca y qué se hace con su resultado:

| Hook de Horizon | Llamada Java sintetizada | Uso del valor |
|---|---|---|
| `compute_fov_modifier` | `MinecraftClient.getInstance().gameRenderer.getFov(camera, tickDelta, changingFov)` | el retorno es el modificador |
| `input_key` | `KeyBinding.setKeyPressed` y despacho a las teclas de FerroLoom | — |
| `command` | despacho Brigadier del shim | — |
| `client_tick_pre/post` | `MinecraftClient.tick()` con la estructura vanilla | — |

Consecuencias:

- Un miembro del shim está **vivo** solo si un `call_site` lo invoca. Al arrancar, FerroLoom cruza
  los targets de los mixins remapeados con la tabla y emite `FerroUninvokedMixinWarning` (mod, config,
  método) por cada mixin que nunca correrá.
- Un mixin sobre un método vivo cuya estructura no reproduzcamos falla al aplicarse:
  `FerroUnsupportedMixinException`, con el mod y el punto de inyección.
- Nunca se parchea `require`/`defaultRequire` en silencio. Si un objetivo no se puede modelar, el
  arranque lo dice con el mod nombrado y ofrece desactivarlo; `ferroloom.mixin_tolerance = "warn"` en
  desarrollo lo convierte en aviso.
- Fidelidad de v0.1: `compute_fov_modifier` corre una vez por tick de cliente, no por frame. Los mods
  de zoom con easing por frame verán pasos de 20 Hz; se documenta.

### 6.6 Shim y fidelidad

El generador produce **todas** las clases de `net.minecraft.*` que Yarn 26.3 describe, con jerarquía,
campos, métodos y descriptores. Eso garantiza que Mixin encuentre su target y que un mod compile.

La fidelidad se decide por miembro:

- `delegate:<op>`: el cuerpo delega en Rust. Es la mayoría de lo que un mod llama.
- `java:<cuerpo>`: cuerpo propio que reproduce el comportamiento del método (p. ej. `MathHelper.clamp`,
  cálculos de FOV). Se escribe a mano, con tests, sin copiar código de Mojang.
- `unimplemented`: lanza `FerroUnimplementedException("net.minecraft... método <n> no implementado; el
  mod <id> lo necesita")`.

Un mixin sobre un miembro `unimplemented` se aplica sin error; el fallo llega al llamarlo, con el mod y
el método en el mensaje. Los `@Shadow` de campos funcionan porque los campos existen.

Prioridad de implementación: la que pida la lista de mods objetivo (Zoom primero), más los métodos que
use FerroFabric-API.

### 6.7 Remapeo y Mixin

- El jar del mod llega en intermediary. FerroLoom lo remapea a Yarn named con tiny-remapper al
  instalarlo, incluidos los refmaps de sus configs de Mixin.
- El resultado se guarda en `<game>/ferroloom/cache/<sha256>-named.jar`; el original no se toca.
- Mixin corre con el fork de Fabric sobre nuestro classloader, y solo ve el shim y los mods.
- Access wideners: el shim genera todo público; los AW se parsean, se registran en el log y no cambian
  nada. Se documenta la diferencia.
- `environment: client|server` se respeta al descubrir y al cargar clases.

### 6.8 FerroFabric-API

- Fork de Fabric API 26.3. Se conservan paquetes, nombres de clases y de módulos para que un mod no
  distinga.
- **Se conservan los mixins de Fabric API.** No se reescriben como suscripciones a hooks por defecto:
  cada módulo de v0.1 se audita, sus configs de mixin aportan filas a `call_sites` (§6.5) y, cuando
  hace falta, cuerpos estructuralmente fieles en el shim. Reescribir un módulo es la excepción y se
  registra en `docs/ferrofabric-deviations.md` con el motivo.
- `defaultRequire: 1` se respeta: un mixin que no encuentra su punto de inyección falla en voz alta.
- Se elimina solo lo que dependa de internals que el shim no puede modelar, y se sustituye por
  llamadas al shim o a `FerroNative`.
- Versión propia (`26.3-ferro.1`) que Fabric Loader reporta como `fabric-api`.
- Los módulos se cargan como mods embebidos del loader, igual que en Fabric.

### 6.9 Empaquetado y distribución

El `.hmod` no puede contener jars (límite del paquete nativo). Por tanto:

1. CI construye y publica: `ferroloom-loader.jar`, `ferro-shim.jar`, `ferro-shim-client.jar`,
   `ferrofabric-api-*.jar`, cada uno con SHA-256. Se publica un `manifest.json` versionado.
2. Al arrancar, FerroLoom descarga lo que falte a `<game>/ferroloom/lib/`, verifica SHA-256 y lo cachea.
   Sin red y sin caché, el mod se desactiva con un mensaje que dice qué falta y dónde ponerlo a mano.
3. `FERROLOOM_LIB_DIR` permite desarrollo local con jars sin publicar.
4. El `.hmod` contiene solo Rust y el `manifest.json` de artefactos (como recurso `.rs`/`.toml`)... — ver
   decisión abierta D1.

Esto hace a FerroLoom dependiente de red la primera vez. Se documenta en la página del mod y en el
registro.

### 6.10 Hilos y ciclo de vida

- La JVM se crea una vez y vive hasta que el proceso termina. FerroLoom no la destruye al cerrar un
  mundo: los mods de Fabric asumen un ciclo de vida largo.
- Un segundo mundo no repite `main`; sí repite eventos de mundo.
- Si la JVM muere o un mod lanza `Error`, FerroLoom marca el mod como fallido, lo saca del despacho y
  sigue. Un `OutOfMemoryError` de la JVM deja FerroLoom inerte con un aviso.
- Apagado: `FerroRuntime.shutdown()` desde el último tick, con timeout; si no responde, se deja morir
  con el proceso.

### 6.11 Diagnóstico

- Log propio `ferroloom.log` con una línea por evento clave y el stack Java completo.
- Pantalla de Mods: FerroLoom aparece como un mod que declara cuántos mods de Fabric cargó y cuáles
  fallaron, con el motivo.
- Errores tipados y accionables, nunca `NullPointerException` genéricos:
  - `FerroUnimplementedException` (método del shim sin implementar)
  - `FerroStaleHandleException` (handle caducado)
  - `FerroNotInCallbackException` (lectura/escritura fuera de callback)
  - `FerroUnsupportedMixinException` (mixin que apunta a un miembro inexistente en el shim)

## 7. Seguridad y licencias

- FerroLoom ejecuta código Java arbitrario con los permisos del jugador. No hay sandbox. Se avisa en la
  página del mod, en el registro y en la pantalla de Mods, como Horizon hace con los mods nativos.
- El shim y FerroFabric-API no contienen código de Mojang. Los cuerpos `java:` son propios.
- Fabric Loader y Fabric API son Apache-2.0: se conservan LICENSE y NOTICE, y se documentan los cambios.
- Connector y Launchpad son referencia arquitectónica; no se copia código (GPL-3.0 con Classpath
  Exception en Launchpad).
- El registro de Horizon revisa el código del mod nativo; FerroLoom debe declarar con claridad qué
  descarga y por qué.

## 8. Alcance de v0.1

### 8.1 Criterios de aceptación

1. `ferroloom.hmod` se instala y arranca en Horizon, encuentra Java y levanta la JVM.
2. Un mod Fabric de ejemplo, escrito y compilado con Fabric Loom contra 26.3 (intermediary), corre:
   - entrypoint `main` y `client`;
   - registra una tecla y responde a ella (la tecla se reasigna en FerroLoom; en v0.1 no aparece aún
     en la pantalla de Controls de Horizon);
   - registra un comando `/ferro-hola`;
   - dibuja una línea en el HUD;
   - lee la posición del jugador y el estado del bloque bajo sus pies;
   - envía un mensaje al chat.
3. `Logical Zoom` (MIT; jar sin modificar) funciona: tecla, configuración, y su mixin sobre
   `GameRenderer.getFov` se ejecuta mediante el punto de llamada de `compute_fov_modifier` (§6.5). Si
   no hay build para 26.3, se usa `Just Zoom`.
4. Lecturas directas y escrituras en cola funcionan; `flushNow()` aplica en el acto.
5. Los fallos son accionables: un mod con un método no implementado muestra su nombre y el método.
6. Ningún mixin de un mod instalado se descarta en silencio: o corre, o el log y la pantalla de Mods
   dicen por qué (`FerroUninvokedMixinWarning`, `FerroUnsupportedMixinException`).
7. Tests: `cargo test` para handles, cola y conversión de eventos; tests Java para el remapper y el
   generador; una prueba de humo con `horizon-ctl` que arranca el juego y comprueba el log.

### 8.2 Fuera de v0.1

- Render 3D del mundo desde Java, shaders, partículas complejas.
- Networking de mods (canales S2C/C2S), sincronización de datos.
- Mods de rendimiento y mods que mixinean dentro de métodos internos no modelados.
- Servidores dedicados con mods de mundo (solo se valida el cliente).
- Fabric API completa.
- Actualizaciones automáticas de FerroLoom.

## 9. No-objetivos y anti-objetivos

No-objetivos: compatibilidad binaria con Forge/NeoForge; ejecutar modpacks grandes; emular la JVM de
Minecraft; sustituir Horizon.

Anti-objetivos (si pasa, el proyecto ha fallado aunque "funcione"):

- Meter código de Mojang en el shim.
- Que un método no implementado devuelva un valor plausible en vez de fallar.
- Requerir JDK para compilar el `.hmod`.
- Forkear Horizon.
- Que un mod roto tumbe el juego.

## 10. Riesgos y spikes

| # | Riesgo | Mitigación |
|---|---|---|
| R1 | Fabric Loader no arranca sin un juego real | Spike S2: embeber el loader con un `GameProvider` falso |
| R2 | Mixin no aplica sobre clases generadas | Spike S3: mixin sobre un shim generado |
| R3 | El mod nativo no puede cargar `libjvm`/dlopen | Spike S1: `.hmod` que arranca una JVM y escribe en el log |
| R4 | Puente imposible sin reentrancia de `&mut Host` | Spike S4: lectura directa + escritura en cola en un tick real |
| R5 | Revisión del registro rechaza JVM embebida o descarga | Hablar con maintainers antes de publicar; modo local primero |
| R6 | Fidelidad del shim insuficiente para Zoom | Elegir Zoom por su mixin concreto; validar antes de comprometerse |
| R7 | Rendimiento: cruces JNI por campo | No es objetivo de v0.1; medir después |
| R8 | Cambios de Horizon rompen la crate | Pins exactos; CI contra Nightly |

**Spikes primero.** S1-S4 son cuatro pruebas desechables que, juntas, demuestran o refutan la
arquitectura antes de escribir el shim.

## 11. Estructura de repos

```
Ferro loom/
  FerroLoom/                 # repo Rust + Java
    Cargo.toml               # crate del mod nativo
    horizon-mod.toml
    src/                     # jvm, host, handles, write_queue, diag
    java/
      ferroloom-loader/      # fork Apache-2.0 de Fabric Loader
      shim-generator/        # Yarn -> Java
      shim-runtime/          # FerroNative, FerroRuntime, excepciones
    examples/example-mod/    # mod Fabric de prueba (Loom)
  FerroFabric-API/           # fork Apache-2.0 de Fabric API 26.3
  docs/superpowers/specs/    # este documento
  refs/                      # clones de referencia (no se publican)
```

## 12. Decisiones abiertas para el builder

- **D1 — Artefactos.** ¿Los jars se descargan en runtime (propuesto) o se incrustan como base64 en
  ficheros `.rs`/`.toml` del paquete? La primera opción exige red la primera vez; la segunda hincha el
  fuente. Propuesta: descarga con SHA-256 y `FERROLOOM_LIB_DIR` para desarrollo.
- **D2 — Fork del loader.** ¿Fork mínimo de Fabric Loader o reimplementar solo la secuencia de arranque
  sobre las clases `impl` de upstream? Connector acabó forkeando; empezar por fork mínimo y medir.
- **D3 — Versión de Java.** La que instale el launcher oficial para 26.3; validar en runtime y rechazar
  versiones anteriores con un mensaje claro.
- **D4 — Zoom (resuelto).** Objetivo primario: **Logical Zoom** (MIT, un keybind y un mixin sobre
  `GameRenderer.getFov`). Alternativa si no hay build 26.3: **Just Zoom**. Zoomify se descarta para
  v0.1 (Kotlin, LGPL, superficie grande).
- **D5 — Nombre del paquete de compatibilidad.** `ferrofabric-api` se registra ante Fabric Loader como
  `fabric-api` con versión propia; confirmar que ningún mod compara la versión exacta contra upstream.
- **D6 — Servidor.** v0.1 valida solo cliente; el arranque en servidor dedicado se diseña pero no se
  valida.