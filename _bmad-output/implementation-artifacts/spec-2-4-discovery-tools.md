---
title: 'Epic 2 Story 2.4: JDK Discovery, Build Tool Detection & Workspace Discovery'
type: 'feature'
created: '2026-08-12'
status: 'done'
baseline_commit: 'd0cdc348602b840172ec7b31978af503ba62f521'
review_loop_iteration: 0
context: ['_bmad-output/implementation-artifacts/epic-2-context.md']
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Neovim integration requires JDK installation discovery, build tool detection, and workspace root identification. Currently this logic lives in Lua (many places); moving it to the engine centralizes discovery, improves maintainability, and enables better caching.

**Approach:** Implement three subcommands: `discover-jdk --version <N>` (scan `/usr/lib/jvm`, `~/.sdkman/candidates/java/`, `$JAVA_HOME` for specific JDK); `discover-build-tool --dir <path>` (detect Maven/Gradle/SBT via presence of build files and wrappers); `discover-workspace --dir <path>` (find project root by scanning upward for build files, multi-module indicator).

## Boundaries & Constraints

**Always:**
- Use `os-lib` exclusively for filesystem operations (no runtime reflection).
- Subcommands: `discover-jdk --version <N>`, `discover-build-tool --dir <path>`, `discover-workspace --dir <path>`.
- Response envelope: `CumulusResponse[T]` with zero runtime reflection (uPickle only).
- JDK search paths: `/usr/lib/jvm/java-<N>*`, `~/.sdkman/candidates/java/<N>*`, `$JAVA_HOME`.
- Build tool detection: scan for `pom.xml`, `build.gradle`, `build.sbt`, wrapper executables (`./mvnw`, `./gradlew`).
- All JSON output conforms to `SPEC-031` schema.

**Ask First:**
- Adding support for alternative JDK locations (e.g., `/opt/java`, custom paths from environment variables).
- Optional caching of discovery results with TTL.

**Never:**
- Do not invoke JDK, build tools, or any external executables; detect via filesystem only.
- Do not modify file permissions or create files during discovery.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Find installed JDK 21 | `discover-jdk --version 21` with JDK 21 in `/usr/lib/jvm/java-21-openjdk` | Return `{"success":true,"data":{"java_home":"/usr/lib/jvm/java-21-openjdk","version":"21.0.3"}}` | JDK not found → `INVALID_INPUT` error; ambiguous matches → choose first alphabetically |
| Detect Maven project | `discover-build-tool --dir /maven-project` with pom.xml present | Return `{"build_tool":"maven","wrapper":"/maven-project/mvnw","executable":true}` | No build files → `INVALID_INPUT`; multiple tools detected → return primary (Maven > Gradle > SBT precedence) |
| Find workspace root | `discover-workspace --dir /nested/maven/src/main/java` | Scan upward; return root `/nested/maven` where pom.xml exists | No build files found → return input dir or `INVALID_INPUT` (specify behavior) |
| Non-executable wrapper | Gradle wrapper present but not executable | Return `{"build_tool":"gradle","wrapper":"./gradlew","executable":false,"recommendation":"chmod +x ./gradlew"}` | Flag in response; no error |
| Missing JDK version | `discover-jdk --version 99` when version 99 not installed | Return error with code `INVALID_INPUT` and message "JDK version 99 not found" | Graceful error; suggest available versions (optional) |

</frozen-after-approval>

## Code Map

- `engine/src/main/scala/cumulus/Main.scala:60-100` -- CLI router: add `discover-jdk`, `discover-build-tool`, `discover-workspace` subcommands
- `engine/src/main/scala/cumulus/workspace/JdkDiscoverer.scala` -- **NEW**: Scan standard JDK locations; match version; return JAVA_HOME and version string
- `engine/src/main/scala/cumulus/workspace/BuildToolDetector.scala` -- **NEW**: Scan directory for build files (pom.xml, build.gradle, build.sbt, wrappers); prioritize tool; check executable bit
- `engine/src/main/scala/cumulus/workspace/WorkspaceScanner.scala` -- **NEW**: Scan upward from directory for project root; return root path, detected build files, multi-module indicator
- `engine/src/main/scala/cumulus/workspace/WorkspaceModels.scala` -- **NEW**: Case classes for `JdkInfo`, `BuildToolInfo`, `WorkspaceInfo` with uPickle derivation
- `engine/src/test/scala/cumulus/workspace/DiscoveryTest.scala` -- **NEW**: Unit tests for JDK/build tool/workspace discovery across various filesystem layouts

## Tasks & Acceptance

**Execution:**
- [x] `engine/src/main/scala/cumulus/workspace/JdkDiscoverer.scala` -- CREATE: Scan `/usr/lib/jvm/java-<version>*`, `~/.sdkman/candidates/java/<version>*`, `$JAVA_HOME` for matching JDK version; return path and parsed version string.
- [x] `engine/src/main/scala/cumulus/workspace/BuildToolDetector.scala` -- CREATE: Detect Maven/Gradle/SBT by presence of build files in directory; check wrapper executables; return tool name, wrapper path, executable status.
- [x] `engine/src/main/scala/cumulus/workspace/WorkspaceScanner.scala` -- CREATE: Scan upward from directory for presence of build files (pom.xml, build.gradle, build.sbt, .mvn, .gradle); return project root and multi-module indicator.
- [x] `engine/src/main/scala/cumulus/workspace/WorkspaceModels.scala` -- CREATE: Define response case classes with uPickle derivation.
- [x] `engine/src/main/scala/cumulus/Main.scala` -- EDIT: Add three subcommands to router.
- [x] `engine/src/test/scala/cumulus/workspace/DiscoveryTest.scala` -- CREATE: Unit tests for all three discovery functions covering success and error cases.

**Acceptance Criteria:**
- Given a system with JDK 21 installed in `/usr/lib/jvm/java-21-openjdk`, when `discover-jdk --version 21` is executed, then stdout returns `{"success":true,"data":{"java_home":"/usr/lib/jvm/java-21-openjdk","version":"21.x.x"},"error":null,"error_code":null}`.
- Given a Maven project directory, when `discover-build-tool --dir .` is executed, then stdout returns `{"build_tool":"maven","wrapper":"/path/to/mvnw","executable":true}`.
- Given a project root directory, when `discover-workspace --dir /nested/path` is executed, then stdout returns the parent directory containing build files as the workspace root.
- Given all above scenarios, `sbt test` passes all tests, and `sbt nativeImage` produces a working binary.

### Review Findings

- [x] [Review][Decision] Maven inter-module dependency resolution — Implement per-module pom.xml dependency extraction or document/preserve declaration-order fallback with warning.
- [x] [Review][Patch] Adopt os-lib exclusively across GradleParser, DependencyExtractor, DagSolver, BuildToolDetector, JdkDiscoverer, and WorkspaceScanner [`engine/src/main/scala/cumulus/workspace/WorkspaceScanner.scala:96`]
- [x] [Review][Patch] Support multi-argument and multi-line include statements and includeBuild directives in GradleParser and DependencyExtractor [`engine/src/main/scala/cumulus/build/GradleParser.scala:111`]
- [x] [Review][Patch] Normalize leading colons on Gradle module paths (strip leading colon) so relative paths are cleanly produced [`engine/src/main/scala/cumulus/build/GradleParser.scala:118`]
- [x] [Review][Patch] Support Kotlin DSL (settings.gradle.kts and build.gradle.kts) in WorkspaceScanner, BuildToolDetector, DependencyExtractor, and Main.scala [`engine/src/main/scala/cumulus/Main.scala:72`]
- [x] [Review][Patch] Broaden JDK discovery vendor patterns (java-*, jdk-*, temurin-*, zulu-*, graalvm-*) in JdkDiscoverer [`engine/src/main/scala/cumulus/workspace/JdkDiscoverer.scala:25`]
- [x] [Review][Patch] Fix error codes: return INVALID_INPUT when JDK not found in discover-jdk and for missing --dir in compute-build-order [`engine/src/main/scala/cumulus/Main.scala:708`]
- [x] [Review][Patch] Add unit and CLI tests for computeBuildOrderForDirectory, Gradle multi-include, and JdkDiscoverer in MainTest.scala / DiscoveryTest.scala [`engine/src/test/scala/cumulus/MainTest.scala:1`]
- [x] [Review][Defer] Custom Gradle projectDir overrides (`project(':...').projectDir`) — deferred, pre-existing
- [x] [Review][Defer] Inspecting JDK `release` file for micro version parsing — deferred, pre-existing

## Design Notes

**JDK Version Matching:** Version strings in paths (e.g., `java-21-openjdk`) may include minor/patch versions. For `discover-jdk --version 21`, match any path starting with `java-21` (major version). Extract actual version from `<JAVA_HOME>/bin/java -version` output (optional refinement for future story).

**Build Tool Precedence:** When multiple build files exist (unlikely but possible), prioritize: Maven > Gradle > SBT (based on typical project patterns).

**Workspace Root Algorithm:** Scan upward from input directory. Stop at first directory containing any of: `pom.xml`, `build.gradle`, `build.gradle.kts`, `build.sbt`, or directories `.mvn`, `.gradle`. Return that directory as root. If no build file found, return input directory or error (spec to clarify behavior).

## Verification

**Commands:**
- `cd engine && sbt test` -- expected: all discovery tests pass.
- `cd engine && sbt nativeImage` -- expected: binary builds successfully.
- `./engine/target/native-image/cumulus-engine discover-jdk --version 21` -- expected: returns JDK path if installed.
- `./engine/target/native-image/cumulus-engine discover-build-tool --dir .` -- expected: returns detected build tool.
- `./engine/target/native-image/cumulus-engine discover-workspace --dir .` -- expected: returns workspace root.

## Suggested Review Order

**CLI Integration & Entry Points**

- Three new subcommand dispatches for JDK, build tool, and workspace discovery.
  [`Main.scala:331`](../../../engine/src/main/scala/cumulus/Main.scala#L331)

**Core Discovery Implementations**

- JDK discovery with version matching using word boundary regex; scans standard locations.
  [`JdkDiscoverer.scala:19`](../../../engine/src/main/scala/cumulus/workspace/JdkDiscoverer.scala#L19)

- Build tool detection with wrapper identification and executable status checking.
  [`BuildToolDetector.scala:14`](../../../engine/src/main/scala/cumulus/workspace/BuildToolDetector.scala#L14)

- Workspace scanner that finds project root by scanning upward; detects multi-module projects.
  [`WorkspaceScanner.scala:17`](../../../engine/src/main/scala/cumulus/workspace/WorkspaceScanner.scala#L17)

**Data Models & Serialization**

- Response envelope case classes with uPickle derivation for JDK, build tool, and workspace info.
  [`WorkspaceModels.scala:280`](../../../engine/src/main/scala/cumulus/workspace/WorkspaceModels.scala#L280)

**Test Coverage**

- 17 comprehensive tests covering discovery across Maven, Gradle, SBT, multi-module detection, and error cases.
  [`DiscoveryTest.scala:453`](../../../engine/src/test/scala/cumulus/workspace/DiscoveryTest.scala#L453)
