use crate::jvm::{Jvm, JvmError};

#[test]
fn a_second_start_is_refused_without_creating_anything() {
    let Some(_) = super::jvm() else {
        eprintln!("skip: no JDK or runtime jar");
        return;
    };
    let config = super::config().expect("config after a started JVM");
    assert!(matches!(Jvm::start(&config), Err(JvmError::AlreadyCreated)));
}