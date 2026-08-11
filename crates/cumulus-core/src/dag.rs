use crate::multimodule;
use regex::Regex;
use serde::{Deserialize, Serialize};
use std::collections::{HashMap, HashSet, VecDeque};
use std::fs;
use std::path::{Path, PathBuf};

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
pub struct ModuleBuildStep {
    pub step: usize,
    pub module_name: String,
    pub path: String,
    pub build_command: String,
}

pub fn compute_build_order(root_dir: &Path) -> Vec<ModuleBuildStep> {
    let pom_path = root_dir.join("pom.xml");
    let settings_gradle = root_dir.join("settings.gradle");
    let settings_gradle_kts = root_dir.join("settings.gradle.kts");

    if pom_path.exists() {
        compute_maven_build_order(&pom_path, root_dir)
    } else if settings_gradle.exists() {
        compute_gradle_build_order(&settings_gradle, root_dir)
    } else if settings_gradle_kts.exists() {
        compute_gradle_build_order(&settings_gradle_kts, root_dir)
    } else {
        Vec::new()
    }
}

fn compute_maven_build_order(pom_path: &Path, root_dir: &Path) -> Vec<ModuleBuildStep> {
    let raw_modules = multimodule::parse_maven_modules(pom_path);
    if raw_modules.is_empty() {
        return Vec::new();
    }

    let mut module_names = HashSet::new();
    let mut module_map = HashMap::new();

    for m in &raw_modules {
        module_names.insert(m.name.clone());
        module_map.insert(m.name.clone(), m.path.clone());
    }

    // Graph: module -> set of modules it depends on
    let mut deps: HashMap<String, HashSet<String>> = HashMap::new();
    let mut indegree: HashMap<String, usize> = HashMap::new();

    for name in &module_names {
        deps.insert(name.clone(), HashSet::new());
        indegree.insert(name.clone(), 0);
    }

    let artifact_re = Regex::new(r"<artifactId>\s*([^<]+?)\s*</artifactId>").unwrap();

    for m in &raw_modules {
        let mod_pom = PathBuf::from(&m.path).join("pom.xml");
        if let Ok(content) = fs::read_to_string(&mod_pom) {
            for cap in artifact_re.captures_iter(&content) {
                let dep_name = cap[1].trim();
                if module_names.contains(dep_name) && dep_name != m.name {
                    deps.get_mut(&m.name).unwrap().insert(dep_name.to_string());
                }
            }
        }
    }

    // Compute indegrees: u -> v means v depends on u (u must be built before v)
    // graph: u -> set of nodes that depend on u
    let mut graph: HashMap<String, Vec<String>> = HashMap::new();
    for name in &module_names {
        graph.insert(name.clone(), Vec::new());
    }

    for (node, node_deps) in &deps {
        for d in node_deps {
            graph.get_mut(d).unwrap().push(node.clone());
            *indegree.get_mut(node).unwrap() += 1;
        }
    }

    // Kahn's Algorithm
    let mut queue = VecDeque::new();
    for (name, deg) in &indegree {
        if *deg == 0 {
            queue.push_back(name.clone());
        }
    }

    let mut order = Vec::new();
    while let Some(u) = queue.pop_front() {
        order.push(u.clone());
        if let Some(neighbors) = graph.get(&u) {
            for v in neighbors {
                let deg = indegree.get_mut(v).unwrap();
                *deg -= 1;
                if *deg == 0 {
                    queue.push_back(v.clone());
                }
            }
        }
    }

    // If order length != module_names length, fallback to raw module list
    if order.len() != raw_modules.len() {
        order = raw_modules.iter().map(|m| m.name.clone()).collect();
    }

    let mut result = Vec::new();
    for (idx, name) in order.iter().enumerate() {
        let path = module_map.get(name).cloned().unwrap_or_default();
        let rel_path = Path::new(&path)
            .strip_prefix(root_dir)
            .unwrap_or_else(|_| Path::new(&path))
            .to_str()
            .unwrap_or(&path)
            .to_string();

        let build_command = format!("mvn -pl {} clean compile", rel_path);

        result.push(ModuleBuildStep {
            step: idx + 1,
            module_name: name.clone(),
            path,
            build_command,
        });
    }

    result
}

fn compute_gradle_build_order(settings_path: &Path, _root_dir: &Path) -> Vec<ModuleBuildStep> {
    let raw_modules = multimodule::parse_gradle_modules(settings_path);
    if raw_modules.is_empty() {
        return Vec::new();
    }

    let mut result = Vec::new();
    for (idx, m) in raw_modules.iter().enumerate() {
        let build_command = format!("./gradlew :{}:build", m.name);
        result.push(ModuleBuildStep {
            step: idx + 1,
            module_name: m.name.clone(),
            path: m.path.clone(),
            build_command,
        });
    }

    result
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_compute_maven_build_order() {
        let dir = tempfile::tempdir().unwrap();
        let root = dir.path();

        let root_pom = root.join("pom.xml");
        fs::write(
            &root_pom,
            r#"<project><modules><module>common</module><module>service</module><module>app</module></modules></project>"#,
        )
        .unwrap();

        let common_dir = root.join("common");
        fs::create_dir_all(&common_dir).unwrap();
        fs::write(
            common_dir.join("pom.xml"),
            r#"<project><artifactId>common</artifactId></project>"#,
        )
        .unwrap();

        let service_dir = root.join("service");
        fs::create_dir_all(&service_dir).unwrap();
        fs::write(
            service_dir.join("pom.xml"),
            r#"<project><artifactId>service</artifactId><dependencies><dependency><artifactId>common</artifactId></dependency></dependencies></project>"#,
        )
        .unwrap();

        let app_dir = root.join("app");
        fs::create_dir_all(&app_dir).unwrap();
        fs::write(
            app_dir.join("pom.xml"),
            r#"<project><artifactId>app</artifactId><dependencies><dependency><artifactId>service</artifactId></dependency></dependencies></project>"#,
        )
        .unwrap();

        let steps = compute_build_order(root);
        assert_eq!(steps.len(), 3);
        assert_eq!(steps[0].module_name, "common");
        assert_eq!(steps[1].module_name, "service");
        assert_eq!(steps[2].module_name, "app");
    }
}
