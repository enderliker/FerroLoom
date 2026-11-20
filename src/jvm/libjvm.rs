use std::path::{Path, PathBuf};

#[derive(Debug)]
pub enum LibjvmError {
    NotFound { tried: Vec<PathBuf> },
    LoadFailed { path: PathBuf, reason: String },
}

impl std::fmt::Display for LibjvmError {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            LibjvmError::NotFound { tried } => {
                let paths = tried
                    .iter()
                    .map(|path| path.display().to_string())
                    .collect::<Vec<_>>()
                    .join(", ");
                write!(f, "libjvm not found; tried {paths}")
            }
            LibjvmError::LoadFailed { path, reason } => {
                write!(f, "could not load {}: {reason}", path.display())
            }
        }
    }
}

impl std::error::Error for LibjvmError {}

const CANDIDATES: &[&str] = &[
    "lib/server/libjvm.so",
    "lib/libjvm.so",
    "jre/lib/amd64/server/libjvm.so",
    "bin/server/jvm.dll",
    "lib/server/libjvm.dylib",
    "lib/libjvm.dylib",
];

pub fn find_libjvm(java_home: &Path) -> Result<PathBuf, LibjvmError> {
    let mut tried = Vec::new();
    for candidate in CANDIDATES {
        let path = java_home.join(candidate);
        if path.is_file() {
            return Ok(path);
        }
        tried.push(path);
    }
    Err(LibjvmError::NotFound { tried })
}

/// # Safety
/// Loads a shared library from `path`. The caller must keep the returned `Library` alive for as
/// long as anything obtained from it is used.
pub unsafe fn load(path: &Path) -> Result<libloading::Library, LibjvmError> {
    unsafe { libloading::Library::new(path) }.map_err(|error| LibjvmError::LoadFailed {
        path: path.to_path_buf(),
        reason: error.to_string(),
    })
}
