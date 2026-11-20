use ferroloom::jvm::{Jvm, JvmConfig, JvmError};
use std::path::PathBuf;

#[test]
fn a_second_start_is_refused_without_creating_anything() {
    ferroloom::jvm::mark_created_for_test();
    let config = JvmConfig {
        java_home: PathBuf::from("/nonexistent"),
        classpath: vec![],
        xmx_mib: 64,
        options: vec![],
    };
    assert!(matches!(Jvm::start(&config), Err(JvmError::AlreadyCreated)));
}

#[test]
#[ignore = "starts a real JVM; run with --ignored on a machine with a JDK"]
fn starts_a_real_jvm_and_then_refuses_the_next() {
    if ferroloom::jvm::locate_for_test().is_err() {
        eprintln!("skip: no JDK found");
        return;
    }
    let config = JvmConfig::for_tests();
    let first = Jvm::start(&config);
    assert!(first.is_ok(), "first start failed: {:?}", first.err());
    let second = Jvm::start(&config);
    assert!(matches!(second, Err(JvmError::AlreadyCreated)));
}