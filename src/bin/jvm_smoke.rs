#[cfg(not(target_arch = "wasm32"))]
mod native {
    use ferroloom::jvm::{Jvm, JvmConfig};

    pub fn run() -> Result<(), Box<dyn std::error::Error>> {
        let config = JvmConfig::for_tests();
        let jvm = Jvm::start(&config).map_err(|error| format!("{error:?}"))?;
        let pong = jvm
            .call_static_string(
                "dev/ferroloom/runtime/FerroRuntime",
                "ping",
                "()Ljava/lang/String;",
                &[],
            )
            .map_err(|error| format!("{error:?}"))?;
        println!("{pong}");
        Ok(())
    }
}

#[cfg(not(target_arch = "wasm32"))]
fn main() -> Result<(), Box<dyn std::error::Error>> {
    native::run()
}

#[cfg(target_arch = "wasm32")]
fn main() {}