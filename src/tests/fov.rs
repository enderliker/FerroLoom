#[test]
fn the_fov_log_line_names_the_modifier_and_the_base() {
    assert_eq!(
        crate::horizon::fov_line(1.0, 1.0 / 3.0),
        "fov: modifier 0.3333 (base 1.0000)"
    );
}
