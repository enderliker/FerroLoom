/// One operation a Java caller can ask the engine for.
#[repr(i32)]
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum Op {
    Ping = 1,
    PlayerPos = 2,
    Log = 3,
    PanicForTest = 99,
}

impl Op {
    pub fn from_i32(value: i32) -> Option<Op> {
        match value {
            1 => Some(Op::Ping),
            2 => Some(Op::PlayerPos),
            3 => Some(Op::Log),
            99 => Some(Op::PanicForTest),
            _ => None,
        }
    }
}