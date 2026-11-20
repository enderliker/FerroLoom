use ferroloom::bridge::window::{
    current_player_pos, with_window, BridgeError, EngineWindow,
};

struct Fake(Option<[f64; 3]>);

impl EngineWindow for Fake {
    fn read_player_pos(&self) -> Option<[f64; 3]> {
        self.0
    }

    fn log(&mut self, _: &str) {}
}

#[test]
fn reads_inside_the_window() {
    let mut fake = Fake(Some([1.0, 64.0, -2.0]));
    let read = with_window(&mut fake, current_player_pos);
    assert_eq!(read, Ok([1.0, 64.0, -2.0]));
}

#[test]
fn reads_outside_the_window_are_refused() {
    assert_eq!(current_player_pos(), Err(BridgeError::NotInCallback));
}

#[test]
fn a_foreign_thread_is_refused() {
    let mut fake = Fake(Some([0.0, 0.0, 0.0]));
    let inside = with_window(&mut fake, || {
        std::thread::spawn(current_player_pos).join().unwrap()
    });
    assert_eq!(inside, Err(BridgeError::NotInCallback));
}

#[test]
fn a_panic_restores_the_empty_window() {
    let mut fake = Fake(Some([0.0, 0.0, 0.0]));
    let result = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| {
        with_window(&mut fake, || panic!("boom"))
    }));
    assert!(result.is_err());
    assert_eq!(current_player_pos(), Err(BridgeError::NotInCallback));
}