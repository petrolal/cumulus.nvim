use regex::Regex;
use serde::{Deserialize, Serialize};
use std::fs;
use std::path::Path;

#[derive(Debug, Serialize, Deserialize)]
pub struct TestContext {
    pub class_name: Option<String>,
    pub method_name: Option<String>,
}

/// Detect the nearest test class and method name at a given line in a Java/Kotlin file.
/// Returns JSON: `{"class_name": "...", "method_name": "..."}` (either may be null).
pub fn detect_test_context(file: &Path, cursor_line: usize) -> TestContext {
    let content = match fs::read_to_string(file) {
        Ok(c) => c,
        Err(_) => {
            return TestContext {
                class_name: None,
                method_name: None,
            }
        }
    };

    let lines: Vec<&str> = content.lines().collect();

    // Detect class name — scan whole file for first class declaration
    let re_class =
        Regex::new(r"(?:public\s+)?(?:class|object)\s+([A-Za-z0-9_]+)").unwrap();
    let class_name = lines.iter().find_map(|line| {
        re_class
            .captures(line)
            .and_then(|c| c.get(1))
            .map(|m| m.as_str().to_string())
    });

    // Detect nearest method — scan upward from cursor line
    let re_method_java =
        Regex::new(r"(?:void|fun)\s+([A-Za-z0-9_]+)\s*\(").unwrap();
    let scan_end = cursor_line.min(lines.len());
    let method_name = (0..scan_end).rev().find_map(|i| {
        re_method_java
            .captures(lines[i])
            .and_then(|c| c.get(1))
            .map(|m| m.as_str().to_string())
    });

    TestContext {
        class_name,
        method_name,
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::io::Write;

    #[test]
    fn test_detect_class_and_method() {
        let dir = tempfile::tempdir().unwrap();
        let file = dir.path().join("UserServiceTest.java");
        let mut f = fs::File::create(&file).unwrap();
        writeln!(
            f,
            r#"public class UserServiceTest {{
    @Test
    void testFindUser() {{
        // cursor here (line 4)
    }}
}}"#
        )
        .unwrap();

        let ctx = detect_test_context(&file, 4);
        assert_eq!(ctx.class_name.as_deref(), Some("UserServiceTest"));
        assert_eq!(ctx.method_name.as_deref(), Some("testFindUser"));
    }

    #[test]
    fn test_no_method_at_top() {
        let dir = tempfile::tempdir().unwrap();
        let file = dir.path().join("MyTest.java");
        let mut f = fs::File::create(&file).unwrap();
        writeln!(f, "public class MyTest {{}}").unwrap();

        let ctx = detect_test_context(&file, 1);
        assert_eq!(ctx.class_name.as_deref(), Some("MyTest"));
        assert!(ctx.method_name.is_none());
    }
}
