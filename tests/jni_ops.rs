use ferroloom::jvm::{locate_for_test, Jvm, JvmConfig};
use jni::objects::JValue;
use std::path::PathBuf;

fn config() -> Option<JvmConfig> {
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

#[test]
fn ping_round_trips_and_panic_becomes_a_java_exception() {
    let Some(config) = config() else {
        eprintln!("skip: no JDK");
        return;
    };
    let jvm = Jvm::start(&config).expect("jvm starts");
    let out = jvm
        .call_static(
            "dev/ferroloom/runtime/FerroNative",
            "call",
            "(IJ)J",
            &[JValue::Int(1), JValue::Long(41)],
        )
        .expect("ping call");
    assert_eq!(out, 42);

    // The deliberate panic is caught at the FFI boundary; keep the test output clean.
    let previous = std::panic::take_hook();
    std::panic::set_hook(Box::new(|_| {}));
    let error = jvm.call_static(
        "dev/ferroloom/runtime/FerroNative",
        "call",
        "(IJ)J",
        &[JValue::Int(99), JValue::Long(0)],
    );
    std::panic::set_hook(previous);
    let error = error.unwrap_err();
    let text = format!("{error:?}");
    assert!(
        text.contains("ferroloom: internal error"),
        "expected the panic to cross as a Java exception, got: {text}"
    );
}