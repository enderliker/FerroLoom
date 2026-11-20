use ferroloom::jvm::locate::{JvmLocateError, LocateSources, locate};
use std::fs;

fn fake_jdk(dir: &std::path::Path, version: &str) {
    fs::create_dir_all(dir.join("bin")).unwrap();
    fs::write(dir.join("bin/java"), b"#!/bin/sh\n").unwrap();
    fs::write(dir.join("release"), format!("JAVA_VERSION=\"{version}\"\n")).unwrap();
}

#[test]
fn explicit_path_wins_and_reports_version() {
    let tmp = tempfile::tempdir().unwrap();
    fake_jdk(tmp.path(), "21.0.5");
    let home = locate(LocateSources {
        explicit: Some(tmp.path()),
        minecraft_roots: &[],
        java_home: None,
    })
    .unwrap();
    assert_eq!(home.version.major, 21);
    assert_eq!(home.version.full, "21.0.5");
}

#[test]
fn java_home_env_is_the_fallback() {
    let tmp = tempfile::tempdir().unwrap();
    fake_jdk(tmp.path(), "25");
    let home = locate(LocateSources {
        explicit: None,
        minecraft_roots: &[],
        java_home: Some(tmp.path()),
    })
    .unwrap();
    assert_eq!(home.version.major, 25);
}

#[test]
fn newest_minecraft_runtime_wins_over_java_home() {
    let mc = tempfile::tempdir().unwrap();
    fake_jdk(&mc.path().join("java-runtime-alpha"), "21.0.5");
    fake_jdk(&mc.path().join("java-runtime-delta"), "25.0.1");
    let jh = tempfile::tempdir().unwrap();
    fake_jdk(jh.path(), "21.0.9");
    let roots = [mc.path().to_path_buf()];
    let home = locate(LocateSources {
        explicit: None,
        minecraft_roots: &roots,
        java_home: Some(jh.path()),
    })
    .unwrap();
    assert_eq!(home.version.major, 25);
}

#[test]
fn nothing_found_names_every_path_tried() {
    let root = std::path::PathBuf::from("/nonexistent-ferroloom-root");
    let jh = std::path::PathBuf::from("/nonexistent-ferroloom-java-home");
    match locate(LocateSources {
        explicit: None,
        minecraft_roots: &[root.clone()],
        java_home: Some(&jh),
    })
    .unwrap_err()
    {
        JvmLocateError::NotFound { tried } => {
            assert!(tried.iter().any(|p| p == &root), "missing root: {tried:?}");
            assert!(
                tried.iter().any(|p| p == &jh),
                "missing java_home: {tried:?}"
            );
        }
        e => panic!("{e:?}"),
    }
}

#[test]
fn java_17_is_refused_with_both_versions() {
    let tmp = tempfile::tempdir().unwrap();
    fake_jdk(tmp.path(), "17.0.10");
    match locate(LocateSources {
        explicit: Some(tmp.path()),
        minecraft_roots: &[],
        java_home: None,
    })
    .unwrap_err()
    {
        JvmLocateError::VersionTooOld { found, minimum } => {
            assert_eq!(found.major, 17);
            assert_eq!(minimum, 21);
        }
        e => panic!("{e:?}"),
    }
}
