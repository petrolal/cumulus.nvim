use regex::Regex;
use serde::{Deserialize, Serialize};
use std::collections::HashSet;
use std::fs;
use std::path::Path;

#[derive(Debug, Serialize, Deserialize, PartialEq, Eq)]
pub struct MigrationIssue {
    pub file: String,
    pub line: Option<usize>,
    pub severity: String,
    pub message: String,
}

pub fn validate_migrations_dir(dir_path: &Path) -> Vec<MigrationIssue> {
    let mut issues = Vec::new();
    if !dir_path.exists() {
        return issues;
    }

    let re_flyway = Regex::new(r"^(V|U)(\d+(?:\.\d+)*)__([A-Za-z0-9_]+)\.sql$").unwrap();
    let re_repeatable = Regex::new(r"^R__([A-Za-z0-9_]+)\.sql$").unwrap();

    let mut seen_versions = HashSet::new();

    if let Ok(entries) = fs::read_dir(dir_path) {
        for entry in entries.flatten() {
            let p = entry.path();
            if p.is_file() {
                let filename = p.file_name().and_then(|n| n.to_str()).unwrap_or("");
                if filename.ends_with(".sql") {
                    if let Some(caps) = re_flyway.captures(filename) {
                        let version = caps.get(2).unwrap().as_str().to_string();
                        if !seen_versions.insert(version.clone()) {
                            issues.push(MigrationIssue {
                                file: p.to_string_lossy().to_string(),
                                line: None,
                                severity: "ERROR".to_string(),
                                message: format!("Duplicate Flyway migration version: V{}", version),
                            });
                        }
                    } else if !re_repeatable.is_match(filename) {
                        issues.push(MigrationIssue {
                            file: p.to_string_lossy().to_string(),
                            line: None,
                            severity: "WARN".to_string(),
                            message: format!("File name '{}' does not match standard Flyway convention V<Ver>__<Desc>.sql", filename),
                        });
                    }
                }
            }
        }
    }

    issues
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_validate_migrations() {
        let dir = tempfile::tempdir().unwrap();
        let f1 = dir.path().join("V1__init.sql");
        let f2 = dir.path().join("V1__duplicate.sql");
        let f3 = dir.path().join("invalid_name.sql");

        fs::File::create(&f1).unwrap();
        fs::File::create(&f2).unwrap();
        fs::File::create(&f3).unwrap();

        let issues = validate_migrations_dir(dir.path());
        assert!(issues.iter().any(|i| i.message.contains("Duplicate Flyway")));
        assert!(issues.iter().any(|i| i.message.contains("does not match standard Flyway")));
    }
}
