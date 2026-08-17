---
stepsCompleted: ["step-01-validate-prerequisites", "step-02-design-epics", "step-03-create-stories", "step-04-final-validation"]
inputDocuments: ["_bmad-output/planning-artifacts/architecture/ARCHITECTURE-SPINE.md", "epic-review.md (Winston's Epic Review & Scala Enhancement Proposals)"]
---

# cumulus.nvim - Epic Breakdown

## Overview

This document provides the complete epic and story breakdown for cumulus.nvim, decomposing the requirements from the Architecture Spine (Scala 3 + GraalVM Native Engine Migration), Winston's Epic Review (Lua absorption, Scala upgrades, CI/CD), and codebase analysis into implementable stories.

## Requirements Inventory

### Functional Requirements

FR1: Create Scala 3 + GraalVM Native Engine (`engine/`) to replace Rust native binary (`crates/cumulus-core`), with SBT project structure using `sbt-native-packager` (`GraalVMNativeImagePlugin`) and `sbt-buildinfo` for compile-time version metadata injection.
FR2: Implement CLI Subcommand Router in Scala matching all `SPEC-031` subcommands 1-to-1 (`parse-pom`, `parse-gradle-tasks`, `compute-build-order`, `detect-springboot-app`, `extract-codelens`, `parse-spring-beans`, `extract-endpoints`, `validate-migrations`, `validate-k8s-manifest`, `session-sanitize`, `resolve-deps`, `check-dep-versions`, `check-network`, `parse-checkstyle`, `parse-coverage`, `parse-git-conflicts`, `index-log`, `parse-build-log`, `parse-stacktrace`, `resolve-stacktrace-symbol`, `verify-gradle-wrapper`, `check-jdtls-sync`, `detect-test-context`, `parse-test-output`, `generate-java-header`, `optimize-imports`, `ping`).
FR3: Implement zero-reflection compile-time JSON serialization via `uPickle` using `CumulusResponse[T]` envelope `{ success: Boolean, data: Option[T], error: Option[String], error_code: Option[String] }`.
FR4: Implement Build & Multi-Module analysis in Scala using `scala-xml` for POM parsing (`MavenParser`), regex for Gradle output (`GradleParser`), and Kahn's topological sort for DAG build ordering (`DagSolver`).
FR5: Implement Code & Framework Analysis in Scala (`CodeLensExtractor`, `AppDetector`, `BeanGraph`, `EndpointScanner`, `ImportOptimizer`, `JavaHeaderGenerator`).
FR6: Implement Testing & Diagnostic Log Analyzers in Scala (`TestContextDetector`, `TestParser`, `LogParser`, `StacktraceResolver`, `LogIndexer`).
FR7: Implement DevOps & Integrity Tooling in Scala (`CoverageParser` via `scala-xml`, `MigrationValidator`, `K8sValidator`, `ConflictParser`, `SessionSanitizer`, `GradleWrapperVerifier`, `NetworkChecker`, `CheckstyleParser` via `scala-xml`).
FR8: Implement Lua Business Logic Absorption subcommands: `discover-jdk`, `discover-build-tool`, `assemble-test-command`, `discover-workspace`, and `manage-theme` — moving ~492 lines of business logic from Lua into the Scala engine.
FR9: Migrate Neovim Lua integration layer from `rust.lua` to `engine.lua`, refactor all `util/` files to delegate to new engine subcommands, and update `health.lua` to verify GraalVM native binary.
FR10: Purge all Rust codebase (`crates/cumulus-core/`), Cargo configuration files, and references.
FR11: Implement GitHub Actions CI/CD pipeline for cross-platform GraalVM native-image compilation (x86_64-linux, aarch64-linux, darwin-arm64) with automated release binary publishing.
FR12: Implement auto-download mechanism in `engine.lua` to fetch pre-built platform binaries from GitHub Releases with SHA-256 verification.
FR13: Implement Terraform & OpenTofu Tooling Suite (`<leader>ot`) with init, validate, plan, apply, fmt, tflint, security audits (trivy/tfsec), and state outputs in non-blocking interactive terminals.
FR14: Implement AWS CloudFormation & SAM Suite (`<leader>oc`) with template validation, cfn-lint diagnostics, sam validate, sam build, sam local invoke, sam local start-api, and cfn-guard.
FR15: Implement Ansible Automation Suite (`<leader>oy`) with playbook syntax checking (--syntax-check), ansible-lint, dry-run checks (--check), interactive execution, inventory graph inspection, vault management, and ansible-doc.
FR16: Implement DevOps Buffer-Scoped Dynamic Keymaps and Which-Key Registration (`<leader>o` hierarchy with `<leader>ot`, `<leader>oc`, `<leader>oy`, `<leader>od`, `<leader>ok`).
FR17: Integrate DevOps Mason Tool Provisioning, Diagnostics Linting (`nvim-lint`), and Autoformatting (`conform.nvim`).
FR18: Interactive non-blocking terminal execution (`Snacks.terminal` / split buffers) for all DevOps CLI tools.
FR19: Implement DevOps Workspace Root Discovery for Terraform/OpenTofu, AWS CloudFormation/SAM, Ansible, Docker, and Helm/Kubernetes across project directories.
FR20: Implement Global DevOps Platform Execution without requiring an active buffer, running workspace-level tools in the detected root directory.
FR21: Implement Smart File vs Workspace Fallbacks for single-file operations (format, lint) when invoked from non-DevOps buffers.
FR22: Implement Proactive Workspace Diagnostics and warning toasts when DevOps commands are invoked in workspaces lacking the target tool's configuration.
FR23: Ensure WhichKey registers and displays `<leader>o` and all DevOps sub-groups (`<leader>ot`, `<leader>oc`, `<leader>oy`, `<leader>od`, `<leader>ok`) with icons and descriptions globally.

### NonFunctional Requirements

NFR1: GraalVM native binary startup latency under 10ms for CLI process invocation via `vim.system()`.
NFR2: 100% backward compatibility with `SPEC-031` JSON output envelope schemas.
NFR3: Zero runtime reflection configuration required for GraalVM `native-image` compilation (AD-2). All serialization via uPickle compile-time macros. Reflection-based serializers (Jackson, Gson, snakeyaml) are strictly prohibited unless accompanied by targeted `reflect-config.json`.
NFR4: Graceful fallback and clear health check diagnostics in Neovim when binary is uncompiled or unavailable.
NFR5: Scala codebase must be the dominant portion of the project — Lua files reduced to pure Neovim UI glue (keymaps, pickers, plugin specs) with zero business logic.
NFR6: All XML parsing (POM, JaCoCo, Checkstyle) must use `scala-xml` with XPath-like node selectors (`\` and `\\`) — no regex-based XML parsing or SAX event streaming.
NFR7: Filesystem operations must use `os-lib` for clean, zero-reflection directory traversal and file I/O.
NFR8: All DevOps execution must run in interactive, non-blocking terminal sessions (`Snacks.terminal` or split buffers) without locking Neovim UI.
NFR9: Project root marker discovery must execute in < 5ms using Neovim's `vim.fs.root()`.
NFR10: The developer experience and error feedback of `<leader>o` must strictly mirror the UX conventions of the JVM platform suite (`<leader>j`).

### Additional Requirements

- Engine directory: `{project-root}/engine/`
- Build System: `sbt` with `sbt-native-packager` (`GraalVMNativeImagePlugin`) and `sbt-buildinfo`
- Language: Scala 3.4+
- Serialization Library: `uPickle` (`upickle.default.*`)
- Filesystem Library: `os-lib` (`com.lihaoyi::os-lib`)
- XML Library: `scala-xml` (`org.scala-lang.modules::scala-xml`)
- Test Framework: `munit` (`org.scalameta::munit`)
- CLI Routing: Direct Scala pattern matching on `args: Array[String]` (AD-3)
- Stdout reserved exclusively for JSON payload; all logging/debug text to stderr (AD-5)
- SBT project support (parse-sbt-tasks, discover-build-tool) for Scala developer audience
- Cross-platform binary distribution via GitHub Actions and GitHub Releases

### UX Design Requirements

N/A (Backend Engine & Platform Tooling)

### FR Coverage Map

FR1: Epic 1 (Story 1.1) — SBT project setup with sbt-native-packager, sbt-buildinfo, and all dependencies
FR2: Epic 1 (1.2 — ping), Epic 2 (2.1, 2.2, 2.3, 2.4), Epic 3 (3.1, 3.2, 3.3), Epic 4 (4.1, 4.2, 4.3, 4.4), Epic 5 (5.1, 5.2, 5.3, 5.4)
FR3: Epic 1 (Story 1.2) — CumulusResponse[T] envelope with uPickle macroRW
FR4: Epic 2 (Story 2.1, 2.2, 2.3) — Maven scala-xml parser, Gradle regex parser, Kahn's DAG solver
FR5: Epic 3 (Story 3.1, 3.2, 3.3) — CodeLens, Spring Boot/Beans, Endpoints/Imports/JavaHeader
FR6: Epic 4 (Story 4.1, 4.2, 4.3) — Test context/output, build log/stacktrace, log indexer
FR7: Epic 5 (Story 5.1, 5.2, 5.3) — Coverage/Checkstyle/Migrations, K8s/Git/Session, Wrapper/Network/Deps
FR8: Epic 2 (2.4 — discover-jdk, discover-build-tool, discover-workspace), Epic 4 (4.4 — assemble-test-command), Epic 5 (5.4 — manage-theme)
FR9: Epic 6 (Story 6.1) — engine.lua bridge + util/ refactoring
FR10: Epic 6 (Story 6.2) — Complete Rust purge
FR11: Epic 7 (Story 7.1) — GitHub Actions CI/CD pipeline
FR12: Epic 7 (Story 7.2) — Auto-download binary mechanism
FR13: Epic 8 (Story 8.1) — Terraform/OpenTofu CLI, linter & security suite
FR14: Epic 8 (Story 8.2) — AWS CloudFormation & SAM validation & local test suite
FR15: Epic 8 (Story 8.3) — Ansible syntax, lint, execution & inventory suite
FR16: Epic 8 (Story 8.4) — DevOps buffer-scoped dynamic keymaps & WhichKey registration
FR17: Epic 8 (Story 8.4) — Mason tool provisioning, nvim-lint & conform integration
FR18: Epic 8 (Story 8.1, 8.2, 8.3) — Non-blocking terminal execution
FR19: Epic 9 (Story 9.1) — DevOps workspace root discovery
FR20: Epic 9 (Story 9.1, 9.2) — Global DevOps platform execution without active buffer
FR21: Epic 9 (Story 9.2) — Smart file vs workspace fallback for lint and format
FR22: Epic 9 (Story 9.1, 9.2) — Proactive workspace diagnostics for missing configurations
FR23: Epic 9 (Story 9.3) — WhichKey global group consistency & keymap scoping

## Epic List

### Epic 1: Scala 3 Engine Foundation & Standard Protocol
Developers can compile and run `cumulus-engine` with GraalVM native-image, receiving standardized JSON responses. The SBT project is configured with all dependencies (uPickle, os-lib, scala-xml, munit), sbt-buildinfo injects compile-time metadata, and the `CumulusResponse[T]` envelope + CLI router are operational.
**FRs covered:** FR1, FR2 (ping), FR3

### Epic 2: Build Tooling, Multi-Module DAG & Workspace Intelligence
JVM developers can query Maven goals, Gradle tasks, SBT tasks, sub-modules, and compute topological DAG build ordering. The engine discovers project roots, build tools, and JDK installations — absorbing workspace discovery logic previously in Lua.
**FRs covered:** FR2 (build commands), FR4, FR8 (discover-jdk, discover-build-tool, discover-workspace)

### Epic 3: Code Intelligence & Framework Analysis
JVM developers receive instant Java/Kotlin CodeLens hints, Spring Boot debug configurations, Spring Bean dependency graphs, REST endpoint extraction, import optimization, and Java header generation — all using scala-xml for XML parsing where applicable.
**FRs covered:** FR2 (code commands), FR5

### Epic 4: Testing, Diagnostics & Log Processing
Developers can detect test context at cursor, assemble platform-correct test CLI commands (Maven/Gradle/SBT), parse test results, resolve stacktrace frames to source files, parse build logs, and index high-speed log files.
**FRs covered:** FR2 (test/log commands), FR6, FR8 (assemble-test-command)

### Epic 5: DevOps, Integrity & Platform Tools
Developers can analyze JaCoCo coverage (scala-xml), validate Checkstyle reports (scala-xml), validate Flyway migrations, validate Kubernetes manifests, parse Git conflicts, sanitize Neovim sessions, verify Gradle wrappers, manage cloud theme state, and check network connectivity.
**FRs covered:** FR2 (devops commands), FR7, FR8 (manage-theme)

### Epic 6: Neovim Lua Bridge Migration & Complete Rust Removal
Neovim connects to `cumulus-engine` via the new `engine.lua` bridge. All `util/` files are refactored to delegate business logic to engine subcommands. The Lua codebase is pure UI glue. Health checks verify the GraalVM binary. All Rust code (`crates/cumulus-core/`) is completely purged.
**FRs covered:** FR9, FR10

### Epic 7: CI/CD Pipeline & Cross-Platform Distribution
Pre-built native binaries are automatically compiled for x86_64-linux, aarch64-linux, and darwin-arm64 via GitHub Actions. End users can auto-download the correct binary without installing SBT or GraalVM.
**FRs covered:** FR11, FR12

### Epic 8: DevOps & Infrastructure Platform Suite (<leader>o)
DevOps and Cloud engineers have access to a complete compiler, validator, linter, test, and execution toolchain for Terraform/OpenTofu, AWS CloudFormation/SAM, and Ansible under `<leader>o`, with buffer-local keymap scoping, Mason package provisioning, and non-blocking interactive terminals.
**FRs covered:** FR13, FR14, FR15, FR16, FR17, FR18

### Epic 9: DevOps Project-Root Discovery & Global Platform Suite (<leader>o)
DevOps and Cloud engineers can execute infrastructure and cloud automation tools from any open buffer in Neovim, with automatic root directory detection, non-blocking execution, smart fallbacks for single-file tools, and proactive diagnostics.
**FRs covered:** FR19, FR20, FR21, FR22, FR23

---

## Epic 1: Scala 3 Engine Foundation & Standard Protocol

Developers can compile and run `cumulus-engine` with GraalVM native-image, receiving standardized JSON responses. The SBT project is configured with all dependencies (uPickle, os-lib, scala-xml, munit), sbt-buildinfo injects compile-time metadata, and the `CumulusResponse[T]` envelope + CLI router are operational.

### Story 1.1: SBT Scala 3 Engine Project Initialization

As a maintainer of `cumulus.nvim`,
I want an SBT project configured with Scala 3.4+, `sbt-native-packager`, `sbt-buildinfo`, and all core dependencies (uPickle, os-lib, scala-xml, munit),
So that `cumulus-engine` compiles to a GraalVM native binary with compile-time metadata.

**Acceptance Criteria:**

**Given** the directory `{project-root}/engine`
**When** `sbt graalvm-native-image:packageBin` is executed
**Then** the build succeeds and produces an AOT compiled binary named `cumulus-engine`
**And** the binary startup latency is under 10ms
**And** `build.sbt` declares dependencies on uPickle, os-lib, scala-xml, and munit (test)
**And** `project/plugins.sbt` includes `sbt-native-packager` and `sbt-buildinfo`
**And** `sbt-buildinfo` generates `BuildInfo.version`, `BuildInfo.scalaVersion`, `BuildInfo.gitCommit`, and `BuildInfo.buildTime`
**And** `project/build.properties` specifies `sbt.version=1.9.9`

### Story 1.2: Protocol Envelope (`CumulusResponse[T]`) & CLI Router with Ping

As a developer calling `cumulus-engine`,
I want CLI subcommands routed via Scala pattern matching and output formatted as uPickle `CumulusResponse[T]`,
So that responses conform strictly to the `SPEC-031` envelope schema.

**Acceptance Criteria:**

**Given** the `cumulus-engine` binary
**When** `cumulus-engine ping` is executed
**Then** stdout returns `{"success":true,"data":{"status":"ok","version":"<BuildInfo.version>","scala":"<BuildInfo.scalaVersion>","commit":"<BuildInfo.gitCommit>","built":"<BuildInfo.buildTime>"},"error":null,"error_code":null}`
**And** uPickle macro derivation handles serialization with zero runtime reflection
**And** `CumulusError` enum defines `FILE_NOT_FOUND`, `PARSE_ERROR`, `INVALID_INPUT`, `NETWORK_ERROR`, `TIMEOUT`, `INTERNAL_ERROR` error codes
**And** error responses set `success: false` with populated `error` and `error_code` fields
**And** unknown subcommands return an `INVALID_INPUT` error response
**And** all debug/log output goes to stderr, never stdout

---

## Epic 2: Build Tooling, Multi-Module DAG & Workspace Intelligence

JVM developers can query Maven goals, Gradle tasks, SBT tasks, sub-modules, and compute topological DAG build ordering. The engine discovers project roots, build tools, and JDK installations — absorbing workspace discovery logic previously in Lua.

### Story 2.1: Maven Goals & Submodule Parser (scala-xml)

As a Maven developer,
I want `cumulus-engine parse-pom` and `parse-modules --tool maven`,
So that Maven lifecycle goals, plugin goals, and submodules are extracted from `pom.xml` using type-safe scala-xml node traversal.

**Acceptance Criteria:**

**Given** a project with `pom.xml`
**When** `cumulus-engine parse-pom --file pom.xml` is called
**Then** stdout returns JSON containing lifecycle goals (`clean`, `compile`, `test`, `install`) and plugin-specific goals (Spring Boot `spring-boot:run`, Quarkus, Surefire, Failsafe, Exec)
**And** `parse-modules --tool maven --file pom.xml` returns module names and relative paths via `(pom \ "modules" \ "module")` scala-xml traversal
**And** output envelope matches `SPEC-031` format exactly
**And** no regex or SAX event streaming is used for XML parsing

### Story 2.2: Gradle Task & Submodule Parser

As a Gradle developer,
I want `cumulus-engine parse-gradle-tasks` and `parse-modules --tool gradle`,
So that Gradle tasks from `./gradlew tasks` output and `settings.gradle` included modules are extracted.

**Acceptance Criteria:**

**Given** Gradle tasks output text via stdin or `settings.gradle` file
**When** `parse-gradle-tasks` (via stdin) or `parse-modules --tool gradle --file settings.gradle` is invoked
**Then** stdout returns parsed task names (deduplicated via `Set`, with standard aliases like `clean test`, `clean build`)
**And** module list includes project names and relative paths parsed from `include '...'` directives
**And** output uses `CumulusResponse[T]` envelope

### Story 2.3: Multi-Module Topological Build Order Solver (Kahn's DAG)

As a developer working on a multi-module project,
I want `cumulus-engine compute-build-order --dir <path>`,
So that Kahn's algorithm computes the correct topological build order across Maven or Gradle modules.

**Acceptance Criteria:**

**Given** a multi-module Maven or Gradle repository
**When** `cumulus-engine compute-build-order --dir .` is run
**Then** stdout returns ordered `ModuleBuildStep` entries with step number, module name, path, and build command
**And** circular dependencies fall back safely to raw module declaration order
**And** cross-module `<dependency>` references in POM are parsed via scala-xml `(dep \ "groupId")`, `(dep \ "artifactId")`
**And** `os-lib` is used for directory traversal to discover module POMs

### Story 2.4: JDK Discovery, Build Tool Detection & Workspace Discovery

As a JVM developer using Neovim,
I want `cumulus-engine discover-jdk`, `discover-build-tool`, and `discover-workspace`,
So that JDK home resolution, build tool detection, and project root finding happen in the engine instead of Lua.

**Acceptance Criteria:**

**Given** a system with JDK 21 installed under `/usr/lib/jvm/` or `~/.sdkman/candidates/java/`
**When** `cumulus-engine discover-jdk --version 21` is executed
**Then** stdout returns `{"success":true,"data":{"java_home":"/usr/lib/jvm/java-21-openjdk","version":"21.0.3"}}`
**And** JDK discovery scans `/usr/lib/jvm/java-<version>*`, `~/.sdkman/candidates/java/<version>*`, and `$JAVA_HOME`
**And** `discover-build-tool --dir .` returns detected tool (`maven`/`gradle`/`sbt`), wrapper path (`./mvnw`, `./gradlew`), executable status, and `chmod +x` recommendation if needed
**And** `discover-workspace --dir .` returns project root path, detected build files (`pom.xml`, `build.gradle`, `build.gradle.kts`, `build.sbt`), and multi-module indicator
**And** `os-lib` is used for all filesystem scanning

---

## Epic 3: Code Intelligence & Framework Analysis

JVM developers receive instant Java/Kotlin CodeLens hints, Spring Boot debug configurations, Spring Bean dependency graphs, REST endpoint extraction, import optimization, and Java header generation — all using scala-xml for XML parsing where applicable.

### Story 3.1: Java & Kotlin CodeLens Extractor

As a Java/Kotlin developer,
I want `cumulus-engine extract-codelens --file <path>`,
So that `@Test`, `main`, `@Scheduled`, and event listeners render CodeLens action hints.

**Acceptance Criteria:**

**Given** a `.java` or `.kt` source file
**When** `cumulus-engine extract-codelens --file <path>` is executed
**Then** stdout returns `CodeLensItem` entries with line numbers and titles ("▶ Run Test", "▶ Run Main", "⏰ Scheduled Task", "🎧 Event Listener")
**And** `@KafkaListener` and `@EventListener` annotations are detected
**And** `os-lib` is used to read the source file

### Story 3.2: Spring Boot Debug Config & Bean Dependency Graph

As a Spring Boot developer,
I want `cumulus-engine detect-springboot-app` and `parse-spring-beans`,
So that main classes, active profiles, JVM debug args, and `@Component` dependency graphs are extracted.

**Acceptance Criteria:**

**Given** a Spring Boot codebase
**When** `detect-springboot-app --dir .` is invoked
**Then** stdout returns main class (file containing `@SpringBootApplication`), project name, build tool, JVM debug args (`-agentlib:jdwp=...`), and active profiles from `application.yml`/`application.properties`
**And** `parse-spring-beans --dir .` returns bean names, class names, source file, line number, and injected dependency list (`@Autowired`, `@Inject` fields)
**And** Spring stereotypes detected: `@Component`, `@Service`, `@Repository`, `@Controller`, `@RestController`, `@Configuration`
**And** `os-lib` is used for recursive directory traversal

### Story 3.3: REST Endpoint Extractor, Import Optimizer & Java Header Generator

As a JVM developer,
I want `cumulus-engine extract-endpoints`, `optimize-imports`, and `generate-java-header`,
So that REST endpoints are discovered, imports are deduplicated/sorted, and new Java files get correct package headers.

**Acceptance Criteria:**

**Given** source files with Spring/JAX-RS annotations or import statements
**When** `extract-endpoints --dir .` is called
**Then** endpoints are returned with HTTP method (`GET`, `POST`, `PUT`, `DELETE`, `PATCH`), path (resolving class-level `@RequestMapping` base paths), class name, and handler method name
**And** Spring (`@GetMapping`, `@PostMapping`, etc.) and JAX-RS (`@Path`, `@GET`, etc.) annotations are both supported
**And** `optimize-imports` (via stdin) deduplicates, sorts lexically (using `SortedSet`), and outputs formatted import block after package declaration
**And** `generate-java-header --file <path>` infers package from `/src/main/java/` or `/src/` directory structure and outputs `package ...;` and `public class ClassName { }` lines

---

## Epic 4: Testing, Diagnostics & Log Processing

Developers can detect test context at cursor, assemble platform-correct test CLI commands (Maven/Gradle/SBT), parse test results, resolve stacktrace frames to source files, parse build logs, and index high-speed log files.

### Story 4.1: Test Context Detector & Test Output Parser

As a developer running unit tests,
I want `cumulus-engine detect-test-context` and `parse-test-output`,
So that the nearest test class/method at cursor is detected and JUnit/Maven/Gradle test logs are parsed.

**Acceptance Criteria:**

**Given** a test file and cursor line number
**When** `detect-test-context --file <path> --line <n>` is executed
**Then** stdout returns `{"class_name":"...","method_name":"..."}` by scanning for class declaration and backward-searching from cursor line for nearest `@Test` method
**And** `parse-test-output` (via stdin) parses JUnit 5, Maven Surefire, and Gradle test output formats
**And** each test result includes class name, method name, status (`PASSED`, `FAILED`, `SKIPPED`), and optional failure message with file/line

### Story 4.2: Build Log Diagnostic Parser & Stacktrace Drill-Down

As a developer debugging build errors,
I want `cumulus-engine parse-build-log`, `parse-stacktrace`, and `resolve-stacktrace-symbol`,
So that build diagnostics and stacktrace frames resolve directly to workspace file paths and line numbers.

**Acceptance Criteria:**

**Given** build output or stacktrace log text via stdin
**When** `parse-build-log --tool maven` or `parse-build-log --tool gradle` is run
**Then** ANSI escape codes are stripped and diagnostics include file, line, col, severity, and message
**And** `parse-stacktrace` (via stdin) extracts class name, method name, file, and line from `at com.pkg.Class.method(File.java:123)` frames
**And** `resolve-stacktrace-symbol --line "at com.example.Service.method(Service.java:42)" --dir .` resolves package to filesystem path checking `src/main/java`, `src/main/kotlin`, `src/test/java`, `src/test/kotlin`
**And** `os-lib` is used for source file path resolution

### Story 4.3: High-Speed Log File Indexer

As a developer inspecting large log files,
I want `cumulus-engine index-log`,
So that ERROR, WARN, FATAL, and SEVERE messages are indexed with line numbers and timestamps.

**Acceptance Criteria:**

**Given** log file content via stdin
**When** `cumulus-engine index-log` is invoked
**Then** stdout returns indexed `LogIndexEntry` entries with line number, severity level, timestamp (if present), and message text
**And** common log formats are supported (Log4j, Logback, java.util.logging patterns)

### Story 4.4: Test Command Assembly

As a developer running tests from Neovim,
I want `cumulus-engine assemble-test-command`,
So that platform-correct Maven/Gradle/SBT test CLI commands are assembled in the engine instead of Lua.

**Acceptance Criteria:**

**Given** a detected test context with class, method, build tool, and project directory
**When** `cumulus-engine assemble-test-command --tool maven --class FooTest --method testBar --dir .` is executed
**Then** stdout returns `{"command":"mvn test -Dtest=FooTest#testBar","cwd":"/project/root"}`
**And** Gradle produces `gradle test --tests FooTest.testBar`
**And** SBT produces `sbt "testOnly *FooTest -- -t testBar"`
**And** multi-module projects include the correct module flag (`-pl :module` for Maven, `-p subproject` for Gradle)
**And** wrapper executables (`./mvnw`, `./gradlew`) are used when available

---

## Epic 5: DevOps, Integrity & Platform Tools

Developers can analyze JaCoCo coverage (scala-xml), validate Checkstyle reports (scala-xml), validate Flyway migrations, validate Kubernetes manifests, parse Git conflicts, sanitize Neovim sessions, verify Gradle wrappers, manage cloud theme state, and check network connectivity.

### Story 5.1: JaCoCo Coverage & Checkstyle Report Parsers (scala-xml)

As a DevOps engineer,
I want `cumulus-engine parse-coverage` and `parse-checkstyle`,
So that JaCoCo XML coverage reports and Checkstyle XML audit reports are parsed using scala-xml.

**Acceptance Criteria:**

**Given** `jacoco.xml` or Checkstyle XML report
**When** `parse-coverage --file jacoco.xml` or `parse-checkstyle` (via stdin) is executed
**Then** coverage returns file paths with covered lines (`ci > 0`) and missed lines (`mi > 0`) using `(report \ "package" \ "sourcefile" \ "line")` scala-xml traversal
**And** checkstyle returns file, line, col, severity, message using `(audit \ "file" \\ "error")` scala-xml traversal
**And** no regex or SAX event streaming is used for XML parsing (NFR6)

### Story 5.2: Flyway Migration, Kubernetes Manifest & Git Conflict Validators

As a Cloud-Native developer,
I want `cumulus-engine validate-migrations`, `validate-k8s-manifest`, and `parse-git-conflicts`,
So that migration version conflicts, K8s YAML errors, and Git merge conflicts are detected.

**Acceptance Criteria:**

**Given** Flyway SQL scripts directory, Kubernetes YAML via stdin, or buffer content with conflict markers
**When** `validate-migrations --dir .`, `validate-k8s-manifest` (via stdin), or `parse-git-conflicts` (via stdin) is called
**Then** migrations flags duplicate version numbers and filenames not matching `V<Ver>__<Desc>.sql` or `R__<Desc>.sql` patterns
**And** K8s validation checks top-level `apiVersion` and `kind` field presence
**And** conflict parser returns start line, separator line, end line, current header, and incoming header for each conflict block
**And** `os-lib` is used for migration directory traversal

### Story 5.3: Session Sanitizer, Gradle Wrapper Verifier, Network Checker & Dependency Tools

As a Neovim user and JVM developer,
I want `cumulus-engine session-sanitize`, `verify-gradle-wrapper`, `check-network`, `check-jdtls-sync`, `resolve-deps`, and `check-dep-versions`,
So that sessions are cleaned, wrapper integrity is verified, network is checked, and dependencies are analyzed.

**Acceptance Criteria:**

**Given** a `.vim` session file, project directory, or dependency manifest
**When** the respective subcommand is executed
**Then** `session-sanitize --file <path>` removes ephemeral buffers (`[No Name]`, `term://`, `snacks_*`) and floating windows from session files, returning cleaned line count and total line count
**And** `verify-gradle-wrapper --dir .` checks `distributionUrl`, `distributionSha256Sum` in `gradle-wrapper.properties`, scans CI config files for version mismatches, and reports `GradleWrapperStatus`
**And** `check-network --host repo.maven.apache.org:443` performs TCP socket connection with configurable timeout
**And** `check-jdtls-sync --dir . --start-time <epoch>` compares mtime of build files (`pom.xml`, `build.gradle`, `build.gradle.kts`, `settings.gradle`, `gradle/libs.versions.toml`) against start timestamp
**And** `resolve-deps --file pom.xml` extracts dependencies with `${property}` macro expansion via scala-xml, or parses `libs.versions.toml` for Gradle version catalogs
**And** `check-dep-versions --file pom.xml` evaluates semver age (`CURRENT`, `PATCH_OUTDATED`, `MINOR_OUTDATED`, `MAJOR_OUTDATED`) using cached version metadata from `~/.cache/nvim/dependency-versions.json`

### Story 5.4: Cloud Theme State Management

As a Neovim user,
I want `cumulus-engine manage-theme`,
So that cloud theme persistence (read/write `~/.config/cumulus/theme/state`) is engine-managed instead of Lua file I/O.

**Acceptance Criteria:**

**Given** a theme state file at `~/.config/cumulus/theme/state`
**When** `cumulus-engine manage-theme --action get` is executed
**Then** stdout returns `{"success":true,"data":{"theme":"aws","variant":"dark"}}`
**And** `manage-theme --action set --theme azure` writes the updated `KEY=VALUE` state file
**And** missing state file returns a default theme (`aws`) gracefully without error
**And** `os-lib` is used for file I/O

---

## Epic 6: Neovim Lua Bridge Migration & Complete Rust Removal

Neovim connects to `cumulus-engine` via the new `engine.lua` bridge. All `util/` files are refactored to delegate business logic to engine subcommands. The Lua codebase is pure UI glue. Health checks verify the GraalVM binary. All Rust code (`crates/cumulus-core/`) is completely purged.

### Story 6.1: Lua Integration Layer (`engine.lua`) & Util File Refactoring

As a Neovim user,
I want `lua/cumulus/util/engine.lua` replacing `rust.lua`, all `util/` files refactored to delegate business logic to engine subcommands, and `health.lua` updated,
So that Neovim executes `cumulus-engine` seamlessly and Lua contains zero business logic.

**Acceptance Criteria:**

**Given** Neovim runtime with `cumulus-engine` compiled
**When** `:checkhealth cumulus` is run
**Then** `cumulus-engine` is detected and reported as active with version, Scala version, and commit hash from `ping` response
**And** `engine.lua` binary discovery checks: (1) `cumulus-engine` on `$PATH`, (2) `engine/target/graalvm-native-image/cumulus-engine`, (3) `~/.local/share/nvim/cumulus/bin/cumulus-engine`
**And** `engine.lua` provides all 32+ subcommand wrapper functions with the same Lua API signatures as `rust.lua`
**And** `maven.lua` delegates build tool detection to `discover-build-tool` (removing `find_maven()` Lua logic)
**And** `gradle.lua` delegates build tool detection to `discover-build-tool` (removing `find_gradle()` Lua logic)
**And** `test-runner.lua` delegates CLI assembly to `assemble-test-command` (removing Lua flag construction)
**And** `lsp-java.lua` and `lsp-kotlin.lua` delegate JDK discovery to `discover-jdk` (removing `find_java21_home()` Lua logic)
**And** `theme/init.lua` delegates state I/O to `manage-theme` (removing Lua `KEY=VALUE` file parsing)
**And** `multimodule.lua` delegates root finding to `discover-workspace` (removing Lua `findfile` logic)
**And** `autocmds.lua` Java header fallback delegates to `generate-java-header` (removing Lua regex fallback)
**And** `health.lua` removes `cargo` from binary check list and reports `sbt` + GraalVM build instructions when engine is missing

### Story 6.2: Purge Legacy Rust Engine (`crates/cumulus-core`)

As a project maintainer,
I want `crates/cumulus-core/` deleted, Cargo references removed, and all Rust artifacts purged,
So that the codebase is 100% Rust-free.

**Acceptance Criteria:**

**Given** the project repository with all engine subcommands implemented in Scala
**When** Rust cleanup is executed
**Then** `crates/cumulus-core/` directory is completely deleted (all `.rs` source files, `Cargo.toml`, `Cargo.lock`, `target/` build artifacts)
**And** the `crates/` directory itself is removed if empty
**And** `.gitignore` is updated to remove Rust/Cargo patterns (`/target`, `Cargo.lock`) and add SBT/Scala patterns (`engine/target/`, `engine/project/target/`)
**And** no file in the repository references `cumulus-core`, `cargo build`, or `crates/` paths
**And** `health.lua` no longer checks for `cargo` binary availability
**And** README or documentation references to Rust/Cargo are updated to reference Scala/SBT/GraalVM

---

## Epic 7: CI/CD Pipeline & Cross-Platform Distribution

Pre-built native binaries are automatically compiled for x86_64-linux, aarch64-linux, and darwin-arm64 via GitHub Actions. End users can auto-download the correct binary without installing SBT or GraalVM.

### Story 7.1: GitHub Actions GraalVM Native Build Matrix

As a maintainer,
I want a CI pipeline that compiles `cumulus-engine` on x86_64-linux, aarch64-linux, and darwin-arm64,
So that pre-built binaries are available in GitHub Releases.

**Acceptance Criteria:**

**Given** a push to `main` or a version tag (`v*`)
**When** GitHub Actions workflow `.github/workflows/release-engine.yml` runs
**Then** three platform-specific `cumulus-engine` binaries are compiled using GraalVM native-image via `sbt graalvm-native-image:packageBin`
**And** binaries are uploaded as release assets with platform suffix (`cumulus-engine-linux-x86_64`, `cumulus-engine-linux-aarch64`, `cumulus-engine-darwin-arm64`)
**And** a `checksums.sha256` manifest is generated and published alongside
**And** the workflow uses `graalvm/setup-graalvm` action with Java 21 and `sbt` setup
**And** the workflow runs `sbt test` before native compilation to prevent broken releases

### Story 7.2: Auto-Download Binary in Engine Bridge

As a Neovim user,
I want `engine.lua` to auto-download the correct pre-built binary on first run,
So that I never need to install SBT or GraalVM manually.

**Acceptance Criteria:**

**Given** `:checkhealth cumulus` reports "engine not found"
**When** the user runs `:CumulusInstallEngine` command
**Then** the correct platform binary is detected via `vim.loop.os_uname()` (sysname + machine)
**And** the binary is fetched from the latest GitHub Release using `curl` or `wget`
**And** the downloaded binary is verified against the `checksums.sha256` manifest
**And** the binary is placed in `~/.local/share/nvim/cumulus/bin/cumulus-engine` and made executable (`chmod +x`)
**And** subsequent `:checkhealth cumulus` reports the engine as active
**And** the health check output suggests running `:CumulusInstallEngine` when the binary is missing
**And** download progress is reported via `vim.notify` during the fetch

---

## Epic 8: DevOps & Infrastructure Platform Suite (<leader>o)

DevOps and Cloud engineers have access to a complete compiler, validator, linter, test, and execution toolchain for Terraform/OpenTofu, AWS CloudFormation/SAM, and Ansible under `<leader>o`, with buffer-local keymap scoping, Mason package provisioning, and non-blocking interactive terminals.

### Story 8.1: Terraform & OpenTofu Tooling Suite (<leader>ot)

As a DevOps / Cloud Engineer,
I want dedicated buffer-scoped commands under `<leader>ot` for Terraform and OpenTofu (`init`, `validate`, `plan`, `apply`, `fmt`, `tflint`, security audit with `trivy`/`tfsec`, and `output`),
So that I can write, test, validate, and apply infrastructure as code interactively and safely from within Neovim.

**Acceptance Criteria:**

**Given** an open buffer with filetype `terraform`, `terraform-vars`, or `hcl`
**When** the user presses `<leader>oti`
**Then** `terraform init` (or `tofu init` if tofu is detected) is executed in a non-blocking interactive terminal
**And** `<leader>otv` executes `terraform validate`
**And** `<leader>otp` executes `terraform plan` in an interactive terminal
**And** `<leader>ota` executes `terraform apply` in an interactive terminal with prompt support
**And** `<leader>otf` formats the buffer using `terraform fmt`
**And** `<leader>otl` triggers `tflint` diagnostic linting on the current workspace/file
**And** `<leader>ots` runs a security audit using `trivy config .` or `tfsec`
**And** `<leader>oto` displays Terraform output values in a float/terminal
**And** if neither `terraform` nor `tofu` is installed in PATH, a clear warning notification is displayed

### Story 8.2: AWS CloudFormation & SAM Validation, Compilation & Local Testing Suite (<leader>oc)

As a Cloud Engineer developing on AWS,
I want dedicated buffer-scoped commands under `<leader>oc` for AWS CloudFormation and SAM (`cfn-lint`, `validate-template`, `sam validate`, `sam build`, `sam local invoke`, `sam local start-api`, and `cfn-guard`),
So that I can validate, build, and locally test CloudFormation and SAM serverless stacks with instantaneous feedback.

**Acceptance Criteria:**

**Given** an open CloudFormation or SAM template buffer (`yaml.cfn`, `yaml.sam`, or YAML buffer matching CloudFormation schema)
**When** the user presses `<leader>ocv`
**Then** `aws cloudformation validate-template --template-body file://<current_file>` is executed with output displayed
**And** `<leader>ocl` runs `cfn-lint` on the current template with diagnostics populated
**And** `<leader>ocV` runs `sam validate`
**And** `<leader>ocb` executes `sam build` in a non-blocking terminal
**And** `<leader>oci` runs `sam local invoke` in an interactive terminal
**And** `<leader>ocr` launches `sam local start-api` in an interactive terminal session
**And** `<leader>ocg` triggers `cfn-guard validate` for policy-as-code rules if configured
**And** if AWS CLI or SAM CLI is missing, a helpful notification directs the user to install them

### Story 8.3: Ansible Playbook Syntax Checking, Linting, Execution & Inventory Suite (<leader>oy)

As an Infrastructure Engineer,
I want dedicated buffer-scoped commands under `<leader>oy` for Ansible (`--syntax-check`, `ansible-lint`, dry-run `--check`, execution, `ansible-inventory --graph`, `ansible-doc`, and Ansible Vault),
So that I can verify, lint, dry-run, execute, and secure Ansible automation playbooks and roles without switching out of my editor.

**Acceptance Criteria:**

**Given** an open buffer with filetype `yaml.ansible` or `ansible`
**When** the user presses `<leader>oys`
**Then** `ansible-playbook --syntax-check <current_file>` runs and reports syntax errors or success
**And** `<leader>oyl` runs `ansible-lint <current_file>` on the active playbook
**And** `<leader>oyc` executes `ansible-playbook --check <current_file>` (dry-run check mode) in a non-blocking terminal
**And** `<leader>oyr` executes `ansible-playbook <current_file>` in an interactive terminal
**And** `<leader>oyi` runs `ansible-inventory --graph` to inspect inventory host topology
**And** `<leader>oyd` prompts for a module name and displays `ansible-doc` documentation
**And** `<leader>oyv` provides Ansible Vault actions (view, encrypt, decrypt)

### Story 8.4: DevOps Language-Scoped Buffers, Mason Automation & WhichKey Integration

As a polyglot engineer using Cumulus,
I want the `<leader>o` DevOps keymap groups and tools to be dynamically scoped to relevant buffer filetypes, automatically provisioned via Mason, and cleanly organized in WhichKey,
So that DevOps keymaps only appear when editing relevant files, all necessary binaries are managed seamlessly, and the keymap hierarchy is self-documenting.

**Acceptance Criteria:**

**Given** Neovim started with `cumulus.nvim`
**When** opening a Terraform buffer, `<leader>ot` appears in WhichKey with icon `󱁢 ` and label `terraform/opentofu`
**And** opening a CloudFormation/SAM buffer, `<leader>oc` appears in WhichKey with icon `󰅟 ` and label `cloudformation/sam`
**And** opening an Ansible buffer, `<leader>oy` appears in WhichKey with icon `󰚰 ` and label `ansible`
**And** opening an unrelated filetype (e.g. Java, Python, Markdown), `<leader>ot`, `<leader>oc`, and `<leader>oy` do not appear in WhichKey
**And** `tools-mason.lua` declares `terraform-ls`, `tflint`, `cfn-lint`, `ansible-language-server`, `ansible-lint`, and `yaml-language-server` in `ensure_installed`
**And** `tools-linting.lua` (`nvim-lint`) binds `tflint` to `terraform`, `cfn_lint` to CloudFormation, and `ansible_lint` to Ansible buffers
**And** `tools-formatting.lua` (`conform.nvim`) formats `terraform` with `terraform_fmt` and YAML with appropriate formatters

---

## Epic 9: DevOps Project-Root Discovery & Global Platform Suite (<leader>o)

DevOps and Cloud engineers can execute infrastructure and cloud automation tools (Terraform/OpenTofu, CloudFormation/SAM, Ansible, Docker, Helm/K8s) from any open buffer in Neovim, with automatic root directory detection, non-blocking execution, smart fallbacks for single-file tools, and proactive diagnostics.

### Story 9.1: DevOps Root & Workspace Discovery Engine

As a DevOps / Cloud Engineer,
I want Cumulus to automatically detect project and tool root directories for Terraform, CloudFormation/SAM, Ansible, Docker, and Helm,
So that tooling commands know where infrastructure configurations live regardless of which buffer is currently active.

**Acceptance Criteria:**

**Given** a Neovim session in a project containing `main.tf`, `terragrunt.hcl`, or `*.tf`
**When** calling `devops.find_tf_root()`
**Then** the absolute path to the directory containing Terraform files is returned
**And** root discovery functions for CloudFormation (`find_cfn_root`), Ansible (`find_ansible_root`), Docker (`find_docker_root`), and Helm (`find_helm_root`) locate their respective configuration roots
**And** if no matching configuration exists in the workspace, `nil` is returned safely without errors

### Story 9.2: Global DevOps Execution with Root Awareness & Smart Fallback

As a DevOps / Cloud Engineer,
I want `<leader>o` commands to execute against the detected tool root directory and provide smart fallbacks for file-level operations,
So that I can plan, build, test, and apply infrastructure from any buffer without manually navigating to specific files.

**Acceptance Criteria:**

**Given** any open buffer (e.g. `README.md`, `Main.java`) in a workspace with Terraform configuration
**When** the user executes `<leader>otp` (`terraform plan`)
**Then** the command runs inside the detected Terraform root directory in a non-blocking `Snacks.terminal`
**And** when executed in a workspace lacking Terraform files, a warning toast is displayed: "No Terraform/OpenTofu configuration found in workspace"
**And** file-scoped actions like `terraform fmt` format the active buffer when editing `.tf` files, or format the detected root directory when invoked from a non-DevOps buffer
**And** CloudFormation (`<leader>oc`), Ansible (`<leader>oy`), Docker (`<leader>od`), and Helm (`<leader>ok`) commands exhibit the same root-aware behavior

### Story 9.3: Universal Keymap Registration & WhichKey Scope Configuration

As a polyglot engineer using Cumulus,
I want `<leader>o` keymaps to be globally registered (mirroring `<leader>j`) and cleanly organized in WhichKey,
So that all DevOps tools are immediately discoverable and accessible with consistent visual hierarchy across the entire editor.

**Acceptance Criteria:**

**Given** Neovim started with `cumulus.nvim`
**When** the user presses `<leader>o` in any buffer
**Then** WhichKey surfaces all DevOps sub-groups (`<leader>ot`, `<leader>oc`, `<leader>oy`, `<leader>od`, `<leader>ok`) with their respective icons and descriptions
**And** pressing any DevOps shortcut executes the root-aware command seamlessly
**And** buffer-specific LSP linting (`nvim-lint`) and autoformatting (`conform.nvim`) continue to work seamlessly when editing specific DevOps files

