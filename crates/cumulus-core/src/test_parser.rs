use regex::Regex;
use serde::{Deserialize, Serialize};

#[derive(Debug, Serialize, Deserialize, PartialEq, Eq)]
pub struct TestResultEntry {
    pub test_name: String,
    pub class_name: String,
    pub status: String,
    pub failure_message: Option<String>,
    pub file: Option<String>,
    pub line: Option<usize>,
}

pub fn parse_test_output(log_content: &str) -> Vec<TestResultEntry> {
    let mut results = Vec::new();

    // Gradle pattern: ClassName > methodName FAILED / PASSED / SKIPPED
    let re_gradle_result = Regex::new(r"^\s*([^\s>]+)\s*>\s*([^\s]+)\s+(FAILED|PASSED|SKIPPED)").unwrap();
    // Maven surefire failure pattern:   ClassName.testName:line message
    let re_mvn_failure = Regex::new(r"^\s*\[ERROR\]\s+([a-zA-Z0-9_\.]+)\.([a-zA-Z0-9_]+):?(\d+)?\s*(.*)").unwrap();

    for line in log_content.lines() {
        let line_clean = strip_ansi(line);

        if let Some(caps) = re_gradle_result.captures(&line_clean) {
            let class_name = caps.get(1).unwrap().as_str().to_string();
            let test_name = caps.get(2).unwrap().as_str().to_string();
            let status = caps.get(3).unwrap().as_str().to_string();

            results.push(TestResultEntry {
                test_name,
                class_name,
                status,
                failure_message: None,
                file: None,
                line: None,
            });
        } else if let Some(caps) = re_mvn_failure.captures(&line_clean) {
            let class_name = caps.get(1).unwrap().as_str().to_string();
            let test_name = caps.get(2).unwrap().as_str().to_string();
            let line_num: Option<usize> = caps.get(3).and_then(|m| m.as_str().parse().ok());
            let msg = caps.get(4).map(|m| m.as_str().to_string()).filter(|s| !s.is_empty());

            if !class_name.is_empty() && !test_name.is_empty() && test_name != "java" {
                results.push(TestResultEntry {
                    test_name,
                    class_name,
                    status: "FAILED".to_string(),
                    failure_message: msg,
                    file: None,
                    line: line_num,
                });
            }
        }
    }

    results
}

fn strip_ansi(input: &str) -> String {
    let re = Regex::new(r"\x1B\[[0-?]*[ -/]*[@-~]").unwrap();
    re.replace_all(input, "").to_string()
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_parse_gradle_test_output() {
        let log = r#"
com.example.UserServiceTest > testFindUser FAILED
com.example.UserServiceTest > testCreateUser PASSED
"#;
        let results = parse_test_output(log);
        assert_eq!(results.len(), 2);
        assert_eq!(results[0].test_name, "testFindUser");
        assert_eq!(results[0].status, "FAILED");
        assert_eq!(results[1].test_name, "testCreateUser");
        assert_eq!(results[1].status, "PASSED");
    }

    #[test]
    fn test_parse_maven_test_output() {
        let log = r#"
[ERROR]   UserServiceTest.testFindUser:45 expected <true> but was <false>
"#;
        let results = parse_test_output(log);
        assert_eq!(results.len(), 1);
        assert_eq!(results[0].class_name, "UserServiceTest");
        assert_eq!(results[0].test_name, "testFindUser");
        assert_eq!(results[0].line, Some(45));
    }
}
