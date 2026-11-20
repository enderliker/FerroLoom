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
    pub mods_dir: PathBuf,
    pub cache_dir: PathBuf,
}

impl Settings {
    pub fn from_env() -> Settings {
        let xmx = std::env::var("FERROLOOM_XMX_MIB").ok();
        let java_home = std::env::var("FERROLOOM_JAVA_HOME").ok();
        let lib_dir = std::env::var("FERROLOOM_LIB_DIR").ok();
        let mut settings = Settings::parse_with(
            xmx.as_deref(),
            java_home.as_deref(),
            lib_dir.as_deref(),
            std::env::var("FERROLOOM_MODS_DIR").ok().as_deref(),
            std::env::var("FERROLOOM_CACHE_DIR").ok().as_deref(),
        );

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
        Settings::parse_with(xmx, java_home, lib_dir, None, None)
    }

    pub fn parse_with(
        xmx: Option<&str>,
        java_home: Option<&str>,
        lib_dir: Option<&str>,
        mods_dir: Option<&str>,
        cache_dir: Option<&str>,
    ) -> Settings {
        let xmx_mib = xmx
            .and_then(|value| value.trim().parse::<u32>().ok())
            .unwrap_or(DEFAULT_XMX_MIB)
            .clamp(MIN_XMX_MIB, MAX_XMX_MIB);
        let base = lib_dir
            .map(PathBuf::from)
            .and_then(|dir| dir.parent().map(PathBuf::from))
            .or_else(|| {
                std::env::var_os("HOME")
                    .map(|home| PathBuf::from(home).join(".local/share/horizon"))
            })
            .unwrap_or_else(|| PathBuf::from("."));
        Settings {
            java_home: java_home.map(PathBuf::from),
            lib_dir: lib_dir.map(PathBuf::from),
            minecraft_roots: Vec::new(),
            xmx_mib,
            mods_dir: mods_dir
                .map(PathBuf::from)
                .unwrap_or_else(|| base.join("ferroloom-mods")),
            cache_dir: cache_dir
                .map(PathBuf::from)
                .unwrap_or_else(|| base.join("ferroloom-cache")),
        }
    }
}
