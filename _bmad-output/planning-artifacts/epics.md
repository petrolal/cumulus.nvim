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

### NonFunctional Requirements

NFR1: GraalVM native binary startup latency under 10ms for CLI process invocation via `vim.system()`.
NFR2: 100% backward compatibility with `SPEC-031` JSON output envelope schemas.
NFR3: Zero runtime reflection configuration required for GraalVM `native-image` compilation (AD-2). All serialization via uPickle compile-time macros. Reflection-based serializers (Jackson, Gson, snakeyaml) are strictly prohibited unless accompanied by targeted `reflect-config.json`.
NFR4: Graceful fallback and clear health check diagnostics in Neovim when binary is uncompiled or unavailable.
NFR5: Scala codebase must be the dominant portion of the project — Lua files reduced to pure Neovim UI glue (keymaps, pickers, plugin specs) with zero business logic.
NFR6: All XML parsing (POM, JaCoCo, Checkstyle) must use `scala-xml` with XPath-like node selectors (`\` and `\\`) — no regex-based XML parsing or SAX event streaming.
NFR7: Filesystem operations must use `os-lib` for clean, zero-reflection directory traversal and file I/O.

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

N/A (Backend Engine Migration)

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
