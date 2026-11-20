use super::locate::JavaVersion;

/// Parses the `JAVA_VERSION="…"` line of a JDK's `release` file.
pub fn parse_release(text: &str) -> Option<JavaVersion> {
    let line = text
        .lines()
        .find(|line| line.starts_with("JAVA_VERSION="))?;
    let value = line.split_once('=')?.1.trim().trim_matches('"');
    let major = value.split(['.', '_', '+', '-']).next()?.parse().ok()?;
    Some(JavaVersion {
        major,
        full: value.to_string(),
    })
}
