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

#[test]
fn mods_and_cache_come_from_the_environment_or_the_lib_dir() {
    let settings = ferroloom::settings::Settings::parse_with(
        Some("512"),
        None,
        Some("/lib"),
        Some("mods"),
        Some("cache"),
    );
    assert_eq!(settings.mods_dir, std::path::PathBuf::from("mods"));
    assert_eq!(settings.cache_dir, std::path::PathBuf::from("cache"));
    let defaults = ferroloom::settings::Settings::parse_with(
        None,
        None,
        Some("/home/u/.local/share/horizon/ferroloom-lib"),
        None,
        None,
    );
    assert!(defaults.mods_dir.ends_with("ferroloom-mods"));
    assert!(defaults.cache_dir.ends_with("ferroloom-cache"));
}
