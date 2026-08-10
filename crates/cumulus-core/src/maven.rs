use std::fs;
use std::path::Path;

pub fn get_maven_goals(pom_path: &Path) -> Vec<String> {
    let mut goals = vec![
        "clean".to_string(),
        "validate".to_string(),
        "compile".to_string(),
        "test".to_string(),
        "package".to_string(),
        "verify".to_string(),
        "install".to_string(),
        "deploy".to_string(),
        "clean compile".to_string(),
        "clean test".to_string(),
        "clean package".to_string(),
        "clean install".to_string(),
        "clean verify".to_string(),
    ];

    let content = match fs::read_to_string(pom_path) {
        Ok(c) => c,
        Err(_) => return goals,
    };

    let mut plugin_goals = Vec::new();

    if content.contains("spring-boot-maven-plugin") {
        plugin_goals.push("spring-boot:run".to_string());
        plugin_goals.push("spring-boot:build-image".to_string());
        plugin_goals.push("spring-boot:repackage".to_string());
    }
    if content.contains("quarkus-maven-plugin") {
        plugin_goals.push("quarkus:dev".to_string());
        plugin_goals.push("quarkus:build".to_string());
        plugin_goals.push("quarkus:test".to_string());
    }
    if content.contains("maven-surefire-plugin") || content.contains("<test") {
        plugin_goals.push("test-compile".to_string());
        plugin_goals.push("surefire:test".to_string());
    }
    if content.contains("maven-failsafe-plugin") {
        plugin_goals.push("failsafe:integration-test".to_string());
        plugin_goals.push("verify".to_string());
    }
    if content.contains("exec-maven-plugin") {
        plugin_goals.push("exec:java".to_string());
        plugin_goals.push("exec:exec".to_string());
    }

    plugin_goals.push("dependency:tree".to_string());
    plugin_goals.push("dependency:analyze".to_string());
    plugin_goals.push("dependency:resolve".to_string());
    plugin_goals.push("help:effective-pom".to_string());
    plugin_goals.push("help:active-profiles".to_string());

    for goal in plugin_goals {
        if !goals.contains(&goal) {
            goals.push(goal);
        }
    }

    goals
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::io::Write;

    #[test]
    fn test_maven_goals_parsing() {
        let dir = tempfile::tempdir().unwrap();
        let pom_file = dir.path().join("pom.xml");
        let mut file = fs::File::create(&pom_file).unwrap();
        writeln!(file, "<project><build><plugins><plugin><artifactId>spring-boot-maven-plugin</artifactId></plugin></plugins></build></project>").unwrap();

        let goals = get_maven_goals(&pom_file);
        assert!(goals.contains(&"spring-boot:run".to_string()));
    }
}
