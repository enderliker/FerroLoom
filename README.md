# FerroLoom

Host de JVM para Horizon (Minecraft 26.3 en Rust). Plan 1: esqueleto caminante del puente.

FerroLoom se instala como un mod nativo `.hmod`. Al registrarse localiza Java, crea una JVM
embebida y, en cada `client_tick_pre`, llama a Java y aplica las escrituras que Java encoló.
Todavía **no** carga mods de Fabric: eso es el Plan 2.

## Qué funciona

- Descubrimiento de JDK: raíces del runtime de Minecraft → `JAVA_HOME`, con rechazo de
  versiones anteriores a 21.
- Carga de `libjvm` con errores tipados y una sola JVM por proceso.
- Puente JNI con `RegisterNatives`; un pánico de Rust cruza como `RuntimeException`.
- Arena de handles generacional, ventana de lectura por callback y cola de escrituras.
- Round-trip completo de un tick: Java lee la posición, encola un log y Rust lo aplica.
- 24 tests de Rust y 3 de Java.

## Qué no hace todavía

- Fabric Loader, remapeo de mods y el shim de `net.minecraft.*` (Plan 2).
- El mod de ejemplo y Zoom (Plan 3).
- FerroFabric-API (Plan 4).
- Servidor dedicado y hooks de worker (v0.2).
- Empaquetado y registro (Plan 5).

## Construir y probar

```sh
cargo test
cargo horizon-mod check
cd java && ./gradlew :ferroloom-runtime:test
cargo horizon-mod build          # deja target/horizon-mod/ferroloom.hmod
```

## Ajustes

| Variable | Para qué | Defecto |
|---|---|---|
| `FERROLOOM_LIB_DIR` | Carpeta con `ferroloom-runtime.jar` | (obligatoria) |
| `FERROLOOM_JAVA_HOME` | JDK explícito | — |
| `FERROLOOM_MC_RUNTIME` | Raíces del runtime de Minecraft, separadas por `:` | `~/.minecraft/runtime` |
| `FERROLOOM_XMX_MIB` | Heap de la JVM, 256..8192 | 1024 |
| `JAVA_HOME` | JDK de respaldo | — |

## Probar en el juego

Ver `../docs/smoke.md` y `../tools/smoke.sh`. El proceso del juego debe heredar
`FERROLOOM_LIB_DIR` y `JAVA_HOME`; con el launcher, fíjalos con
`horizon-launcher instance launch <instancia> --env "NAME=value ..."`.

## Limitación conocida (Tarea 12)

El launcher rechaza el paquete al construir la instancia: su grafo fijado usa `jni 0.22.4`
y el mod usa `jni 0.21.1`; el chequeo de `Cargo.lock` exige que el grafo del juego no
cambie. Para arrancar en el juego hace falta adaptar el puente a `jni 0.22` (API
`EnvUnowned`) o una excepción del launcher. El host y el puente están verificados por la
suite y por el binario `jvm_smoke`; lo no verificado es el arranque dentro de Horizon.

## Licencia

Apache-2.0. Ver `NOTICE`. Sin código ni assets de Mojang.