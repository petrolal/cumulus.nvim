use std::path::Path;

pub fn generate_java_header(filepath: &str) -> Vec<String> {
    let path = Path::new(filepath);

    let filename = path
        .file_stem()
        .and_then(|s| s.to_str())
        .unwrap_or("MainClass");

    let filepath_str = filepath.replace('\\', "/");
    let mut package_path = None;

    // Pattern 1: src/*/java/pkg/subpkg/ClassName.java
    if let Some(pos) = filepath_str.find("/src/") {
        let after_src = &filepath_str[pos + 5..]; // skip "/src/"
        if let Some(java_pos) = after_src.find("/java/") {
            let pkg_part = &after_src[java_pos + 6..];
            if let Some(last_slash) = pkg_part.rfind('/') {
                package_path = Some(pkg_part[..last_slash].to_string());
            }
        } else if let Some(last_slash) = after_src.rfind('/') {
            package_path = Some(after_src[..last_slash].to_string());
        }
    }

    let mut lines = Vec::new();

    if let Some(pkg) = package_path {
        let package_name = pkg.replace('/', ".");
        lines.push(format!("package {};", package_name));
        lines.push("".to_string());
    }

    lines.push(format!("public class {} {{", filename));
    lines.push("    ".to_string());
    lines.push("}".to_string());

    lines
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_generate_java_header() {
        let header = generate_java_header("/project/src/main/java/com/example/demo/UserService.java");
        assert_eq!(header[0], "package com.example.demo;");
        assert_eq!(header[1], "");
        assert_eq!(header[2], "public class UserService {");
    }
}
