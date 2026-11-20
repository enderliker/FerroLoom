use jni::objects::JValue;

use super::window::{with_window, EngineWindow};
use super::writes::{with_queue, DrainReport, WriteOp, WriteQueue, WriteSink};
use crate::jvm::{Jvm, JvmError};

pub struct TickHost<'a> {
    pub window: &'a mut dyn EngineWindow,
    pub writes: &'a mut WriteQueue,
}

/// Calls Java for one client tick with the engine window and write queue visible to it, then
/// applies everything Java queued.
pub fn on_client_tick(jvm: &Jvm, tick: u64, host: TickHost<'_>) -> Result<DrainReport, JvmError> {
    let TickHost { window, writes } = host;

    let call = with_window(&mut *window, || {
        with_queue(&mut *writes, || {
            jvm.call_static_void(
                "dev/ferroloom/runtime/FerroRuntime",
                "onTick",
                "(J)V",
                &[JValue::Long(tick as i64)],
            )
        })
    });
    call?;

    let mut sink = WindowSink { window };
    Ok(writes.drain(&mut sink))
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
        }
    }
}