use regex::Regex;
use serde::{Deserialize, Serialize};
use std::collections::HashMap;
use std::fs;
use std::path::Path;

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
pub struct DependencyInfo {
    pub group: String,
    pub artifact: String,
    pub version: String,
    pub scope: String,
}

pub fn resolve_dependencies(file_path: &Path) -> Vec<DependencyInfo> {
    let content = match fs::read_to_string(file_path) {
        Ok(c) => c,
        Err(_) => return Vec::new(),
    };

    let filename = file_path
        .file_name()
        .and_then(|n| n.to_str())
        .unwrap_or("");

    if filename.ends_with(".toml") || content.contains("[libraries]") || content.contains("[versions]") {
        parse_version_catalog(&content)
    } else {
        parse_pom_dependencies(&content)
    }
}

fn parse_pom_dependencies(content: &str) -> Vec<DependencyInfo> {
    let mut properties = HashMap::new();
    let prop_re = Regex::new(r"<([a-zA-Z0-9_\-\.]+)>([^<]+)</([a-zA-Z0-9_\-\.]+)>").unwrap();

    // Extract properties block if present
    if let Some(prop_start) = content.find("<properties>") {
        if let Some(prop_end) = content[prop_start..].find("</properties>") {
            let prop_block = &content[prop_start..prop_start + prop_end];
            for cap in prop_re.captures_iter(prop_block) {
                if cap[1] == cap[3] {
                    properties.insert(cap[1].to_string(), cap[2].trim().to_string());
                }
            }
        }
    }

    let mut dependencies = Vec::new();
    let dep_re = Regex::new(r"(?s)<dependency>(.*?)</dependency>").unwrap();
    let group_re = Regex::new(r"<groupId>\s*([^<]+?)\s*</groupId>").unwrap();
    let artifact_re = Regex::new(r"<artifactId>\s*([^<]+?)\s*</artifactId>").unwrap();
    let version_re = Regex::new(r"<version>\s*([^<]+?)\s*</version>").unwrap();
    let scope_re = Regex::new(r"<scope>\s*([^<]+?)\s*</scope>").unwrap();

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

    for cap in dep_re.captures_iter(content) {
        let block = &cap[1];

        let group = group_re
            .captures(block)
            .map(|c| resolve_prop(c[1].trim()))
            .unwrap_or_default();
        let artifact = artifact_re
            .captures(block)
            .map(|c| resolve_prop(c[1].trim()))
            .unwrap_or_default();
        let version = version_re
            .captures(block)
            .map(|c| resolve_prop(c[1].trim()))
            .unwrap_or_else(|| "inherited".to_string());
        let scope = scope_re
            .captures(block)
            .map(|c| c[1].trim().to_string())
            .unwrap_or_else(|| "compile".to_string());

        if !group.is_empty() && !artifact.is_empty() {
            dependencies.push(DependencyInfo {
                group,
                artifact,
                version,
                scope,
            });
        }
    }

    dependencies
}

fn parse_version_catalog(content: &str) -> Vec<DependencyInfo> {
    let mut versions = HashMap::new();
    let mut dependencies = Vec::new();

    let mut in_versions = false;
    let mut in_libraries = false;

    let var_re = Regex::new(r#"^\s*([a-zA-Z0-9_\-\.]+)\s*=\s*"([^"]+)""#).unwrap();
    let lib_string_re = Regex::new(r#"^\s*([a-zA-Z0-9_\-\.]+)\s*=\s*"([^":]+):([^":]+):([^"]+)""#).unwrap();
    let lib_module_re = Regex::new(r#"module\s*=\s*"([^":]+):([^"]+)""#).unwrap();
    let lib_version_re = Regex::new(r#"version\s*=\s*"([^"]+)""#).unwrap();
    let lib_version_ref_re = Regex::new(r#"version\.ref\s*=\s*"([^"]+)""#).unwrap();
    let lib_group_re = Regex::new(r#"group\s*=\s*"([^"]+)""#).unwrap();
    let lib_name_re = Regex::new(r#"name\s*=\s*"([^"]+)""#).unwrap();

    for line in content.lines() {
        let line_trimmed = line.trim();
        if line_trimmed.starts_with('#') || line_trimmed.is_empty() {
            continue;
        }

        if line_trimmed == "[versions]" {
            in_versions = true;
            in_libraries = false;
            continue;
        } else if line_trimmed == "[libraries]" {
            in_versions = false;
            in_libraries = true;
            continue;
        } else if line_trimmed.starts_with('[') {
            in_versions = false;
            in_libraries = false;
            continue;
        }

        if in_versions {
            if let Some(cap) = var_re.captures(line_trimmed) {
                versions.insert(cap[1].to_string(), cap[2].to_string());
            }
        } else if in_libraries {
            if let Some(cap) = lib_string_re.captures(line_trimmed) {
                dependencies.push(DependencyInfo {
                    group: cap[2].to_string(),
                    artifact: cap[3].to_string(),
                    version: cap[4].to_string(),
                    scope: "implementation".to_string(),
                });
            } else if line_trimmed.contains('{') {
                let group = if let Some(m) = lib_module_re.captures(line_trimmed) {
                    m[1].to_string()
                } else if let Some(g) = lib_group_re.captures(line_trimmed) {
                    g[1].to_string()
                } else {
                    String::new()
                };

                let artifact = if let Some(m) = lib_module_re.captures(line_trimmed) {
                    m[2].to_string()
                } else if let Some(n) = lib_name_re.captures(line_trimmed) {
                    n[1].to_string()
                } else {
                    String::new()
                };

                let version = if let Some(vr) = lib_version_ref_re.captures(line_trimmed) {
                    versions.get(&vr[1]).cloned().unwrap_or_else(|| vr[1].to_string())
                } else if let Some(v) = lib_version_re.captures(line_trimmed) {
                    v[1].to_string()
                } else {
                    "latest".to_string()
                };

                if !group.is_empty() && !artifact.is_empty() {
                    dependencies.push(DependencyInfo {
                        group,
                        artifact,
                        version,
                        scope: "implementation".to_string(),
                    });
                }
            }
        }
    }

    dependencies
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_parse_pom_dependencies() {
        let xml = r#"
<project>
    <properties>
        <spring.version>3.2.0</spring.version>
    </properties>
    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
            <version>${spring.version}</version>
        </dependency>
        <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter-api</artifactId>
            <version>5.10.0</version>
            <scope>test</scope>
        </dependency>
    </dependencies>
</project>
        "#;

        let deps = parse_pom_dependencies(xml);
        assert_eq!(deps.len(), 2);
        assert_eq!(deps[0].group, "org.springframework.boot");
        assert_eq!(deps[0].artifact, "spring-boot-starter-web");
        assert_eq!(deps[0].version, "3.2.0");
        assert_eq!(deps[0].scope, "compile");

        assert_eq!(deps[1].group, "org.junit.jupiter");
        assert_eq!(deps[1].scope, "test");
    }

    #[test]
    fn test_parse_version_catalog() {
        let toml = r#"
[versions]
groovy = "3.0.5"

[libraries]
groovy-core = "org.codehaus.groovy:groovy:3.0.5"
groovy-json = { module = "org.codehaus.groovy:groovy-json", version.ref = "groovy" }
groovy-nio = { group = "org.codehaus.groovy", name = "groovy-nio", version = "3.0.5" }
        "#;

        let deps = parse_version_catalog(toml);
        assert_eq!(deps.len(), 3);
        assert_eq!(deps[0].group, "org.codehaus.groovy");
        assert_eq!(deps[0].artifact, "groovy");
        assert_eq!(deps[0].version, "3.0.5");

        assert_eq!(deps[1].artifact, "groovy-json");
        assert_eq!(deps[1].version, "3.0.5");

        assert_eq!(deps[2].artifact, "groovy-nio");
        assert_eq!(deps[2].version, "3.0.5");
    }
}
