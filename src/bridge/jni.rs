use std::any::Any;
use std::ffi::CString;
use std::os::raw::{c_char, c_void};
use std::panic::{catch_unwind, AssertUnwindSafe};

use crate::jni_sys::*;
use crate::jvm::{delete, java_string, JvmError};

use super::ops::Op;
use super::writes::{self, WriteOp};

pub const FERRO_NATIVE_CLASS: &str = "dev/ferroloom/runtime/FerroNative";

/// Binds the Java class's `native` methods to the Rust functions below. Native method lookup by
/// symbol would need the host binary's symbols exported; registering is explicit and portable.
pub fn register_natives(env: *mut JNIEnv) -> Result<(), JvmError> {
    unsafe {
        let class = find_class(env, FERRO_NATIVE_CLASS)?;
        let methods = [
            JNINativeMethod {
                name: c"call".as_ptr() as *mut c_char,
                signature: c"(IJ)J".as_ptr() as *mut c_char,
                fnPtr: ferroloom_call as *mut c_void,
            },
            JNINativeMethod {
                name: c"callString".as_ptr() as *mut c_char,
                signature: c"(ILjava/lang/String;)Ljava/lang/String;".as_ptr() as *mut c_char,
                fnPtr: ferroloom_call_string as *mut c_void,
            },
        ];
        let register = (**env).RegisterNatives.ok_or_else(|| JvmError::Jni {
            reason: "RegisterNatives is missing".to_string(),
        })?;
        let code = register(env, class, methods.as_ptr(), methods.len() as jint);
        delete(env, class as jobject);
        if code != 0 {
            return Err(JvmError::Jni {
                reason: format!("RegisterNatives returned {code}"),
            });
        }
        Ok(())
    }
}

unsafe fn find_class(env: *mut JNIEnv, name: &str) -> Result<jclass, JvmError> {
    let name = CString::new(name).map_err(|_| JvmError::Jni {
        reason: "class name contains NUL".to_string(),
    })?;
    let find = (unsafe { (**env).FindClass }).ok_or_else(|| JvmError::Jni {
        reason: "FindClass is missing".to_string(),
    })?;
    let class = unsafe { find(env, name.as_ptr()) };
    if class.is_null() {
        if let Some(error) = unsafe { crate::jvm::take_exception(env) } {
            return Err(error);
        }
        return Err(JvmError::Jni {
            reason: format!("class not found: {name:?}"),
        });
    }
    Ok(class)
}

unsafe fn throw(env: *mut JNIEnv, message: &str) {
    let text = match CString::new(format!("ferroloom: internal error: {message}")) {
        Ok(text) => text,
        Err(_) => return,
    };
    let find = match unsafe { (**env).FindClass } {
        Some(find) => find,
        None => return,
    };
    let class = unsafe { find(env, c"java/lang/RuntimeException".as_ptr()) };
    if class.is_null() {
        return;
    }
    if let Some(throw_new) = unsafe { (**env).ThrowNew } {
        unsafe { throw_new(env, class, text.as_ptr()) };
    }
    unsafe { delete(env, class as jobject) };
}

unsafe extern "system" fn ferroloom_call(
    env: *mut JNIEnv,
    _class: jclass,
    op: jint,
    a: jlong,
) -> jlong {
    match catch_unwind(AssertUnwindSafe(|| dispatch_call(op, a))) {
        Ok(value) => value,
        Err(payload) => {
            unsafe { throw(env, &panic_message(payload)) };
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
    env: *mut JNIEnv,
    _class: jclass,
    op: jint,
    text: jstring,
) -> jstring {
    let outcome = catch_unwind(AssertUnwindSafe(|| {
        let input = unsafe { java_string(env, text) }.unwrap_or_default();
        if matches!(Op::from_i32(op), Some(Op::Log)) {
            let _ = writes::enqueue_current(WriteOp::Log(input.clone()));
        }
        let out = CString::new(input).ok()?;
        let new_string = unsafe { (**env).NewStringUTF }?;
        Some(unsafe { new_string(env, out.as_ptr()) })
    }));
    match outcome {
        Ok(Some(value)) => value,
        Ok(None) => std::ptr::null_mut(),
        Err(payload) => {
            unsafe { throw(env, &panic_message(payload)) };
            std::ptr::null_mut()
        }
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