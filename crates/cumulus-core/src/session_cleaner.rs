use serde::Serialize;
use std::fs;
use std::path::PathBuf;

#[derive(Debug, Serialize, Clone)]
pub struct SessionSanitizeResult {
    pub success: bool,
    pub cleaned_lines: usize,
    pub total_lines: usize,
}

/// Parse and sanitize a .vim session file by removing ephemeral buffers and floating windows.
/// Strips:
/// - [No Name] scratch buffers
/// - snacks_dashboard, snacks_picker floating windows
/// - Terminal buffers (term://...)
/// - Related window creation/layout commands
pub fn sanitize_session(file_path: &PathBuf) -> SessionSanitizeResult {
    if !file_path.exists() {
        return SessionSanitizeResult {
            success: false,
            cleaned_lines: 0,
            total_lines: 0,
        };
    }

    let content = match fs::read_to_string(file_path) {
        Ok(c) => c,
        Err(_) => {
            return SessionSanitizeResult {
                success: false,
                cleaned_lines: 0,
                total_lines: 0,
            }
        }
    };

    let lines: Vec<&str> = content.lines().collect();
    let total_lines = lines.len();

    // Parse and filter lines
    let (filtered_lines, removed_count) = filter_session_lines(&lines);

    // Write back to file
    let cleaned_content = filtered_lines.join("\n");
    if cleaned_content != content {
        if let Err(_) = fs::write(file_path, cleaned_content) {
            return SessionSanitizeResult {
                success: false,
                cleaned_lines: removed_count,
                total_lines,
            };
        }
    }

    SessionSanitizeResult {
        success: true,
        cleaned_lines: removed_count,
        total_lines,
    }
}

/// Filter session lines by removing ephemeral buffers and floating windows.
fn filter_session_lines(lines: &[&str]) -> (Vec<String>, usize) {
    let mut filtered = Vec::new();
    let mut ephemeral_buffers = std::collections::HashSet::new();

    // First pass: identify ephemeral buffers
    for line in lines {
        if line.starts_with("badd +") {
            if is_ephemeral_buffer(line) {
                ephemeral_buffers.insert(line.to_string());
                continue;
            }
        }
        filtered.push(line.to_string());
    }

    let removed_after_first_pass = lines.len() - filtered.len();

    // Second pass: remove window creation commands that reference ephemeral buffers
    let final_filtered: Vec<String> = filtered
        .iter()
        .filter(|line| {
            // Skip window creation commands for floating windows / snacks
            if is_floating_window_command(line) {
                return false;
            }
            // Skip buffer focus commands if the buffer was removed
            if should_skip_buffer_reference(line) {
                return false;
            }
            true
        })
        .map(|s| s.to_string())
        .collect();

    let removed_after_second_pass = filtered.len() - final_filtered.len();
    let total_removed = removed_after_first_pass + removed_after_second_pass;

    (final_filtered, total_removed)
}

/// Check if a buffer add line is ephemeral (should be removed)
fn is_ephemeral_buffer(line: &str) -> bool {
    // [No Name] buffers - unnamed, scratch
    if line.contains("[No Name]") {
        return true;
    }

    // Terminal buffers
    if line.contains("term://") {
        return true;
    }

    // Snacks dashboard/picker buffers
    if line.contains("snacks_dashboard") || line.contains("snacks_picker") || line.contains("snacks_explorer") {
        return true;
    }

    false
}

/// Check if a line is a floating window command (should be removed)
fn is_floating_window_command(line: &str) -> bool {
    // Window creation commands for floating windows
    if line.contains("setlocal") && (line.contains("buftype=nofile") || line.contains("buftype=") && line.contains("floating")) {
        return true;
    }

    // Lines referencing snacks or floating patterns
    if line.contains("snacks_") {
        return true;
    }

    // Floating window border/style commands
    if line.contains("floating") {
        return true;
    }

    false
}

/// Check if a line references an ephemeral buffer that was removed
fn should_skip_buffer_reference(line: &str) -> bool {
    // This is a simplified check - we'd need buffer IDs to be more precise
    // For now, skip lines that explicitly reference [No Name] or terminal buffers
    if line.contains("[No Name]") || line.contains("term://") || line.contains("snacks_") {
        return true;
    }

    false
}

#[cfg(test)]
mod tests {
    use super::*;
    use tempfile::TempDir;

    #[test]
    fn test_filter_no_name_buffers() {
        let lines = vec![
            r#"badd +0 /home/user/project/src/main.rs"#,
            r#"badd +0 [No Name]"#,
            r#"badd +0 /home/user/project/build.gradle"#,
        ];

        let (filtered, removed) = filter_session_lines(&lines);
        assert_eq!(removed, 1);
        assert_eq!(filtered.len(), 2);
        assert!(filtered[0].contains("main.rs"));
        assert!(filtered[1].contains("build.gradle"));
    }

    #[test]
    fn test_filter_terminal_buffers() {
        let lines = vec![
            r#"badd +0 /home/user/project/src/main.rs"#,
            r#"badd +0 term://localhost:1000:1"#,
            r#"badd +0 /home/user/project/Makefile"#,
        ];

        let (filtered, removed) = filter_session_lines(&lines);
        assert_eq!(removed, 1);
        assert!(filtered.iter().all(|l| !l.contains("term://")));
    }

    #[test]
    fn test_filter_snacks_buffers() {
        let lines = vec![
            r#"badd +0 /home/user/project/src/main.rs"#,
            r#"badd +0 snacks_dashboard"#,
            r#"badd +0 /home/user/project/test.lua"#,
            r#"badd +0 snacks_picker"#,
        ];

        let (filtered, removed) = filter_session_lines(&lines);
        assert_eq!(removed, 2);
        assert!(!filtered.iter().any(|l| l.contains("snacks_")));
    }

    #[test]
    fn test_sanitize_session_file() {
        let temp_dir = TempDir::new().unwrap();
        let session_file = temp_dir.path().join("session.vim");

        let session_content = r#"badd +0 /home/user/src/main.rs
badd +0 [No Name]
badd +0 /home/user/src/test.rs
badd +0 term://localhost:123:456
badd +0 snacks_dashboard
badd +0 /home/user/build.gradle"#;

        fs::write(&session_file, session_content).unwrap();

        let result = sanitize_session(&session_file);
        assert!(result.success);
        assert!(result.cleaned_lines > 0);

        // Verify file was cleaned
        let cleaned = fs::read_to_string(&session_file).unwrap();
        assert!(!cleaned.contains("[No Name]"));
        assert!(!cleaned.contains("term://"));
        assert!(!cleaned.contains("snacks_dashboard"));
        assert!(cleaned.contains("main.rs"));
        assert!(cleaned.contains("build.gradle"));
    }

    #[test]
    fn test_sanitize_nonexistent_file() {
        let temp_dir = TempDir::new().unwrap();
        let session_file = temp_dir.path().join("nonexistent.vim");

        let result = sanitize_session(&session_file);
        assert!(!result.success);
    }

    #[test]
    fn test_is_ephemeral_buffer() {
        assert!(is_ephemeral_buffer(r#"badd +0 [No Name]"#));
        assert!(is_ephemeral_buffer(r#"badd +0 term://localhost:1:1"#));
        assert!(is_ephemeral_buffer(r#"badd +0 snacks_dashboard"#));
        assert!(!is_ephemeral_buffer(r#"badd +0 /home/user/main.rs"#));
    }
}
