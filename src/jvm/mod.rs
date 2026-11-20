pub mod libjvm;
pub mod locate;
pub mod release;

use std::path::PathBuf;
use std::sync::atomic::{AtomicBool, Ordering};

pub use locate::{locate, JavaHome, JavaVersion, JvmLocateError, LocateSources, MINIMUM_JAVA};

/// `JNI_CreateJavaVM` may run once per process.
pub static CREATED: AtomicBool = AtomicBool::new(false);

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
        let java_home = locate_for_test()
            .map(|home| home.path)
            .unwrap_or_default();
        let classpath = std::env::var_os("FERROLOOM_LIB_DIR")
            .map(|dir| vec![PathBuf::from(dir).join("ferroloom-runtime.jar")])
            .unwrap_or_default();
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

pub struct Jvm {
    vm: jni::JavaVM,
}

impl Jvm {
    pub fn start(config: &JvmConfig) -> Result<Jvm, JvmError> {
        if CREATED.swap(true, Ordering::SeqCst) {
            return Err(JvmError::AlreadyCreated);
        }
        let result = Self::start_inner(config);
        if result.is_err() {
            // A failed start created nothing, so a later attempt may still succeed.
            CREATED.store(false, Ordering::SeqCst);
        }
        result
    }

    fn start_inner(config: &JvmConfig) -> Result<Jvm, JvmError> {
        let lib_path = libjvm::find_libjvm(&config.java_home).map_err(JvmError::Libjvm)?;

        let separator = if cfg!(windows) { ";" } else { ":" };
        let classpath = config
            .classpath
            .iter()
            .map(|path| path.to_string_lossy())
            .collect::<Vec<_>>()
            .join(separator);

        let mut builder = jni::InitArgsBuilder::new().version(jni::JNIVersion::V8);
        if !classpath.is_empty() {
            builder = builder.option(format!("-Djava.class.path={classpath}"));
        }
        builder = builder.option(format!("-Xmx{}M", config.xmx_mib));
        for option in &config.options {
            builder = builder.option(option.clone());
        }
        let args = builder
            .build()
            .map_err(|error| JvmError::Attach { reason: error.to_string() })?;

        let jvm_path = lib_path.clone();
        let vm = jni::JavaVM::with_libjvm(args, || Ok(jvm_path.clone()))
            .map_err(|error| JvmError::Start {
                reason: format!("{error:?}"),
            })?;
        Ok(Jvm { vm })
    }

    pub fn with_env<R>(&self, f: impl FnOnce(&mut jni::JNIEnv) -> R) -> Result<R, JvmError> {
        let mut guard = self
            .vm
            .attach_current_thread()
            .map_err(|error| JvmError::Attach { reason: error.to_string() })?;
        Ok(f(&mut guard))
    }

    pub fn call_static(
        &self,
        class: &str,
        method: &str,
        signature: &str,
        args: &[jni::objects::JValue<'_, '_>],
    ) -> Result<i64, JvmError> {
        self.with_env(|env| {
            let class = find_class(env, class)?;
            let result = env.call_static_method(&class, method, signature, args);
            if let Some(exception) = take_exception(env) {
                return Err(exception);
            }
            match result.map_err(|error| JvmError::Jni { reason: error.to_string() })? {
                jni::objects::JValueOwned::Long(value) => Ok(value),
                other => Err(JvmError::Jni {
                    reason: format!("expected a long return, got {other:?}"),
                }),
            }
        })?
    }

    pub fn call_static_string(
        &self,
        class: &str,
        method: &str,
        signature: &str,
        args: &[jni::objects::JValue<'_, '_>],
    ) -> Result<String, JvmError> {
        self.with_env(|env| {
            let class = find_class(env, class)?;
            let result = env.call_static_method(&class, method, signature, args);
            if let Some(exception) = take_exception(env) {
                return Err(exception);
            }
            let object = match result
                .map_err(|error| JvmError::Jni { reason: error.to_string() })?
            {
                jni::objects::JValueOwned::Object(object) => object,
                other => {
                    return Err(JvmError::Jni {
                        reason: format!("expected an object return, got {other:?}"),
                    })
                }
            };
            let text_object = jni::objects::JString::from(object);
            let text = env
                .get_string(&text_object)
                .map_err(|error| JvmError::Jni { reason: error.to_string() })?;
            Ok(text.to_string_lossy().into_owned())
        })?
    }
}

fn find_class<'local>(
    env: &mut jni::JNIEnv<'local>,
    name: &str,
) -> Result<jni::objects::JClass<'local>, JvmError> {
    match env.find_class(name) {
        Ok(class) => Ok(class),
        Err(error) => Err(take_exception(env)
            .unwrap_or(JvmError::Jni { reason: error.to_string() })),
    }
}


fn take_exception(env: &mut jni::JNIEnv<'_>) -> Option<JvmError> {
    if !env.exception_check().unwrap_or(false) {
        return None;
    }
    let throwable = env.exception_occurred().ok();
    let _ = env.exception_clear();

    let class = throwable.as_ref().and_then(|throwable| {
        let class = env.get_object_class(throwable).ok()?;
        let name = match env
            .call_method(&class, "getName", "()Ljava/lang/String;", &[])
            .ok()?
        {
            jni::objects::JValueOwned::Object(object) => object,
            _ => return None,
        };
        let name = jni::objects::JString::from(name);
        let name = env.get_string(&name).ok()?;
        Some(name.to_string_lossy().into_owned())
    });
    let message = throwable.as_ref().and_then(|throwable| {
        let text = match env
            .call_method(throwable, "toString", "()Ljava/lang/String;", &[])
            .ok()?
        {
            jni::objects::JValueOwned::Object(object) => object,
            _ => return None,
        };
        let text = jni::objects::JString::from(text);
        let text = env.get_string(&text).ok()?;
        Some(text.to_string_lossy().into_owned())
    });

    Some(JvmError::JavaException {
        class: class.unwrap_or_default(),
        message: message.unwrap_or_default(),
    })
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