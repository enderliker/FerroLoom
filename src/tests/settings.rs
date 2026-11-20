use ferroloom::settings::Settings;

#[test]
fn env_defaults_are_sane() {
    assert_eq!(Settings::from_env().xmx_mib, 1024);
}

#[test]
fn xmx_is_parsed_and_clamped() {
    assert_eq!(Settings::parse(Some("16"), None, None).xmx_mib, 256);
    assert_eq!(Settings::parse(Some("999999"), None, None).xmx_mib, 8192);
}