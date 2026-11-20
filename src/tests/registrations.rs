use ferroloom::bridge::registrations::{classpath, PendingRegistrations};

#[test]
fn the_report_names_keys_commands_and_hud() {
    let report = PendingRegistrations::parse(
        "KEY\texample\tkey\tkey.keyboard.g\nCMD\texample\tferro-hola\nHUD\nLOG\texample: main\n",
    );
    assert_eq!(report.logs, vec!["example: main"]);
    assert_eq!(report.keys.len(), 1);
    assert_eq!(report.keys[0].mod_id, "example");
    assert_eq!(report.keys[0].key_id, "key");
    assert_eq!(report.keys[0].default_key, "key.keyboard.g");
    assert_eq!(report.commands.len(), 1);
    assert_eq!(report.commands[0].name, "ferro-hola");
    assert!(report.hud);
}

#[test]
fn unknown_report_lines_are_ignored_and_empty_is_empty() {
    let report = PendingRegistrations::parse("WAT\tx\n");
    assert!(report.keys.is_empty() && report.commands.is_empty() && !report.hud);
    assert!(PendingRegistrations::parse("").keys.is_empty());
}

#[test]
fn classpath_lists_every_jar_sorted() {
    let tmp = tempfile::tempdir().unwrap();
    std::fs::write(tmp.path().join("b.jar"), b"").unwrap();
    std::fs::write(tmp.path().join("a.jar"), b"").unwrap();
    std::fs::write(tmp.path().join("x.txt"), b"").unwrap();
    assert_eq!(
        classpath(tmp.path()).unwrap(),
        vec![tmp.path().join("a.jar"), tmp.path().join("b.jar")]
    );
}
