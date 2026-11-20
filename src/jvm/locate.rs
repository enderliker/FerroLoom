use std::path::{Path, PathBuf};

use super::release::parse_release;

/// The oldest Java FerroLoom runs on.
pub const MINIMUM_JAVA: u32 = 21;

#[derive(Clone, Debug, PartialEq, Eq)]
pub struct JavaVersion {
    pub major: u32,
    pub full: String,
}

#[derive(Clone, Debug, PartialEq, Eq)]
pub struct JavaHome {
    pub path: PathBuf,
    pub version: JavaVersion,
}

#[derive(Clone, Debug, PartialEq, Eq)]
pub enum JvmLocateError {
    NotFound { tried: Vec<PathBuf> },
    VersionTooOld { found: JavaVersion, minimum: u32 },
    Unreadable { path: PathBuf, reason: String },
}

impl std::fmt::Display for JvmLocateError {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            JvmLocateError::NotFound { tried } => {
                let paths = tried
                    .iter()
                    .map(|path| path.display().to_string())
                    .collect::<Vec<_>>()
                    .join(", ");
                write!(f, "no JDK found; tried {paths}")
            }
            JvmLocateError::VersionTooOld { found, minimum } => write!(
                f,
                "Java {} is too old; FerroLoom needs Java {minimum} or newer",
                found.full
            ),
            JvmLocateError::Unreadable { path, reason } => {
                write!(f, "{}: {reason}", path.display())
            }
        }
    }
}

impl std::error::Error for JvmLocateError {}

#[derive(Clone, Copy, Debug, Default)]
pub struct LocateSources<'a> {
    pub explicit: Option<&'a Path>,
    pub minecraft_roots: &'a [PathBuf],
    pub java_home: Option<&'a Path>,
}

/// A JDK home has `bin/java` and a readable `release` naming its version.
fn candidate(dir: &Path) -> Result<Option<JavaHome>, JvmLocateError> {
    if !dir.join("bin/java").is_file() {
        return Ok(None);
    }
    let release = match std::fs::read_to_string(dir.join("release")) {
        Ok(text) => text,
        Err(error) => {
            return Err(JvmLocateError::Unreadable {
                path: dir.to_path_buf(),
                reason: error.to_string(),
            })
        }
    };
    match parse_release(&release) {
        Some(version) => Ok(Some(JavaHome {
            path: dir.to_path_buf(),
            version,
        })),
        None => Err(JvmLocateError::Unreadable {
            path: dir.to_path_buf(),
            reason: "no JAVA_VERSION line in release".to_string(),
        }),
    }
}

fn check(home: JavaHome) -> Result<JavaHome, JvmLocateError> {
    if home.version.major < MINIMUM_JAVA {
        Err(JvmLocateError::VersionTooOld {
            found: home.version,
            minimum: MINIMUM_JAVA,
        })
    } else {
        Ok(home)
    }
}

/// Collects JDK homes under `root`, up to `depth` directory levels.
fn collect_under(root: &Path, depth: u32, found: &mut Vec<JavaHome>) {
    if depth == 0 {
        return;
    }
    let Ok(entries) = std::fs::read_dir(root) else {
        return;
    };
    for entry in entries.flatten() {
        let path = entry.path();
        if !path.is_dir() {
            continue;
        }
        if let Ok(Some(home)) = candidate(&path) {
            found.push(home);
        }
        collect_under(&path, depth - 1, found);
    }
}

pub fn locate(sources: LocateSources<'_>) -> Result<JavaHome, JvmLocateError> {
    let mut tried = Vec::new();

    if let Some(dir) = sources.explicit {
        tried.push(dir.to_path_buf());
        return match candidate(dir)? {
            Some(home) => check(home),
            None => Err(JvmLocateError::NotFound { tried }),
        };
    }

    let mut found = Vec::new();
    for root in sources.minecraft_roots {
        tried.push(root.clone());
        collect_under(root, 3, &mut found);
    }
    found.sort_by(|a, b| {
        b.version
            .major
            .cmp(&a.version.major)
            .then_with(|| b.version.full.cmp(&a.version.full))
    });

    if let Some(home) = found.into_iter().next() {
        match check(home) {
            Ok(home) => return Ok(home),
            Err(JvmLocateError::VersionTooOld { found, .. }) => {
                // A newer JAVA_HOME still beats an old Minecraft runtime.
                if let Some(dir) = sources.java_home {
                    tried.push(dir.to_path_buf());
                    if let Ok(Some(home)) = candidate(dir) {
                        if home.version.major >= MINIMUM_JAVA {
                            return Ok(home);
                        }
                    }
                }
                return Err(JvmLocateError::VersionTooOld {
                    found,
                    minimum: MINIMUM_JAVA,
                });
            }
            Err(error) => return Err(error),
        }
    }

    if let Some(dir) = sources.java_home {
        tried.push(dir.to_path_buf());
        if let Some(home) = candidate(dir)? {
            return check(home);
        }
    }

    Err(JvmLocateError::NotFound { tried })
}