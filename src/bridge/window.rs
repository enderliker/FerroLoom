use std::ptr::NonNull;
use std::sync::Mutex;
use std::thread::ThreadId;

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

struct WindowCell {
    window: NonNull<dyn EngineWindow>,
    setter: ThreadId,
}

// SAFETY: the cell is only reachable while `with_window` is running, and the thread that set it
// blocks while the bridge thread uses it.
unsafe impl Send for WindowCell {}

static CURRENT: Mutex<Option<WindowCell>> = Mutex::new(None);
static ENGINE_THREAD: Mutex<Option<ThreadId>> = Mutex::new(None);

/// The thread that created the JVM and serves every call. Reads are allowed from it even though
/// the window was set by the game thread.
pub(crate) fn set_engine_thread(id: ThreadId) {
    *ENGINE_THREAD.lock().unwrap() = Some(id);
}

fn allowed(setter: ThreadId) -> bool {
    let current = std::thread::current().id();
    current == setter || *ENGINE_THREAD.lock().unwrap() == Some(current)
}

/// Runs `f` with `window` visible to the JNI callbacks on this thread (and on the engine thread).
pub fn with_window<R>(window: &mut dyn EngineWindow, f: impl FnOnce() -> R) -> R {
    // The cell outlives the borrow only for the duration of `f`; the guard restores it before the
    // borrow ends.
    let extended: &mut (dyn EngineWindow + 'static) = unsafe { std::mem::transmute(window) };
    let cell = WindowCell {
        window: NonNull::from(extended),
        setter: std::thread::current().id(),
    };
    let previous = CURRENT.lock().unwrap().replace(cell);
    struct Restore(Option<WindowCell>);
    impl Drop for Restore {
        fn drop(&mut self) {
            *CURRENT.lock().unwrap() = self.0.take();
        }
    }
    let _restore = Restore(previous);
    f()
}

fn current_window() -> Result<NonNull<dyn EngineWindow>, BridgeError> {
    let pointer = {
        let guard = CURRENT.lock().unwrap();
        let cell = guard.as_ref().ok_or(BridgeError::NotInCallback)?;
        if !allowed(cell.setter) {
            return Err(BridgeError::NotInCallback);
        }
        cell.window
    };
    Ok(pointer)
}

pub fn current_player_pos() -> Result<[f64; 3], BridgeError> {
    let pointer = current_window()?;
    unsafe { pointer.as_ref().read_player_pos() }.ok_or(BridgeError::Unavailable)
}

pub fn current_log(message: &str) -> Result<(), BridgeError> {
    let mut pointer = current_window()?;
    unsafe { pointer.as_mut() }.log(message);
    Ok(())
}