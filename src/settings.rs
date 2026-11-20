use std::path::PathBuf;

pub const MIN_XMX_MIB: u32 = 256;
pub const MAX_XMX_MIB: u32 = 8192;
pub const DEFAULT_XMX_MIB: u32 = 1024;

#[derive(Clone, Debug)]
pub struct Settings {
    pub java_home: Option<PathBuf>,
    pub lib_dir: Option<PathBuf>,
    pub minecraft_roots: Vec<PathBuf>,
    pub xmx_mib: u32,
}

impl Settings {
    pub fn from_env() -> Settings {
        let xmx = std::env::var("FERROLOOM_XMX_MIB").ok();
        let java_home = std::env::var("FERROLOOM_JAVA_HOME").ok();
        let lib_dir = std::env::var("FERROLOOM_LIB_DIR").ok();
        let mut settings = Settings::parse(xmx.as_deref(), java_home.as_deref(), lib_dir.as_deref());

        settings.minecraft_roots = std::env::var("FERROLOOM_MC_RUNTIME")
            .map(|value| value.split(':').map(PathBuf::from).collect())
            .unwrap_or_default();
        if settings.minecraft_roots.is_empty() {
            if let Some(home) = std::env::var_os("HOME") {
                let runtime = PathBuf::from(home).join(".minecraft/runtime");
                if runtime.is_dir() {
                    settings.minecraft_roots.push(runtime);
                }
            }
        }
        // Plan 1 dev fallback: the launcher's Play button does not pass FERROLOOM_LIB_DIR to the
        // game, so look for the staged runtime jar in the instance home.
        if settings.lib_dir.is_none() {
            if let Some(home) = std::env::var_os("HOME") {
                let staged = PathBuf::from(home).join(".local/share/horizon/ferroloom-lib");
                if staged.join("ferroloom-runtime.jar").is_file() {
                    settings.lib_dir = Some(staged);
                }
            }
        }
        settings
    }

    pub fn parse(xmx: Option<&str>, java_home: Option<&str>, lib_dir: Option<&str>) -> Settings {
        let xmx_mib = xmx
            .and_then(|value| value.trim().parse::<u32>().ok())
            .unwrap_or(DEFAULT_XMX_MIB)
            .clamp(MIN_XMX_MIB, MAX_XMX_MIB);
        Settings {
            java_home: java_home.map(PathBuf::from),
            lib_dir: lib_dir.map(PathBuf::from),
            minecraft_roots: Vec::new(),
            xmx_mib,
        }
    }
}