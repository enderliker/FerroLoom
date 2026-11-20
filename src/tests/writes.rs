use ferroloom::bridge::writes::{WriteOp, WriteQueue, WriteSink};

struct Sink(Vec<String>, bool);

impl WriteSink for Sink {
    fn apply(&mut self, op: WriteOp) -> Result<(), String> {
        match op {
            WriteOp::Log(text) | WriteOp::Chat(text) => {
                self.0.push(text);
                if self.1 { Err("nope".into()) } else { Ok(()) }
            }
        }
    }
}

#[test]
fn drains_in_fifo_order() {
    let mut queue = WriteQueue::new();
    queue.enqueue(WriteOp::Log("a".into()));
    queue.enqueue(WriteOp::Log("b".into()));
    let mut sink = Sink(vec![], false);
    let report = queue.drain(&mut sink);
    assert_eq!(report.applied, 2);
    assert_eq!(sink.0, vec!["a", "b"]);
    assert_eq!(queue.len(), 0);
}

#[test]
fn one_failure_does_not_stop_the_rest() {
    let mut queue = WriteQueue::new();
    queue.enqueue(WriteOp::Log("a".into()));
    queue.enqueue(WriteOp::Log("b".into()));
    let mut sink = Sink(vec![], true);
    let report = queue.drain(&mut sink);
    assert_eq!(report.failed.len(), 2);
    assert_eq!(sink.0.len(), 2);
    assert_eq!(queue.len(), 0);
}

#[test]
fn chat_and_log_keep_their_order() {
    let mut queue = WriteQueue::new();
    queue.enqueue(WriteOp::Chat("hello".into()));
    queue.enqueue(WriteOp::Log("pos=1".into()));
    let mut sink = Sink(vec![], false);
    let report = queue.drain(&mut sink);
    assert_eq!(report.applied, 2);
    assert_eq!(sink.0, vec!["hello", "pos=1"]);
}

#[test]
fn a_foreign_thread_cannot_use_the_game_threads_queue() {
    let mut queue = WriteQueue::new();
    let outcome = ferroloom::bridge::writes::with_queue(&mut queue, || {
        std::thread::spawn(|| {
            ferroloom::bridge::writes::enqueue_current(WriteOp::Log("foreign".into()))
        })
        .join()
        .unwrap()
    });
    assert_eq!(
        outcome,
        Err(ferroloom::bridge::window::BridgeError::NotInCallback)
    );
    assert_eq!(queue.len(), 0);
}
