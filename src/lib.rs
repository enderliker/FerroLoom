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

use mod_api::runtime::Flow;
use mod_api::{
    ClientHost, ClientLevelLoadEvent, ClientLevelUnloadEvent, ClientSetupEvent,
    ClientStoppingEvent, ClientTickPreEvent, CommandEvent, ComputeFovModifierEvent,
    GuiLayerDrawEvent, Hooks, RegisterGuiLayersEvent, Registrar, RegistrationContext,
};

#[derive(Default)]
struct Mod;

#[mod_api::horizon_mod]
impl Hooks for Mod {
    fn register(&self, registrar: &mut dyn Registrar, context: &RegistrationContext) {
        #[cfg(not(target_arch = "wasm32"))]
        horizon::register(context, registrar);
        #[cfg(target_arch = "wasm32")]
        let _ = (context, registrar);
    }

    fn client_setup(&self, _host: &mut dyn ClientHost, _event: &mut ClientSetupEvent) {
        #[cfg(not(target_arch = "wasm32"))]
        horizon::client_setup(_host);
    }

    fn client_stopping(&self, host: &mut dyn ClientHost, _event: &mut ClientStoppingEvent) {
        #[cfg(not(target_arch = "wasm32"))]
        horizon::client_stopping(host);
        #[cfg(target_arch = "wasm32")]
        let _ = host;
    }

    fn client_level_load(&self, host: &mut dyn ClientHost, event: &mut ClientLevelLoadEvent<'_>) {
        #[cfg(not(target_arch = "wasm32"))]
        horizon::client_level_load(host, event.dimension);
        #[cfg(target_arch = "wasm32")]
        let _ = (host, event);
    }

    fn client_level_unload(
        &self,
        host: &mut dyn ClientHost,
        event: &mut ClientLevelUnloadEvent<'_>,
    ) {
        #[cfg(not(target_arch = "wasm32"))]
        horizon::client_level_unload(host, event.dimension);
        #[cfg(target_arch = "wasm32")]
        let _ = (host, event);
    }

    fn compute_fov_modifier(
        &self,
        _host: &dyn ClientHost,
        event: &mut ComputeFovModifierEvent<'_>,
    ) {
        #[cfg(not(target_arch = "wasm32"))]
        horizon::compute_fov_modifier(event);
    }

    fn command(&self, _host: &mut dyn ClientHost, event: &mut CommandEvent<'_>) -> Flow {
        #[cfg(not(target_arch = "wasm32"))]
        if horizon::command(_host, event.command) {
            return Flow::Cancel;
        }
        #[cfg(target_arch = "wasm32")]
        let _ = event;
        Flow::Continue
    }

    fn register_gui_layers(
        &self,
        _context: &RegistrationContext,
        event: &mut RegisterGuiLayersEvent<'_>,
    ) {
        #[cfg(not(target_arch = "wasm32"))]
        horizon::register_gui_layers(event);
        #[cfg(target_arch = "wasm32")]
        let _ = event;
    }

    fn gui_layer_draw(&self, _host: &dyn ClientHost, event: &mut GuiLayerDrawEvent<'_>) {
        #[cfg(not(target_arch = "wasm32"))]
        horizon::gui_layer_draw(event);
        #[cfg(target_arch = "wasm32")]
        let _ = event;
    }

    fn client_tick_pre(&self, host: &mut dyn ClientHost, _event: &mut ClientTickPreEvent) {
        #[cfg(not(target_arch = "wasm32"))]
        horizon::client_tick(host);
        #[cfg(target_arch = "wasm32")]
        let _ = host;
    }
}
