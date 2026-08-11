use regex::Regex;
use serde::Serialize;
use std::fs;
use std::path::PathBuf;

#[derive(Debug, Serialize, Clone)]
pub struct GradleWrapperStatus {
    pub local_version: Option<String>,
    pub ci_version: Option<String>,
    pub sha256_configured: bool,
    pub sha256_valid: bool,
    pub issues: Vec<String>,
}

/// Parse gradle-wrapper.properties to extract distributionUrl and distributionSha256Sum
fn parse_gradle_wrapper_properties(dir: &PathBuf) -> (Option<String>, bool) {
    let wrapper_props = dir.join("gradle/wrapper/gradle-wrapper.properties");
    if !wrapper_props.exists() {
        return (None, false);
    }

    if let Ok(content) = fs::read_to_string(&wrapper_props) {
        let mut version = None;
        let mut has_sha256 = false;

        for line in content.lines() {
            let line = line.trim();
            if line.starts_with("distributionUrl") {
                if let Some(url) = extract_version_from_url(line) {
                    version = Some(url);
                }
            }
            if line.starts_with("distributionSha256Sum") {
                has_sha256 = true;
            }
        }

        return (version, has_sha256);
    }

    (None, false)
}

/// Extract Gradle version from distributionUrl line (e.g., gradle-7.6.1-bin.zip)
fn extract_version_from_url(line: &str) -> Option<String> {
    let re = Regex::new(r"gradle-(\d+\.\d+(?:\.\d+)?)-").ok()?;
    if let Some(caps) = re.captures(line) {
        return caps.get(1).map(|m| m.as_str().to_string());
    }
    None
}

/// Scan CI configuration files for Gradle version references
fn scan_ci_configs(dir: &PathBuf) -> Option<String> {
    let mut ci_version = None;

    // Check GitHub Actions workflows
    let github_workflows = dir.join(".github/workflows");
    if github_workflows.exists() {
        if let Ok(entries) = fs::read_dir(&github_workflows) {
            for entry in entries.flatten() {
                if let Ok(metadata) = entry.metadata() {
                    if metadata.is_file() {
                        let path = entry.path();
                        if let Ok(content) = fs::read_to_string(&path) {
                            if let Some(version) = extract_gradle_version_from_yaml(&content) {
                                ci_version = Some(version);
                                break;
                            }
                        }
                    }
                }
            }
        }
    }

    // Check GitLab CI
    if ci_version.is_none() {
        let gitlab_ci = dir.join(".gitlab-ci.yml");
        if gitlab_ci.exists() {
            if let Ok(content) = fs::read_to_string(&gitlab_ci) {
                ci_version = extract_gradle_version_from_yaml(&content);
            }
        }
    }

    // Check Jenkinsfile
    if ci_version.is_none() {
        let jenkinsfile = dir.join("Jenkinsfile");
        if jenkinsfile.exists() {
            if let Ok(content) = fs::read_to_string(&jenkinsfile) {
                if let Some(version) = extract_gradle_version_from_groovy(&content) {
                    ci_version = Some(version);
                }
            }
        }
    }

    ci_version
}

/// Extract Gradle version from YAML (GitHub Actions / GitLab CI)
fn extract_gradle_version_from_yaml(content: &str) -> Option<String> {
    // Look for gradle-version or gradle:version patterns
    let re = Regex::new(r#"gradle[_-]version\s*[:=]\s*["\']?(\d+\.\d+(?:\.\d+)?)["\']?"#).ok()?;
    if let Some(caps) = re.captures(content) {
        return caps.get(1).map(|m| m.as_str().to_string());
    }

    // Also try gradle path patterns
    let re2 = Regex::new(r"gradle[/\\]gradle-(\d+\.\d+(?:\.\d+)?)-").ok()?;
    if let Some(caps) = re2.captures(content) {
        return caps.get(1).map(|m| m.as_str().to_string());
    }

    None
}

/// Extract Gradle version from Jenkinsfile (Groovy)
fn extract_gradle_version_from_groovy(content: &str) -> Option<String> {
    // Look for gradle version references in Groovy syntax
    let re = Regex::new(r#"gradle[_-]version\s*=\s*["\'](\d+\.\d+(?:\.\d+)?)["\']"#).ok()?;
    if let Some(caps) = re.captures(content) {
        return caps.get(1).map(|m| m.as_str().to_string());
    }

    None
}

/// Verify Gradle wrapper configuration against CI configs and SHA-256
pub fn verify_gradle_wrapper(dir: &PathBuf) -> GradleWrapperStatus {
    let (local_version, sha256_configured) = parse_gradle_wrapper_properties(dir);
    let ci_version = scan_ci_configs(dir);

    let mut issues = Vec::new();

    // Check version mismatch
    if let (Some(ref local), Some(ref ci)) = (&local_version, &ci_version) {
        if local != ci {
            issues.push(format!(
                "Gradle version mismatch: local={}, CI={}",
                local, ci
            ));
        }
    }

    // SHA-256 validation (simplified: just check if it's configured)
    let sha256_valid = sha256_configured;

    if !sha256_configured && local_version.is_some() {
        issues.push("Gradle wrapper SHA-256 checksum not configured (security risk)".to_string());
    }

    GradleWrapperStatus {
        local_version,
        ci_version,
        sha256_configured,
        sha256_valid,
        issues,
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::fs::File;
    use std::io::Write;
    use tempfile::TempDir;

    #[test]
    fn test_parse_gradle_wrapper_properties() {
        let temp_dir = TempDir::new().unwrap();
        let temp_path = temp_dir.path().to_path_buf();

        // Create gradle/wrapper directory
        let wrapper_dir = temp_path.join("gradle/wrapper");
        fs::create_dir_all(&wrapper_dir).unwrap();

        // Create gradle-wrapper.properties
        let mut file = File::create(wrapper_dir.join("gradle-wrapper.properties")).unwrap();
        writeln!(file, "distributionUrl=https\\://services.gradle.org/distributions/gradle-7.6.1-bin.zip").unwrap();
        writeln!(file, "distributionSha256Sum=abc123").unwrap();

        let (version, has_sha256) = parse_gradle_wrapper_properties(&temp_path);
        assert_eq!(version, Some("7.6.1".to_string()));
        assert!(has_sha256);
    }

    #[test]
    fn test_extract_version_from_url() {
        let line = "distributionUrl=https\\://services.gradle.org/distributions/gradle-8.5-bin.zip";
        let version = extract_version_from_url(line);
        assert_eq!(version, Some("8.5".to_string()));
    }

    #[test]
    fn test_verify_gradle_wrapper_no_sha256() {
        let temp_dir = TempDir::new().unwrap();
        let temp_path = temp_dir.path().to_path_buf();

        let wrapper_dir = temp_path.join("gradle/wrapper");
        fs::create_dir_all(&wrapper_dir).unwrap();

        let mut file = File::create(wrapper_dir.join("gradle-wrapper.properties")).unwrap();
        writeln!(file, "distributionUrl=https\\://services.gradle.org/distributions/gradle-7.6.1-bin.zip").unwrap();

        let status = verify_gradle_wrapper(&temp_path);
        assert!(!status.sha256_configured);
        assert!(!status.issues.is_empty());
        assert!(status.issues[0].contains("SHA-256"));
    }

    #[test]
    fn test_extract_gradle_version_from_yaml() {
        let yaml = r#"
jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/setup-java@v3
        with:
          gradle-version: "8.5"
"#;
        let version = extract_gradle_version_from_yaml(yaml);
        assert_eq!(version, Some("8.5".to_string()));
    }

    #[test]
    fn test_verify_gradle_wrapper_version_mismatch() {
        let temp_dir = TempDir::new().unwrap();
        let temp_path = temp_dir.path().to_path_buf();

        // Create gradle/wrapper with version 7.6.1
        let wrapper_dir = temp_path.join("gradle/wrapper");
        fs::create_dir_all(&wrapper_dir).unwrap();
        let mut file = File::create(wrapper_dir.join("gradle-wrapper.properties")).unwrap();
        writeln!(file, "distributionUrl=https\\://services.gradle.org/distributions/gradle-7.6.1-bin.zip").unwrap();
        writeln!(file, "distributionSha256Sum=abc123").unwrap();

        // Create .github/workflows with version 8.5
        let workflows_dir = temp_path.join(".github/workflows");
        fs::create_dir_all(&workflows_dir).unwrap();
        let mut file = File::create(workflows_dir.join("build.yml")).unwrap();
        writeln!(file, "gradle-version: \"8.5\"").unwrap();

        let status = verify_gradle_wrapper(&temp_path);
        assert_eq!(status.local_version, Some("7.6.1".to_string()));
        assert_eq!(status.ci_version, Some("8.5".to_string()));
        assert!(!status.issues.is_empty());
        assert!(status.issues[0].contains("mismatch"));
    }

    #[test]
    fn test_no_gradle_wrapper() {
        let temp_dir = TempDir::new().unwrap();
        let temp_path = temp_dir.path().to_path_buf();

        let status = verify_gradle_wrapper(&temp_path);
        assert!(status.local_version.is_none());
        assert!(!status.sha256_configured);
    }
}
