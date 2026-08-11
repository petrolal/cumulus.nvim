use once_cell::sync::Lazy;
use regex::Regex;
use serde::{Deserialize, Serialize};

#[derive(Debug, Serialize, Deserialize, PartialEq, Eq)]
pub struct DiagnosticEntry {
    pub file: String,
    pub line: usize,
    pub col: Option<usize>,
    pub message: String,
    pub severity: String,
}

// Lazy-compiled regex patterns for Maven log parsing
static MAVEN_ERROR_FILE_LINE_COL: Lazy<Regex> =
    Lazy::new(|| Regex::new(r"\[ERROR\]\s+([^\s:]+):\[(\d+)(?:,(\d+))?\]\s+(.+)").unwrap());
static MAVEN_ERROR_FILE_COLON: Lazy<Regex> =
    Lazy::new(|| Regex::new(r"([^\s:]+\.java):(\d+):(?:\s*(\d+):)?\s*(.+)").unwrap());

// Lazy-compiled regex patterns for Gradle log parsing
static GRADLE_ERROR_KOTLIN: Lazy<Regex> =
    Lazy::new(|| Regex::new(r"e:\s+([^\s:]+\.kt):\s*\((\d+),\s*(\d+)\):\s*(.+)").unwrap());
static GRADLE_ERROR_JAVA: Lazy<Regex> =
    Lazy::new(|| Regex::new(r"([^\s:]+\.java):(\d+):\s*error:\s*(.+)").unwrap());

pub fn parse_maven_log(log_content: &str) -> Vec<DiagnosticEntry> {
    let mut diagnostics = Vec::new();

    for line in log_content.lines() {
        let line_clean = strip_ansi_escapes(line);

        if let Some(caps) = MAVEN_ERROR_FILE_LINE_COL.captures(&line_clean) {
            let file = caps.get(1).unwrap().as_str().to_string();
            let line_num: usize = caps.get(2).unwrap().as_str().parse().unwrap_or(1);
            let col_num: Option<usize> = caps.get(3).and_then(|m| m.as_str().parse().ok());
            let message = caps.get(4).unwrap().as_str().to_string();

            diagnostics.push(DiagnosticEntry {
                file,
                line: line_num,
                col: col_num,
                message,
                severity: "ERROR".to_string(),
            });
        } else if let Some(caps) = MAVEN_ERROR_FILE_COLON.captures(&line_clean) {
            let file = caps.get(1).unwrap().as_str().to_string();
            let line_num: usize = caps.get(2).unwrap().as_str().parse().unwrap_or(1);
            let col_num: Option<usize> = caps.get(3).and_then(|m| m.as_str().parse().ok());
            let message = caps.get(4).unwrap().as_str().to_string();

            diagnostics.push(DiagnosticEntry {
                file,
                line: line_num,
                col: col_num,
                message,
                severity: "ERROR".to_string(),
            });
        }
    }

    diagnostics
}

pub fn parse_gradle_log(log_content: &str) -> Vec<DiagnosticEntry> {
    let mut diagnostics = Vec::new();

    for line in log_content.lines() {
        let line_clean = strip_ansi_escapes(line);

        if let Some(caps) = GRADLE_ERROR_KOTLIN.captures(&line_clean) {
            let file = caps.get(1).unwrap().as_str().to_string();
            let line_num: usize = caps.get(2).unwrap().as_str().parse().unwrap_or(1);
            let col_num: Option<usize> = caps.get(3).and_then(|m| m.as_str().parse().ok());
            let message = caps.get(4).unwrap().as_str().to_string();

            diagnostics.push(DiagnosticEntry {
                file,
                line: line_num,
                col: col_num,
                message,
                severity: "ERROR".to_string(),
            });
        } else if let Some(caps) = GRADLE_ERROR_JAVA.captures(&line_clean) {
            let file = caps.get(1).unwrap().as_str().to_string();
            let line_num: usize = caps.get(2).unwrap().as_str().parse().unwrap_or(1);
            let message = caps.get(3).unwrap().as_str().to_string();

            diagnostics.push(DiagnosticEntry {
                file,
                line: line_num,
                col: None,
                message,
                severity: "ERROR".to_string(),
            });
        }
    }

    diagnostics
}

// Lazy-compiled regex for ANSI escape sequence removal
static ANSI_ESCAPE: Lazy<Regex> =
    Lazy::new(|| Regex::new(r"\x1B\[[0-?]*[ -/]*[@-~]").unwrap());

fn strip_ansi_escapes(input: &str) -> String {
    ANSI_ESCAPE.replace_all(input, "").to_string()
}

#[derive(Debug, Serialize, Deserialize, PartialEq, Eq)]
pub struct StackTraceEntry {
    pub class_name: String,
    pub method_name: String,
    pub file: String,
    pub line: usize,
}

pub fn parse_stacktrace(log_content: &str) -> Vec<StackTraceEntry> {
    let mut entries = Vec::new();
    // Match: at com.example.MyClass.myMethod(MyClass.java:123)
    let re_trace = Regex::new(r"at\s+([a-zA-Z0-9_\.]+)\.([a-zA-Z0-9_<>$]+)\(([a-zA-Z0-9_\.]+\.(?:java|kt|groovy)):(\d+)\)").unwrap();

    for line in log_content.lines() {
        let line_clean = strip_ansi_escapes(line);
        if let Some(caps) = re_trace.captures(&line_clean) {
            let class_name = caps.get(1).unwrap().as_str().to_string();
            let method_name = caps.get(2).unwrap().as_str().to_string();
            let file = caps.get(3).unwrap().as_str().to_string();
            let line_num: usize = caps.get(4).unwrap().as_str().parse().unwrap_or(1);

            entries.push(StackTraceEntry {
                class_name,
                method_name,
                file,
                line: line_num,
            });
        }
    }

    entries
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_maven_log_parsing() {
        let log = r#"
[INFO] Building my-app 1.0.0
[ERROR] /src/main/java/com/example/App.java:[42,10] cannot find symbol
[ERROR] /src/main/java/com/example/App.java:[50] ';' expected
"#;
        let diags = parse_maven_log(log);
        assert_eq!(diags.len(), 2);
        assert_eq!(diags[0].file, "/src/main/java/com/example/App.java");
        assert_eq!(diags[0].line, 42);
        assert_eq!(diags[0].col, Some(10));
        assert_eq!(diags[0].message, "cannot find symbol");
    }

    #[test]
    fn test_gradle_log_parsing() {
        let log = r#"
e: /src/main/kotlin/com/example/App.kt: (15, 20): Unresolved reference: foo
/src/main/java/com/example/Utils.java:30: error: incompatible types
"#;
        let diags = parse_gradle_log(log);
        assert_eq!(diags.len(), 2);
        assert_eq!(diags[0].file, "/src/main/kotlin/com/example/App.kt");
        assert_eq!(diags[0].line, 15);
        assert_eq!(diags[0].col, Some(20));
        assert_eq!(diags[1].file, "/src/main/java/com/example/Utils.java");
        assert_eq!(diags[1].line, 30);
    }

    #[test]
    fn test_stacktrace_parsing() {
        let log = r#"
java.lang.NullPointerException: Cannot invoke method
    at com.example.service.UserService.findUser(UserService.java:88)
    at com.example.controller.UserController.getUser(UserController.java:42)
"#;
        let trace = parse_stacktrace(log);
        assert_eq!(trace.len(), 2);
        assert_eq!(trace[0].class_name, "com.example.service.UserService");
        assert_eq!(trace[0].method_name, "findUser");
        assert_eq!(trace[0].file, "UserService.java");
        assert_eq!(trace[0].line, 88);
    }

    #[test]
    fn test_maven_log_with_ansi_escapes() {
        // Test that ANSI escape sequences are stripped correctly
        let log = "[ERROR] \x1B[31m/src/main/java/App.java:[1,2]\x1B[0m compile error";
        let diags = parse_maven_log(log);
        assert_eq!(diags.len(), 1);
        assert_eq!(diags[0].file, "/src/main/java/App.java");
        assert_eq!(diags[0].line, 1);
        assert_eq!(diags[0].col, Some(2));
    }

    #[test]
    fn test_maven_log_both_patterns() {
        // Test both Maven regex patterns in one log
        let log = r#"
[ERROR] /path/to/File.java:[12,34] Pattern 1 error
/path/to/Other.java:99:Column format error
"#;
        let diags = parse_maven_log(log);
        assert_eq!(diags.len(), 2);
        assert_eq!(diags[0].line, 12);
        assert_eq!(diags[0].col, Some(34));
        assert_eq!(diags[1].line, 99);
    }

    #[test]
    fn test_gradle_log_kotlin_and_java() {
        // Test both Kotlin and Java patterns
        let log = r#"
e: /src/main/kotlin/App.kt: (10, 15): Kotlin error
/src/main/java/Main.java:20: error: Java error
"#;
        let diags = parse_gradle_log(log);
        assert_eq!(diags.len(), 2);
        assert_eq!(diags[0].file, "/src/main/kotlin/App.kt");
        assert_eq!(diags[0].col, Some(15));
        assert_eq!(diags[1].file, "/src/main/java/Main.java");
        assert_eq!(diags[1].col, None); // Java pattern doesn't extract column
    }

    #[test]
    fn test_empty_logs() {
        assert_eq!(parse_maven_log("").len(), 0);
        assert_eq!(parse_gradle_log("").len(), 0);
        assert_eq!(parse_stacktrace("").len(), 0);
    }

    #[test]
    fn test_non_matching_lines() {
        // Lines that don't match any pattern should be ignored
        let log = r#"
[INFO] Some info message
Some random text
More info without errors
"#;
        assert_eq!(parse_maven_log(log).len(), 0);
        assert_eq!(parse_gradle_log(log).len(), 0);
    }

    #[test]
    fn test_strip_ansi_escapes() {
        let text = "Hello \x1B[31mRed\x1B[0m World";
        assert_eq!(strip_ansi_escapes(text), "Hello Red World");
    }
}
