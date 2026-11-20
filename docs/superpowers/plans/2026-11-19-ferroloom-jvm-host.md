# FerroLoom — Plan 1: JVM host walking skeleton

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Un `.hmod` nativo de Horizon que arranca una JVM embebida y intercambia lecturas y escrituras con el motor en cada tick, sin shim ni Fabric Loader todavía.

**Architecture:** Una sola crate Rust (`ferroloom`) es el paquete nativo: localiza Java, carga `libjvm`, crea la JVM y expone un puente JNI con protocolo de opcodes, arena de handles, ventana de lectura y cola de escrituras. Un jar Java (`ferroloom-runtime`) contiene el lado Java del puente y su arranque. Una capa fina conecta los hooks de Horizon con el puente y no conoce el protocolo.

**Tech Stack:** Rust edición 2024 (toolchain 1.95, el del launcher de Horizon), `jni` 0.21 con feature `invocation`, `libloading` 0.8, Gradle 8 + toolchain JDK 21, JUnit 5, `horizon-mod-api`.

**Spec:** `docs/superpowers/specs/2026-11-19-ferroloom-design.md`

**Scope:** Plan 1 de 5. Le siguen: 2) Fabric Loader + remapper + generador de shim; 3) mod de ejemplo + Zoom; 4) módulos de FerroFabric-API; 5) empaquetado y registro. Este plan **no** carga ningún mod de Fabric: prueba el host, el puente y el ciclo de vida.

## Global Constraints

- Rust edición 2024, toolchain 1.95. El launcher compila con su propio toolchain; no fijar `rust-toolchain.toml` en el paquete.
- **Sin JDK en build.** `libjvm` se carga en runtime con `jni`/`invocation` (`java-locator` + `libloading`).
- El paquete `.hmod` solo puede contener `horizon-mod.toml`, `Cargo.toml`, `Cargo.lock`, `build.rs`, `.rs`/`.toml` bajo `src/` y textos raíz `README*`/`LICENSE*`/`NOTICE*`. **Nada de jars** dentro del paquete.
- Toda dependencia de crates.io se fija con `horizon-launcher native-lock .` antes de construir.
- Sin código ni assets de Mojang.
- Licencia Apache-2.0 para FerroLoom; Fabric Loader y Fabric API son Apache-2.0 y se citan en NOTICE cuando entren (Plan 2).
- v0.1 y este plan son **solo cliente**. No se despachan hooks de servidor ni de worker.
- Java 21+ obligatorio en runtime. Sin Java, el mod queda inerte con un aviso; nunca tumba el juego.
- Plataforma objetivo del plan: Linux x86-64. macOS y Windows se añaden sin cambiar interfaces.
- Nada de red en build ni en runtime en este plan: el jar de la runtime se toma de `FERROLOOM_LIB_DIR`.

## Review Focus

1. **Java ausente o demasiado viejo** (`JAVA_HOME` a Java 8/17): el usuario espera un aviso que diga qué encontró y qué necesita, y que el juego siga abriendo. Lo cubre Task 2.
2. **`libjvm` ausente, corrupta o de otra arquitectura**: espera un error tipado con la ruta probada, no un `dlopen` que aborta. Lo cubre Task 3.
3. **Segunda creación de JVM** (recarga del mod, dos instancias): espera una negativa limpia (`AlreadyCreated`), no un crash del proceso. Lo cubre Task 4.
4. **Llamada al motor desde un hilo creado por Java**: espera `FerroNotInCallbackException`, nunca comportamiento indefinido. Lo cubre Task 8.
5. **Pánico de Rust dentro de un upcall**: espera una excepción Java normal y el juego vivo, nunca un unwind a través de JNI. Lo cubre Task 6.

---

### Task 1: Paquete y builds

**Files:**
- Create: `FerroLoom/` (scaffold de `cargo horizon-mod new`, luego editar)
- Create: `FerroLoom/java/settings.gradle.kts`
- Create: `FerroLoom/java/ferroloom-runtime/build.gradle.kts`
- Create: `FerroLoom/java/ferroloom-runtime/src/main/java/dev/ferroloom/runtime/FerroRuntime.java`
- Create: `FerroLoom/java/ferroloom-runtime/src/test/java/dev/ferroloom/runtime/FerroRuntimeTest.java`
- Create: `FerroLoom/.gitignore`

**Interfaces:**
- Consumes: nada.
- Produces: `dev.ferroloom.runtime.FerroRuntime#ping(): String`; layout de repositorio (`src/` para Rust, `java/` para Java).

- [ ] **Step 1: Scaffold the native package**

Run:
```bash
cd '/home/ender/projects/Ferro loom'
cargo horizon-mod new FerroLoom --template native
```
Expected: `FerroLoom/horizon-mod.toml` con `[native]` y commits exactos, `Cargo.toml`, `src/lib.rs`, `assets/`, `data/`, `.horizon/sdk/`.

- [ ] **Step 2: Initialize the repo and ignores**

```bash
cd FerroLoom && git init
printf '%s\n' '.horizon/' 'target/' 'java/.gradle/' 'java/**/build/' '*.log' >> .gitignore
```

- [ ] **Step 3: Write the failing Java test**

```java
package dev.ferroloom.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

class FerroRuntimeTest {
    @Test
    void pingReturnsPong() {
        assertEquals("pong", FerroRuntime.ping());
    }
}
```

- [ ] **Step 4: Add the Gradle skeleton and run the test to see it fail**

`java/settings.gradle.kts`:
```kotlin
rootProject.name = "ferroloom-java"
include("ferroloom-runtime")
```

`java/ferroloom-runtime/build.gradle.kts`:
```kotlin
plugins { `java-library` }
java { toolchain { languageVersion.set(JavaLanguageVersion.of(21)) } }
repositories { mavenCentral() }
dependencies { testImplementation(platform("org.junit:junit-bom:5.11.4")); testImplementation("org.junit.jupiter:junit-jupiter") }
tasks.test { useJUnitPlatform() }
```

Run:
```bash
cd java && gradle wrapper --gradle-version 8.10 && ./gradlew :ferroloom-runtime:test
```
Expected: FAIL — `FerroRuntime` has no `ping`.

- [ ] **Step 5: Implement `ping`**

`FerroRuntime.java`:
```java
package dev.ferroloom.runtime;

public final class FerroRuntime {
    private FerroRuntime() {}
    public static String ping() { return "pong"; }
}
```

- [ ] **Step 6: Run both builds to verify they pass**

```bash
cd java && ./gradlew :ferroloom-runtime:test
cd .. && cargo test
```
Expected: Gradle PASS (1 test); `cargo test` PASS (0 tests, compiles).

- [ ] **Step 7: Commit**

```bash
git add -A && git commit -m "chore: scaffold ferroloom package and java build"
```

---

### Task 2: JVM locator and version gate

**Files:**
- Create: `FerroLoom/src/jvm/mod.rs`
- Create: `FerroLoom/src/jvm/locate.rs`
- Create: `FerroLoom/src/jvm/release.rs`
- Modify: `FerroLoom/src/lib.rs` (add `mod jvm;`)
- Test: `FerroLoom/tests/jvm_locate.rs`

**Interfaces:**
- Consumes: nothing.
- Produces:
  - `pub struct JavaHome { pub path: PathBuf, pub version: JavaVersion }`
  - `pub struct JavaVersion { pub major: u32, pub full: String }`
  - `pub enum JvmLocateError { NotFound { tried: Vec<PathBuf> }, VersionTooOld { found: JavaVersion, minimum: u32 }, Unreadable { path: PathBuf, reason: String } }`
  - `pub struct LocateSources<'a> { pub explicit: Option<&'a Path>, pub minecraft_roots: &'a [PathBuf], pub java_home: Option<&'a Path> }`
  - `pub fn locate(sources: LocateSources<'_>) -> Result<JavaHome, JvmLocateError>`
  - `pub const MINIMUM_JAVA: u32 = 21;`

- [ ] **Step 1: Write the failing tests**

```rust
use ferroloom::jvm::locate::{locate, JvmLocateError, LocateSources};
use std::fs;

fn fake_jdk(dir: &std::path::Path, version: &str) {
    fs::create_dir_all(dir.join("bin")).unwrap();
    fs::write(dir.join("bin/java"), b"#!/bin/sh\n").unwrap();
    fs::write(dir.join("release"), format!("JAVA_VERSION=\"{version}\"\n")).unwrap();
}

#[test]
fn explicit_path_wins_and_reports_version() {
    let tmp = tempfile::tempdir().unwrap();
    fake_jdk(tmp.path(), "21.0.5");
    let home = locate(LocateSources { explicit: Some(tmp.path()), minecraft_roots: &[], java_home: None }).unwrap();
    assert_eq!(home.version.major, 21);
    assert_eq!(home.version.full, "21.0.5");
}

#[test]
fn java_home_env_is_the_fallback() {
    let tmp = tempfile::tempdir().unwrap();
    fake_jdk(tmp.path(), "25");
    let home = locate(LocateSources { explicit: None, minecraft_roots: &[], java_home: Some(tmp.path()) }).unwrap();
    assert_eq!(home.version.major, 25);
}

#[test]
fn newest_minecraft_runtime_wins_over_java_home() {
    let mc = tempfile::tempdir().unwrap();
    fake_jdk(&mc.path().join("java-runtime-alpha"), "21.0.5");
    fake_jdk(&mc.path().join("java-runtime-delta"), "25.0.1");
    let jh = tempfile::tempdir().unwrap();
    fake_jdk(jh.path(), "21.0.9");
    let roots = [mc.path().to_path_buf()];
    let home = locate(LocateSources { explicit: None, minecraft_roots: &roots, java_home: Some(jh.path()) }).unwrap();
    assert_eq!(home.version.major, 25);
}

#[test]
fn nothing_found_names_every_path_tried() {
    let err = locate(LocateSources::default()).unwrap_err();
    match err { JvmLocateError::NotFound { tried } => assert!(!tried.is_empty()), e => panic!("{e:?}") }
}

#[test]
fn java_17_is_refused_with_both_versions() {
    let tmp = tempfile::tempdir().unwrap();
    fake_jdk(tmp.path(), "17.0.10");
    match locate(LocateSources { explicit: Some(tmp.path()), minecraft_roots: &[], java_home: None }).unwrap_err() {
        JvmLocateError::VersionTooOld { found, minimum } => { assert_eq!(found.major, 17); assert_eq!(minimum, 21); }
        e => panic!("{e:?}"),
    }
}
```
`LocateSources::default()` yields all-empty sources; derive `Default`.

- [ ] **Step 2: Add dev-dependency and run the tests to see them fail**

Add to `Cargo.toml` under `[dev-dependencies]`: `tempfile = "3"`.
Run: `cargo test --test jvm_locate`
Expected: FAIL — module `jvm` does not exist.

- [ ] **Step 3: Implement the locator**

`release.rs`: `pub fn parse_release(text: &str) -> Option<JavaVersion>` reading `JAVA_VERSION="…"`; major is the leading integer before `.` or `"`.
`locate.rs`: order is `explicit`, then each `minecraft_roots` entry's immediate children (`<root>/java-runtime-*/`), newest version first, then `java_home`. A JDK candidate is valid when `bin/java` and `release` exist; error variants as declared. `JavaVersion.full` keeps the quoted string.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `cargo test --test jvm_locate`
Expected: PASS (4 tests).

- [ ] **Step 5: Commit**

```bash
git add Cargo.toml src/jvm tests/jvm_locate.rs && git commit -m "feat(jvm): locate a JDK and refuse versions below 21"
```

---

### Task 3: libjvm discovery and load

**Files:**
- Create: `FerroLoom/src/jvm/libjvm.rs`
- Modify: `FerroLoom/src/jvm/mod.rs`
- Test: `FerroLoom/tests/jvm_libjvm.rs`

**Interfaces:**
- Consumes: `JavaHome` from Task 2.
- Produces:
  - `pub fn find_libjvm(java_home: &Path) -> Result<PathBuf, LibjvmError>`
  - `pub enum LibjvmError { NotFound { tried: Vec<PathBuf> }, LoadFailed { path: PathBuf, reason: String }, CreateVm { code: i32 } }`
  - `pub unsafe fn load(path: &Path) -> Result<libloading::Library, LibjvmError>`
  - `pub unsafe fn create_vm(lib: &libloading::Library, options: &[String]) -> Result<*mut jni::sys::JavaVM, LibjvmError>`

- [ ] **Step 1: Write the failing tests**

```rust
use ferroloom::jvm::libjvm::{find_libjvm, load, LibjvmError};
use std::fs;

#[test]
fn finds_server_libjvm_on_linux_layout() {
    let tmp = tempfile::tempdir().unwrap();
    let p = tmp.path().join("lib/server/libjvm.so");
    fs::create_dir_all(p.parent().unwrap()).unwrap();
    fs::write(&p, b"x").unwrap();
    assert_eq!(find_libjvm(tmp.path()).unwrap(), p);
}

#[test]
fn missing_libjvm_lists_candidates() {
    let tmp = tempfile::tempdir().unwrap();
    match find_libjvm(tmp.path()).unwrap_err() {
        LibjvmError::NotFound { tried } => assert!(tried.iter().any(|p| p.ends_with("lib/server/libjvm.so"))),
        e => panic!("{e:?}"),
    }
}

#[test]
fn garbage_file_fails_to_load_with_its_path() {
    let tmp = tempfile::tempdir().unwrap();
    let p = tmp.path().join("libjvm.so");
    fs::write(&p, b"not an elf").unwrap();
    match unsafe { load(&p) } {
        Err(LibjvmError::LoadFailed { path, reason }) => { assert_eq!(path, p); assert!(!reason.is_empty()); }
        _ => panic!("expected LoadFailed"),
    }
}
```

- [ ] **Step 2: Run the tests to see them fail**

Run: `cargo test --test jvm_libjvm`
Expected: FAIL — module not found.

- [ ] **Step 3: Implement discovery and loading**

Candidates in order: `lib/server/libjvm.so`, `lib/libjvm.so`, `bin/server/jvm.dll`, `lib/server/libjvm.dylib`, `lib/libjvm.dylib`, `jre/lib/amd64/server/libjvm.so`. `load` uses `libloading::Library::new` and maps the error text into `LoadFailed`.

`create_vm` declares the ABI-stable C structs `JavaVMOption { option_string: *mut c_char, extra_info: *mut c_void }` and `JavaVMInitArgs { version: jint, n_options: jint, options: *mut JavaVMOption, ignore_unrecognized: jboolean }` with `version = 0x0001_0008`, takes the `JNI_CreateJavaVM` symbol from the library, and returns the `*mut JavaVM` (or `CreateVm { code }`). Keep the `Library` alive for the VM's lifetime: store it in `Jvm`.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `cargo test --test jvm_libjvm`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add src/jvm tests/jvm_libjvm.rs && git commit -m "feat(jvm): find and load libjvm with typed errors"
```

---

### Task 4: Create the JVM once

**Files:**
- Create: `FerroLoom/src/jvm/mod.rs` (add `Jvm`, `JvmConfig`, `JvmError`)
- Create: `FerroLoom/src/bin/jvm_smoke.rs`
- Test: `FerroLoom/tests/jvm_start.rs`

**Interfaces:**
- Consumes: `locate`, `find_libjvm`, `load`.
- Produces:
  - `pub struct JvmConfig { pub java_home: PathBuf, pub classpath: Vec<PathBuf>, pub xmx_mib: u32, pub options: Vec<String> }`
  - `pub struct Jvm { vm: jni::JavaVM, lib: libloading::Library }`
  - `pub enum JvmError { AlreadyCreated, Locate(JvmLocateError), Libjvm(LibjvmError), Create { code: i32 }, Attach { reason: String }, JavaException { class: String, message: String } }`
  - `impl Jvm { pub fn start(cfg: &JvmConfig) -> Result<Jvm, JvmError>; pub fn with_env<R>(&self, f: impl FnOnce(&mut jni::JNIEnv) -> R) -> Result<R, JvmError>; pub fn call_static(&self, class: &str, method: &str, sig: &str, args: &[i64]) -> Result<i64, JvmError>; }`
  - `impl JvmConfig { pub fn for_tests() -> JvmConfig }` (classpath from `FERROLOOM_LIB_DIR`)
  - `pub fn locate_for_test() -> Result<JavaHome, JvmLocateError>`
  - `pub fn mark_created_for_test()`
  - `pub static CREATED: AtomicBool`

- [ ] **Step 1: Write the failing test**

```rust
use ferroloom::jvm::{locate_for_test, Jvm, JvmConfig, JvmError};

#[test]
fn a_second_start_is_refused_without_creating_anything() {
    // Fast path: the guard is checked before any JVM work.
    ferroloom::jvm::mark_created_for_test();
    let cfg = JvmConfig { java_home: std::path::PathBuf::from("/nonexistent"), classpath: vec![], xmx_mib: 64, options: vec![] };
    assert!(matches!(Jvm::start(&cfg), Err(JvmError::AlreadyCreated)));
}
```
Also add one ignored integration test: `#[ignore]` `starts_a_real_jvm` that calls `locate_for_test()` and starts a VM, then asserts `Jvm::start` again is `AlreadyCreated`.

- [ ] **Step 2: Run the test to see it fail**

Run: `cargo test --test jvm_start`
Expected: FAIL — items not found.

- [ ] **Step 3: Implement creation**

Add `jni = { version = "0.21", features = ["invocation"] }` and `libloading = "0.8"` to `[dependencies]`. `start` checks `CREATED.compare_exchange(false, true)`; then `find_libjvm` + `load` + `create_vm` (Task 3) with options `-Djava.class.path=…`, `-Xmx{m}M` and `cfg.options`; wrap the returned pointer with `unsafe { jni::JavaVM::from_raw(ptr) }` and keep the `Library` in `Jvm`. **Never call `JavaVM::new`**: it would let `java-locator` pick a different JVM than the one we resolved. Map a failed create to `Create { code }`. `call_static` maps a pending Java exception to `JavaException { class, message }` (read and clear it). Expose `mark_created_for_test()`, `locate_for_test()` and `JvmConfig::for_tests()`.

- [ ] **Step 4: Write the smoke binary**

`src/bin/jvm_smoke.rs`: reads the jar path from `FERROLOOM_LIB_DIR` (appends `ferroloom-runtime.jar`), starts the JVM, calls `dev.ferroloom.runtime.FerroRuntime.ping()` with `with_env`, prints the returned string.

If `cargo horizon-mod check` refuses a second target under `src/bin`, move the file to `tests/jvm_smoke.rs` and run it with `cargo test --test jvm_smoke -- --nocapture`. Either location proves the same thing; do not fight the packager.

- [ ] **Step 5: Run the tests**

```bash
cargo test --test jvm_start
# with a JDK available:
FERROLOOM_LIB_DIR=<path to jar dir> cargo run --bin jvm_smoke
```
Expected: unit test PASS; smoke prints `pong` (after Task 5). Until Task 5, expect a `ClassNotFound` error naming `FerroRuntime`, which is the correct failure.

- [ ] **Step 6: Commit**

```bash
git add Cargo.toml src/jvm src/bin tests/jvm_start.rs && git commit -m "feat(jvm): create the JVM once and call into Java"
```

---

### Task 5: Java runtime and bootstrap

**Files:**
- Modify: `FerroLoom/java/ferroloom-runtime/src/main/java/dev/ferroloom/runtime/FerroRuntime.java`
- Create: `FerroLoom/java/ferroloom-runtime/src/main/java/dev/ferroloom/runtime/FerroBootstrap.java`
- Test: `FerroLoom/java/ferroloom-runtime/src/test/java/dev/ferroloom/runtime/FerroBootstrapTest.java`

**Interfaces:**
- Consumes: `FerroRuntime#ping`.
- Produces: `FerroRuntime#onTick(long)`, `FerroRuntime#shutdown()`, `FerroBootstrap#main(String[])` que imprime `FerroLoom runtime ready` y luego `ready` es verificable.

- [ ] **Step 1: Write the failing tests**

```java
@Test void bootstrapReportsReady() throws Exception {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    PrintStream old = System.out;
    System.setOut(new PrintStream(out, true, StandardCharsets.UTF_8));
    try { FerroBootstrap.main(new String[0]); } finally { System.setOut(old); }
    assertTrue(out.toString(StandardCharsets.UTF_8).contains("FerroLoom runtime ready"));
}

@Test void shutdownIsIdempotent() { FerroRuntime.shutdown(); FerroRuntime.shutdown(); }
```

- [ ] **Step 2: Run to see them fail**

Run: `cd java && ./gradlew :ferroloom-runtime:test`
Expected: FAIL — `FerroBootstrap` missing.

- [ ] **Step 3: Implement**

```java
public final class FerroBootstrap {
    public static void main(String[] args) { System.out.println("FerroLoom runtime ready"); }
}
```
`FerroRuntime#onTick(long tick)` empty; `shutdown()` sets a static flag.

- [ ] **Step 4: Run to verify pass**

Run: `cd java && ./gradlew :ferroloom-runtime:test`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add java && git commit -m "feat(java): runtime bootstrap and lifecycle"
```

---

### Task 6: JNI opcode protocol and panic boundary

**Files:**
- Create: `FerroLoom/src/bridge/mod.rs`
- Create: `FerroLoom/src/bridge/ops.rs`
- Create: `FerroLoom/src/bridge/jni.rs`
- Create: `FerroLoom/java/ferroloom-runtime/src/main/java/dev/ferroloom/runtime/FerroNative.java`
- Modify: `FerroLoom/src/lib.rs` (add `mod bridge;`)
- Test: `FerroLoom/tests/jni_ops.rs`

**Interfaces:**
- Consumes: `Jvm` from Task 4.
- Produces:
  - `#[repr(i32)] pub enum Op { Ping = 1, PlayerPos = 2, Log = 3, PanicForTest = 99 }`
  - Java: `FerroNative.call(int op, long a) -> long`, `FerroNative.callString(int op, String s) -> String`, `FerroNative.playerPosition() -> double[]`
  - Rust externs named `Java_dev_ferroloom_runtime_FerroNative_call`, `_callString`, `_playerPosition`
  - Every extern body is wrapped in `std::panic::catch_unwind`; a panic throws `java.lang.RuntimeException("ferroloom: internal error: <msg>")`.

- [ ] **Step 1: Write the failing test**

```rust
#[test]
fn ping_round_trips_and_panic_becomes_a_java_exception() {
    if ferroloom::jvm::locate_for_test().is_err() { eprintln!("skip: no JDK"); return; }
    let jvm = ferroloom::jvm::Jvm::start(&ferroloom::jvm::JvmConfig::for_tests()).unwrap();
    let out = jvm.call_static("dev/ferroloom/runtime/FerroNative", "call", "(IJ)J", &[1, 41]).unwrap();
    assert_eq!(out, 42);
    let err = jvm.call_static("dev/ferroloom/runtime/FerroNative", "call", "(IJ)J", &[99, 0]).unwrap_err();
    assert!(err.to_string().contains("ferroloom: internal error"));
}
```
Add `Jvm::call_static(class, method, sig, args) -> Result<long, JvmError>` as a thin helper over `with_env`.

- [ ] **Step 2: Run to see it fail**

Run: `cargo test --test jni_ops`
Expected: FAIL — no `bridge` module / `call_static`.

- [ ] **Step 3: Implement the externs and Java natives**

Rust: `JNIEXPORT` with `extern "system"`; inside, `catch_unwind(AssertUnwindSafe(|| dispatch(env, op, a)))`; on `Err`, `env.throw_new("java/lang/RuntimeException", msg)` and return `0`.
Dispatch: `Ping → a + 1`; `PanicForTest → panic!("boom")`; `Log` enqueues through Task 9's queue once it exists (return 0 until then).
Java `FerroNative` declares the three `native` methods.

- [ ] **Step 4: Run to verify pass**

Run: `cargo test --test jni_ops`
Expected: PASS (or a printed skip on a machine without a JDK).

- [ ] **Step 5: Commit**

```bash
git add src/bridge tests/jni_ops.rs java && git commit -m "feat(bridge): opcode protocol with panic-to-exception boundary"
```

---

### Task 7: Handle arena

**Files:**
- Create: `FerroLoom/src/bridge/handles.rs`
- Test: `FerroLoom/tests/handles.rs`

**Interfaces:**
- Produces:
  - `#[derive(Clone, Copy, PartialEq, Eq, Hash, Debug)] pub struct Handle(u64)`
  - `pub struct HandleArena<T>`
  - `impl<T> HandleArena<T> { pub fn insert(&mut self, v: T) -> Handle; pub fn get(&self, h: Handle) -> Option<&T>; pub fn get_mut(&mut self, h: Handle) -> Option<&mut T>; pub fn remove(&mut self, h: Handle) -> Option<T>; pub fn invalidate_all(&mut self); }`

- [ ] **Step 1: Write the failing tests**

```rust
#[test]
fn insert_get_remove_round_trips() {
    let mut a = ferroloom::bridge::handles::HandleArena::new();
    let h = a.insert("player");
    assert_eq!(a.get(h), Some(&"player"));
    assert_eq!(a.remove(h), Some("player"));
    assert_eq!(a.get(h), None);
}

#[test]
fn invalidate_all_makes_old_handles_stale() {
    let mut a = ferroloom::bridge::handles::HandleArena::new();
    let h = a.insert(7);
    a.invalidate_all();
    assert_eq!(a.get(h), None);
    assert_ne!(a.insert(8), h);
}
```

- [ ] **Step 2: Run to see fail** — `cargo test --test handles` → FAIL.
- [ ] **Step 3: Implement** — `Handle` packs `index: u32 | generation: u32`; arena uses `Vec<Option<(u32, T)>>` plus a free list; `invalidate_all` bumps a global generation and clears slots.
- [ ] **Step 4: Run to verify pass** — `cargo test --test handles` → PASS (2 tests).
- [ ] **Step 5: Commit** — `git add src/bridge/handles.rs tests/handles.rs && git commit -m "feat(bridge): generational handle arena"`

---

### Task 8: Read window and foreign-thread rejection

**Files:**
- Create: `FerroLoom/src/bridge/window.rs`
- Test: `FerroLoom/tests/window.rs`

**Interfaces:**
- Produces:
  - `pub trait EngineWindow { fn read_player_pos(&self) -> Option<[f64; 3]>; fn log(&mut self, msg: &str); }`
  - `pub fn with_window<R>(w: &mut dyn EngineWindow, f: impl FnOnce() -> R) -> R`
  - `pub fn current_player_pos() -> Result<[f64; 3], BridgeError>`
  - `pub fn current_log(msg: &str) -> Result<(), BridgeError>`
  - `pub enum BridgeError { NotInCallback, Unavailable }`

- [ ] **Step 1: Write the failing tests**

```rust
struct Fake(Option<[f64; 3]>);
impl ferroloom::bridge::window::EngineWindow for Fake {
    fn read_player_pos(&self) -> Option<[f64; 3]> { self.0 }
    fn log(&mut self, _: &str) {}
}

#[test]
fn reads_inside_the_window() {
    let mut f = Fake(Some([1.0, 64.0, -2.0]));
    let got = ferroloom::bridge::window::with_window(&mut f, || ferroloom::bridge::window::current_player_pos());
    assert_eq!(got, Ok([1.0, 64.0, -2.0]));
}

#[test]
fn reads_outside_the_window_are_refused() {
    assert_eq!(ferroloom::bridge::window::current_player_pos(), Err(ferroloom::bridge::window::BridgeError::NotInCallback));
}

#[test]
fn a_foreign_thread_is_refused() {
    let mut f = Fake(Some([0.0, 0.0, 0.0]));
    let h = std::thread::spawn(|| ferroloom::bridge::window::current_player_pos());
    let inside = ferroloom::bridge::window::with_window(&mut f, || h.join().unwrap());
    assert_eq!(inside, Err(ferroloom::bridge::window::BridgeError::NotInCallback));
}

#[test]
fn a_panic_restores_the_empty_window() {
    let mut f = Fake(Some([0.0, 0.0, 0.0]));
    let _ = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| {
        ferroloom::bridge::window::with_window(&mut f, || panic!("boom"))
    }));
    assert!(ferroloom::bridge::window::current_player_pos().is_err());
}
```

- [ ] **Step 2: Run to see fail** — `cargo test --test window` → FAIL.
- [ ] **Step 3: Implement** — a `thread_local!` holding `Cell<*mut dyn EngineWindow>` (or a `RefCell<Option<NonNull<dyn EngineWindow>>>`); `with_window` saves the previous value, sets, runs `f`, restores via a guard (`Drop`) so panics restore; the thread-local is per thread, which makes the foreign thread see `None`.
- [ ] **Step 4: Run to verify pass** — `cargo test --test window` → PASS (4 tests).
- [ ] **Step 5: Commit** — `git add src/bridge/window.rs tests/window.rs && git commit -m "feat(bridge): scoped read window with foreign-thread refusal"`

---

### Task 9: Write queue

**Files:**
- Create: `FerroLoom/src/bridge/writes.rs`
- Test: `FerroLoom/tests/writes.rs`

**Interfaces:**
- Produces:
  - `pub enum WriteOp { Log(String) }`
  - `pub trait WriteSink { fn apply(&mut self, op: WriteOp) -> Result<(), String>; }`
  - `pub struct WriteQueue` with `new()`, `enqueue(WriteOp)`, `drain(&mut dyn WriteSink) -> DrainReport`, `len()`
  - `pub struct DrainReport { pub applied: usize, pub failed: Vec<(usize, String)> }`

- [ ] **Step 1: Write the failing tests**

```rust
struct Sink(Vec<String>, bool);
impl ferroloom::bridge::writes::WriteSink for Sink {
    fn apply(&mut self, op: ferroloom::bridge::writes::WriteOp) -> Result<(), String> {
        match op { ferroloom::bridge::writes::WriteOp::Log(s) => { self.0.push(s); if self.1 { Err("nope".into()) } else { Ok(()) } } }
    }
}

#[test]
fn drains_in_fifo_order() {
    let mut q = ferroloom::bridge::writes::WriteQueue::new();
    q.enqueue(ferroloom::bridge::writes::WriteOp::Log("a".into()));
    q.enqueue(ferroloom::bridge::writes::WriteOp::Log("b".into()));
    let mut s = Sink(vec![], false);
    let r = q.drain(&mut s);
    assert_eq!(r.applied, 2);
    assert_eq!(s.0, vec!["a", "b"]);
    assert_eq!(q.len(), 0);
}

#[test]
fn one_failure_does_not_stop_the_rest() {
    let mut q = ferroloom::bridge::writes::WriteQueue::new();
    q.enqueue(ferroloom::bridge::writes::WriteOp::Log("a".into()));
    q.enqueue(ferroloom::bridge::writes::WriteOp::Log("b".into()));
    let mut s = Sink(vec![], true);
    let r = q.drain(&mut s);
    assert_eq!(r.failed.len(), 2);
    assert_eq!(s.0.len(), 2);
}
```

- [ ] **Step 2: Run to see fail** — `cargo test --test writes` → FAIL.
- [ ] **Step 3: Implement** — `VecDeque<WriteOp>`; `drain` pops until empty, applies each, records index and error; `applied` counts successes.
- [ ] **Step 4: Run to verify pass** — `cargo test --test writes` → PASS (2 tests).
- [ ] **Step 5: Commit** — `git add src/bridge/writes.rs tests/writes.rs && git commit -m "feat(bridge): write queue with isolated failures"`

---

### Task 10: Tick round-trip

**Files:**
- Create: `FerroLoom/src/bridge/upcall.rs`
- Modify: `FerroLoom/src/bin/jvm_smoke.rs` (tick mode)
- Test: `FerroLoom/tests/tick_roundtrip.rs`

**Interfaces:**
- Consumes: `Jvm`, `with_window`, `WriteQueue`, `WriteSink`, `FerroRuntime#onTick`.
- Produces:
  - `pub struct TickHost<'a> { pub window: &'a mut dyn EngineWindow, pub writes: &'a mut WriteQueue }`
  - `pub fn on_client_tick(vm: &Jvm, tick: u64, host: TickHost) -> Result<DrainReport, JvmError>`

- [ ] **Step 1: Write the failing test**

```rust
struct FakeWindow(Option<[f64; 3]>, Vec<String>);
impl ferroloom::bridge::window::EngineWindow for FakeWindow {
    fn read_player_pos(&self) -> Option<[f64; 3]> { self.0 }
    fn log(&mut self, msg: &str) { self.1.push(msg.to_string()); }
}

#[test]
fn java_reads_position_and_its_log_write_is_applied_after_the_call() {
    if ferroloom::jvm::locate_for_test().is_err() { eprintln!("skip: no JDK"); return; }
    let jvm = ferroloom::jvm::Jvm::start(&ferroloom::jvm::JvmConfig::for_tests()).unwrap();
    let mut w = FakeWindow(Some([1.5, 64.0, -2.5]), vec![]);
    let mut q = ferroloom::bridge::writes::WriteQueue::new();
    let report = ferroloom::bridge::upcall::on_client_tick(&jvm, 7, ferroloom::bridge::upcall::TickHost { window: &mut w, writes: &mut q }).unwrap();
    assert_eq!(report.applied, 1);
    assert_eq!(w.logs, vec!["pos=1.5,64.0,-2.5 tick=7"]);
}
```
`FakeWindow` records `log` calls; Java `onTick` reads `FerroNative.playerPosition()` and calls `FerroNative.callString(3, "pos=... tick=" + tick)`.

- [ ] **Step 2: Run to see fail** — `cargo test --test tick_roundtrip` → FAIL.
- [ ] **Step 3: Implement** — `on_client_tick` opens the window, calls `FerroRuntime.onTick(J)` via `with_env`, then `q.drain(sink)` where `sink` forwards to `window.log`; returns the report. Extend Java `onTick` and Rust op `Log` to `q.enqueue(WriteOp::Log(s))`.
- [ ] **Step 4: Run to verify pass** — `cargo test --test tick_roundtrip` → PASS.
- [ ] **Step 5: Commit** — `git add src/bridge src/bin tests/tick_roundtrip.rs java && git commit -m "feat(bridge): tick upcall with queued writes drained after return"`

---

### Task 11: Wire into the Horizon mod

**Files:**
- Modify: `FerroLoom/src/lib.rs`
- Create: `FerroLoom/src/horizon.rs`
- Create: `FerroLoom/src/settings.rs`
- Test: `FerroLoom/tests/settings.rs`

**Interfaces:**
- Consumes: `locate`, `Jvm`, `on_client_tick`, `HandleArena`.
- Produces:
  - `pub struct Settings { pub java_home: Option<PathBuf>, pub lib_dir: Option<PathBuf>, pub minecraft_roots: Vec<PathBuf>, pub xmx_mib: u32 }`
  - `impl Settings { pub fn from_env() -> Settings; pub fn parse(xmx: Option<&str>, java_home: Option<&str>, lib_dir: Option<&str>) -> Settings }` reading `FERROLOOM_JAVA_HOME`, `FERROLOOM_LIB_DIR`, `FERROLOOM_XMX_MIB` (default 1024) and `FERROLOOM_MC_RUNTIME` (colon-separated roots); `minecraft_roots` defaults to `~/.minecraft/runtime` when it exists.
  - `impl Hooks for Mod` with `register(&self, registrar: &mut dyn Registrar, cx: &RegistrationContext)` y `client_tick_pre(&self, host: &mut dyn ClientHost, _: &mut ClientTickPreEvent)`.

- [ ] **Step 1: Write the failing settings test**

```rust
#[test]
fn env_defaults_are_sane() {
    let s = ferroloom::settings::Settings::from_env();
    assert_eq!(s.xmx_mib, 1024);
}

#[test]
fn xmx_is_parsed_and_clamped() {
    let s = ferroloom::settings::Settings::parse(Some("16"), None, None);
    assert_eq!(s.xmx_mib, 256);
}
```

- [ ] **Step 2: Run to see fail** — `cargo test --test settings` → FAIL.
- [ ] **Step 3: Implement settings and hooks**

`register`: `Settings::from_env()`; if `lib_dir` missing, `cx.message("FerroLoom: set FERROLOOM_LIB_DIR to the folder with ferroloom-runtime.jar")` and return. Otherwise `let bar = cx.progress("FerroLoom", 2).ok();` `locate(LocateSources { explicit: settings.java_home.as_deref(), minecraft_roots: &settings.minecraft_roots, java_home: std::env::var_os("JAVA_HOME")… })`, `Jvm::start(...)`; on error `cx.message(...)` and return; store the `Jvm` in `OnceLock`. On success `bar.step()` and `cx.message("FerroLoom: JVM ready")`.
`client_tick_pre`: if no `Jvm`, return. Build `HorizonWindow { host }` implementing `EngineWindow` (`read_player_pos` = `host.local_player().and_then(|p| host.entity_position(p.entity()))`; `log` = `eprintln!`), and a `FileSink`? No: pass a `WriteQueue` and a sink that calls `eprintln!`. Call `on_client_tick(&jvm, tick, TickHost { window: &mut w, writes: &mut q })`; increment a `static TICK: AtomicU64`.

- [ ] **Step 4: Verify it compiles against the SDK**

Run: `cargo test --test settings` and `cargo horizon-mod check`
Expected: PASS and `check` reports the package is valid.

- [ ] **Step 5: Commit** — `git add src && git commit -m "feat: start the JVM at registration and drive it from client_tick_pre"`

---

### Task 12: In-game smoke

**Files:**
- Create: `FerroLoom/tools/smoke.sh` (dev only; not packed)
- Create: `FerroLoom/docs/smoke.md`

**Interfaces:**
- Consumes: the built `.hmod`, a Horizon instance, `horizon-ctl`.
- Produces: evidence that the mod starts the JVM and exchanges one tick.

- [ ] **Step 1: Write the script**

```bash
#!/usr/bin/env bash
set -euo pipefail
: "${FERROLOOM_LIB_DIR:?}"
horizon-launcher native-build . --instance "${HORIZON_INSTANCE:?}" --trust-native-code
horizon-ctl --socket "$PWD/ctl.sock" launch --world Smoke --creative --offscreen --wait
horizon-ctl --socket "$PWD/ctl.sock" state
horizon-ctl --socket "$PWD/ctl.sock" quit
```

- [ ] **Step 2: Run it once and capture the log**

Run:
```bash
./tools/smoke.sh 2>&1 | tee ferroloom.log
```
Expected in the log: `FerroLoom: JVM ready` and at least one `pos=... tick=1`.

- [ ] **Step 3: Document the manual check**

`docs/smoke.md`: requisitos (instancia preparada, `FERROLOOM_LIB_DIR`, `horizon-ctl`), el comando y las dos líneas que prueban el éxito, y qué hacer si falta Java.

- [ ] **Step 4: Commit** — `git add tools docs && git commit -m "test: in-game smoke script for the jvm host"`

---

### Task 13: Failure behaviour

**Files:**
- Modify: `FerroLoom/src/lib.rs` (messages), `FerroLoom/src/jvm/mod.rs` (`JvmError` display)
- Test: `FerroLoom/tests/failures.rs`

**Interfaces:**
- Consumes: everything above.
- Produces: `impl std::fmt::Display for JvmError` with actionable messages, one per variant.

- [ ] **Step 1: Write the failing tests**

```rust
#[test]
fn every_error_message_names_the_cause() {
    let e = ferroloom::jvm::JvmError::Create { code: -6 };
    let s = e.to_string();
    assert!(s.contains("could not create"), "got: {s}");
}

#[test]
fn a_java_exception_keeps_its_class_and_message() {
    let e = ferroloom::jvm::JvmError::JavaException { class: "java.lang.RuntimeException".into(), message: "boom".into() };
    let s = e.to_string();
    assert!(s.contains("java.lang.RuntimeException") && s.contains("boom"), "got: {s}");
}

#[test]
fn version_too_old_mentions_both_versions() {
    let e = ferroloom::jvm::JvmError::Locate(ferroloom::jvm::JvmLocateError::VersionTooOld {
        found: ferroloom::jvm::JavaVersion { major: 17, full: "17.0.10".into() }, minimum: 21 });
    let s = e.to_string();
    assert!(s.contains("17.0.10") && s.contains("21"), "got: {s}");
}
```

- [ ] **Step 2: Run to see fail** — `cargo test --test failures` → FAIL.
- [ ] **Step 3: Implement `Display`** for `JvmError`, `JvmLocateError`, `LibjvmError`: each names the path/version it tried and the action (`install Java 21+`, `set FERROLOOM_JAVA_HOME`, `check FERROLOOM_LIB_DIR`). `register` sends the message through `cx.message` and leaves the mod inert.
- [ ] **Step 4: Run all tests**

```bash
cargo test
cd java && ./gradlew :ferroloom-runtime:test
```
Expected: all PASS.

- [ ] **Step 5: Commit** — `git add src tests && git commit -m "feat: actionable failures for java, libjvm and vm creation"`

---

### Task 14: Pins, docs and handoff

**Files:**
- Create: `FerroLoom/README.md`
- Create: `FerroLoom/NOTICE`
- Modify: `FerroLoom/Cargo.lock` (pins)

**Interfaces:** none.

- [ ] **Step 1: Pin registry dependencies**

```bash
horizon-launcher native-lock .
```
Expected: `Cargo.lock` updated and accepted; commit it.

- [ ] **Step 2: Write the README**

Include: what the skeleton does and does not do (no Fabric mods yet), how to build Java and Rust, the env vars, the smoke command, and the known limitations (client-only, no shim, no registry packaging).

- [ ] **Step 3: Write NOTICE**

`jni` (Apache-2.0/MIT), `libloading` (ISC), and a line saying Fabric Loader/API enter in Plan 2.

- [ ] **Step 4: Final verification**

```bash
cargo test && cargo horizon-mod check
cd java && ./gradlew :ferroloom-runtime:test
```
Expected: all PASS; `check` valid.

- [ ] **Step 5: Commit** — `git add -A && git commit -m "docs: plan 1 handoff, pins and notices"`