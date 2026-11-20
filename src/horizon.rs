use std::io::Write;
use std::path::PathBuf;
use std::sync::atomic::{AtomicU64, Ordering};
use std::sync::OnceLock;

use mod_api::{ClientHost, RegistrationContext};

use crate::bridge::upcall::{on_client_tick, TickHost};
use crate::bridge::window::EngineWindow;
use crate::bridge::writes::WriteQueue;
use crate::jvm::{locate, Jvm, JvmConfig, LocateSources};
use crate::settings::Settings;

static JVM: OnceLock<Jvm> = OnceLock::new();
static TICKS: AtomicU64 = AtomicU64::new(0);

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
}

pub fn register(context: &RegistrationContext) {
    let settings = Settings::from_env();
    let Some(lib_dir) = settings.lib_dir.clone() else {
        context.message(
            "FerroLoom: set FERROLOOM_LIB_DIR to the folder with ferroloom-runtime.jar",
        );
        return;
    };

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

    let config = JvmConfig {
        java_home: home.path,
        classpath: vec![lib_dir.join("ferroloom-runtime.jar")],
        xmx_mib: settings.xmx_mib,
        options: vec![],
    };
    match Jvm::start(&config) {
        Ok(jvm) => {
            let _ = JVM.set(jvm);
            log_line("JVM ready");
            context.message("FerroLoom: JVM ready");
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
            if report.applied > 0 {
                log_line(&format!("tick {tick}: {} writes applied", report.applied));
            }
        }
        Err(error) => log_line(&format!("tick {tick} failed: {error}")),
    }
}