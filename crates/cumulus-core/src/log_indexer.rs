use regex::Regex;
use serde::{Deserialize, Serialize};

#[derive(Debug, Serialize, Deserialize, PartialEq, Eq)]
pub struct LogIndexEntry {
    pub line: usize,
    pub level: String,
    pub timestamp: Option<String>,
    pub message: String,
}

pub fn index_log_content(content: &str) -> Vec<LogIndexEntry> {
    let mut entries = Vec::new();

    // Pattern: 2026-08-10 16:00:00.123 [main] ERROR com.example.App - Error message
    let re_log = Regex::new(
        r#"^(?:(\d{4}-\d{2}-\d{2}[\sT]\d{2}:\d{2}:\d{2}(?:\.\d+)?)\s+)?(?:\[[^\]]+\]\s+)?(ERROR|WARN|FATAL|SEVERE)\s+(.*)"#
    ).unwrap();

    for (idx, line) in content.lines().enumerate() {
        let line_clean = line.trim();
        if let Some(caps) = re_log.captures(line_clean) {
            let timestamp = caps.get(1).map(|m| m.as_str().to_string());
            let level = caps.get(2).unwrap().as_str().to_string();
            let message = caps.get(3).unwrap().as_str().to_string();

            entries.push(LogIndexEntry {
                line: idx + 1,
                level,
                timestamp,
                message,
            });
        }
    }

    entries
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_index_log_content() {
        let log = r#"
2026-08-10 16:00:00 [main] INFO com.example.App - Starting server
2026-08-10 16:00:05 [main] ERROR com.example.App - Connection refused
2026-08-10 16:00:10 [main] WARN com.example.App - Retry attempt 1
"#;
        let entries = index_log_content(log);
        assert_eq!(entries.len(), 2);
        assert_eq!(entries[0].line, 3);
        assert_eq!(entries[0].level, "ERROR");
        assert_eq!(entries[0].message, "com.example.App - Connection refused");
        assert_eq!(entries[1].line, 4);
        assert_eq!(entries[1].level, "WARN");
    }
}
