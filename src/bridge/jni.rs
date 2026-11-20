use std::any::Any;
use std::ffi::c_void;
use std::panic::{catch_unwind, AssertUnwindSafe};

use jni::objects::JString;
use jni::sys::{
    jclass, jdoubleArray, jint, jlong, jstring, JNIEnv as RawJNIEnv,
};
use jni::{JNIEnv, NativeMethod};

use super::ops::Op;
use super::writes::{self, WriteOp};
use crate::jvm::{Jvm, JvmError};

pub const FERRO_NATIVE_CLASS: &str = "dev/ferroloom/runtime/FerroNative";

/// Binds the Java class's `native` methods to the Rust functions below. Native method lookup by
/// symbol would need the host binary's symbols exported; registering is explicit and portable.
pub fn register_natives(jvm: &Jvm) -> Result<(), JvmError> {
    jvm.with_env(|env| {
        let class = env
            .find_class(FERRO_NATIVE_CLASS)
            .map_err(|error| JvmError::Jni {
                reason: error.to_string(),
            })?;
        let methods = [
            NativeMethod {
                name: "call".into(),
                sig: "(IJ)J".into(),
                fn_ptr: ferroloom_call as *mut c_void,
            },
            NativeMethod {
                name: "callString".into(),
                sig: "(ILjava/lang/String;)Ljava/lang/String;".into(),
                fn_ptr: ferroloom_call_string as *mut c_void,
            },
            NativeMethod {
                name: "playerPosition".into(),
                sig: "()[D".into(),
                fn_ptr: ferroloom_player_position as *mut c_void,
            },
        ];
        env.register_native_methods(&class, &methods)
            .map_err(|error| JvmError::Jni {
                reason: error.to_string(),
            })
    })?
}

unsafe extern "system" fn ferroloom_call(
    env: *mut RawJNIEnv,
    _class: jclass,
    op: jint,
    a: jlong,
) -> jlong {
    match catch_unwind(AssertUnwindSafe(|| dispatch_call(op, a))) {
        Ok(value) => value,
        Err(payload) => {
            throw(env, &panic_message(payload));
            0
        }
    }
}

fn dispatch_call(op: jint, a: jlong) -> jlong {
    match Op::from_i32(op) {
        Some(Op::Ping) => a + 1,
        Some(Op::PanicForTest) => panic!("boom"),
        _ => 0,
    }
}

unsafe extern "system" fn ferroloom_call_string(
    env: *mut RawJNIEnv,
    _class: jclass,
    op: jint,
    text: jstring,
) -> jstring {
    let outcome = catch_unwind(AssertUnwindSafe(|| {
        let mut env = unsafe { JNIEnv::from_raw(env) }.ok()?;
        let input = unsafe { JString::from_raw(text) };
        let input = env.get_string(&input).ok()?.to_string_lossy().into_owned();
        if matches!(Op::from_i32(op), Some(Op::Log)) {
            let _ = writes::enqueue_current(WriteOp::Log(input.clone()));
        }
        env.new_string(input).ok().map(|value| value.into_raw())
    }));
    match outcome {
        Ok(Some(value)) => value,
        Ok(None) => std::ptr::null_mut(),
        Err(payload) => {
            throw(env, &panic_message(payload));
            std::ptr::null_mut()
        }
    }
}

unsafe extern "system" fn ferroloom_player_position(
    env: *mut RawJNIEnv,
    _class: jclass,
) -> jdoubleArray {
    let outcome = catch_unwind(AssertUnwindSafe(|| {
        let env = unsafe { JNIEnv::from_raw(env) }.ok()?;
        let [x, y, z] = crate::bridge::window::current_player_pos().ok()?;
        let array = env.new_double_array(3).ok()?;
        env.set_double_array_region(&array, 0, &[x, y, z]).ok()?;
        Some(array.into_raw())
    }));
    match outcome {
        Ok(Some(value)) => value,
        Ok(None) => std::ptr::null_mut(),
        Err(payload) => {
            throw(env, &panic_message(payload));
            std::ptr::null_mut()
        }
    }
}

fn throw(env: *mut RawJNIEnv, message: &str) {
    if let Ok(mut env) = unsafe { JNIEnv::from_raw(env) } {
        let _ = env.throw_new(
            "java/lang/RuntimeException",
            format!("ferroloom: internal error: {message}"),
        );
    }
}

fn panic_message(payload: Box<dyn Any + Send>) -> String {
    if let Some(text) = payload.downcast_ref::<&str>() {
        (*text).to_string()
    } else if let Some(text) = payload.downcast_ref::<String>() {
        text.clone()
    } else {
        "unknown panic".to_string()
    }
}