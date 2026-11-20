/// What the engine exposes to the write sink.
///
/// Engine reads stay on the game thread (the API checks the load phase per thread), so the JVM
/// never reads the world directly; the game thread pushes what Java needs before each call.
pub trait EngineWindow {
    #[allow(dead_code)]
    fn read_player_pos(&self) -> Option<[f64; 3]>;
    fn log(&mut self, message: &str);
    /// Sends a chat line; windows that cannot may fall back to the log.
    fn send_chat(&mut self, message: &str) {
        self.log(&format!("chat: {message}"));
    }
}

#[allow(dead_code)]
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum BridgeError {
    /// The call did not happen inside an engine callback.
    NotInCallback,
    /// The engine could not answer right now.
    Unavailable,
}
