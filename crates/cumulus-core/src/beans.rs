use regex::Regex;
use serde::{Deserialize, Serialize};
use std::fs;
use std::path::Path;

#[derive(Debug, Serialize, Deserialize, PartialEq, Eq)]
pub struct BeanEntry {
    pub class_name: String,
    pub bean_name: String,
    pub file: String,
    pub line: usize,
    pub injected_deps: Vec<String>,
}

pub fn extract_beans_from_file(filepath: &str) -> Vec<BeanEntry> {
    let content = match fs::read_to_string(filepath) {
        Ok(c) => c,
        Err(_) => return Vec::new(),
    };

    let mut beans = Vec::new();

    let re_stereo = Regex::new(r#"@(Component|Service|Repository|Controller|RestController|Configuration)(?:\s*\(\s*["']([^"']+)["']\s*\))?"#).unwrap();
    let re_class = Regex::new(r#"(?:public\s+)?class\s+([A-Za-z0-9_]+)"#).unwrap();
    let re_inject = Regex::new(r#"@(Autowired|Inject)"#).unwrap();
    let re_field = Regex::new(r#"(?:private|protected|public)?\s+([A-Za-z0-9_<>]+)\s+([A-Za-z0-9_]+)\s*;"#).unwrap();

    let mut current_bean: Option<BeanEntry> = None;
    let mut pending_inject = false;

    for (idx, line) in content.lines().enumerate() {
        let line_trim = line.trim();

        if let Some(caps) = re_stereo.captures(line_trim) {
            let bean_override = caps.get(2).map(|m| m.as_str().to_string());
            if let Some(bean) = current_bean.take() {
                beans.push(bean);
            }
            current_bean = Some(BeanEntry {
                class_name: String::new(),
                bean_name: bean_override.unwrap_or_default(),
                file: filepath.to_string(),
                line: idx + 1,
                injected_deps: Vec::new(),
            });
        }

        if current_bean.is_some() && current_bean.as_ref().unwrap().class_name.is_empty() {
            if let Some(caps) = re_class.captures(line_trim) {
                let class_name = caps.get(1).unwrap().as_str().to_string();
                if let Some(ref mut b) = current_bean {
                    b.class_name = class_name.clone();
                    if b.bean_name.is_empty() {
                        let mut chars = class_name.chars();
                        b.bean_name = match chars.next() {
                            None => String::new(),
                            Some(f) => f.to_lowercase().collect::<String>() + chars.as_str(),
                        };
                    }
                }
            }
        }

        if re_inject.is_match(line_trim) {
            pending_inject = true;
        } else if pending_inject {
            if let Some(caps) = re_field.captures(line_trim) {
                let dep_type = caps.get(1).unwrap().as_str().to_string();
                if let Some(ref mut b) = current_bean {
                    b.injected_deps.push(dep_type);
                }
                pending_inject = false;
            }
        }
    }

    if let Some(bean) = current_bean {
        beans.push(bean);
    }

    beans
}

pub fn extract_beans_from_dir(dir_path: &Path) -> Vec<BeanEntry> {
    let mut results = Vec::new();
    if !dir_path.exists() {
        return results;
    }

    fn walk_dir(path: &Path, results: &mut Vec<BeanEntry>) {
        if let Ok(entries) = fs::read_dir(path) {
            for entry in entries.flatten() {
                let p = entry.path();
                if p.is_dir() {
                    let dir_name = p.file_name().and_then(|n| n.to_str()).unwrap_or("");
                    if dir_name != "target" && dir_name != "build" && dir_name != ".git" {
                        walk_dir(&p, results);
                    }
                } else if let Some(ext) = p.extension().and_then(|e| e.to_str()) {
                    if ext == "java" || ext == "kt" {
                        let mut file_beans = extract_beans_from_file(p.to_str().unwrap_or(""));
                        results.append(&mut file_beans);
                    }
                }
            }
        }
    }

    walk_dir(dir_path, &mut results);
    results
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_extract_beans() {
        let dir = tempfile::tempdir().unwrap();
        let file_path = dir.path().join("UserService.java");
        let content = "package com.example.service;\nimport org.springframework.stereotype.Service;\nimport org.springframework.beans.factory.annotation.Autowired;\n@Service\npublic class UserService {\n@Autowired\nprivate UserRepository userRepository;\n}\n";
        fs::write(&file_path, content).unwrap();

        let beans = extract_beans_from_file(file_path.to_str().unwrap());
        assert_eq!(beans.len(), 1);
        assert_eq!(beans[0].class_name, "UserService");
        assert_eq!(beans[0].bean_name, "userService");
        assert_eq!(beans[0].injected_deps, vec!["UserRepository"]);
    }
}
