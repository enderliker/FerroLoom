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
        eprintln!("[ferroloom] {message}");
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

    let mut progress = context.progress("FerroLoom", 2).ok();
    let env_home = std::env::var_os("JAVA_HOME").map(PathBuf::from);
    let found = locate(LocateSources {
        explicit: settings.java_home.as_deref(),
        minecraft_roots: &settings.minecraft_roots,
        java_home: env_home.as_deref(),
    });
    let home = match found {
        Ok(home) => home,
        Err(error) => {
            context.message(&format!("FerroLoom: {error:?}"));
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
            context.message("FerroLoom: JVM ready");
            if let Some(progress) = progress.as_mut() {
                progress.step();
            }
        }
        Err(error) => {
            context.message(&format!("FerroLoom: {error:?}"));
        }
    }
}

pub fn client_tick(host: &mut dyn ClientHost) {
    let Some(jvm) = JVM.get() else {
        return;
    };
    let tick = TICKS.fetch_add(1, Ordering::Relaxed);
    let mut writes = WriteQueue::new();
    let mut window = ClientWindow { host };
    match on_client_tick(
        jvm,
        tick,
        TickHost {
            window: &mut window,
            writes: &mut writes,
        },
    ) {
        Ok(report) if !report.failed.is_empty() => {
            eprintln!("[ferroloom] {} queued writes failed", report.failed.len());
        }
        Ok(_) => {}
        Err(error) => eprintln!("[ferroloom] tick failed: {error:?}"),
    }
}