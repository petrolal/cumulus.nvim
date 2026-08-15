---
title: 'Epic 5 Story 5.3: Session Sanitizer, Gradle Wrapper Verifier, Network Checker & Dependency Tools'
type: 'feature'
created: '2026-08-15'
status: 'done'
baseline_commit: 'c42b03f'
review_loop_iteration: 0
context: ['_bmad-output/implementation-artifacts/epic-5-context.md']
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Neovim JVM developers require engine-level tooling to sanitize `.vim` session files, verify Gradle wrapper integrity and CI version alignment, check TCP network connectivity, detect stale JDTLS project build files, and resolve/analyze project dependency versions without heavy external runtimes.

**Approach:** Implement `SessionSanitizer`, `WrapperVerifier`, `NetworkChecker`, `JdtlsSyncChecker`, `DepResolver`, and `DepLens` in Scala 3 with compile-time uPickle serialization, exposed via `cumulus-engine session-sanitize`, `verify-gradle-wrapper`, `check-network`, `check-jdtls-sync`, `resolve-deps`, and `check-dep-versions` CLI subcommands returning standardized `CumulusResponse[T]` envelopes.

## Boundaries & Constraints

**Always:**
- All subcommands must return standard `CumulusResponse[T]` envelope (`{ "success": Boolean, "data": Option[T], "error": Option[String], "error_code": Option[String] }`).
- Zero runtime reflection: use `uPickle` compile-time macros (`derives ReadWriter` or `macroRW`).
- Stdout is strictly reserved for JSON payloads; all errors and diagnostics go to stderr or error envelope fields.
- All filesystem traversals and file I/O must strictly use `os-lib` (NFR7).
- `session-sanitize` accepts `--file <path>` and returns `SessionSanitizeResult(success: Boolean, cleaned_lines: Int, total_lines: Int)`. Removes ephemeral buffers (`[No Name]`, `term://`, `snacks_*`), window creation commands with `buftype=nofile` or `floating`, and cleans the session file on disk.
- `verify-gradle-wrapper` accepts `--dir <path>` (defaults to `.`) and returns `GradleWrapperStatus(local_version: Option[String], ci_version: Option[String], sha256_configured: Boolean, sha256_valid: Boolean, issues: Seq[String])`.
- `check-network` accepts `--host <host:port>` (and optional `--timeout <ms>`, default 3000ms) and returns `NetworkStatus(connected: Boolean, host: String, port: Int, elapsed_ms: Long)`.
- `check-jdtls-sync` accepts `--dir <path>` and `--start-time <epoch_seconds>` and returns `SyncStatus(sync_needed: Boolean, modified_file: Option[String])` checking build files (`pom.xml`, `build.gradle`, `build.gradle.kts`, `settings.gradle`, `settings.gradle.kts`, `gradle/libs.versions.toml`).
- `resolve-deps` accepts `--file <path>` (Maven `pom.xml` or Gradle `libs.versions.toml`) and returns `Seq[DependencyInfo(group: String, artifact: String, version: String, scope: String)]` expanding `${property}` macros in POMs.
- `check-dep-versions` accepts `--file <path>` and returns `Seq[DependencyLens(group: String, artifact: String, current_version: String, latest_version: String, line: Int, age_status: String)]` with semver age evaluation (`CURRENT`, `PATCH_OUTDATED`, `MINOR_OUTDATED`, `MAJOR_OUTDATED`, `UNKNOWN`) comparing against cached versions in `~/.cache/nvim/dependency-versions.json`.

**Ask First:**
- N/A

**Never:**
- Never use runtime-reflection serializers (Jackson, Gson, SnakeYAML).
- Never write non-JSON text to stdout.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Session sanitize standard file | `.vim` session with `[No Name]`, `term://`, and normal buffer | Cleans file, strips ephemeral buffers, returns `cleaned_lines > 0` | N/A |
| Session sanitize missing file | `session-sanitize --file /nonexistent.vim` | `SessionSanitizeResult(success = false, cleaned_lines = 0, total_lines = 0)` | Return result with success: false |
| Gradle wrapper verified with SHA | `gradle-wrapper.properties` with valid distribution and sha256 | `GradleWrapperStatus(local_version = Some("8.5"), sha256_configured = true, issues = [])` | N/A |
| Gradle wrapper missing SHA | Wrapper properties without `distributionSha256Sum` | `sha256_configured = false` with issue `"Gradle wrapper SHA-256 checksum not configured (security risk)"` | N/A |
| Gradle wrapper CI mismatch | Local wrapper 8.5 vs CI config 8.0 in `.github/workflows/ci.yml` | `issues` contains `"Gradle version mismatch: local=8.5, CI=8.0"` | N/A |
| Network check open port | `check-network --host 127.0.0.1:80` (or reachable socket) | `NetworkStatus(connected = true/false, host = "127.0.0.1", port = 80, elapsed_ms = ...)` | N/A |
| Network check invalid host format | `check-network --host invalid_no_port` | `{"success":false,"data":null,"error":"...","error_code":"INVALID_INPUT"}` | INVALID_INPUT code |
| JDTLS sync needed | `pom.xml` modified at timestamp > `start-time` | `SyncStatus(sync_needed = true, modified_file = Some("pom.xml"))` | N/A |
| JDTLS sync up-to-date | All build files older than `start-time` | `SyncStatus(sync_needed = false, modified_file = None)` | N/A |
| Resolve Maven POM dependencies | `pom.xml` with `<properties><spring.version>3.2.0</spring.version></properties>` and `<version>${spring.version}</version>` | Returns `DependencyInfo` with `version = "3.2.0"` | N/A |
| Resolve TOML version catalog | `libs.versions.toml` with `[versions]` and `[libraries]` | Returns `DependencyInfo` for all declared libraries | N/A |
| Check dep versions with cache | File with dependencies matching cache | `DependencyLens` with `age_status` correctly calculated | N/A |

</frozen-after-approval>

## Code Map

- `engine/src/main/scala/cumulus/devops/DevopsModels.scala` -- Add data models for `SessionSanitizeResult`, `GradleWrapperStatus`, `NetworkStatus`, `SyncStatus`, `DependencyInfo`, `DependencyLens`.
- `engine/src/main/scala/cumulus/util/SessionSanitizer.scala` -- Session file cleaner stripping ephemeral buffers and floating windows.
- `engine/src/main/scala/cumulus/gradle/WrapperVerifier.scala` -- Gradle wrapper configuration verifier checking distribution URL, SHA-256, and CI config alignment.
- `engine/src/main/scala/cumulus/util/NetworkChecker.scala` -- TCP socket connectivity checker.
- `engine/src/main/scala/cumulus/workspace/JdtlsSyncChecker.scala` -- Mtime comparator for build files vs engine start timestamp.
- `engine/src/main/scala/cumulus/dep/DepResolver.scala` -- Dependency extractor supporting Maven POM property interpolation and Gradle TOML catalogs.
- `engine/src/main/scala/cumulus/dep/DepLens.scala` -- Dependency version scanner with line numbers and semver age calculation against local version cache.
- `engine/src/main/scala/cumulus/Main.scala` -- CLI routing for all 6 subcommands.
- `engine/src/test/scala/cumulus/util/SessionSanitizerTest.scala` -- Unit tests for session sanitization.
- `engine/src/test/scala/cumulus/gradle/WrapperVerifierTest.scala` -- Unit tests for Gradle wrapper verification and CI scanning.
- `engine/src/test/scala/cumulus/util/NetworkCheckerTest.scala` -- Unit tests for network connectivity checker.
- `engine/src/test/scala/cumulus/workspace/JdtlsSyncCheckerTest.scala` -- Unit tests for JDTLS build file sync detector.
- `engine/src/test/scala/cumulus/dep/DepResolverTest.scala` -- Unit tests for Maven and Gradle dependency resolution.
- `engine/src/test/scala/cumulus/dep/DepLensTest.scala` -- Unit tests for dependency version classification and line mapping.
- `engine/src/test/scala/cumulus/MainTest.scala` -- CLI integration tests for Story 5.3 subcommands.

## Tasks & Acceptance

**Execution:**
- [ ] `engine/src/main/scala/cumulus/devops/DevopsModels.scala` -- Add `SessionSanitizeResult`, `GradleWrapperStatus`, `NetworkStatus`, `SyncStatus`, `DependencyInfo`, `DependencyLens` case classes deriving `ReadWriter`.
- [ ] `engine/src/main/scala/cumulus/util/SessionSanitizer.scala` -- Implement `SessionSanitizer.sanitizeSession(filePath: String): CumulusResponse[SessionSanitizeResult]`.
- [ ] `engine/src/main/scala/cumulus/gradle/WrapperVerifier.scala` -- Implement `WrapperVerifier.verifyGradleWrapper(dir: String): CumulusResponse[GradleWrapperStatus]`.
- [ ] `engine/src/main/scala/cumulus/util/NetworkChecker.scala` -- Implement `NetworkChecker.checkNetwork(hostPort: String, timeoutMs: Long): CumulusResponse[NetworkStatus]`.
- [ ] `engine/src/main/scala/cumulus/workspace/JdtlsSyncChecker.scala` -- Implement `JdtlsSyncChecker.checkJdtlsSync(dir: String, startTime: Long): CumulusResponse[SyncStatus]`.
- [ ] `engine/src/main/scala/cumulus/dep/DepResolver.scala` -- Implement `DepResolver.resolveDependencies(filePath: String): CumulusResponse[Seq[DependencyInfo]]`.
- [ ] `engine/src/main/scala/cumulus/dep/DepLens.scala` -- Implement `DepLens.checkDepVersions(filePath: String): CumulusResponse[Seq[DependencyLens]]`.
- [ ] `engine/src/main/scala/cumulus/Main.scala` -- Wire `session-sanitize`, `verify-gradle-wrapper`, `check-network`, `check-jdtls-sync`, `resolve-deps`, `check-dep-versions` into CLI router.
- [ ] `engine/src/test/scala/cumulus/util/SessionSanitizerTest.scala` -- Test session sanitization removing ephemeral lines.
- [ ] `engine/src/test/scala/cumulus/gradle/WrapperVerifierTest.scala` -- Test Gradle wrapper properties, SHA-256 checks, and CI config scanning.
- [ ] `engine/src/test/scala/cumulus/util/NetworkCheckerTest.scala` -- Test TCP connectivity checks and timeout handling.
- [ ] `engine/src/test/scala/cumulus/workspace/JdtlsSyncCheckerTest.scala` -- Test build file mtime comparisons.
- [ ] `engine/src/test/scala/cumulus/dep/DepResolverTest.scala` -- Test Maven POM macro resolution and Gradle TOML parsing.
- [ ] `engine/src/test/scala/cumulus/dep/DepLensTest.scala` -- Test semver age classification and line extraction.
- [ ] `engine/src/test/scala/cumulus/MainTest.scala` -- Add CLI integration tests for Story 5.3 subcommands.

**Acceptance Criteria:**
- Given a `.vim` session file, `session-sanitize` removes `[No Name]`, `term://`, `snacks_*`, and floating window directives, updating the file on disk.
- Given a Gradle project, `verify-gradle-wrapper` validates local wrapper version, SHA-256 configuration, and flags discrepancies against CI configs.
- Given a `host:port` string, `check-network` returns socket connection status and elapsed time.
- Given project build files and start timestamp, `check-jdtls-sync` detects modified configuration files.
- Given `pom.xml` or `libs.versions.toml`, `resolve-deps` extracts dependencies with macro interpolation.
- Given dependency manifests, `check-dep-versions` maps dependencies with line numbers and semver status.
- All test suites pass via `sbt test`.

## Spec Change Log

_None._

## Design Notes

- **Semver Age Calculation**:
  - `current == latest` -> `CURRENT`
  - `current.major == latest.major && current.minor == latest.minor` -> `PATCH_OUTDATED`
  - `current.major == latest.major && current.minor != latest.minor` -> `MINOR_OUTDATED`
  - `current.major != latest.major` -> `MAJOR_OUTDATED`
  - Parse failures or missing latest version -> `UNKNOWN`
- **POM XML Property Resolution**:
  - Extract `<properties>` key-values and substitute `${property.name}` in `<groupId>`, `<artifactId>`, and `<version>`.
- **TOML Version Catalog**:
  - Parse `[versions]` table, then resolve `version.ref` in `[libraries]` table or direct inline versions.

## Verification

**Commands:**
- `sbt "testOnly cumulus.util.*"` -- expected: SessionSanitizer and NetworkChecker tests pass.
- `sbt "testOnly cumulus.gradle.*"` -- expected: WrapperVerifier tests pass.
- `sbt "testOnly cumulus.workspace.JdtlsSyncCheckerTest"` -- expected: JDTLS sync tests pass.
- `sbt "testOnly cumulus.dep.*"` -- expected: DepResolver and DepLens tests pass.
- `sbt "testOnly cumulus.MainTest"` -- expected: CLI integration tests pass.
- `sbt test` -- expected: All unit and integration tests pass cleanly.

## Suggested Review Order

**DevOps Models**

- Core data models for session sanitizing, wrapper verification, network, sync, and dependencies
  [`DevopsModels.scala:67`](../../engine/src/main/scala/cumulus/devops/DevopsModels.scala#L67)

**Sanitizers & Verifiers**

- Session file cleaner removing ephemeral buffers, floating windows, and preserving trailing newlines
  [`SessionSanitizer.scala:14`](../../engine/src/main/scala/cumulus/util/SessionSanitizer.scala#L14)

- Gradle wrapper verifier checking distributionUrl, SHA-256 configuration, and CI config alignment
  [`WrapperVerifier.scala:18`](../../engine/src/main/scala/cumulus/gradle/WrapperVerifier.scala#L18)

- Network checker with IPv6 support, port validation (1-65535), and socket connection timeouts
  [`NetworkChecker.scala:11`](../../engine/src/main/scala/cumulus/util/NetworkChecker.scala#L11)

- JDTLS classpath sync checker comparing build file mtime against engine start timestamps
  [`JdtlsSyncChecker.scala:12`](../../engine/src/main/scala/cumulus/workspace/JdtlsSyncChecker.scala#L12)

**Dependency Resolvers & Version Lenses**

- Dependency extractor supporting multi-pass Maven property interpolation and Gradle TOML catalogs
  [`DepResolver.scala:17`](../../engine/src/main/scala/cumulus/dep/DepResolver.scala#L17)

- Dependency version lens scanner computing directional semver age against local cache
  [`DepLens.scala:17`](../../engine/src/main/scala/cumulus/dep/DepLens.scala#L17)

**CLI Subcommand Routing**

- Subcommand wiring for session-sanitize, verify-gradle-wrapper, check-network, check-jdtls-sync, resolve-deps, check-dep-versions
  [`Main.scala:667`](../../engine/src/main/scala/cumulus/Main.scala#L667)

**Test Suites**

- Unit tests for session sanitization and buffer filtering
  [`SessionSanitizerTest.scala:6`](../../engine/src/test/scala/cumulus/util/SessionSanitizerTest.scala#L6)

- Unit tests for Gradle wrapper verification, checksums, and CI scanning
  [`WrapperVerifierTest.scala:5`](../../engine/src/test/scala/cumulus/gradle/WrapperVerifierTest.scala#L5)

- Unit tests for network TCP socket connectivity and error handling
  [`NetworkCheckerTest.scala:5`](../../engine/src/test/scala/cumulus/util/NetworkCheckerTest.scala#L5)

- Unit tests for JDTLS build file sync detection
  [`JdtlsSyncCheckerTest.scala:5`](../../engine/src/test/scala/cumulus/workspace/JdtlsSyncCheckerTest.scala#L5)

- Unit tests for Maven and Gradle dependency resolution
  [`DepResolverTest.scala:5`](../../engine/src/test/scala/cumulus/dep/DepResolverTest.scala#L5)

- Unit tests for semver age classification and dependency line mapping
  [`DepLensTest.scala:5`](../../engine/src/test/scala/cumulus/dep/DepLensTest.scala#L5)

- CLI integration tests for all Story 5.3 subcommands
  [`MainTest.scala:434`](../../engine/src/test/scala/cumulus/MainTest.scala#L434)
