use serde::Serialize;
use std::fs;
use std::path::PathBuf;
use std::time::SystemTime;

#[derive(Debug, Serialize, Clone)]
pub struct SyncStatus {
    pub sync_needed: bool,
    pub modified_file: Option<String>,
}

/// Check if JDTLS classpath is stale by comparing build config mtime against start_time.
/// Scans for pom.xml, build.gradle, build.gradle.kts, settings.gradle, settings.gradle.kts,
/// and gradle/libs.versions.toml in the given directory.
pub fn check_jdtls_sync(dir: &PathBuf, start_time: u64) -> SyncStatus {
    let build_files = vec![
        "pom.xml",
        "build.gradle",
        "build.gradle.kts",
        "settings.gradle",
        "settings.gradle.kts",
    ];

    let gradle_libs = dir.join("gradle/libs.versions.toml");

    // Check standard build files
    for file_name in build_files {
        let file_path = dir.join(file_name);
        if file_path.exists() {
            if let Ok(metadata) = fs::metadata(&file_path) {
                if let Ok(modified) = metadata.modified() {
                    if let Ok(duration) = modified.duration_since(SystemTime::UNIX_EPOCH) {
                        let file_mtime = duration.as_secs();
                        if file_mtime > start_time {
                            return SyncStatus {
                                sync_needed: true,
                                modified_file: Some(file_name.to_string()),
                            };
                        }
                    }
                }
            }
        }
    }

    // Check gradle/libs.versions.toml specifically
    if gradle_libs.exists() {
        if let Ok(metadata) = fs::metadata(&gradle_libs) {
            if let Ok(modified) = metadata.modified() {
                if let Ok(duration) = modified.duration_since(SystemTime::UNIX_EPOCH) {
                    let file_mtime = duration.as_secs();
                    if file_mtime > start_time {
                        return SyncStatus {
                            sync_needed: true,
                            modified_file: Some("gradle/libs.versions.toml".to_string()),
                        };
                    }
                }
            }
        }
    }

    SyncStatus {
        sync_needed: false,
        modified_file: None,
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::fs::File;
    use std::io::Write;
    use std::time::SystemTime;
    use tempfile::TempDir;

    #[test]
    fn test_no_sync_needed_when_files_older_than_start_time() {
        let temp_dir = TempDir::new().unwrap();
        let temp_path = temp_dir.path().to_path_buf();

        // Create a pom.xml with old mtime
        let pom_path = temp_path.join("pom.xml");
        File::create(&pom_path).unwrap();

        // Set start_time to now (1 second in future from file creation)
        let start_time = SystemTime::now()
            .duration_since(SystemTime::UNIX_EPOCH)
            .unwrap()
            .as_secs()
            + 1;

        let status = check_jdtls_sync(&temp_path, start_time);
        assert!(!status.sync_needed);
        assert!(status.modified_file.is_none());
    }

    #[test]
    fn test_sync_needed_when_pom_modified_after_start_time() {
        let temp_dir = TempDir::new().unwrap();
        let temp_path = temp_dir.path().to_path_buf();

        // Set start_time to the past (2 seconds ago)
        let start_time = SystemTime::now()
            .duration_since(SystemTime::UNIX_EPOCH)
            .unwrap()
            .as_secs()
            - 2;

        // Create pom.xml (which will have current mtime)
        let pom_path = temp_path.join("pom.xml");
        File::create(&pom_path).unwrap();

        let status = check_jdtls_sync(&temp_path, start_time);
        assert!(status.sync_needed);
        assert_eq!(status.modified_file, Some("pom.xml".to_string()));
    }

    #[test]
    fn test_sync_needed_when_gradle_modified() {
        let temp_dir = TempDir::new().unwrap();
        let temp_path = temp_dir.path().to_path_buf();

        let start_time = SystemTime::now()
            .duration_since(SystemTime::UNIX_EPOCH)
            .unwrap()
            .as_secs()
            - 2;

        let gradle_path = temp_path.join("build.gradle");
        File::create(&gradle_path).unwrap();

        let status = check_jdtls_sync(&temp_path, start_time);
        assert!(status.sync_needed);
        assert_eq!(status.modified_file, Some("build.gradle".to_string()));
    }

    #[test]
    fn test_sync_needed_gradle_libs_versions() {
        let temp_dir = TempDir::new().unwrap();
        let temp_path = temp_dir.path().to_path_buf();

        // Create gradle directory
        let gradle_dir = temp_path.join("gradle");
        fs::create_dir_all(&gradle_dir).unwrap();

        let start_time = SystemTime::now()
            .duration_since(SystemTime::UNIX_EPOCH)
            .unwrap()
            .as_secs()
            - 2;

        let libs_path = gradle_dir.join("libs.versions.toml");
        let mut file = File::create(&libs_path).unwrap();
        writeln!(file, "[versions]").unwrap();

        let status = check_jdtls_sync(&temp_path, start_time);
        assert!(status.sync_needed);
        assert_eq!(status.modified_file, Some("gradle/libs.versions.toml".to_string()));
    }

    #[test]
    fn test_no_files_no_sync_needed() {
        let temp_dir = TempDir::new().unwrap();
        let temp_path = temp_dir.path().to_path_buf();

        let start_time = SystemTime::now()
            .duration_since(SystemTime::UNIX_EPOCH)
            .unwrap()
            .as_secs();

        let status = check_jdtls_sync(&temp_path, start_time);
        assert!(!status.sync_needed);
        assert!(status.modified_file.is_none());
    }
}
