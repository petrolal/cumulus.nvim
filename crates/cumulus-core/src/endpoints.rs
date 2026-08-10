use regex::Regex;
use serde::{Deserialize, Serialize};
use std::fs;
use std::path::Path;

#[derive(Debug, Serialize, Deserialize, PartialEq, Eq)]
pub struct EndpointEntry {
    pub file: String,
    pub line: usize,
    pub http_method: String,
    pub path: String,
    pub class_name: String,
    pub handler_name: String,
}

pub fn extract_endpoints_from_file(filepath: &str) -> Vec<EndpointEntry> {
    let content = match fs::read_to_string(filepath) {
        Ok(c) => c,
        Err(_) => return Vec::new(),
    };

    let mut endpoints = Vec::new();
    let path_obj = Path::new(filepath);
    let class_name = path_obj
        .file_stem()
        .and_then(|s| s.to_str())
        .unwrap_or("UnknownClass")
        .to_string();

    let re_class_path = Regex::new(r#"@RequestMapping\s*\(\s*(?:value\s*=\s*)?["']([^"']*)["']"#).unwrap();
    let re_method_anno = Regex::new(r#"@\b(GetMapping|PostMapping|PutMapping|DeleteMapping|PatchMapping|RequestMapping|Path|GET|POST|PUT|DELETE)\b(?:\s*\(\s*(?:value\s*=\s*)?["']([^"']*)["']\s*\))?"#).unwrap();

    let mut class_prefix = String::new();
    let mut in_class = false;
    let mut pending_method: Option<String> = None;
    let mut pending_path: Option<String> = None;

    for (idx, line) in content.lines().enumerate() {
        let line_num = idx + 1;
        let line_trim = line.trim();

        if !in_class {
            if let Some(caps) = re_class_path.captures(line_trim) {
                if let Some(p) = caps.get(1) {
                    class_prefix = p.as_str().to_string();
                }
            }
            if line_trim.contains("class ") {
                in_class = true;
            }
            continue;
        }

        if let Some(caps) = re_method_anno.captures(line_trim) {
            let anno = caps.get(1).map_or("", |m| m.as_str());
            let path_val = caps.get(2).map_or("", |m| m.as_str());

            let method = match anno {
                "GetMapping" | "GET" => "GET",
                "PostMapping" | "POST" => "POST",
                "PutMapping" | "PUT" => "PUT",
                "DeleteMapping" | "DELETE" => "DELETE",
                "PatchMapping" => "PATCH",
                "RequestMapping" | "Path" => "ALL",
                _ => "GET",
            };

            pending_method = Some(method.to_string());
            pending_path = Some(path_val.to_string());
        } else if pending_method.is_some() && !line_trim.starts_with('@') && line_trim.contains('(') {
            let paren_pos = line_trim.find('(').unwrap();
            let before_paren = line_trim[..paren_pos].trim();
            let handler_name = before_paren.split_whitespace().last().unwrap_or("handler");

            if handler_name != "if" && handler_name != "for" && handler_name != "while" && handler_name != "switch" {
                let method = pending_method.take().unwrap();
                let method_path = pending_path.take().unwrap_or_default();

                let raw_path = if class_prefix.is_empty() {
                    if method_path.starts_with('/') || method_path.is_empty() {
                        if method_path.is_empty() { "/".to_string() } else { method_path }
                    } else {
                        format!("/{}", method_path)
                    }
                } else {
                    format!("{}/{}", class_prefix.trim_end_matches('/'), method_path.trim_start_matches('/'))
                };

                let full_path = if raw_path.len() > 1 && raw_path.ends_with('/') {
                    raw_path[..raw_path.len() - 1].to_string()
                } else {
                    raw_path
                };

                endpoints.push(EndpointEntry {
                    file: filepath.to_string(),
                    line: line_num,
                    http_method: method,
                    path: full_path,
                    class_name: class_name.clone(),
                    handler_name: handler_name.to_string(),
                });
            }
        }
    }

    endpoints
}

pub fn extract_endpoints_from_dir(dir_path: &Path) -> Vec<EndpointEntry> {
    let mut results = Vec::new();
    if !dir_path.exists() {
        return results;
    }

    fn walk_dir(path: &Path, results: &mut Vec<EndpointEntry>) {
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
                        let mut file_eps = extract_endpoints_from_file(p.to_str().unwrap_or(""));
                        results.append(&mut file_eps);
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
    fn test_extract_endpoints_file() {
        let dir = tempfile::tempdir().unwrap();
        let file_path = dir.path().join("UserController.java");
        let content = "package com.example.demo;\nimport org.springframework.web.bind.annotation.*;\n@RestController\n@RequestMapping(\"/api/users\")\npublic class UserController {\n@GetMapping(\"/{id}\")\npublic User getUser(@PathVariable String id) { return null; }\n@PostMapping\npublic User createUser(@RequestBody User user) { return null; }\n}\n";
        fs::write(&file_path, content).unwrap();

        let eps = extract_endpoints_from_file(file_path.to_str().unwrap());
        assert_eq!(eps.len(), 2);
        assert_eq!(eps[0].http_method, "GET");
        assert_eq!(eps[0].path, "/api/users/{id}");
        assert_eq!(eps[0].handler_name, "getUser");
        assert_eq!(eps[1].http_method, "POST");
        assert_eq!(eps[1].path, "/api/users");
        assert_eq!(eps[1].handler_name, "createUser");
    }
}
