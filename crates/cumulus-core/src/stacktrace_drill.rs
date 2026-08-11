use regex::Regex;
use serde::{Deserialize, Serialize};
use std::fs;
use std::path::{Path, PathBuf};

#[derive(Debug, Serialize, Deserialize, Clone)]
pub struct StacktraceSymbol {
    pub file_path: String,
    pub line: usize,
    pub class_name: String,
    pub method_name: String,
}

/// Parse stacktrace line to extract class name, method, source file, and line number
/// Format: at com.example.service.UserServiceImpl.findUser(UserServiceImpl.java:142)
fn parse_stacktrace_line(line: &str) -> Option<(String, String, String, usize)> {
    let re_trace = Regex::new(r"at\s+([a-zA-Z0-9_\.]+)\.([a-zA-Z0-9_<>$]+)\(([a-zA-Z0-9_\.]+\.(?:java|kt|groovy)):(\d+)\)").unwrap();

    if let Some(caps) = re_trace.captures(line) {
        let class_name = caps.get(1).unwrap().as_str().to_string();
        let method_name = caps.get(2).unwrap().as_str().to_string();
        let source_file = caps.get(3).unwrap().as_str().to_string();
        let line_num: usize = caps.get(4).unwrap().as_str().parse().unwrap_or(1);

        return Some((class_name, method_name, source_file, line_num));
    }

    None
}

/// Convert package name to directory path
/// com.example.service -> com/example/service
fn package_to_path(package: &str) -> String {
    package.replace(".", "/")
}

/// Find source file in project directory by matching package structure
fn find_source_file(
    class_name: &str,
    source_filename: &str,
    project_dir: &Path,
) -> Option<PathBuf> {
    if !project_dir.exists() {
        return None;
    }

    // Extract package from class name
    // com.example.service.UserServiceImpl -> com/example/service
    let parts: Vec<&str> = class_name.split('.').collect();
    if parts.is_empty() {
        return None;
    }

    let package_path = if parts.len() > 1 {
        package_to_path(&parts[..parts.len() - 1].join("."))
    } else {
        String::new()
    };

    // Search for the source file in common Java/Kotlin source locations
    let source_paths = [
        "src/main/java",
        "src/main/kotlin",
        "src/test/java",
        "src/test/kotlin",
        "src/java",
        "src/kotlin",
        "src",
    ];

    for src_base in &source_paths {
        let base_path = project_dir.join(src_base);
        if !base_path.exists() {
            continue;
        }

        if package_path.is_empty() {
            let candidate = base_path.join(source_filename);
            if candidate.exists() {
                return Some(candidate);
            }
        } else {
            let candidate = base_path.join(&package_path).join(source_filename);
            if candidate.exists() {
                return Some(candidate);
            }
        }
    }

    None
}

pub fn resolve_stacktrace_symbol(line: &str, dir: &Path) -> Option<StacktraceSymbol> {
    let (class_name, method_name, source_filename, line_num) = parse_stacktrace_line(line)?;

    let file_path = find_source_file(&class_name, &source_filename, dir)?;

    Some(StacktraceSymbol {
        file_path: file_path.to_string_lossy().to_string(),
        line: line_num,
        class_name,
        method_name,
    })
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_parse_stacktrace_line() {
        let line = "at com.example.service.UserServiceImpl.findUser(UserServiceImpl.java:142)";
        let result = parse_stacktrace_line(line);
        assert!(result.is_some());

        let (class_name, method_name, file, line_num) = result.unwrap();
        assert_eq!(class_name, "com.example.service.UserServiceImpl");
        assert_eq!(method_name, "findUser");
        assert_eq!(file, "UserServiceImpl.java");
        assert_eq!(line_num, 142);
    }

    #[test]
    fn test_parse_stacktrace_kotlin() {
        let line = "at com.example.service.UserService.getUser(UserService.kt:50)";
        let result = parse_stacktrace_line(line);
        assert!(result.is_some());

        let (class_name, method_name, file, line_num) = result.unwrap();
        assert_eq!(class_name, "com.example.service.UserService");
        assert_eq!(method_name, "getUser");
        assert_eq!(file, "UserService.kt");
        assert_eq!(line_num, 50);
    }

    #[test]
    fn test_package_to_path() {
        assert_eq!(package_to_path("com.example.service"), "com/example/service");
        assert_eq!(package_to_path("org.springframework"), "org/springframework");
    }

    #[test]
    fn test_resolve_stacktrace_symbol() {
        let dir = tempfile::tempdir().unwrap();

        let src_dir = dir.path().join("src/main/java/com/example/service");
        fs::create_dir_all(&src_dir).unwrap();

        let file_path = src_dir.join("UserServiceImpl.java");
        fs::write(&file_path, "public class UserServiceImpl { }").unwrap();

        let line = "at com.example.service.UserServiceImpl.findUser(UserServiceImpl.java:142)";
        let result = resolve_stacktrace_symbol(line, dir.path());

        assert!(result.is_some());
        let symbol = result.unwrap();
        assert_eq!(symbol.class_name, "com.example.service.UserServiceImpl");
        assert_eq!(symbol.method_name, "findUser");
        assert_eq!(symbol.line, 142);
        assert!(symbol.file_path.ends_with("UserServiceImpl.java"));
    }

    #[test]
    fn test_resolve_stacktrace_kotlin() {
        let dir = tempfile::tempdir().unwrap();

        let src_dir = dir.path().join("src/main/kotlin/com/example/service");
        fs::create_dir_all(&src_dir).unwrap();

        let file_path = src_dir.join("UserService.kt");
        fs::write(&file_path, "class UserService { }").unwrap();

        let line = "at com.example.service.UserService.getUser(UserService.kt:50)";
        let result = resolve_stacktrace_symbol(line, dir.path());

        assert!(result.is_some());
        let symbol = result.unwrap();
        assert_eq!(symbol.line, 50);
        assert!(symbol.file_path.ends_with("UserService.kt"));
    }

    #[test]
    fn test_invalid_stacktrace_line() {
        let line = "some random line without stacktrace format";
        let result = parse_stacktrace_line(line);
        assert!(result.is_none());
    }
}
