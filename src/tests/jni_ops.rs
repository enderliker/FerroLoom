use crate::jvm::Arg;

#[test]
fn a_fresh_thread_attaches_and_calls() {
    let Some(jvm) = super::jvm() else {
        eprintln!("skip: no JDK or runtime jar");
        return;
    };
    let value = std::thread::spawn(move || {
        jvm.call_static(
            "dev/ferroloom/runtime/FerroNative",
            "call",
            "(IJ)J",
            &[Arg::Int(1), Arg::Long(41)],
        )
    })
    .join()
    .unwrap()
    .expect("call from a fresh thread");
    assert_eq!(value, 42);
}

#[test]
fn ping_round_trips_and_panic_becomes_a_java_exception() {
    let Some(jvm) = super::jvm() else {
        eprintln!("skip: no JDK or runtime jar");
        return;
    };

    let out = jvm
        .call_static(
            "dev/ferroloom/runtime/FerroNative",
            "call",
            "(IJ)J",
            &[Arg::Int(1), Arg::Long(41)],
        )
        .expect("ping call");
    assert_eq!(out, 42);

    // The deliberate panic is caught at the FFI boundary; keep the test output clean.
    let previous = std::panic::take_hook();
    std::panic::set_hook(Box::new(|_| {}));
    let error = jvm.call_static(
        "dev/ferroloom/runtime/FerroNative",
        "call",
        "(IJ)J",
        &[Arg::Int(99), Arg::Long(0)],
    );
    std::panic::set_hook(previous);
    let error = error.unwrap_err();
    let text = format!("{error:?}");
    assert!(
        text.contains("ferroloom: internal error"),
        "expected the panic to cross as a Java exception, got: {text}"
    );
}
