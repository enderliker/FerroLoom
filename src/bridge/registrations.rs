use std::path::{Path, PathBuf};

/// What the Java loader asked the engine to register while the mods' entrypoints ran.
#[derive(Debug, Default, Clone, PartialEq, Eq)]
pub struct PendingRegistrations {
    pub keys: Vec<KeyRequest>,
    pub commands: Vec<CommandRequest>,
    pub hud: bool,
    pub logs: Vec<String>,
    pub failed: Vec<String>,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct KeyRequest {
    pub mod_id: String,
    pub key_id: String,
    pub default_key: String,
}

impl KeyRequest {
    /// One line for `ferroloom.log`, so the key a mod registered is visible without Controls.
    pub fn describe(&self) -> String {
        format!(
            "key: {}/{} -> {}",
            self.mod_id, self.key_id, self.default_key
        )
    }
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct CommandRequest {
    pub mod_id: String,
    pub name: String,
}

impl PendingRegistrations {
    /// Parses the loader's tab-separated report; unknown lines are ignored.
    pub fn parse(report: &str) -> PendingRegistrations {
        let mut pending = PendingRegistrations::default();
        for line in report.lines() {
            let mut fields = line.split('\t');
            match fields.next() {
                Some("KEY") => {
                    let (Some(mod_id), Some(key_id), Some(default_key)) =
                        (fields.next(), fields.next(), fields.next())
                    else {
                        continue;
                    };
                    pending.keys.push(KeyRequest {
                        mod_id: mod_id.to_string(),
                        key_id: key_id.to_string(),
                        default_key: default_key.to_string(),
                    });
                }
                Some("CMD") => {
                    let (Some(mod_id), Some(name)) = (fields.next(), fields.next()) else {
                        continue;
                    };
                    pending.commands.push(CommandRequest {
                        mod_id: mod_id.to_string(),
                        name: name.to_string(),
                    });
                }
                Some("HUD") => pending.hud = true,
                Some("FAILED") => {
                    if let Some(message) = fields.next() {
                        pending.failed.push(message.to_string());
                    }
                }
                Some("LOG") => {
                    if let Some(message) = fields.next() {
                        pending.logs.push(message.to_string());
                    }
                }
                _ => {}
            }
        }
        pending
    }
}

/// Every jar the JVM must see, in a stable order: loader, its libraries, the shim and ours.
pub fn classpath(lib_dir: &Path) -> Result<Vec<PathBuf>, String> {
    let entries = std::fs::read_dir(lib_dir)
        .map_err(|error| format!("could not read {}: {error}", lib_dir.display()))?;
    let mut jars: Vec<PathBuf> = entries
        .filter_map(Result::ok)
        .map(|entry| entry.path())
        .filter(|path| path.extension().is_some_and(|ext| ext == "jar"))
        .collect();
    jars.sort();
    if jars.is_empty() {
        return Err(format!("no jars in {}", lib_dir.display()));
    }
    Ok(jars)
}
