use std::path::PathBuf;
use std::sync::OnceLock;

use crate::jvm::{locate_for_test, Jvm, JvmConfig};

mod handles;
mod jni_ops;
mod jvm_libjvm;
mod jvm_locate;
mod jvm_start;
mod settings;
mod tick_roundtrip;
mod window;
mod writes;

/// The runtime jar must be built first: `./gradlew :ferroloom-runtime:jar`.
pub fn config() -> Option<JvmConfig> {
    let jar = PathBuf::from(env!("CARGO_MANIFEST_DIR"))
        .join("java/ferroloom-runtime/build/libs/ferroloom-runtime.jar");
    if !jar.exists() {
        eprintln!("skip: build the runtime jar first (./gradlew :ferroloom-runtime:jar)");
        return None;
    }
    let home = locate_for_test().ok()?;
    Some(JvmConfig {
        java_home: home.path,
        classpath: vec![jar],
        xmx_mib: 256,
        options: vec![],
    })
}

/// One JVM per test process: `JNI_CreateJavaVM` may run only once.
pub fn jvm() -> Option<&'static Jvm> {
    static JVM: OnceLock<Jvm> = OnceLock::new();
    let config = config()?;
    Some(JVM.get_or_init(|| Jvm::start(&config).expect("the JVM starts")))
}