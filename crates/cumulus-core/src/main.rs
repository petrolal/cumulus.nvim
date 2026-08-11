mod beans;
mod codelens;
mod conflicts;
mod coverage;
mod dag;
mod dep_resolver;
mod endpoints;
mod gradle;
mod gradle_wrapper;
mod imports;
mod inspection_parser;
mod java_gen;
mod jdtls_sync;
mod k8s_validator;
mod log_indexer;
mod log_parser;
mod maven;
mod migrations;
mod multimodule;
mod network;
mod session_cleaner;
mod test_context;
mod test_parser;

use clap::{Parser, Subcommand};
use cumulus_core::{CumulusError, CumulusResponse};
use std::fs;
use std::io::{self, Read};
use std::path::PathBuf;

/// Read input from file or stdin
fn read_input(file: Option<PathBuf>) -> io::Result<String> {
    match file {
        Some(f) => fs::read_to_string(f),
        None => {
            let mut stdin = String::new();
            io::stdin().read_to_string(&mut stdin)?;
            Ok(stdin)
        }
    }
}

/// Output JSON response to stdout, wrapping data in CumulusResponse envelope
fn output_success<T: serde::Serialize>(value: T, _context: &str) {
    let resp = CumulusResponse::ok(value);
    if let Ok(json) = serde_json::to_string(&resp) {
        println!("{}", json);
    } else {
        eprintln!("cumulus-core: JSON serialization failed");
    }
}

/// Output error response to stderr
fn output_error(err: CumulusError) {
    let resp = CumulusResponse::<()>::err(err);
    if let Ok(json) = serde_json::to_string(&resp) {
        eprintln!("{}", json);
    }
}

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
    /// Check JDTLS classpath sync status by scanning build config mtime
    CheckJdtlsSync {
        #[arg(short, long)]
        dir: PathBuf,
        #[arg(short, long)]
        start_time: u64,
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
    /// Verify Gradle wrapper configuration against CI workflows and SHA-256
    VerifyGradleWrapper {
        #[arg(short, long)]
        dir: PathBuf,
    },
    /// Sanitize a Neovim session file by removing ephemeral buffers and floating windows
    SessionSanitize {
        #[arg(short, long)]
        file: PathBuf,
    },
    /// Simple ping response to confirm binary readiness
    Ping,
}

fn main() {
    let cli = Cli::parse();

    if let Err(e) = run(cli) {
        output_error(e);
        std::process::exit(1);
    }
}

fn run(cli: Cli) -> Result<(), CumulusError> {
    match cli.command {
        Commands::CheckJdtlsSync { dir, start_time } => {
            let status = jdtls_sync::check_jdtls_sync(&dir, start_time);
            output_success(status, "check-jdtls-sync");
        }
        Commands::DetectTestContext { file, line } => {
            let ctx = test_context::detect_test_context(&file, line);
            output_success(ctx, "detect-test-context");
        }
        Commands::Ping => {
            #[derive(serde::Serialize)]
            struct PingResponse {
                status: String,
                version: String,
            }
            let resp = PingResponse {
                status: "ok".to_string(),
                version: "0.1.0".to_string(),
            };
            output_success(resp, "ping");
        }
        Commands::ParsePom { file } => {
            let goals = maven::get_maven_goals(&file);
            output_success(goals, "parse-pom");
        }
        Commands::ParseGradleTasks { file } => {
            let content = read_input(file)
                .map_err(|e| CumulusError::Internal(format!("Failed to read input: {}", e)))?;
            let tasks = gradle::parse_gradle_tasks(&content);
            output_success(tasks, "parse-gradle-tasks");
        }
        Commands::ParseBuildLog { tool, file } => {
            let content = read_input(file)
                .map_err(|e| CumulusError::Internal(format!("Failed to read input: {}", e)))?;
            let diags = match tool.to_lowercase().as_str() {
                "maven" | "mvn" => log_parser::parse_maven_log(&content),
                "gradle" => log_parser::parse_gradle_log(&content),
                _ => Vec::new(),
            };
            output_success(diags, "parse-build-log");
        }
        Commands::ParseModules { tool, file } => {
            let modules = match tool.to_lowercase().as_str() {
                "maven" | "mvn" => multimodule::parse_maven_modules(&file),
                "gradle" => multimodule::parse_gradle_modules(&file),
                _ => Vec::new(),
            };
            output_success(modules, "parse-modules");
        }
        Commands::ParseStacktrace { file } => {
            let content = read_input(file)
                .map_err(|e| CumulusError::Internal(format!("Failed to read input: {}", e)))?;
            let entries = log_parser::parse_stacktrace(&content);
            output_success(entries, "parse-stacktrace");
        }
        Commands::GenerateJavaHeader { file } => {
            let lines = java_gen::generate_java_header(&file);
            output_success(lines, "generate-java-header");
        }
        Commands::ParseTestOutput { file } => {
            let content = read_input(file)
                .map_err(|e| CumulusError::Internal(format!("Failed to read input: {}", e)))?;
            let results = test_parser::parse_test_output(&content);
            output_success(results, "parse-test-output");
        }
        Commands::CheckNetwork { host, timeout } => {
            let online = network::check_connectivity(&host, timeout);
            #[derive(serde::Serialize)]
            struct NetworkResponse {
                online: bool,
            }
            let resp = NetworkResponse { online };
            output_success(resp, "check-network");
        }
        Commands::ParseCheckstyle { file } => {
            let content = read_input(file)
                .map_err(|e| CumulusError::Internal(format!("Failed to read input: {}", e)))?;
            let diags = inspection_parser::parse_checkstyle_xml(&content);
            output_success(diags, "parse-checkstyle");
        }
        Commands::ExtractEndpoints { dir } => {
            let eps = endpoints::extract_endpoints_from_dir(&dir);
            output_success(eps, "extract-endpoints");
        }
        Commands::ParseCoverage { file } => {
            let cov = coverage::parse_coverage_file(&file);
            output_success(cov, "parse-coverage");
        }
        Commands::ValidateMigrations { dir } => {
            let issues = migrations::validate_migrations_dir(&dir);
            output_success(issues, "validate-migrations");
        }
        Commands::ParseSpringBeans { dir } => {
            let beans = beans::extract_beans_from_dir(&dir);
            output_success(beans, "parse-spring-beans");
        }
        Commands::IndexLog { file } => {
            let content = read_input(file)
                .map_err(|e| CumulusError::Internal(format!("Failed to read input: {}", e)))?;
            let entries = log_indexer::index_log_content(&content);
            output_success(entries, "index-log");
        }
        Commands::OptimizeImports { file } => {
            let content = read_input(file)
                .map_err(|e| CumulusError::Internal(format!("Failed to read input: {}", e)))?;
            let lines = imports::optimize_imports(&content);
            output_success(lines, "optimize-imports");
        }
        Commands::ValidateK8sManifest { file } => {
            let content = read_input(file)
                .map_err(|e| CumulusError::Internal(format!("Failed to read input: {}", e)))?;
            let issues = k8s_validator::validate_k8s_manifest_content(&content);
            output_success(issues, "validate-k8s-manifest");
        }
        Commands::ParseGitConflicts { file } => {
            let content = read_input(file)
                .map_err(|e| CumulusError::Internal(format!("Failed to read input: {}", e)))?;
            let blocks = conflicts::parse_git_conflicts_content(&content);
            output_success(blocks, "parse-git-conflicts");
        }
        Commands::ResolveDeps { file } => {
            let deps = dep_resolver::resolve_dependencies(&file);
            output_success(deps, "resolve-deps");
        }
        Commands::ExtractCodelens { file } => {
            let items = codelens::extract_codelens(&file);
            output_success(items, "extract-codelens");
        }
        Commands::ComputeBuildOrder { dir } => {
            let steps = dag::compute_build_order(&dir);
            output_success(steps, "compute-build-order");
        }
        Commands::VerifyGradleWrapper { dir } => {
            let status = gradle_wrapper::verify_gradle_wrapper(&dir);
            output_success(status, "verify-gradle-wrapper");
        }
        Commands::SessionSanitize { file } => {
            let result = session_cleaner::sanitize_session(&file);
            output_success(result, "session-sanitize");
        }
    }

    Ok(())
}
