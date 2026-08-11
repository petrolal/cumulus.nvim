use regex::Regex;
use serde::{Deserialize, Serialize};
use std::fs;
use std::path::Path;

#[derive(Debug, Serialize, Deserialize, Clone)]
pub struct SpringBootConfig {
    pub main_class: String,
    pub project_name: String,
    pub build_tool: String,
    pub jvm_args: String,
    pub profiles: Vec<String>,
}

fn detect_build_tool(dir: &Path) -> String {
    if dir.join("pom.xml").exists() {
        "maven".to_string()
    } else if dir.join("build.gradle").exists() || dir.join("build.gradle.kts").exists() {
        "gradle".to_string()
    } else {
        "unknown".to_string()
    }
}

fn extract_main_class_from_file(filepath: &str) -> Option<(String, String)> {
    let content = fs::read_to_string(filepath).ok()?;

    let re_package = Regex::new(r"^package\s+([a-zA-Z0-9_.]+)\s*;").unwrap();
    let re_springboot = Regex::new(r"@(SpringBootApplication|EnableAutoConfiguration)").unwrap();
    let re_class = Regex::new(r"(?:public\s+)?class\s+([A-Za-z0-9_]+)").unwrap();

    let mut package_name = String::new();
    let mut is_springboot = false;
    let mut class_name = String::new();

    for line in content.lines() {
        let trimmed = line.trim();

        if package_name.is_empty() {
            if let Some(caps) = re_package.captures(trimmed) {
                if let Some(m) = caps.get(1) {
                    package_name = m.as_str().to_string();
                }
            }
        }

        if !is_springboot && re_springboot.is_match(trimmed) {
            is_springboot = true;
        }

        if is_springboot && class_name.is_empty() {
            if let Some(caps) = re_class.captures(trimmed) {
                if let Some(m) = caps.get(1) {
                    class_name = m.as_str().to_string();
                }
            }
        }

        if !package_name.is_empty() && !class_name.is_empty() && is_springboot {
            break;
        }
    }

    if is_springboot && !package_name.is_empty() && !class_name.is_empty() {
        let full_class_name = format!("{}.{}", package_name, class_name);
        Some((full_class_name, class_name))
    } else {
        None
    }
}

fn find_springboot_main_class(dir: &Path) -> Option<String> {
    if !dir.exists() {
        return None;
    }

    fn walk_dir(path: &Path) -> Option<String> {
        if let Ok(entries) = fs::read_dir(path) {
            for entry in entries.flatten() {
                let p = entry.path();
                if p.is_dir() {
                    let dir_name = p.file_name().and_then(|n| n.to_str()).unwrap_or("");
                    if dir_name != "target" && dir_name != "build" && dir_name != ".git" {
                        if let Some(result) = walk_dir(&p) {
                            return Some(result);
                        }
                    }
                } else if let Some(ext) = p.extension().and_then(|e| e.to_str()) {
                    if ext == "java" || ext == "kt" {
                        if let Some((full_class, _)) = extract_main_class_from_file(p.to_str().unwrap_or("")) {
                            return Some(full_class);
                        }
                    }
                }
            }
        }
        None
    }

    walk_dir(dir)
}

fn extract_profiles(dir: &Path) -> Vec<String> {
    let mut profiles = Vec::new();

    let resources_dir = dir.join("src/main/resources");
    if resources_dir.exists() {
        for entry in fs::read_dir(&resources_dir).ok().unwrap_or_else(|| panic!("")).flatten() {
            let path = entry.path();
            if let Some(filename) = path.file_name().and_then(|n| n.to_str()) {
                if filename == "application.yml" || filename == "application.yaml" {
                    if let Ok(content) = fs::read_to_string(&path) {
                        let re_spring_profiles = Regex::new(r"spring:\s*profiles:\s*active:\s*([^\n]+)").unwrap();
                        if let Some(caps) = re_spring_profiles.captures(&content) {
                            if let Some(m) = caps.get(1) {
                                let profile_str = m.as_str().trim();
                                for profile in profile_str.split(',') {
                                    profiles.push(profile.trim().to_string());
                                }
                            }
                        }
                    }
                }
                if filename == "application.properties" {
                    if let Ok(content) = fs::read_to_string(&path) {
                        let re_spring_profiles = Regex::new(r"spring\.profiles\.active\s*=\s*([^\n]+)").unwrap();
                        if let Some(caps) = re_spring_profiles.captures(&content) {
                            if let Some(m) = caps.get(1) {
                                let profile_str = m.as_str().trim();
                                for profile in profile_str.split(',') {
                                    profiles.push(profile.trim().to_string());
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    profiles
}

pub fn detect_springboot_app(dir: &Path) -> SpringBootConfig {
    let main_class = find_springboot_main_class(dir)
        .unwrap_or_else(|| "com.example.Application".to_string());

    let build_tool = detect_build_tool(dir);

    let project_name = dir
        .file_name()
        .and_then(|n| n.to_str())
        .unwrap_or("project")
        .to_string();

    let jvm_args = "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005".to_string();

    let profiles = extract_profiles(dir);

    SpringBootConfig {
        main_class,
        project_name,
        build_tool,
        jvm_args,
        profiles,
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_detect_springboot_app() {
        let dir = tempfile::tempdir().unwrap();

        let src_dir = dir.path().join("src/main/java/com/example");
        fs::create_dir_all(&src_dir).unwrap();

        let file_path = src_dir.join("Application.java");
        let content = "package com.example;\nimport org.springframework.boot.SpringApplication;\nimport org.springframework.boot.autoconfigure.SpringBootApplication;\n@SpringBootApplication\npublic class Application {\npublic static void main(String[] args) { SpringApplication.run(Application.class, args); }\n}\n";
        fs::write(&file_path, content).unwrap();

        let config = detect_springboot_app(dir.path());
        assert_eq!(config.main_class, "com.example.Application");
        assert_eq!(config.jvm_args, "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005");
    }

    #[test]
    fn test_detect_build_tool_maven() {
        let dir = tempfile::tempdir().unwrap();
        fs::write(dir.path().join("pom.xml"), "<project></project>").unwrap();

        let config = detect_springboot_app(dir.path());
        assert_eq!(config.build_tool, "maven");
    }

    #[test]
    fn test_detect_build_tool_gradle() {
        let dir = tempfile::tempdir().unwrap();
        fs::write(dir.path().join("build.gradle"), "plugins {}").unwrap();

        let config = detect_springboot_app(dir.path());
        assert_eq!(config.build_tool, "gradle");
    }

    #[test]
    fn test_extract_profiles_from_yaml() {
        let dir = tempfile::tempdir().unwrap();
        let resources_dir = dir.path().join("src/main/resources");
        fs::create_dir_all(&resources_dir).unwrap();

        let app_yml = resources_dir.join("application.yml");
        let content = "spring:\n  profiles:\n    active: dev,test\n";
        fs::write(&app_yml, content).unwrap();

        let config = detect_springboot_app(dir.path());
        assert!(config.profiles.contains(&"dev".to_string()));
        assert!(config.profiles.contains(&"test".to_string()));
    }

    #[test]
    fn test_extract_profiles_from_properties() {
        let dir = tempfile::tempdir().unwrap();
        let resources_dir = dir.path().join("src/main/resources");
        fs::create_dir_all(&resources_dir).unwrap();

        let app_props = resources_dir.join("application.properties");
        let content = "spring.profiles.active=prod\n";
        fs::write(&app_props, content).unwrap();

        let config = detect_springboot_app(dir.path());
        assert!(config.profiles.contains(&"prod".to_string()));
    }
}
