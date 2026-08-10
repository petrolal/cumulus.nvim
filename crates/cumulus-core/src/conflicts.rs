use serde::{Deserialize, Serialize};

#[derive(Debug, Serialize, Deserialize, PartialEq, Eq)]
pub struct ConflictBlock {
    pub start_line: usize,
    pub sep_line: usize,
    pub end_line: usize,
    pub current_header: String,
    pub incoming_header: String,
}

pub fn parse_git_conflicts_content(content: &str) -> Vec<ConflictBlock> {
    let mut conflicts = Vec::new();
    let mut current_start = None;
    let mut current_sep = None;
    let mut current_header = String::new();

    for (idx, line) in content.lines().enumerate() {
        let line_num = idx + 1;
        let line_trim = line.trim();

        if line_trim.starts_with("<<<<<<< ") || line_trim == "<<<<<<<" {
            current_start = Some(line_num);
            current_header = if line_trim.len() > 7 { line_trim[8..].to_string() } else { "HEAD".to_string() };
        } else if line_trim.starts_with("=======") && current_start.is_some() {
            current_sep = Some(line_num);
        } else if (line_trim.starts_with(">>>>>>> ") || line_trim == ">>>>>>>") && current_start.is_some() && current_sep.is_some() {
            let start = current_start.take().unwrap();
            let sep = current_sep.take().unwrap();
            let incoming_header = if line_trim.len() > 7 { line_trim[8..].to_string() } else { "INCOMING".to_string() };

            conflicts.push(ConflictBlock {
                start_line: start,
                sep_line: sep,
                end_line: line_num,
                current_header: current_header.clone(),
                incoming_header,
            });
        }
    }

    conflicts
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_parse_git_conflicts() {
        let content = r#"
<<<<<<< HEAD
const x = 1;
=======
const x = 2;
>>>>>>> feature-branch
"#;
        let conflicts = parse_git_conflicts_content(content);
        assert_eq!(conflicts.len(), 1);
        assert_eq!(conflicts[0].start_line, 2);
        assert_eq!(conflicts[0].sep_line, 4);
        assert_eq!(conflicts[0].end_line, 6);
        assert_eq!(conflicts[0].current_header, "HEAD");
        assert_eq!(conflicts[0].incoming_header, "feature-branch");
    }
}
