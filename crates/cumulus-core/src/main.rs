mod beans;
mod codelens;
mod conflicts;
mod coverage;
mod dag;
mod dep_resolver;
mod endpoints;
mod gradle;
mod imports;
mod inspection_parser;
mod java_gen;
mod k8s_validator;
mod log_indexer;
mod log_parser;
mod maven;
mod migrations;
mod multimodule;
mod network;
mod test_context;
mod test_parser;

use clap::{Parser, Subcommand};
use std::fs;
use std::io::{self, Read};
use std::path::PathBuf;

#[derive(Parser)]
#[command(name = "cumulus-core")]
#[command(about = "High-performance Rust helper for Cumulus Neovim Distribution", long_about = None)]
struct Cli {
    #[command(subcommand)]
    command: Commands,
}

#[derive(Subcommand)]
enum Commands {
    /// Parse Maven pom.xml and output JSON array of goals
    ParsePom {
        #[arg(short, long)]
        file: PathBuf,
    },
    /// Parse Gradle tasks output or file content and output JSON array of tasks
    ParseGradleTasks {
        #[arg(short, long)]
        file: Option<PathBuf>,
    },
    /// Parse build log output and produce JSON array of diagnostics
    ParseBuildLog {
        #[arg(short, long)]
        tool: String,
        #[arg(short, long)]
        file: Option<PathBuf>,
    },
    /// Parse Maven pom.xml or Gradle settings.gradle for sub-modules
    ParseModules {
        #[arg(short, long)]
        tool: String,
        #[arg(short, long)]
        file: PathBuf,
    },
    /// Parse exception stack trace from log/console output
    ParseStacktrace {
        #[arg(short, long)]
        file: Option<PathBuf>,
    },
    /// Generate package declaration and class header for new Java file
    GenerateJavaHeader {
        #[arg(short, long)]
        file: String,
    },
    /// Parse test output (JUnit 5 / Maven / Gradle) and produce JSON array of test results
    ParseTestOutput {
        #[arg(short, long)]
        file: Option<PathBuf>,
    },
    /// Check network connectivity to remote Maven/Gradle repositories
    CheckNetwork {
        #[arg(short, long, default_value = "repo.maven.apache.org:443")]
        host: String,
        #[arg(short, long, default_value = "1500")]
        timeout: u64,
    },
    /// Parse Checkstyle XML report into JSON array of diagnostics
    ParseCheckstyle {
        #[arg(short, long)]
        file: Option<PathBuf>,
    },
    /// Extract Spring Boot / JAX-RS REST endpoints
    ExtractEndpoints {
        #[arg(short, long)]
        dir: PathBuf,
    },
    /// Parse JaCoCo coverage XML report
    ParseCoverage {
        #[arg(short, long)]
        file: PathBuf,
    },
    /// Validate Flyway migration scripts in directory
    ValidateMigrations {
        #[arg(short, long)]
        dir: PathBuf,
    },
    /// Extract Spring `@Component`/`@Service`/`@Bean` dependency graph
    ParseSpringBeans {
        #[arg(short, long)]
        dir: PathBuf,
    },
    /// Index large log files for ERROR and WARN messages
    IndexLog {
        #[arg(short, long)]
        file: Option<PathBuf>,
    },
    /// Optimize Java/Kotlin import statements
    OptimizeImports {
        #[arg(short, long)]
        file: Option<PathBuf>,
    },
    /// Validate Kubernetes manifest YAML syntax
    ValidateK8sManifest {
        #[arg(short, long)]
        file: Option<PathBuf>,
    },
    /// Parse Git conflict markers in file/buffer
    ParseGitConflicts {
        #[arg(short, long)]
        file: Option<PathBuf>,
    },
    /// Detect nearest test class and method at a given cursor line in a Java/Kotlin file
    DetectTestContext {
        #[arg(short, long)]
        file: PathBuf,
        #[arg(short, long, default_value = "1")]
        line: usize,
    },
    /// Resolve direct dependencies from Maven pom.xml or Gradle libs.versions.toml
    ResolveDeps {
        #[arg(short, long)]
        file: PathBuf,
    },
    /// Extract instant Java & Kotlin CodeLens items from source file
    ExtractCodelens {
        #[arg(short, long)]
        file: PathBuf,
    },
    /// Solve multi-module topological build order and DAG
    ComputeBuildOrder {
        #[arg(short, long)]
        dir: PathBuf,
    },
    /// Simple ping response to confirm binary readiness
    Ping,
}

fn main() -> io::Result<()> {
    let cli = Cli::parse();

    match cli.command {
        Commands::DetectTestContext { file, line } => {
            let ctx = test_context::detect_test_context(&file, line);
            let json = serde_json::to_string(&ctx).unwrap();
            println!("{}", json);
        }
        Commands::Ping => {
            println!(r#"{{"status":"ok","version":"0.1.0"}}"#);
        }
        Commands::ParsePom { file } => {
            let goals = maven::get_maven_goals(&file);
            let json = serde_json::to_string(&goals).unwrap();
            println!("{}", json);
        }
        Commands::ParseGradleTasks { file } => {
            let content = match file {
                Some(f) => fs::read_to_string(f)?,
                None => {
                    let mut stdin = String::new();
                    io::stdin().read_to_string(&mut stdin)?;
                    stdin
                }
            };
            let tasks = gradle::parse_gradle_tasks(&content);
            let json = serde_json::to_string(&tasks).unwrap();
            println!("{}", json);
        }
        Commands::ParseBuildLog { tool, file } => {
            let content = match file {
                Some(f) => fs::read_to_string(f)?,
                None => {
                    let mut stdin = String::new();
                    io::stdin().read_to_string(&mut stdin)?;
                    stdin
                }
            };
            let diags = match tool.to_lowercase().as_str() {
                "maven" | "mvn" => log_parser::parse_maven_log(&content),
                "gradle" => log_parser::parse_gradle_log(&content),
                _ => Vec::new(),
            };
            let json = serde_json::to_string(&diags).unwrap();
            println!("{}", json);
        }
        Commands::ParseModules { tool, file } => {
            let modules = match tool.to_lowercase().as_str() {
                "maven" | "mvn" => multimodule::parse_maven_modules(&file),
                "gradle" => multimodule::parse_gradle_modules(&file),
                _ => Vec::new(),
            };
            let json = serde_json::to_string(&modules).unwrap();
            println!("{}", json);
        }
        Commands::ParseStacktrace { file } => {
            let content = match file {
                Some(f) => fs::read_to_string(f)?,
                None => {
                    let mut stdin = String::new();
                    io::stdin().read_to_string(&mut stdin)?;
                    stdin
                }
            };
            let entries = log_parser::parse_stacktrace(&content);
            let json = serde_json::to_string(&entries).unwrap();
            println!("{}", json);
        }
        Commands::GenerateJavaHeader { file } => {
            let lines = java_gen::generate_java_header(&file);
            let json = serde_json::to_string(&lines).unwrap();
            println!("{}", json);
        }
        Commands::ParseTestOutput { file } => {
            let content = match file {
                Some(f) => fs::read_to_string(f)?,
                None => {
                    let mut stdin = String::new();
                    io::stdin().read_to_string(&mut stdin)?;
                    stdin
                }
            };
            let results = test_parser::parse_test_output(&content);
            let json = serde_json::to_string(&results).unwrap();
            println!("{}", json);
        }
        Commands::CheckNetwork { host, timeout } => {
            let online = network::check_connectivity(&host, timeout);
            println!(r#"{{"online":{}}}"#, online);
        }
        Commands::ParseCheckstyle { file } => {
            let content = match file {
                Some(f) => fs::read_to_string(f)?,
                None => {
                    let mut stdin = String::new();
                    io::stdin().read_to_string(&mut stdin)?;
                    stdin
                }
            };
            let diags = inspection_parser::parse_checkstyle_xml(&content);
            let json = serde_json::to_string(&diags).unwrap();
            println!("{}", json);
        }
        Commands::ExtractEndpoints { dir } => {
            let eps = endpoints::extract_endpoints_from_dir(&dir);
            let json = serde_json::to_string(&eps).unwrap();
            println!("{}", json);
        }
        Commands::ParseCoverage { file } => {
            let cov = coverage::parse_coverage_file(&file);
            let json = serde_json::to_string(&cov).unwrap();
            println!("{}", json);
        }
        Commands::ValidateMigrations { dir } => {
            let issues = migrations::validate_migrations_dir(&dir);
            let json = serde_json::to_string(&issues).unwrap();
            println!("{}", json);
        }
        Commands::ParseSpringBeans { dir } => {
            let beans = beans::extract_beans_from_dir(&dir);
            let json = serde_json::to_string(&beans).unwrap();
            println!("{}", json);
        }
        Commands::IndexLog { file } => {
            let content = match file {
                Some(f) => fs::read_to_string(f)?,
                None => {
                    let mut stdin = String::new();
                    io::stdin().read_to_string(&mut stdin)?;
                    stdin
                }
            };
            let entries = log_indexer::index_log_content(&content);
            let json = serde_json::to_string(&entries).unwrap();
            println!("{}", json);
        }
        Commands::OptimizeImports { file } => {
            let content = match file {
                Some(f) => fs::read_to_string(f)?,
                None => {
                    let mut stdin = String::new();
                    io::stdin().read_to_string(&mut stdin)?;
                    stdin
                }
            };
            let lines = imports::optimize_imports(&content);
            let json = serde_json::to_string(&lines).unwrap();
            println!("{}", json);
        }
        Commands::ValidateK8sManifest { file } => {
            let content = match file {
                Some(f) => fs::read_to_string(f)?,
                None => {
                    let mut stdin = String::new();
                    io::stdin().read_to_string(&mut stdin)?;
                    stdin
                }
            };
            let issues = k8s_validator::validate_k8s_manifest_content(&content);
            let json = serde_json::to_string(&issues).unwrap();
            println!("{}", json);
        }
        Commands::ParseGitConflicts { file } => {
            let content = match file {
                Some(f) => fs::read_to_string(f)?,
                None => {
                    let mut stdin = String::new();
                    io::stdin().read_to_string(&mut stdin)?;
                    stdin
                }
            };
            let blocks = conflicts::parse_git_conflicts_content(&content);
            let json = serde_json::to_string(&blocks).unwrap();
            println!("{}", json);
        }
        Commands::ResolveDeps { file } => {
            let deps = dep_resolver::resolve_dependencies(&file);
            let json = serde_json::to_string(&deps).unwrap();
            println!("{}", json);
        }
        Commands::ExtractCodelens { file } => {
            let items = codelens::extract_codelens(&file);
            let json = serde_json::to_string(&items).unwrap();
            println!("{}", json);
        }
        Commands::ComputeBuildOrder { dir } => {
            let steps = dag::compute_build_order(&dir);
            let json = serde_json::to_string(&steps).unwrap();
            println!("{}", json);
        }
    }

    Ok(())
}
