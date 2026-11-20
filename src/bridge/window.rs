use std::cell::RefCell;
use std::ptr::NonNull;

/// What the engine exposes while a hook is calling into Java.
pub trait EngineWindow {
    fn read_player_pos(&self) -> Option<[f64; 3]>;
    fn log(&mut self, message: &str);
}

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum BridgeError {
    /// The call did not happen inside an engine callback.
    NotInCallback,
    /// The engine could not answer right now.
    Unavailable,
}

thread_local! {
    static CURRENT: RefCell<Option<NonNull<dyn EngineWindow>>> = const { RefCell::new(None) };
}

struct Restore(Option<NonNull<dyn EngineWindow>>);

impl Drop for Restore {
    fn drop(&mut self) {
        CURRENT.with(|current| *current.borrow_mut() = self.0);
    }
}

/// Runs `f` with `window` visible to `current_player_pos` and `current_log` on this thread.
///
/// The window is a borrowed trait object; it is raised to `'static` only for the duration of this
/// call, is reachable only from this thread, and is lowered by the guard before the borrow ends.
pub fn with_window<R>(window: &mut dyn EngineWindow, f: impl FnOnce() -> R) -> R {
    let extended: &mut (dyn EngineWindow + 'static) = unsafe { std::mem::transmute(window) };
    let pointer = NonNull::from(extended);
    let previous = CURRENT.with(|current| current.borrow_mut().replace(pointer));
    let _restore = Restore(previous);
    f()
}

pub fn current_player_pos() -> Result<[f64; 3], BridgeError> {
    let pointer = CURRENT.with(|current| *current.borrow()).ok_or(BridgeError::NotInCallback)?;
    unsafe { pointer.as_ref().read_player_pos() }.ok_or(BridgeError::Unavailable)
}

pub fn current_log(message: &str) -> Result<(), BridgeError> {
    let mut pointer =
        CURRENT.with(|current| *current.borrow()).ok_or(BridgeError::NotInCallback)?;
    unsafe { pointer.as_mut() }.log(message);
    Ok(())
}