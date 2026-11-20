use crate::jvm::{JavaVersion, JvmError, JvmLocateError};

#[test]
fn start_failure_names_the_cause() {
    let text = JvmError::Start {
        reason: "no libjvm".into(),
    }
    .to_string();
    assert!(text.contains("could not create"), "{text}");
    assert!(text.contains("no libjvm"), "{text}");
}

#[test]
fn a_java_exception_keeps_its_class_and_message() {
    let text = JvmError::JavaException {
        class: "java.lang.RuntimeException".into(),
        message: "boom".into(),
    }
    .to_string();
    assert!(
        text.contains("java.lang.RuntimeException") && text.contains("boom"),
        "{text}"
    );
}

#[test]
fn version_too_old_mentions_both_versions() {
    let text = JvmError::Locate(JvmLocateError::VersionTooOld {
        found: JavaVersion {
            major: 17,
            full: "17.0.10".into(),
        },
        minimum: 21,
    })
    .to_string();
    assert!(text.contains("17.0.10") && text.contains("21"), "{text}");
}