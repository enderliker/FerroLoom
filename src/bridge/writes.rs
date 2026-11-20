use std::collections::VecDeque;
use std::ptr::NonNull;
use std::sync::Mutex;

use super::window::BridgeError;

/// An effect Java asked for, applied at the end of the engine callback that asked for it.
pub enum WriteOp {
    Log(String),
    Chat(String),
}

pub trait WriteSink {
    fn apply(&mut self, op: WriteOp) -> Result<(), String>;
}

#[derive(Debug, Default, PartialEq, Eq)]
pub struct DrainReport {
    pub applied: usize,
    pub failed: Vec<(usize, String)>,
}

#[derive(Default)]
pub struct WriteQueue {
    ops: VecDeque<WriteOp>,
    next_index: usize,
}

impl WriteQueue {
    pub fn new() -> WriteQueue {
        WriteQueue::default()
    }

    pub fn enqueue(&mut self, op: WriteOp) {
        self.ops.push_back(op);
    }

    pub fn len(&self) -> usize {
        self.ops.len()
    }

    pub fn is_empty(&self) -> bool {
        self.ops.is_empty()
    }

    /// Applies every queued operation in order. A failure is recorded and the rest still run.
    pub fn drain(&mut self, sink: &mut dyn WriteSink) -> DrainReport {
        let mut report = DrainReport::default();
        while let Some(op) = self.ops.pop_front() {
            let index = self.next_index;
            self.next_index += 1;
            match sink.apply(op) {
                Ok(()) => report.applied += 1,
                Err(reason) => report.failed.push((index, reason)),
            }
        }
        report
    }
}

struct QueueCell {
    owner: std::thread::ThreadId,
    ptr: NonNull<WriteQueue>,
}

// SAFETY: the cell is only reachable while `with_queue` is running, and the thread that set it
// blocks while the bridge thread uses it.
unsafe impl Send for QueueCell {}

static CURRENT: Mutex<Option<QueueCell>> = Mutex::new(None);
static BRIDGE_THREAD: std::sync::OnceLock<std::thread::ThreadId> = std::sync::OnceLock::new();

/// Records the thread that serves JNI calls: it is the only other thread allowed to write,
/// because the game thread blocks on it while the window is open.
pub fn mark_bridge_thread() {
    let _ = BRIDGE_THREAD.set(std::thread::current().id());
}

/// Makes `queue` the destination of `enqueue_current`.
pub fn with_queue<R>(queue: &mut WriteQueue, f: impl FnOnce() -> R) -> R {
    let cell = QueueCell {
        owner: std::thread::current().id(),
        ptr: NonNull::from(&mut *queue),
    };
    let previous = CURRENT.lock().unwrap().replace(cell);
    struct Restore(Option<QueueCell>);
    impl Drop for Restore {
        fn drop(&mut self) {
            *CURRENT.lock().unwrap() = self.0.take();
        }
    }
    let _restore = Restore(previous);
    f()
}

pub fn enqueue_current(op: WriteOp) -> Result<(), BridgeError> {
    // Hold the lock across the push: `with_queue` takes it again to restore, so the queue cannot
    // be dropped while this thread is writing, and only the thread that opened the window may
    // write through its pointer.
    let guard = CURRENT.lock().unwrap();
    let Some(cell) = guard.as_ref() else {
        return Err(BridgeError::NotInCallback);
    };
    let current = std::thread::current().id();
    let bridge = BRIDGE_THREAD.get().copied();
    if current != cell.owner && Some(current) != bridge {
        return Err(BridgeError::NotInCallback);
    }
    // SAFETY: the cell is only reachable under this lock, the window's owner is this thread, and
    // `with_queue` cannot restore (and so cannot end the queue's life) while the lock is held.
    unsafe { &mut *cell.ptr.as_ptr() }.enqueue(op);
    Ok(())
}
