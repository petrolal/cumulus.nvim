use quick_xml::events::Event;
use quick_xml::reader::Reader;
use regex::Regex;
use serde::{Deserialize, Serialize};
use std::fs;
use std::path::Path;

#[derive(Debug, Serialize, Deserialize, PartialEq, Eq)]
pub struct ModuleEntry {
    pub name: String,
    pub path: String,
}

pub fn parse_maven_modules(pom_path: &Path) -> Vec<ModuleEntry> {
    let mut modules = Vec::new();
    let content = match fs::read_to_string(pom_path) {
        Ok(c) => c,
        Err(_) => return modules,
    };

    let parent_dir = pom_path.parent().unwrap_or_else(|| Path::new("."));
    let mut reader = Reader::from_str(&content);
    reader.config_mut().trim_text(true);

    let mut in_modules = false;
    let mut in_module = false;
    let mut buf = Vec::new();

    loop {
        match reader.read_event_into(&mut buf) {
            Ok(Event::Start(ref e)) => match e.name().as_ref() {
                b"modules" => in_modules = true,
                b"module" if in_modules => in_module = true,
                _ => {}
            },
            Ok(Event::End(ref e)) => match e.name().as_ref() {
                b"modules" => in_modules = false,
                b"module" => in_module = false,
                _ => {}
            },
            Ok(Event::Text(e)) if in_module => {
                if let Ok(mod_name) = e.unescape() {
                    let name = mod_name.trim().to_string();
                    if !name.is_empty() {
                        let module_dir = parent_dir.join(&name);
                        let path = module_dir.to_string_lossy().to_string();
                        modules.push(ModuleEntry { name, path });
                    }
                }
            }
            Ok(Event::Eof) => break,
            Err(_) => break,
            _ => {}
        }
        buf.clear();
    }

    modules
}

pub fn parse_gradle_modules(settings_path: &Path) -> Vec<ModuleEntry> {
    let mut modules = Vec::new();
    let content = match fs::read_to_string(settings_path) {
        Ok(c) => c,
        Err(_) => return modules,
    };

    let parent_dir = settings_path.parent().unwrap_or_else(|| Path::new("."));
    // Match include 'foo', ":bar:baz", include("foo")
    let re_include = Regex::new(r#"include\s*(?:\(|\s)\s*['"]([^'"]+)['"]"#).unwrap();

    for line in content.lines() {
        let line_clean = line.trim();
        if line_clean.starts_with("//") || line_clean.starts_with("/*") {
            continue;
        }

        for caps in re_include.captures_iter(line_clean) {
            let raw_name = caps.get(1).unwrap().as_str();
            let clean_name = raw_name.trim_start_matches(':');
            if !clean_name.is_empty() {
                let rel_path = clean_name.replace(':', "/");
                let module_dir = parent_dir.join(&rel_path);
                let path = module_dir.to_string_lossy().to_string();
                modules.push(ModuleEntry {
                    name: clean_name.to_string(),
                    path,
                });
            }
        }
    }

    modules
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::io::Write;

    #[test]
    fn test_parse_maven_modules() {
        let dir = tempfile::tempdir().unwrap();
        let pom_file = dir.path().join("pom.xml");
        let mut file = fs::File::create(&pom_file).unwrap();
        writeln!(
            file,
            "<project><modules><module>core</module><module>api</module></modules></project>"
        )
        .unwrap();

        let mods = parse_maven_modules(&pom_file);
        assert_eq!(mods.len(), 2);
        assert_eq!(mods[0].name, "core");
        assert_eq!(mods[1].name, "api");
    }

    #[test]
    fn test_parse_gradle_modules() {
        let dir = tempfile::tempdir().unwrap();
        let settings_file = dir.path().join("settings.gradle");
        let mut file = fs::File::create(&settings_file).unwrap();
        writeln!(file, "include 'app'\ninclude ':service:api'").unwrap();

        let mods = parse_gradle_modules(&settings_file);
        assert_eq!(mods.len(), 2);
        assert_eq!(mods[0].name, "app");
        assert_eq!(mods[1].name, "service:api");
        assert!(mods[1].path.ends_with("service/api"));
    }
}
