use regex::Regex;
use serde::{Deserialize, Serialize};
use std::collections::HashMap;
use std::fs;
use std::path::{Path, PathBuf};

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
pub struct DependencyLens {
    pub group: String,
    pub artifact: String,
    pub current_version: String,
    pub latest_version: String,
    pub line: usize,
    pub age_status: String,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct VersionMetadata {
    pub group: String,
    pub artifact: String,
    pub latest_version: String,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct VersionCatalog {
    pub dependencies: Vec<VersionMetadata>,
    pub last_updated: i64,
}

/// Classify version age based on semantic versioning
fn classify_version_age(current: &str, latest: &str) -> String {
    let parse_version = |v: &str| -> Vec<u32> {
        v.split('.')
            .take(3)
            .map(|s| s.parse::<u32>().unwrap_or(0))
            .collect()
    };

    let current_parts = parse_version(current);
    let latest_parts = parse_version(latest);

    if current_parts.is_empty() || latest_parts.is_empty() {
        return "UNKNOWN".to_string();
    }

    if current_parts == latest_parts {
        "CURRENT".to_string()
    } else if current_parts[0] == latest_parts[0] {
        if current_parts.get(1) == latest_parts.get(1) {
            "PATCH_OUTDATED".to_string()
        } else {
            "MINOR_OUTDATED".to_string()
        }
    } else {
        "MAJOR_OUTDATED".to_string()
    }
}

/// Get or create version cache file
fn get_version_cache_path() -> PathBuf {
    let cache_dir = dirs::cache_dir()
        .unwrap_or_else(|| PathBuf::from("/tmp"))
        .join("nvim");
    let _ = fs::create_dir_all(&cache_dir);
    cache_dir.join("dependency-versions.json")
}

/// Load cached version metadata
fn load_version_cache() -> HashMap<String, String> {
    let cache_path = get_version_cache_path();
    if let Ok(content) = fs::read_to_string(&cache_path) {
        if let Ok(catalog) = serde_json::from_str::<VersionCatalog>(&content) {
            let mut map = HashMap::new();
            for dep in catalog.dependencies {
                let key = format!("{}:{}", dep.group, dep.artifact);
                map.insert(key, dep.latest_version);
            }
            return map;
        }
    }
    HashMap::new()
}

/// Parse dependencies from file with line numbers
pub fn check_dep_versions(file_path: &Path) -> Vec<DependencyLens> {
    let content = match fs::read_to_string(file_path) {
        Ok(c) => c,
        Err(_) => return Vec::new(),
    };

    let filename = file_path
        .file_name()
        .and_then(|n| n.to_str())
        .unwrap_or("");

    let mut results = if filename.ends_with(".toml") || content.contains("[libraries]") || content.contains("[versions]") {
        parse_gradle_versions_with_lines(&content)
    } else {
        parse_pom_with_lines(&content)
    };

    let version_cache = load_version_cache();
    for lens in &mut results {
        let cache_key = format!("{}:{}", lens.group, lens.artifact);
        if let Some(latest) = version_cache.get(&cache_key) {
            lens.latest_version = latest.clone();
            lens.age_status = classify_version_age(&lens.current_version, latest);
        } else {
            lens.age_status = "UNKNOWN".to_string();
        }
    }

    results
}

/// Parse POM XML with 1-indexed line numbers
fn parse_pom_with_lines(content: &str) -> Vec<DependencyLens> {
    let mut properties: HashMap<String, String> = HashMap::new();
    let mut results = Vec::new();

    let lines: Vec<&str> = content.lines().collect();

    // Extract properties - use simple pattern without backreferences
    let prop_re = Regex::new(r"<([a-zA-Z0-9_\-\.]+)>([^<]+)</([a-zA-Z0-9_\-\.]+)>").unwrap();
    for line in &lines {
        if line.contains("<properties>") || line.contains("</properties>") {
            continue;
        }
        if let Some(caps) = prop_re.captures(line) {
            if caps[1] == caps[3] {
                properties.insert(caps[1].to_string(), caps[2].trim().to_string());
            }
        }
    }

    // Parse dependencies
    let dep_re = Regex::new(r"<groupId>\s*([^<]+?)\s*</groupId>").unwrap();
    let artifact_re = Regex::new(r"<artifactId>\s*([^<]+?)\s*</artifactId>").unwrap();
    let version_re = Regex::new(r"<version>\s*([^<]+?)\s*</version>").unwrap();

    let resolve_prop = |val: &str| -> String {
        let mut result = val.to_string();
        for (k, v) in &properties {
            let placeholder = format!("${{{}}}", k);
            if result.contains(&placeholder) {
                result = result.replace(&placeholder, v);
            }
        }
        result
    };

    for (idx, line) in lines.iter().enumerate() {
        if line.contains("<version>") && line.contains("</version>") {
            // Try to extract version from this line
            if let Some(caps) = version_re.captures(line) {
                let version = resolve_prop(caps[1].trim());

                // Look backwards for groupId and artifactId
                let mut group = String::new();
                let mut artifact = String::new();

                for j in (0..=idx).rev() {
                    if group.is_empty() {
                        if let Some(caps) = dep_re.captures(&lines[j]) {
                            group = resolve_prop(caps[1].trim());
                        }
                    }
                    if artifact.is_empty() {
                        if let Some(caps) = artifact_re.captures(&lines[j]) {
                            artifact = resolve_prop(caps[1].trim());
                        }
                    }
                    if !group.is_empty() && !artifact.is_empty() {
                        break;
                    }
                }

                if !group.is_empty() && !artifact.is_empty() {
                    results.push(DependencyLens {
                        group,
                        artifact,
                        current_version: version,
                        latest_version: "unknown".to_string(),
                        line: idx + 1, // 1-indexed
                        age_status: "UNKNOWN".to_string(),
                    });
                }
            }
        }
    }

    results
}

/// Parse Gradle version catalog with 1-indexed line numbers
fn parse_gradle_versions_with_lines(content: &str) -> Vec<DependencyLens> {
    let mut results = Vec::new();
    let lines: Vec<&str> = content.lines().collect();

    let version_pattern = Regex::new(r#"(\w+)\s*=\s*"([^"]+)""#).unwrap();
    let dep_pattern = Regex::new(r#"module\("([^:]+):([^"]+)"\)"#).unwrap();

    for (idx, line) in lines.iter().enumerate() {
        if line.contains("module(") || (line.contains("=") && line.contains("\"")) {
            if let Some(caps) = dep_pattern.captures(line) {
                let group = caps[1].to_string();
                let artifact = caps[2].to_string();

                // Extract version from same line or nearby lines
                let mut version = "unknown".to_string();
                if let Some(caps_v) = version_pattern.captures(line) {
                    version = caps_v[2].to_string();
                }

                results.push(DependencyLens {
                    group,
                    artifact,
                    current_version: version,
                    latest_version: "unknown".to_string(),
                    line: idx + 1, // 1-indexed
                    age_status: "UNKNOWN".to_string(),
                });
            }
        }
    }

    results
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_classify_version_current() {
        assert_eq!(classify_version_age("3.2.5", "3.2.5"), "CURRENT");
    }

    #[test]
    fn test_classify_version_patch_outdated() {
        assert_eq!(classify_version_age("3.2.0", "3.2.5"), "PATCH_OUTDATED");
    }

    #[test]
    fn test_classify_version_minor_outdated() {
        assert_eq!(classify_version_age("3.1.0", "3.2.5"), "MINOR_OUTDATED");
    }

    #[test]
    fn test_classify_version_major_outdated() {
        assert_eq!(classify_version_age("2.5.0", "3.2.5"), "MAJOR_OUTDATED");
    }

    #[test]
    fn test_parse_pom_with_lines() {
        let pom_content = r#"
<?xml version="1.0" encoding="UTF-8"?>
<project>
  <dependencies>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-web</artifactId>
      <version>3.1.0</version>
    </dependency>
  </dependencies>
</project>
"#;
        let results = parse_pom_with_lines(pom_content);
        assert_eq!(results.len(), 1);
        assert_eq!(results[0].group, "org.springframework.boot");
        assert_eq!(results[0].artifact, "spring-boot-starter-web");
        assert_eq!(results[0].current_version, "3.1.0");
        assert!(results[0].line > 0);
    }

    #[test]
    fn test_parse_gradle_versions_with_lines() {
        let gradle_content = r#"[versions]
spring = "3.1.0"

[libraries]
module("org.springframework.boot:spring-boot-starter-web")
"#;
        let results = parse_gradle_versions_with_lines(gradle_content);
        assert!(!results.is_empty());
        assert_eq!(results[0].group, "org.springframework.boot");
        assert_eq!(results[0].artifact, "spring-boot-starter-web");
    }
}
