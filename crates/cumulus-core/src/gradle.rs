use std::collections::HashSet;

pub fn parse_gradle_tasks(output: &str) -> Vec<String> {
    let mut tasks = Vec::new();
    let mut in_tasks_section = false;

    for line in output.lines() {
        let trimmed = line.trim();
        if trimmed.starts_with("----")
            || trimmed.starts_with("All tasks")
            || trimmed.starts_with("Build tasks")
            || (trimmed.ends_with("tasks") && trimmed.chars().next().map_or(false, |c| c.is_uppercase()))
        {
            in_tasks_section = true;
        } else if trimmed.is_empty() || trimmed.starts_with("To see all tasks") {
            in_tasks_section = false;
        } else if in_tasks_section {
            if let Some(pos) = line.find('-') {
                let task_candidate = line[..pos].trim();
                if !task_candidate.is_empty()
                    && !task_candidate.starts_with('-')
                    && !task_candidate.starts_with("To")
                    && !task_candidate.starts_with("Pattern")
                    && !task_candidate.contains(' ')
                {
                    tasks.push(task_candidate.to_string());
                }
            }
        }
    }

    let mut unique_tasks = Vec::new();
    let mut seen = HashSet::new();
    for task in tasks {
        if seen.insert(task.clone()) {
            unique_tasks.push(task);
        }
    }

    if !unique_tasks.is_empty() {
        unique_tasks.insert(0, "clean build".to_string());
        unique_tasks.insert(0, "clean test".to_string());
    }

    unique_tasks
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_gradle_tasks_parsing() {
        let sample = r#"
All tasks runnable from root project

Build tasks
-----------
assemble - Assembles the outputs of this project.
build - Assembles and tests this project.
clean - Deletes the build directory.
"#;
        let tasks = parse_gradle_tasks(sample);
        assert!(tasks.contains(&"assemble".to_string()));
        assert!(tasks.contains(&"build".to_string()));
        assert!(tasks.contains(&"clean".to_string()));
    }
}
