use ferroloom::jvm::libjvm::{find_libjvm, load, LibjvmError};
use std::fs;

#[test]
fn finds_server_libjvm_on_linux_layout() {
    let tmp = tempfile::tempdir().unwrap();
    let path = tmp.path().join("lib/server/libjvm.so");
    fs::create_dir_all(path.parent().unwrap()).unwrap();
    fs::write(&path, b"x").unwrap();
    assert_eq!(find_libjvm(tmp.path()).unwrap(), path);
}

#[test]
fn missing_libjvm_lists_candidates() {
    let tmp = tempfile::tempdir().unwrap();
    match find_libjvm(tmp.path()).unwrap_err() {
        LibjvmError::NotFound { tried } => {
            assert!(tried.iter().any(|p| p.ends_with("lib/server/libjvm.so")));
        }
        error => panic!("{error:?}"),
    }
}

#[test]
fn garbage_file_fails_to_load_with_its_path() {
    let tmp = tempfile::tempdir().unwrap();
    let path = tmp.path().join("libjvm.so");
    fs::write(&path, b"not an elf").unwrap();
    match unsafe { load(&path) } {
        Err(LibjvmError::LoadFailed {
            path: failed,
            reason,
        }) => {
            assert_eq!(failed, path);
            assert!(!reason.is_empty());
        }
        other => panic!("expected LoadFailed, got {other:?}"),
    }
}