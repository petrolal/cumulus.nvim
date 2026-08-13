# Epic 2 Context: Build Tooling, Multi-Module DAG & Workspace Intelligence

<!-- Compiled from planning artifacts. Edit freely. Regenerate with compile-epic-context if planning docs change. -->

## Goal

JVM developers can query Maven goals, Gradle tasks, SBT tasks, sub-modules, and compute topological DAG build ordering. The engine discovers project roots, build tools, and JDK installations — absorbing workspace discovery logic previously in Lua. This epic moves build intelligence from Neovim Lua into the native Scala engine, enabling fast, reliable multi-module project analysis.

## Stories

- Story 2.1: Maven Goals & Submodule Parser (scala-xml)
- Story 2.2: Gradle Task & Submodule Parser
- Story 2.3: Multi-Module Topological Build Order Solver (Kahn's DAG)
- Story 2.4: JDK Discovery, Build Tool Detection & Workspace Discovery

## Requirements & Constraints

**Functional Requirements:**
- Implement `parse-pom` and `parse-modules --tool maven` subcommands for Maven lifecycle and plugin goal extraction via type-safe scala-xml node traversal.
- Implement `parse-gradle-tasks` and `parse-modules --tool gradle` subcommands for Gradle task extraction from `./gradlew tasks` output and `settings.gradle` module discovery.
- Implement `compute-build-order --dir <path>` subcommand using Kahn's topological sort algorithm to order multi-module builds correctly, with safe fallback to declaration order on circular dependencies.
- Implement `discover-jdk --version <N>`, `discover-build-tool --dir <path>`, and `discover-workspace --dir <path>` subcommands for JDK home resolution, build tool detection (Maven/Gradle/SBT), and project root finding.
- All output uses `CumulusResponse[T]` envelope with zero runtime reflection.

**Non-Functional Requirements:**
- XML parsing (POM, Checkstyle, JaCoCo) must use `scala-xml` with `\` and `\\` node selectors — no regex or SAX event streaming.
- Filesystem operations must use `os-lib` for clean directory traversal and file I/O.
- Cross-module `<dependency>` references in POM parsed via scala-xml `(dep \ "groupId")` and `(dep \ "artifactId")` node access.
- Gradle module names and paths extracted from `include '...'` directives in `settings.gradle`.
- JDK discovery scans `/usr/lib/jvm/`, `~/.sdkman/candidates/java/`, and `$JAVA_HOME` environment variable.
- Build tool detection identifies wrapper executables (`./mvnw`, `./gradlew`) and recommends `chmod +x` if executable bit missing.

## Technical Decisions

- **Scala 3 Idiomatic Code (AD-1):** All new parser modules use top-level definitions, case classes, and functional composition. No legacy Scala 2 patterns.
- **Zero-Reflection Serialization (AD-2):** Response data structures (Maven goals, Gradle tasks, build order steps, workspace metadata) derived via uPickle macros. No reflection-based serializers.
- **CLI Router Extensibility (AD-3):** New subcommands integrated into existing pattern-matching router in `cumulus.Main`. Follows established convention: `args(0)` dispatch, remaining args as flags/options.
- **Build Graph Representation:** Multi-module dependency graphs represented as adjacency lists (Map[ModuleName, Set[Dependencies]]). Kahn's algorithm operates on this graph to produce topological order.
- **Workspace Root Discovery:** Scan upward from `--dir <path>` for presence of `pom.xml`, `build.gradle`, `build.gradle.kts`, `build.sbt`, or `.mvn/`, `.gradle/` directories. Return earliest ancestor containing any of these.

## Cross-Story Dependencies

**Internal (this epic):**
- Stories 2.1 and 2.2 are independent (Maven and Gradle parsers can be built in parallel).
- Story 2.3 depends on completion of 2.1 and 2.2 (topological solver must understand both Maven and Gradle module graphs).
- Story 2.4 is independent; builds on Epic 1's foundation (engine already compiles and runs).

**Downstream (future epics):**
- Epic 3 (Code Intelligence) may call `discover-build-tool` to identify project structure before analyzing code.
- Epic 4 (Testing) needs `discover-jdk` and build tool detection to assemble correct test CLI commands.
- Epic 6 (Lua Bridge Migration) refactors `lua/cumulus/util/maven.lua`, `gradle.lua`, `lsp-java.lua` to delegate to engine subcommands from Story 2.4.

**Upstream (already satisfied):**
- Epic 1 (Engine Foundation) — CLI router, protocol envelope, and BuildInfo all complete and proven.

## Module Structure (Planned)

- `engine/src/main/scala/cumulus/build/MavenParser.scala` — Parse POM via scala-xml, extract lifecycle goals, plugin goals, modules.
- `engine/src/main/scala/cumulus/build/GradleParser.scala` — Parse Gradle task output and `settings.gradle`.
- `engine/src/main/scala/cumulus/build/DagSolver.scala` — Kahn's topological sort for multi-module ordering.
- `engine/src/main/scala/cumulus/workspace/WorkspaceDiscovery.scala` — JDK, build tool, and workspace root detection.
- `engine/src/main/scala/cumulus/data/BuildModels.scala` — Case classes for parsed modules, goals, build steps, and workspace metadata.
