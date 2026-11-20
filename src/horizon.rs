use std::io::Write;
use std::path::PathBuf;
use std::sync::OnceLock;
use std::sync::atomic::{AtomicU64, Ordering};

use mod_api::{ClientHost, KeyRegistration, Registrar, RegistrationContext};

use crate::bridge::registrations::{self, PendingRegistrations};
use crate::bridge::upcall::{TickHost, on_client_tick};
use crate::bridge::window::EngineWindow;
use crate::bridge::writes::{WriteOp, WriteQueue, WriteSink, with_queue};
use crate::jvm::{Arg, Jvm, JvmConfig, LocateSources, locate};
use crate::settings::Settings;

static JVM: OnceLock<Jvm> = OnceLock::new();
static TICKS: AtomicU64 = AtomicU64::new(0);
static REGISTRATIONS: OnceLock<PendingRegistrations> = OnceLock::new();
static FOV_ERROR_LOGGED: std::sync::atomic::AtomicBool = std::sync::atomic::AtomicBool::new(false);
static KEY_STATE: std::sync::Mutex<Vec<bool>> = std::sync::Mutex::new(Vec::new());

/// The mod's own log, beside the game's files (`<instance home>/ferroloom.log`).
fn log_line(message: &str) {
    eprintln!("[ferroloom] {message}");
    let Some(home) = std::env::var_os("HOME") else {
        return;
    };
    let path = PathBuf::from(home).join(".local/share/horizon/ferroloom.log");
    if let Ok(mut file) = std::fs::OpenOptions::new()
        .create(true)
        .append(true)
        .open(path)
    {
        let _ = writeln!(file, "{message}");
    }
}

struct ClientWindow<'a> {
    host: &'a mut dyn ClientHost,
}

impl EngineWindow for ClientWindow<'_> {
    fn read_player_pos(&self) -> Option<[f64; 3]> {
        // The player's read lives behind camera() on ClientHost; entity_position is not in the
        // client host union in this SDK build.
        self.host.camera().map(|camera| camera.position)
    }

    fn log(&mut self, message: &str) {
        log_line(message);
    }

    fn send_chat(&mut self, message: &str) {
        match self.host.send_chat(message) {
            Ok(()) => log_line(&format!("chat: {message}")),
            Err(reason) => log_line(&format!("chat failed: {reason}")),
        }
    }
}

pub fn register(context: &RegistrationContext, registrar: &mut dyn Registrar) {
    let settings = Settings::from_env();
    let Some(lib_dir) = settings.lib_dir.clone() else {
        context
            .message("FerroLoom: set FERROLOOM_LIB_DIR to the folder with ferroloom-runtime.jar");
        return;
    };
    for required in [
        "ferroloom-runtime.jar",
        "ferroloom-loader.jar",
        "ferro-shim.jar",
    ] {
        let jar = lib_dir.join(required);
        if !jar.is_file() {
            let message = format!(
                "FerroLoom: {} not found; set FERROLOOM_LIB_DIR to the staged runtime folder",
                jar.display()
            );
            log_line(&format!("register failed: {message}"));
            context.message(&message);
            return;
        }
    }

    log_line(&format!(
        "register: lib_dir={:?} java_home={:?} xmx={}",
        settings.lib_dir, settings.java_home, settings.xmx_mib
    ));
    let mut progress = context.progress("FerroLoom", 2).ok();
    let env_home = std::env::var_os("JAVA_HOME").map(PathBuf::from);
    // Plan 1 dev fallback: the launcher does not pass JAVA_HOME to the game, so a system JDK
    // under /usr/lib/jvm is still found.
    let mut roots = settings.minecraft_roots.clone();
    if env_home.is_none() {
        roots.push(PathBuf::from("/usr/lib/jvm"));
    }
    let found = locate(LocateSources {
        explicit: settings.java_home.as_deref(),
        minecraft_roots: &roots,
        java_home: env_home.as_deref(),
    });
    let home = match found {
        Ok(home) => home,
        Err(error) => {
            log_line(&format!("register failed: {error}"));
            context.message(&format!("FerroLoom: {error}"));
            return;
        }
    };
    if let Some(progress) = progress.as_mut() {
        progress.step();
    }

    let classpath = match registrations::classpath(&lib_dir) {
        Ok(classpath) => classpath,
        Err(error) => {
            log_line(&format!("register failed: {error}"));
            context.message(&format!("FerroLoom: {error}"));
            return;
        }
    };
    let game_dir = std::env::var_os("HOME")
        .map(|home| PathBuf::from(home).join(".local/share/horizon"))
        .unwrap_or_else(|| PathBuf::from("."));
    let config = JvmConfig {
        java_home: home.path,
        classpath,
        xmx_mib: settings.xmx_mib,
        options: vec![
            format!("-Dferro.modsDir={}", settings.mods_dir.display()),
            format!("-Dferro.cacheDir={}", settings.cache_dir.display()),
            format!("-Dferro.shimDir={}", lib_dir.display()),
            format!("-Dferro.gameDir={}", game_dir.display()),
        ],
    };
    match Jvm::start(&config) {
        Ok(jvm) => {
            match jvm.call_static_string(
                "dev/ferroloom/loader/FerroBootstrap",
                "start",
                "()Ljava/lang/String;",
                &[],
            ) {
                Ok(report) => {
                    let pending = PendingRegistrations::parse(&report);
                    for key in &pending.keys {
                        registrar.register_key_mapping(KeyRegistration::new(
                            &format!("{}_{}", key.mod_id, key.key_id),
                            &key.default_key,
                        ));
                    }
                    for message in &pending.logs {
                        log_line(message);
                    }
                    for failure in &pending.failed {
                        log_line(&format!("mod failed: {failure}"));
                    }
                    log_line(&format!(
                        "JVM ready; loader started; {} key(s), {} command(s), hud={}",
                        pending.keys.len(),
                        pending.commands.len(),
                        pending.hud
                    ));
                    context.message("FerroLoom: Fabric Loader ready");
                    let _ = REGISTRATIONS.set(pending);
                }
                Err(error) => {
                    log_line(&format!("loader start failed: {error}"));
                    context.message(&format!("FerroLoom: {error}"));
                }
            }
            let _ = JVM.set(jvm);
            if let Some(progress) = progress.as_mut() {
                progress.step();
            }
        }
        Err(error) => {
            log_line(&format!("register failed: {error}"));
            context.message(&format!("FerroLoom: {error}"));
        }
    }
}

pub fn compute_fov_modifier(event: &mut mod_api::ComputeFovModifierEvent<'_>) {
    let Some(jvm) = JVM.get() else {
        return;
    };
    let outcome = jvm.call_static_f32(
        "dev/ferroloom/loader/FerroBootstrap",
        "computeFovModifier",
        "(FFZ)F",
        &[Arg::Float(event.base), Arg::Float(0.0), Arg::Int(0)],
    );
    match outcome {
        Ok(value) if value.is_finite() && value > 0.0 => event.modifier = value,
        Ok(_) => {}
        Err(error) => {
            if !FOV_ERROR_LOGGED.swap(true, Ordering::Relaxed) {
                log_line(&format!("compute_fov_modifier failed: {error}"));
            }
        }
    }
}

pub fn client_setup(host: &mut dyn ClientHost) {
    let Some(jvm) = JVM.get() else {
        return;
    };
    let mut writes = WriteQueue::new();
    let outcome = with_queue(&mut writes, || {
        jvm.call_static_void(
            "dev/ferroloom/loader/FerroBootstrap",
            "onClientSetup",
            "()V",
            &[],
        )
    });
    let mut sink = HostSink { host };
    log_drain(writes.drain(&mut sink));
    match outcome {
        Ok(()) => log_line("client setup: client entrypoints ran"),
        Err(error) => log_line(&format!("client setup failed: {error}")),
    }
}

fn send_key(jvm: &Jvm, key: &crate::bridge::registrations::KeyRequest, down: bool) {
    if let Err(error) = jvm.call_static_void(
        "dev/ferroloom/loader/FerroBootstrap",
        "onKey",
        "(Ljava/lang/String;Ljava/lang/String;Z)V",
        &[
            Arg::Text(key.mod_id.clone()),
            Arg::Text(key.key_id.clone()),
            Arg::Int(i32::from(down)),
        ],
    ) {
        log_line(&format!("key dispatch failed: {error}"));
    }
}

pub fn command(host: &mut dyn ClientHost, command: &str) -> bool {
    let Some(jvm) = JVM.get() else {
        return false;
    };
    let mut writes = WriteQueue::new();
    let handled = with_queue(&mut writes, || {
        jvm.call_static_bool(
            "dev/ferroloom/loader/FerroBootstrap",
            "onCommand",
            "(Ljava/lang/String;)Z",
            &[Arg::Text(command.to_string())],
        )
    });
    let mut sink = HostSink { host };
    log_drain(writes.drain(&mut sink));
    match handled {
        Ok(handled) => handled,
        Err(error) => {
            log_line(&format!("command dispatch failed: {error}"));
            false
        }
    }
}

fn log_drain(report: crate::bridge::writes::DrainReport) {
    for (index, reason) in report.failed {
        log_line(&format!("write {index} failed: {reason}"));
    }
}

/// Applies what a mod asked for from a hook that has no tick queue of its own.
pub struct HostSink<'a> {
    pub host: &'a mut dyn ClientHost,
}

impl WriteSink for HostSink<'_> {
    fn apply(&mut self, op: WriteOp) -> Result<(), String> {
        match op {
            WriteOp::Log(text) => {
                log_line(&text);
                Ok(())
            }
            WriteOp::Chat(text) => self.host.send_chat(&text),
        }
    }
}

pub fn register_gui_layers(event: &mut mod_api::RegisterGuiLayersEvent<'_>) {
    if let Some(registrations) = REGISTRATIONS.get() {
        if registrations.hud {
            event.layers.push(mod_api::GuiLayer {
                id: "ferroloom:hud".to_string(),
                placement: mod_api::GuiLayerPlacement::AboveAll,
            });
            log_line("hud layer registered");
        }
    }
}

pub fn gui_layer_draw(event: &mut mod_api::GuiLayerDrawEvent<'_>) {
    if event.layer != "ferroloom:hud" {
        return;
    }
    let Some(jvm) = JVM.get() else {
        return;
    };
    match jvm.call_static_string(
        "dev/ferroloom/loader/FerroBootstrap",
        "hudLine",
        "()Ljava/lang/String;",
        &[],
    ) {
        Ok(line) if !line.is_empty() => event.draw.text(&line, [4.0, 4.0], 0xFFFF_FFFF, true),
        Ok(_) => {}
        Err(error) => log_line(&format!("hud read failed: {error}")),
    }
}

fn dispatch_keys(jvm: &Jvm, host: &mut dyn ClientHost) {
    let Some(registrations) = REGISTRATIONS.get() else {
        return;
    };
    if registrations.keys.is_empty() {
        return;
    }
    let mut states = KEY_STATE.lock().unwrap();
    states.resize(registrations.keys.len(), false);
    for (index, key) in registrations.keys.iter().enumerate() {
        let mapping = format!("{}_{}", key.mod_id, key.key_id);
        let was = states[index];
        let down = host.is_down("ferroloom", &mapping);
        let clicked = host.consume_click("ferroloom", &mapping);
        if clicked && !down {
            send_key(jvm, key, true);
            send_key(jvm, key, false);
        } else {
            if down && !was {
                send_key(jvm, key, true);
            }
            if !down && was {
                send_key(jvm, key, false);
            }
        }
        states[index] = down;
    }
}

pub fn client_tick(host: &mut dyn ClientHost) {
    let Some(jvm) = JVM.get() else {
        return;
    };
    let tick = TICKS.fetch_add(1, Ordering::Relaxed);
    if tick < 3 || tick % 200 == 0 {
        log_line(&format!("client_tick_pre {tick}"));
    }
    // Reads stay on this thread: the engine checks the load phase per thread.
    let position = host.camera().map(|camera| camera.position);
    let mut writes = WriteQueue::new();
    // Key dispatch runs inside the write window so a listener's chat reaches the queue.
    with_queue(&mut writes, || dispatch_keys(jvm, host));
    let mut window = ClientWindow { host };
    match on_client_tick(
        jvm,
        tick,
        position,
        TickHost {
            window: &mut window,
            writes: &mut writes,
        },
    ) {
        Ok(report) if !report.failed.is_empty() => {
            log_line(&format!(
                "tick {tick}: {} queued writes failed",
                report.failed.len()
            ));
        }
        Ok(report) => {
            if report.applied > 0 && (tick < 3 || tick % 200 == 0) {
                log_line(&format!("tick {tick}: {} writes applied", report.applied));
            }
        }
        Err(error) => log_line(&format!("tick {tick} failed: {error}")),
    }
}
