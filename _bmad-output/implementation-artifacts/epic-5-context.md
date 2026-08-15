# Epic 5 Context: DevOps, Integrity & Platform Tools

<!-- Compiled from planning artifacts. Edit freely. Regenerate with compile-epic-context if planning docs change. -->

## Goal

Provide high-speed DevOps, codebase integrity, and environment validation tools within the Scala native engine. This includes parsing JaCoCo coverage and Checkstyle XML reports via `scala-xml`, validating Flyway migrations and Kubernetes manifests, detecting Git conflict markers, sanitizing Neovim sessions, checking network sockets, verifying Gradle wrappers and dependencies, and managing cloud theme state.

## Stories

- Story 5.1: JaCoCo Coverage & Checkstyle Report Parsers (scala-xml) (`parse-coverage`, `parse-checkstyle`)
- Story 5.2: Flyway Migration, Kubernetes Manifest & Git Conflict Validators (`validate-migrations`, `validate-k8s-manifest`, `parse-git-conflicts`)
- Story 5.3: Session Sanitizer, Gradle Wrapper Verifier, Network Checker & Dependency Tools (`session-sanitize`, `verify-gradle-wrapper`, `check-network`, `check-jdtls-sync`, `resolve-deps`, `check-dep-versions`)
- Story 5.4: Cloud Theme State Management (`manage-theme`)

## Requirements & Constraints

- **Protocol Backward Compatibility (`SPEC-031`)**: Every CLI subcommand must wrap its JSON output in the standard `CumulusResponse[T]` envelope (`{ "success": Boolean, "data": Option[T], "error": Option[String], "error_code": Option[String] }`).
- **Zero Runtime Reflection**: All data structures must define compile-time `ReadWriter` instances via `uPickle` (`upickle.default.macroRW`). No Jackson, Gson, or unconfigured reflection.
- **XML Parsing Standard (NFR6)**: All XML report parsing (JaCoCo, Checkstyle) must use `scala-xml` (`org.scala-lang.modules::scala-xml`) with node traversal (`\` and `\\`). No regex-based XML parsing or SAX event streaming.
- **Filesystem Standard (NFR7)**: All file I/O, path checks, and directory traversals must use `os-lib`.
- **Stream I/O**: Checkstyle, K8s manifests, and Git conflict blocks may be passed via stdin or file paths. Coverage, migrations, sessions, etc. take flag arguments (`--file`, `--dir`).
- **Stdout Exclusivity**: Stdout is reserved exclusively for JSON response payloads. Diagnostic messages or errors must go to stderr or error fields.

## Technical Decisions

- **Package Topology**:
  - JaCoCo & Checkstyle parsers: `cumulus.devops.CoverageParser`, `cumulus.devops.CheckstyleParser`
  - Flyway & K8s validators: `cumulus.devops.MigrationValidator`, `cumulus.devops.K8sValidator`
  - Git conflict parser: `cumulus.git.ConflictParser`
  - Session & Network utils: `cumulus.util.SessionSanitizer`, `cumulus.util.NetworkChecker`
  - Theme manager: `cumulus.theme.ThemeManager`
  - Gradle wrapper & Dependencies: `cumulus.gradle.WrapperVerifier`, `cumulus.dep.DepResolver`, `cumulus.dep.DepLens`
- **CLI Subcommand Names**: Exact match with SPEC-031: `parse-coverage`, `parse-checkstyle`, `validate-migrations`, `validate-k8s-manifest`, `parse-git-conflicts`, `session-sanitize`, `verify-gradle-wrapper`, `check-network`, `check-jdtls-sync`, `resolve-deps`, `check-dep-versions`, `manage-theme`.
- **Data Models**:
  - `CoverageReport(files: List[FileCoverage])`, `FileCoverage(path: String, coveredLines: List[Int], missedLines: List[Int], lineCoverage: Double)`
  - `CheckstyleReport(issues: List[CheckstyleIssue])`, `CheckstyleIssue(file: String, line: Int, column: Int, severity: String, message: String, source: Option[String])`

## Cross-Story Dependencies

- Builds on engine infrastructure from Epics 1–4 (CLI routing in `cumulus.Main`, envelope in `cumulus.protocol.Envelope`).
- Supplies subcommands consumed by Lua integrations in Epic 6 (`lua/cumulus/util/engine.lua`).
