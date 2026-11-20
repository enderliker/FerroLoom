use ferroloom::bridge::handles::HandleArena;

#[test]
fn insert_get_remove_round_trips() {
    let mut arena = HandleArena::new();
    let handle = arena.insert("player");
    assert_eq!(arena.get(handle), Some(&"player"));
    assert_eq!(arena.remove(handle), Some("player"));
    assert_eq!(arena.get(handle), None);
}

#[test]
fn invalidate_all_makes_old_handles_stale() {
    let mut arena = HandleArena::new();
    let handle = arena.insert(7);
    arena.invalidate_all();
    assert_eq!(arena.get(handle), None);
    assert_ne!(arena.insert(8), handle);
}