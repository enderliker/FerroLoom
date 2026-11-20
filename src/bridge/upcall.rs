use super::window::EngineWindow;
use super::writes::{with_queue, DrainReport, WriteOp, WriteQueue, WriteSink};
use crate::jvm::{Arg, Jvm, JvmError};

pub struct TickHost<'a> {
    pub window: &'a mut dyn EngineWindow,
    pub writes: &'a mut WriteQueue,
}

/// Pushes the position read on the game thread, runs one Java tick on the JVM thread, then
/// applies everything Java queued.
///
/// The engine's reads are phase-checked per thread, so they must run on the game thread; only the
/// queue crosses to the JVM thread.
pub fn on_client_tick(
    jvm: &Jvm,
    tick: u64,
    position: Option<[f64; 3]>,
    host: TickHost<'_>,
) -> Result<DrainReport, JvmError> {
    let TickHost { window, writes } = host;

    if let Some([x, y, z]) = position {
        jvm.call_static_void(
            "dev/ferroloom/runtime/FerroRuntime",
            "setPosition",
            "(DDD)V",
            &[Arg::Double(x), Arg::Double(y), Arg::Double(z)],
        )?;
    }
    let call = with_queue(&mut *writes, || jvm.tick(tick));

    // Apply what Java queued even when the call itself failed.
    let mut sink = WindowSink { window };
    let report = writes.drain(&mut sink);
    call?;
    Ok(report)
}

struct WindowSink<'a> {
    window: &'a mut dyn EngineWindow,
}

impl WriteSink for WindowSink<'_> {
    fn apply(&mut self, op: WriteOp) -> Result<(), String> {
        match op {
            WriteOp::Log(text) => {
                self.window.log(&text);
                Ok(())
            }
            WriteOp::Chat(text) => {
                self.window.send_chat(&text);
                Ok(())
            }
        }
    }
}