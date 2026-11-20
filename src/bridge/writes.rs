use std::collections::VecDeque;

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