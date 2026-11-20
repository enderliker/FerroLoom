extern crate self as ferroloom;

#[cfg(not(target_arch = "wasm32"))]
pub(crate) mod bridge;
#[cfg(not(target_arch = "wasm32"))]
mod horizon;
#[cfg(not(target_arch = "wasm32"))]
mod jni_sys;
#[cfg(not(target_arch = "wasm32"))]
pub mod jvm;
pub mod settings;

#[cfg(all(test, not(target_arch = "wasm32")))]
mod tests;

use mod_api::{ClientHost, ClientTickPreEvent, Hooks, RegistrationContext, Registrar};

#[derive(Default)]
struct Mod;

#[mod_api::horizon_mod]
impl Hooks for Mod {
    fn register(&self, _registrar: &mut dyn Registrar, context: &RegistrationContext) {
        #[cfg(not(target_arch = "wasm32"))]
        horizon::register(context);
        #[cfg(target_arch = "wasm32")]
        let _ = context;
    }

    fn client_tick_pre(&self, host: &mut dyn ClientHost, _event: &mut ClientTickPreEvent) {
        #[cfg(not(target_arch = "wasm32"))]
        horizon::client_tick(host);
        #[cfg(target_arch = "wasm32")]
        let _ = host;
    }
}