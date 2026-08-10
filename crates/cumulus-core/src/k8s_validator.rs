use serde::{Deserialize, Serialize};

#[derive(Debug, Serialize, Deserialize, PartialEq, Eq)]
pub struct K8sValidationIssue {
    pub line: usize,
    pub col: Option<usize>,
    pub message: String,
    pub severity: String,
}

pub fn validate_k8s_manifest_content(content: &str) -> Vec<K8sValidationIssue> {
    let mut issues = Vec::new();
    let mut has_api_version = false;
    let mut has_kind = false;

    for line in content.lines() {
        let trimmed = line.trim();
        if trimmed.starts_with("apiVersion:") {
            has_api_version = true;
        } else if trimmed.starts_with("kind:") {
            has_kind = true;
        }
    }

    if !has_api_version && content.contains("kind:") {
        issues.push(K8sValidationIssue {
            line: 1,
            col: None,
            message: "Missing top-level 'apiVersion' field in Kubernetes manifest".to_string(),
            severity: "ERROR".to_string(),
        });
    }

    if !has_kind && content.contains("apiVersion:") {
        issues.push(K8sValidationIssue {
            line: 1,
            col: None,
            message: "Missing top-level 'kind' field in Kubernetes manifest".to_string(),
            severity: "ERROR".to_string(),
        });
    }

    issues
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_validate_k8s_manifest() {
        let yaml = r#"
kind: Deployment
metadata:
  name: nginx
"#;
        let issues = validate_k8s_manifest_content(yaml);
        assert_eq!(issues.len(), 1);
        assert!(issues[0].message.contains("apiVersion"));
    }
}
