use ferroloom::bridge::upcall::{on_client_tick, TickHost};
use ferroloom::bridge::window::EngineWindow;
use ferroloom::bridge::writes::WriteQueue;
use ferroloom::jvm::{locate_for_test, Jvm, JvmConfig};
use std::path::PathBuf;

struct FakeWindow(Option<[f64; 3]>, Vec<String>);

impl EngineWindow for FakeWindow {
    fn read_player_pos(&self) -> Option<[f64; 3]> {
        self.0
    }

    fn log(&mut self, message: &str) {
        self.1.push(message.to_string());
    }
}

fn config() -> Option<JvmConfig> {
    let jar = PathBuf::from(env!("CARGO_MANIFEST_DIR"))
        .join("java/ferroloom-runtime/build/libs/ferroloom-runtime.jar");
    if !jar.exists() {
        eprintln!("skip: build the runtime jar first (./gradlew :ferroloom-runtime:jar)");
        return None;
    }
    let home = locate_for_test().ok()?;
    Some(JvmConfig {
        java_home: home.path,
        classpath: vec![jar],
        xmx_mib: 256,
        options: vec![],
    })
}

#[test]
fn java_reads_position_and_its_log_write_is_applied_after_the_call() {
    let Some(config) = config() else {
        eprintln!("skip: no JDK");
        return;
    };
    let jvm = Jvm::start(&config).expect("jvm starts");

    let mut window = FakeWindow(Some([1.5, 64.0, -2.5]), vec![]);
    let mut writes = WriteQueue::new();
    let report = on_client_tick(
        &jvm,
        7,
        TickHost {
            window: &mut window,
            writes: &mut writes,
        },
    )
    .expect("tick");

    assert_eq!(report.applied, 1);
    assert_eq!(window.1, vec!["pos=1.5,64.0,-2.5 tick=7"]);
}