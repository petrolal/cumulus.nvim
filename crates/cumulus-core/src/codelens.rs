use regex::Regex;
use serde::{Deserialize, Serialize};
use std::fs;
use std::path::Path;

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
pub struct CodeLensItem {
    pub line: usize,
    pub title: String,
    pub command: String,
    pub args: Vec<String>,
}

pub fn extract_codelens(file_path: &Path) -> Vec<CodeLensItem> {
    let content = match fs::read_to_string(file_path) {
        Ok(c) => c,
        Err(_) => return Vec::new(),
    };

    let filename = file_path
        .file_name()
        .and_then(|n| n.to_str())
        .unwrap_or("")
        .to_string();

    let path_str = file_path.to_str().unwrap_or("").to_string();

    let test_re = Regex::new(r"@Test|@ParameterizedTest|@RepeatedTest").unwrap();
    let main_re = Regex::new(r"public\s+static\s+void\s+main|fun\s+main\b").unwrap();
    let scheduled_re = Regex::new(r"@Scheduled\b").unwrap();
    let listener_re = Regex::new(r"@EventListener\b|@KafkaListener\b|@RabbitListener\b").unwrap();

    let mut items = Vec::new();

    for (idx, line) in content.lines().enumerate() {
        let line_num = idx + 1;
        let line_trimmed = line.trim();

        if line_trimmed.starts_with("//") || line_trimmed.starts_with('*') || line_trimmed.starts_with("/*") {
            continue;
        }

        if test_re.is_match(line_trimmed) {
            items.push(CodeLensItem {
                line: line_num,
                title: "▶ Run Test".to_string(),
                command: "cumulus.run_test".to_string(),
                args: vec![path_str.clone(), line_num.to_string(), filename.clone()],
            });
        } else if main_re.is_match(line_trimmed) {
            items.push(CodeLensItem {
                line: line_num,
                title: "▶ Run Main".to_string(),
                command: "cumulus.run_main".to_string(),
                args: vec![path_str.clone(), line_num.to_string(), filename.clone()],
            });
        } else if scheduled_re.is_match(line_trimmed) {
            items.push(CodeLensItem {
                line: line_num,
                title: "⏰ Scheduled Task".to_string(),
                command: "cumulus.scheduled".to_string(),
                args: vec![path_str.clone(), line_num.to_string(), filename.clone()],
            });
        } else if listener_re.is_match(line_trimmed) {
            items.push(CodeLensItem {
                line: line_num,
                title: "🎧 Event Listener".to_string(),
                command: "cumulus.event_listener".to_string(),
                args: vec![path_str.clone(), line_num.to_string(), filename.clone()],
            });
        }
    }

    items
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_extract_codelens_java() {
        let code = r#"
package com.example;

public class ApplicationTests {

    public static void main(String[] args) {
    }

    @Test
    void contextLoads() {
    }

    @Scheduled(cron = "0 0 * * * *")
    public void runJob() {
    }
}
        "#;

        let dir = tempfile::tempdir().unwrap();
        let file_path = dir.path().join("ApplicationTests.java");
        fs::write(&file_path, code).unwrap();

        let items = extract_codelens(&file_path);
        assert_eq!(items.len(), 3);
        assert_eq!(items[0].title, "▶ Run Main");
        assert_eq!(items[1].title, "▶ Run Test");
        assert_eq!(items[2].title, "⏰ Scheduled Task");
    }

    #[test]
    fn test_extract_codelens_kotlin() {
        let code = r#"
package com.example

class AppTest {
    @Test
    fun testApp() {
    }

    @EventListener
    fun handleEvent(event: Any) {
    }
}

fun main() {
}
        "#;

        let dir = tempfile::tempdir().unwrap();
        let file_path = dir.path().join("AppTest.kt");
        fs::write(&file_path, code).unwrap();

        let items = extract_codelens(&file_path);
        assert_eq!(items.len(), 3);
        assert_eq!(items[0].title, "▶ Run Test");
        assert_eq!(items[1].title, "🎧 Event Listener");
        assert_eq!(items[2].title, "▶ Run Main");
    }
}
