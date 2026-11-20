pub mod libjvm;
pub mod locate;
pub mod release;

use std::ffi::{CStr, CString};
use std::os::raw::{c_char, c_void};
use std::path::PathBuf;
use std::sync::Mutex;
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::mpsc::{self, Sender};

use crate::jni_sys::*;

pub use locate::{JavaHome, JavaVersion, JvmLocateError, LocateSources, MINIMUM_JAVA, locate};

/// `JNI_CreateJavaVM` may run once per process.
pub static CREATED: AtomicBool = AtomicBool::new(false);
static VM_LIVE: AtomicBool = AtomicBool::new(false);

const JNI_VERSION_1_8: jint = 0x0001_0008;

#[derive(Clone, Debug)]
pub struct JvmConfig {
    pub java_home: PathBuf,
    pub classpath: Vec<PathBuf>,
    pub xmx_mib: u32,
    pub options: Vec<String>,
}

impl JvmConfig {
    /// A configuration for tests and the smoke binary: the first JDK found and the runtime jar
    /// named by `FERROLOOM_LIB_DIR`.
    pub fn for_tests() -> JvmConfig {
        let java_home = locate_for_test().map(|home| home.path).unwrap_or_default();
        let classpath = match std::env::var_os("FERROLOOM_LIB_DIR") {
            Some(dir) => vec![PathBuf::from(dir).join("ferroloom-runtime.jar")],
            None => {
                let jar = PathBuf::from(env!("CARGO_MANIFEST_DIR"))
                    .join("java/ferroloom-runtime/build/libs/ferroloom-runtime.jar");
                if jar.exists() { vec![jar] } else { vec![] }
            }
        };
        JvmConfig {
            java_home,
            classpath,
            xmx_mib: 256,
            options: vec![],
        }
    }
}

#[derive(Debug)]
pub enum JvmError {
    AlreadyCreated,
    Locate(JvmLocateError),
    Libjvm(libjvm::LibjvmError),
    Start { reason: String },
    Attach { reason: String },
    Jni { reason: String },
    JavaException { class: String, message: String },
}

impl std::fmt::Display for JvmError {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            JvmError::AlreadyCreated => write!(f, "a JVM is already running in this process"),
            JvmError::Locate(error) => write!(f, "{error}"),
            JvmError::Libjvm(error) => write!(f, "{error}"),
            JvmError::Start { reason } => write!(f, "could not create the JVM: {reason}"),
            JvmError::Attach { reason } => {
                write!(f, "could not attach this thread to the JVM: {reason}")
            }
            JvmError::Jni { reason } => write!(f, "JNI error: {reason}"),
            JvmError::JavaException { class, message } => {
                write!(f, "Java threw {class}: {message}")
            }
        }
    }
}

impl std::error::Error for JvmError {}

/// One argument to a static Java call.
#[derive(Clone, Debug)]
pub enum Arg {
    Int(i32),
    Long(i64),
    Float(f32),
    Double(f64),
    Text(String),
}

/// Converts call arguments, returning the local string refs so they can be released after the call.
fn jvalues(env: *mut JNIEnv, args: &[Arg]) -> Result<(Vec<jvalue>, Vec<jobject>), JvmError> {
    let mut values = Vec::with_capacity(args.len());
    let mut strings = Vec::new();
    for arg in args {
        let value = arg.as_jvalue(env)?;
        if matches!(arg, Arg::Text(_)) {
            strings.push(unsafe { value.l });
        }
        values.push(value);
    }
    Ok((values, strings))
}

fn free_jstrings(env: *mut JNIEnv, strings: Vec<jobject>) {
    for string in strings {
        unsafe { delete(env, string) };
    }
}

impl Arg {
    fn as_jvalue(&self, env: *mut JNIEnv) -> Result<jvalue, JvmError> {
        Ok(match self {
            Arg::Int(value) => jvalue { i: *value },
            Arg::Long(value) => jvalue { j: *value },
            Arg::Float(value) => jvalue { f: *value },
            Arg::Double(value) => jvalue { d: *value },
            Arg::Text(text) => {
                let text = CString::new(text.as_str()).map_err(|_| JvmError::Jni {
                    reason: "argument contains NUL".to_string(),
                })?;
                let new_string = unsafe { (**env).NewStringUTF }.ok_or_else(|| JvmError::Jni {
                    reason: "NewStringUTF is missing".to_string(),
                })?;
                let string = unsafe { new_string(env, text.as_ptr()) };
                jvalue {
                    l: string as jobject,
                }
            }
        })
    }
}

enum Task {
    Long {
        class: String,
        method: String,
        signature: String,
        args: Vec<Arg>,
        reply: Sender<Result<i64, JvmError>>,
    },
    Void {
        class: String,
        method: String,
        signature: String,
        args: Vec<Arg>,
        reply: Sender<Result<(), JvmError>>,
    },
    String {
        class: String,
        method: String,
        signature: String,
        args: Vec<Arg>,
        reply: Sender<Result<String, JvmError>>,
    },
    Bool {
        class: String,
        method: String,
        signature: String,
        args: Vec<Arg>,
        reply: Sender<Result<bool, JvmError>>,
    },
    Float {
        class: String,
        method: String,
        signature: String,
        args: Vec<Arg>,
        reply: Sender<Result<f32, JvmError>>,
    },
    Tick {
        tick: u64,
        reply: Sender<Result<(), JvmError>>,
    },
}

/// The VM runs on a dedicated thread that created it and serves every call. The game's threads
/// never attach: HotSpot refuses to attach them in this host, and one thread keeps every JNI
/// callback on the thread whose class loaders, window and queue are valid.
pub struct Jvm {
    tasks: Mutex<Sender<Task>>,
    _lib: libloading::Library,
}

type CreateJavaVm = unsafe extern "system" fn(*mut JavaVM, *mut *mut c_void, *mut c_void) -> jint;

impl Jvm {
    pub fn start(config: &JvmConfig) -> Result<Jvm, JvmError> {
        if CREATED.swap(true, Ordering::SeqCst) {
            return Err(JvmError::AlreadyCreated);
        }
        let result = Self::start_inner(config);
        if result.is_err() && !VM_LIVE.load(Ordering::SeqCst) {
            // A failure before the VM existed can be retried; once it exists, the guard stays
            // set because a second `JNI_CreateJavaVM` is unsupported.
            CREATED.store(false, Ordering::SeqCst);
        }
        result
    }

    fn start_inner(config: &JvmConfig) -> Result<Jvm, JvmError> {
        let (ready_sender, ready_receiver) = mpsc::channel();
        let config = config.clone();
        std::thread::Builder::new()
            .name("ferroloom-jvm".to_string())
            .stack_size(16 * 1024 * 1024)
            .spawn(move || {
                crate::bridge::writes::mark_bridge_thread();
                if let Err(error) = create_and_serve(&config, ready_sender) {
                    eprintln!("[ferroloom] JVM thread ended: {error}");
                }
            })
            .map_err(|error| JvmError::Start {
                reason: format!("could not start the JVM thread: {error}"),
            })?;
        match ready_receiver.recv() {
            Ok(Ok((lib, tasks))) => Ok(Jvm {
                tasks: Mutex::new(tasks),
                _lib: lib,
            }),
            Ok(Err(error)) => Err(error),
            Err(error) => Err(JvmError::Start {
                reason: format!("the JVM thread ended without a result: {error}"),
            }),
        }
    }

    fn request(&self, task: Task) -> Result<(), JvmError> {
        self.tasks
            .lock()
            .unwrap()
            .send(task)
            .map_err(|_| JvmError::Jni {
                reason: "the JVM thread is gone".to_string(),
            })
    }

    pub fn call_static(
        &self,
        class: &str,
        method: &str,
        signature: &str,
        args: &[Arg],
    ) -> Result<i64, JvmError> {
        let (reply, response) = mpsc::channel();
        self.request(Task::Long {
            class: class.to_string(),
            method: method.to_string(),
            signature: signature.to_string(),
            args: args.to_vec(),
            reply,
        })?;
        response.recv().map_err(|_| JvmError::Jni {
            reason: "the JVM thread dropped the call".to_string(),
        })?
    }

    pub fn call_static_void(
        &self,
        class: &str,
        method: &str,
        signature: &str,
        args: &[Arg],
    ) -> Result<(), JvmError> {
        let (reply, response) = mpsc::channel();
        self.request(Task::Void {
            class: class.to_string(),
            method: method.to_string(),
            signature: signature.to_string(),
            args: args.to_vec(),
            reply,
        })?;
        response.recv().map_err(|_| JvmError::Jni {
            reason: "the JVM thread dropped the call".to_string(),
        })?
    }

    pub fn call_static_bool(
        &self,
        class: &str,
        method: &str,
        signature: &str,
        args: &[Arg],
    ) -> Result<bool, JvmError> {
        let (reply, response) = mpsc::channel();
        self.request(Task::Bool {
            class: class.to_string(),
            method: method.to_string(),
            signature: signature.to_string(),
            args: args.to_vec(),
            reply,
        })?;
        response.recv().map_err(|_| JvmError::Jni {
            reason: "the JVM thread dropped the call".to_string(),
        })?
    }

    pub fn call_static_f32(
        &self,
        class: &str,
        method: &str,
        signature: &str,
        args: &[Arg],
    ) -> Result<f32, JvmError> {
        let (reply, response) = mpsc::channel();
        self.request(Task::Float {
            class: class.to_string(),
            method: method.to_string(),
            signature: signature.to_string(),
            args: args.to_vec(),
            reply,
        })?;
        response.recv().map_err(|_| JvmError::Jni {
            reason: "the JVM thread dropped the call".to_string(),
        })?
    }

    pub fn call_static_string(
        &self,
        class: &str,
        method: &str,
        signature: &str,
        args: &[Arg],
    ) -> Result<String, JvmError> {
        let (reply, response) = mpsc::channel();
        self.request(Task::String {
            class: class.to_string(),
            method: method.to_string(),
            signature: signature.to_string(),
            args: args.to_vec(),
            reply,
        })?;
        response.recv().map_err(|_| JvmError::Jni {
            reason: "the JVM thread dropped the call".to_string(),
        })?
    }

    /// Runs `FerroRuntime.onTick` on the JVM thread, where the read window and the write queue
    /// set by the game thread are visible.
    pub fn tick(&self, tick: u64) -> Result<(), JvmError> {
        let (reply, response) = mpsc::channel();
        self.request(Task::Tick { tick, reply })?;
        response.recv().map_err(|_| JvmError::Jni {
            reason: "the JVM thread dropped the tick".to_string(),
        })?
    }
}

fn create_and_serve(
    config: &JvmConfig,
    ready: Sender<Result<(libloading::Library, Sender<Task>), JvmError>>,
) -> Result<(), JvmError> {
    let (_vm, env, lib) = match create_vm(config) {
        Ok(value) => value,
        Err(error) => {
            let _ = ready.send(Err(error));
            return Ok(());
        }
    };

    if let Err(error) = crate::bridge::jni::register_natives(env) {
        let _ = ready.send(Err(error));
        return Ok(());
    }
    file_log("JVM ready (bridge thread attached)");

    let (task_sender, task_receiver) = mpsc::channel::<Task>();
    let _ = ready.send(Ok((lib, task_sender)));
    while let Ok(task) = task_receiver.recv() {
        execute(env, task);
    }
    Ok(())
}

fn create_vm(config: &JvmConfig) -> Result<(usize, *mut JNIEnv, libloading::Library), JvmError> {
    let lib_path = libjvm::find_libjvm(&config.java_home).map_err(JvmError::Libjvm)?;
    let lib = unsafe { libjvm::load(&lib_path) }.map_err(JvmError::Libjvm)?;
    let create: libloading::Symbol<'_, CreateJavaVm> = unsafe { lib.get(b"JNI_CreateJavaVM\0") }
        .map_err(|error| {
            JvmError::Libjvm(libjvm::LibjvmError::LoadFailed {
                path: lib_path.clone(),
                reason: error.to_string(),
            })
        })?;

    let separator = if cfg!(windows) { ";" } else { ":" };
    let classpath = config
        .classpath
        .iter()
        .map(|path| path.to_string_lossy())
        .collect::<Vec<_>>()
        .join(separator);

    let mut options: Vec<String> = Vec::new();
    if !classpath.is_empty() {
        options.push(format!("-Djava.class.path={classpath}"));
    }
    options.push(format!("-Xmx{}M", config.xmx_mib));
    options.extend(config.options.iter().cloned());

    let owned: Vec<CString> = options
        .iter()
        .map(|option| CString::new(option.as_str()).unwrap_or_default())
        .collect();
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
        ignoreUnrecognized: 0,
    };

    let mut vm: JavaVM = std::ptr::null();
    let mut penv: *mut c_void = std::ptr::null_mut();
    let code = unsafe { create(&mut vm, &mut penv, &mut args as *mut _ as *mut c_void) };
    if code != 0 {
        return Err(JvmError::Start {
            reason: format!("JNI_CreateJavaVM returned {code}"),
        });
    }
    VM_LIVE.store(true, Ordering::SeqCst);
    Ok((vm as usize, penv as *mut JNIEnv, lib))
}

fn execute(env: *mut JNIEnv, task: Task) {
    match task {
        Task::Long {
            class,
            method,
            signature,
            args,
            reply,
        } => {
            let _ = reply.send(unsafe { call_long_env(env, &class, &method, &signature, &args) });
        }
        Task::Void {
            class,
            method,
            signature,
            args,
            reply,
        } => {
            let _ = reply.send(unsafe { call_void_env(env, &class, &method, &signature, &args) });
        }
        Task::String {
            class,
            method,
            signature,
            args,
            reply,
        } => {
            let _ = reply.send(unsafe { call_string_env(env, &class, &method, &signature, &args) });
        }
        Task::Bool {
            class,
            method,
            signature,
            args,
            reply,
        } => {
            let _ = reply.send(unsafe { call_bool_env(env, &class, &method, &signature, &args) });
        }
        Task::Float {
            class,
            method,
            signature,
            args,
            reply,
        } => {
            let _ = reply.send(unsafe { call_float_env(env, &class, &method, &signature, &args) });
        }
        Task::Tick { tick, reply } => {
            let _ = reply.send(unsafe {
                call_void_env(
                    env,
                    "dev/ferroloom/runtime/FerroRuntime",
                    "onTick",
                    "(J)V",
                    &[Arg::Long(tick as i64)],
                )
            });
        }
    }
}

unsafe fn call_long_env(
    env: *mut JNIEnv,
    class: &str,
    method: &str,
    signature: &str,
    args: &[Arg],
) -> Result<i64, JvmError> {
    let class = unsafe { find_class(env, class)? };
    let method = unsafe { static_method(env, class, method, signature)? };
    let (args, strings) = jvalues(env, args)?;
    let call = unsafe { (**env).CallStaticLongMethodA }.ok_or_else(|| JvmError::Jni {
        reason: "CallStaticLongMethodA is missing".to_string(),
    })?;
    let value = unsafe { call(env, class, method, args.as_ptr()) };
    let exception = unsafe { take_exception(env) };
    free_jstrings(env, strings);
    unsafe { delete(env, class as jobject) };
    match exception {
        Some(error) => Err(error),
        None => Ok(value),
    }
}

unsafe fn call_void_env(
    env: *mut JNIEnv,
    class: &str,
    method: &str,
    signature: &str,
    args: &[Arg],
) -> Result<(), JvmError> {
    let class = unsafe { find_class(env, class)? };
    let method = unsafe { static_method(env, class, method, signature)? };
    let (args, strings) = jvalues(env, args)?;
    let call = unsafe { (**env).CallStaticVoidMethodA }.ok_or_else(|| JvmError::Jni {
        reason: "CallStaticVoidMethodA is missing".to_string(),
    })?;
    unsafe { call(env, class, method, args.as_ptr()) };
    let exception = unsafe { take_exception(env) };
    free_jstrings(env, strings);
    unsafe { delete(env, class as jobject) };
    match exception {
        Some(error) => Err(error),
        None => Ok(()),
    }
}

unsafe fn call_bool_env(
    env: *mut JNIEnv,
    class: &str,
    method: &str,
    signature: &str,
    args: &[Arg],
) -> Result<bool, JvmError> {
    let class = unsafe { find_class(env, class)? };
    let method = unsafe { static_method(env, class, method, signature)? };
    let (args, strings) = jvalues(env, args)?;
    let call = unsafe { (**env).CallStaticBooleanMethodA }.ok_or_else(|| JvmError::Jni {
        reason: "CallStaticBooleanMethodA is missing".to_string(),
    })?;
    let value = unsafe { call(env, class, method, args.as_ptr()) };
    let exception = unsafe { take_exception(env) };
    free_jstrings(env, strings);
    unsafe { delete(env, class as jobject) };
    match exception {
        Some(error) => Err(error),
        None => Ok(value != 0),
    }
}

unsafe fn call_float_env(
    env: *mut JNIEnv,
    class: &str,
    method: &str,
    signature: &str,
    args: &[Arg],
) -> Result<f32, JvmError> {
    let class = unsafe { find_class(env, class)? };
    let method = unsafe { static_method(env, class, method, signature)? };
    let (args, strings) = jvalues(env, args)?;
    let call = unsafe { (**env).CallStaticFloatMethodA }.ok_or_else(|| JvmError::Jni {
        reason: "CallStaticFloatMethodA is missing".to_string(),
    })?;
    let value = unsafe { call(env, class, method, args.as_ptr()) };
    let exception = unsafe { take_exception(env) };
    free_jstrings(env, strings);
    unsafe { delete(env, class as jobject) };
    match exception {
        Some(error) => Err(error),
        None => Ok(value),
    }
}

unsafe fn call_string_env(
    env: *mut JNIEnv,
    class: &str,
    method: &str,
    signature: &str,
    args: &[Arg],
) -> Result<String, JvmError> {
    let class = unsafe { find_class(env, class)? };
    let method = unsafe { static_method(env, class, method, signature)? };
    let (args, strings) = jvalues(env, args)?;
    let call = unsafe { (**env).CallStaticObjectMethodA }.ok_or_else(|| JvmError::Jni {
        reason: "CallStaticObjectMethodA is missing".to_string(),
    })?;
    let object = unsafe { call(env, class, method, args.as_ptr()) };
    let exception = unsafe { take_exception(env) };
    free_jstrings(env, strings);
    unsafe { delete(env, class as jobject) };
    if let Some(error) = exception {
        unsafe { delete(env, object) };
        return Err(error);
    }
    let text = if object.is_null() {
        String::new()
    } else {
        unsafe { java_string(env, object as jstring) }.unwrap_or_default()
    };
    unsafe { delete(env, object) };
    Ok(text)
}

pub(crate) unsafe fn delete(env: *mut JNIEnv, object: jobject) {
    if object.is_null() {
        return;
    }
    if let Some(delete_local) = unsafe { (**env).DeleteLocalRef } {
        unsafe { delete_local(env, object) };
    }
}

unsafe fn find_class(env: *mut JNIEnv, name: &str) -> Result<jclass, JvmError> {
    let name = CString::new(name).map_err(|_| JvmError::Jni {
        reason: "class name contains NUL".to_string(),
    })?;
    let find = (**env).FindClass.ok_or_else(|| JvmError::Jni {
        reason: "FindClass is missing".to_string(),
    })?;
    let class = unsafe { find(env, name.as_ptr()) };
    if class.is_null() {
        if let Some(error) = unsafe { take_exception(env) } {
            return Err(error);
        }
        return Err(JvmError::Jni {
            reason: "class not found".to_string(),
        });
    }
    Ok(class)
}

unsafe fn static_method(
    env: *mut JNIEnv,
    class: jclass,
    name: &str,
    signature: &str,
) -> Result<jmethodID, JvmError> {
    let name = CString::new(name).map_err(|_| JvmError::Jni {
        reason: "method name contains NUL".to_string(),
    })?;
    let signature = CString::new(signature).map_err(|_| JvmError::Jni {
        reason: "signature contains NUL".to_string(),
    })?;
    let get = (**env).GetStaticMethodID.ok_or_else(|| JvmError::Jni {
        reason: "GetStaticMethodID is missing".to_string(),
    })?;
    let method = unsafe { get(env, class, name.as_ptr(), signature.as_ptr()) };
    if method.is_null() {
        if let Some(error) = unsafe { take_exception(env) } {
            return Err(error);
        }
        return Err(JvmError::Jni {
            reason: "static method not found".to_string(),
        });
    }
    Ok(method)
}

pub(crate) unsafe fn java_string(env: *mut JNIEnv, string: jstring) -> Option<String> {
    if string.is_null() {
        return None;
    }
    let get = unsafe { (**env).GetStringUTFChars }?;
    let chars = unsafe { get(env, string, std::ptr::null_mut()) };
    if chars.is_null() {
        return None;
    }
    let text = unsafe { CStr::from_ptr(chars) }
        .to_string_lossy()
        .into_owned();
    if let Some(release) = unsafe { (**env).ReleaseStringUTFChars } {
        unsafe { release(env, string, chars) };
    }
    Some(text)
}

pub(crate) unsafe fn take_exception(env: *mut JNIEnv) -> Option<JvmError> {
    let check = unsafe { (**env).ExceptionCheck }?;
    if unsafe { check(env) } == 0 {
        return None;
    }
    let occurred = unsafe { (**env).ExceptionOccurred }?;
    let throwable = unsafe { occurred(env) };
    if let Some(clear) = unsafe { (**env).ExceptionClear } {
        unsafe { clear(env) };
    }

    let class = unsafe {
        match (**env).GetObjectClass {
            Some(get) => get(env, throwable),
            None => std::ptr::null_mut(),
        }
    };
    let name = if class.is_null() {
        String::new()
    } else {
        unsafe {
            object_string(
                env,
                class as jobject,
                c"getName".as_ptr(),
                c"()Ljava/lang/String;".as_ptr(),
            )
        }
        .unwrap_or_default()
    };
    let message = unsafe {
        object_string(
            env,
            throwable,
            c"toString".as_ptr(),
            c"()Ljava/lang/String;".as_ptr(),
        )
    }
    .unwrap_or_default();

    unsafe {
        delete(env, throwable);
        delete(env, class as jobject);
    }
    Some(JvmError::JavaException {
        class: name,
        message,
    })
}

unsafe fn object_string(
    env: *mut JNIEnv,
    object: jobject,
    name: *const c_char,
    signature: *const c_char,
) -> Option<String> {
    let class = unsafe { (**env).GetObjectClass? }(env, object);
    let method = unsafe { (**env).GetMethodID? }(env, class, name, signature);
    let call = unsafe { (**env).CallObjectMethodA? };
    let value = unsafe { call(env, object, method, std::ptr::null()) };
    let text = unsafe { java_string(env, value as jstring) };
    unsafe {
        delete(env, value);
        delete(env, class as jobject);
    }
    text
}

/// Diagnostics for the in-game bridge, beside the game's files.
pub(crate) fn file_log(message: &str) {
    let Some(home) = std::env::var_os("HOME") else {
        return;
    };
    let path = PathBuf::from(home).join(".local/share/horizon/ferroloom.log");
    if let Ok(mut file) = std::fs::OpenOptions::new()
        .create(true)
        .append(true)
        .open(path)
    {
        use std::io::Write;
        let _ = writeln!(file, "{message}");
    }
}

/// Locates a JDK from `FERROLOOM_JAVA_HOME` or `JAVA_HOME`; used by tests and the smoke binary.
pub fn locate_for_test() -> Result<JavaHome, JvmLocateError> {
    let explicit = std::env::var_os("FERROLOOM_JAVA_HOME").map(PathBuf::from);
    let java_home = std::env::var_os("JAVA_HOME").map(PathBuf::from);
    locate(LocateSources {
        explicit: explicit.as_deref(),
        minecraft_roots: &[],
        java_home: java_home.as_deref(),
    })
}

pub fn mark_created_for_test() {
    CREATED.store(true, Ordering::SeqCst);
}
