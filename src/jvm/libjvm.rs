use std::ffi::CString;
use std::os::raw::{c_char, c_void};
use std::path::{Path, PathBuf};

use jni::sys::{jboolean, jint, JavaVMInitArgs, JavaVMOption, JNIEnv, JavaVM};

#[derive(Debug)]
pub enum LibjvmError {
    NotFound { tried: Vec<PathBuf> },
    LoadFailed { path: PathBuf, reason: String },
    CreateVm { code: i32 },
}

const JNI_VERSION_1_8: jint = 0x0001_0008;

const CANDIDATES: &[&str] = &[
    "lib/server/libjvm.so",
    "lib/libjvm.so",
    "jre/lib/amd64/server/libjvm.so",
    "bin/server/jvm.dll",
    "lib/server/libjvm.dylib",
    "lib/libjvm.dylib",
];

pub fn find_libjvm(java_home: &Path) -> Result<PathBuf, LibjvmError> {
    let mut tried = Vec::new();
    for candidate in CANDIDATES {
        let path = java_home.join(candidate);
        if path.is_file() {
            return Ok(path);
        }
        tried.push(path);
    }
    Err(LibjvmError::NotFound { tried })
}

/// # Safety
/// Loads a shared library from `path`. The caller must keep the returned `Library` alive for as
/// long as anything obtained from it is used.
pub unsafe fn load(path: &Path) -> Result<libloading::Library, LibjvmError> {
    unsafe { libloading::Library::new(path) }.map_err(|error| LibjvmError::LoadFailed {
        path: path.to_path_buf(),
        reason: error.to_string(),
    })
}

type CreateJavaVm =
    unsafe extern "system" fn(*mut JavaVM, *mut JNIEnv, *mut c_void) -> jint;

/// # Safety
/// Calls `JNI_CreateJavaVM`, which must be called at most once per process.
#[allow(non_snake_case)]
pub unsafe fn create_vm(
    lib: &libloading::Library,
    path: &Path,
    options: &[String],
) -> Result<JavaVM, LibjvmError> {
    let create: libloading::Symbol<'_, CreateJavaVm> =
        unsafe { lib.get(b"JNI_CreateJavaVM\0") }.map_err(|error| LibjvmError::LoadFailed {
            path: path.to_path_buf(),
            reason: error.to_string(),
        })?;

    let mut owned = Vec::with_capacity(options.len());
    for option in options {
        owned.push(
            CString::new(option.as_str()).map_err(|_| LibjvmError::CreateVm { code: -1 })?,
        );
    }
    let mut c_options: Vec<JavaVMOption> = owned
        .iter()
        .map(|text| JavaVMOption {
            optionString: text.as_ptr() as *mut c_char,
            extraInfo: std::ptr::null_mut(),
        })
        .collect();

    let mut args = JavaVMInitArgs {
        version: JNI_VERSION_1_8,
        nOptions: c_options.len() as jint,
        options: c_options.as_mut_ptr(),
        ignoreUnrecognized: 0 as jboolean,
    };

    let mut vm: JavaVM = std::ptr::null();
    let mut env: JNIEnv = std::ptr::null();
    let code = unsafe { create(&mut vm, &mut env, &mut args as *mut _ as *mut c_void) };
    if code != 0 {
        return Err(LibjvmError::CreateVm { code });
    }
    Ok(vm)
}