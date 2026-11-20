use std::collections::VecDeque;
use std::ptr::NonNull;
use std::sync::Mutex;

use super::window::BridgeError;

/// An effect Java asked for, applied at the end of the engine callback that asked for it.
pub enum WriteOp {
    Log(String),
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

struct QueueCell(NonNull<WriteQueue>);

// SAFETY: the cell is only reachable while `with_queue` is running, and the thread that set it
// blocks while the bridge thread uses it.
unsafe impl Send for QueueCell {}

static CURRENT: Mutex<Option<QueueCell>> = Mutex::new(None);

/// Makes `queue` the destination of `enqueue_current`.
pub fn with_queue<R>(queue: &mut WriteQueue, f: impl FnOnce() -> R) -> R {
    let previous = CURRENT.lock().unwrap().replace(QueueCell(NonNull::from(&mut *queue)));
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
    let pointer = {
        let guard = CURRENT.lock().unwrap();
        guard.as_ref().map(|cell| cell.0)
    };
    let Some(mut pointer) = pointer else {
        return Err(BridgeError::NotInCallback);
    };
    unsafe { pointer.as_mut() }.enqueue(op);
    Ok(())
}