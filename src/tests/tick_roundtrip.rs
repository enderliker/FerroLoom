use crate::bridge::upcall::{TickHost, on_client_tick};
use crate::bridge::window::EngineWindow;
use crate::bridge::writes::WriteQueue;

struct FakeWindow(Option<[f64; 3]>, Vec<String>);

impl EngineWindow for FakeWindow {
    fn read_player_pos(&self) -> Option<[f64; 3]> {
        self.0
    }

    fn log(&mut self, message: &str) {
        self.1.push(message.to_string());
    }
}

#[test]
fn java_reads_position_and_its_log_write_is_applied_after_the_call() {
    let Some(jvm) = super::jvm() else {
        eprintln!("skip: no JDK or runtime jar");
        return;
    };

    let mut window = FakeWindow(Some([1.5, 64.0, -2.5]), vec![]);
    let mut writes = WriteQueue::new();
    let report = on_client_tick(
        jvm,
        7,
        Some([1.5, 64.0, -2.5]),
        TickHost {
            window: &mut window,
            writes: &mut writes,
        },
    )
    .expect("tick");

    assert_eq!(report.applied, 1);
    assert_eq!(window.1, vec!["pos=1.5,64.0,-2.5 tick=7"]);
}
