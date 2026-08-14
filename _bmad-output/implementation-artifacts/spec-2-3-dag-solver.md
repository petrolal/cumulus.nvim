---
title: 'Epic 2 Story 2.3: Multi-Module Topological Build Order Solver (Kahn''s DAG)'
type: 'feature'
created: '2026-08-12'
status: 'done'
baseline_commit: '56e1a37b62a31fbee19205d691f2b551802342d5'
review_loop_iteration: 0
context: ['_bmad-output/implementation-artifacts/epic-2-context.md']
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Multi-module Maven and Gradle projects have complex inter-module dependencies. Without correct build ordering, developers may attempt to build modules in the wrong sequence, causing failures. Story 2.1 & 2.2 extract module lists, but no ordering algorithm exists.

**Approach:** Implement `compute-build-order --dir <path>` subcommand using Kahn's topological sort algorithm. Scan both Maven (`pom.xml`) and Gradle (`settings.gradle` + `build.gradle`) for module dependencies. Return ordered `ModuleBuildStep` entries. Gracefully handle circular dependencies by falling back to declaration order with warnings.

## Boundaries & Constraints

**Always:**
- Use Kahn's algorithm for topological sorting of module DAG.
- Parse Maven `<dependency>` references via scala-xml (Story 2.1 precedent).
- Parse Gradle dependency declarations via text parsing (regex or simple text scanning).
- Response envelope: `CumulusResponse[T]` with zero runtime reflection.
- Subcommand: `compute-build-order --dir <path>`.
- All JSON output conforms to `SPEC-031` schema.

**Ask First:**
- Support for transitive dependencies (currently direct module references only).
- Support for profile-specific or platform-specific dependency variants.

**Never:**
- Do not invoke Maven or Gradle; analyze static files only.
- Do not use external SAT solvers or constraint libraries; Kahn's algorithm only.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Simple linear deps (A → B → C) | Maven multi-module with core → web → services dependency chain | Return ordered steps: [core (1), web (2), services (3)] with module name, path, and build command | N/A |
| Diamond deps (A → B, A → C, B → D, C → D) | Maven with aggregator depending on both; both intermediate modules depending on leaf | Kahn's produces valid topological order; aggregator last | N/A |
| Circular deps (A → B → C → A) | POM with circular module references | Detect cycle; log warning to stderr; fall back to declaration order; return success with warnings field | Warnings field optional; if present, list detected cycles |
| Missing module (A depends on nonexistent B) | POM with dependency to module not declared in modules list | Return error with `PARSE_ERROR` or continue with warning; specify behavior | Skip missing references or flag as error (spec to clarify) |
| Single module (no deps) | Single-module project or aggregator with no inter-module deps | Return single ordered step | N/A |

</frozen-after-approval>

## Code Map

- `engine/src/main/scala/cumulus/Main.scala:60-100` -- CLI router: add `compute-build-order --dir <path>` subcommand
- `engine/src/main/scala/cumulus/build/DagSolver.scala` -- **NEW**: Kahn's algorithm implementation; topological sort of module DAG with cycle detection
- `engine/src/main/scala/cumulus/build/DependencyExtractor.scala` -- **NEW**: Extract module dependencies from Maven POMs (via scala-xml) and Gradle build files (via text parsing)
- `engine/src/main/scala/cumulus/build/BuildModels.scala` -- **NEW**: Case classes for `ModuleBuildStep` (step: Int, name: String, path: String, buildCommand: String), `BuildOrder` response
- `engine/src/test/scala/cumulus/build/DagSolverTest.scala` -- **NEW**: Unit tests for Kahn's algorithm (linear, diamond, cycles, disconnected graphs)

## Tasks & Acceptance

**Execution:**
- [x] `engine/src/main/scala/cumulus/build/DependencyExtractor.scala` -- CREATE: Implement Maven dependency extraction using scala-xml `(dep \ "groupId")` and `(dep \ "artifactId")`. Implement Gradle dependency extraction via regex parsing of `dependencies { ... }` blocks.
- [x] `engine/src/main/scala/cumulus/build/DagSolver.scala` -- CREATE: Implement Kahn's topological sort with cycle detection. Nodes are module names; edges are `module-depends-on` relationships. Return ordered list or error on cycle.
- [x] `engine/src/main/scala/cumulus/build/BuildModels.scala` -- CREATE: Define `ModuleBuildStep`, `BuildOrder`, and response case classes with uPickle derivation.
- [x] `engine/src/main/scala/cumulus/Main.scala` -- EDIT: Add `compute-build-order --dir <path>` subcommand. Scan directory for pom.xml and build.gradle files; delegate to extractors and solver.
- [x] `engine/src/test/scala/cumulus/build/DagSolverTest.scala` -- CREATE: Unit tests for Kahn's algorithm covering linear chains, diamond graphs, cycles, and disconnected components.

**Acceptance Criteria:**
- Given a multi-module Maven project with module A depending on B (via inter-module `<dependency>`), when `compute-build-order --dir .` is executed, then stdout returns ordered `ModuleBuildStep` entries with A after B (correct dependency order).
- Given a diamond dependency graph (A → B, A → C; B → D, C → D), the solver returns a valid topological order (e.g., D first, then B and C, then A).
- Given circular module dependencies (A → B → A), the solver detects the cycle, logs to stderr, and returns success with declaration order and warnings.
- Given a single-module project, the solver returns one step with correct module name and path.
- Given the above, `sbt test` passes all tests, and `sbt nativeImage` produces a working binary.

## Design Notes

**Kahn's Algorithm Recap:**
1. Count in-degrees of all nodes (modules).
2. Enqueue nodes with in-degree 0.
3. While queue not empty: dequeue node, add to sorted order, decrement in-degree of neighbors, enqueue neighbors with in-degree 0.
4. If sorted order length ≠ total nodes, a cycle exists.

**Gradle Dependency Parsing:** Look for patterns like:
```gradle
dependencies {
  implementation project(':core')
  testImplementation project(':test-utils')
}
```

Extract module references via regex: `project\('([^']+)'\)`. Normalize module names (strip leading colons if present).

## Verification

**Commands:**
- `cd engine && sbt test` -- expected: all tests pass, including DAG solver edge cases.
- `cd engine && sbt nativeImage` -- expected: binary builds successfully.
- `./engine/target/native-image/cumulus-engine compute-build-order --dir /path/to/multi-module-project` -- expected: JSON with ordered module steps.

## Suggested Review Order

**CLI Integration & Entry Point**

- New subcommand dispatch wiring and directory detection logic.
  [`Main.scala:279`](../../../engine/src/main/scala/cumulus/Main.scala#L279)

- Maven project detection and single-module case; null-safety on parser result.
  [`Main.scala:57`](../../../engine/src/main/scala/cumulus/Main.scala#L57)

- Gradle project detection with settings/build file handling; module validation.
  [`Main.scala:137`](../../../engine/src/main/scala/cumulus/Main.scala#L137)

**Core Algorithm & Data Flow**

- Kahn's algorithm implementation with in-degree computation and cycle detection.
  [`DagSolver.scala:290`](../../../engine/src/main/scala/cumulus/build/DagSolver.scala#L290)

- Build order wrapper with graceful fallback to declaration order on cycle.
  [`DagSolver.scala:359`](../../../engine/src/main/scala/cumulus/build/DagSolver.scala#L359)

**Dependency Extraction**

- Gradle multi-module dependency extraction across build.gradle files.
  [`DependencyExtractor.scala:116`](../../../engine/src/main/scala/cumulus/build/DependencyExtractor.scala#L116)

- Gradle single-file dependency regex with whitespace-tolerant pattern matching.
  [`DependencyExtractor.scala:84`](../../../engine/src/main/scala/cumulus/build/DependencyExtractor.scala#L84)

- Maven inter-module dependency extraction via XML parsing.
  [`DependencyExtractor.scala:24`](../../../engine/src/main/scala/cumulus/build/DependencyExtractor.scala#L24)

**Data Models**

- Response envelope case classes with uPickle serialization derives.
  [`BuildModels.scala:215`](../../../engine/src/main/scala/cumulus/build/BuildModels.scala#L215)

**Verification & Tests**

- Comprehensive test suite covering linear chains, diamonds, cycles, and edge cases.
  [`DagSolverTest.scala:550`](../../../engine/src/test/scala/cumulus/build/DagSolverTest.scala#L550)
