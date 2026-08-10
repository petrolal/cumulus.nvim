use std::collections::BTreeSet;

pub fn optimize_imports(content: &str) -> Vec<String> {
    let mut non_import_lines = Vec::new();
    let mut import_lines = BTreeSet::new();
    let mut in_imports = false;

    for line in content.lines() {
        let trimmed = line.trim();
        if trimmed.starts_with("import ") && trimmed.ends_with(';') {
            in_imports = true;
            import_lines.insert(trimmed.to_string());
        } else {
            if in_imports && trimmed.is_empty() {
                // Skip empty lines between import blocks
                continue;
            }
            non_import_lines.push(line.to_string());
        }
    }

    let mut result = Vec::new();
    let mut inserted_imports = false;

    for line in non_import_lines {
        let trimmed = line.trim();
        if !inserted_imports && (trimmed.starts_with("package ") || trimmed.starts_with("public ") || trimmed.starts_with("@")) {
            result.push(line.clone());
            if trimmed.starts_with("package ") {
                result.push("".to_string());
                for imp in &import_lines {
                    result.push(imp.clone());
                }
                if !import_lines.is_empty() {
                    result.push("".to_string());
                }
                inserted_imports = true;
            }
        } else {
            result.push(line);
        }
    }

    if !inserted_imports && !import_lines.is_empty() {
        for imp in &import_lines {
            result.push(imp.clone());
        }
    }

    result
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_optimize_imports() {
        let code = r#"
package com.example;

import java.util.List;
import java.util.ArrayList;
import java.util.List;

public class Demo {
}
"#;
        let lines = optimize_imports(code);
        let joined = lines.join("\n");
        assert!(joined.contains("import java.util.ArrayList;"));
        assert!(joined.contains("import java.util.List;"));
    }
}
