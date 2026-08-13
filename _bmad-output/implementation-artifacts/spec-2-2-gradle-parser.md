---
title: 'Epic 2 Story 2.2: Gradle Task & Submodule Parser'
type: 'feature'
created: '2026-08-12'
status: 'ready-for-dev'
review_loop_iteration: 0
context: ['_bmad-output/implementation-artifacts/epic-2-context.md']
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Gradle projects need task discovery and multi-module structure extraction from `./gradlew tasks` output and `settings.gradle` files. Without this, Gradle developers lack build intelligence in the engine alongside Maven support.

**Approach:** Implement `parse-gradle-tasks` and `parse-modules --tool gradle` subcommands. Parse `./gradlew tasks` output via regex to extract task names (deduplicated via Set). Extract `include '...'` directives from `settings.gradle` to discover module paths.

## Boundaries & Constraints

**Always:**
- Parse Gradle task output via text parsing (not execution of `./gradlew`; parse only static output).
- Response envelope: `CumulusResponse[T]` with zero runtime reflection (uPickle macros only).
- Subcommands: `parse-gradle-tasks` (via stdin) and `parse-modules --tool gradle --file <path>`.
- All JSON output conforms to `SPEC-031` schema.
- stdout reserved for JSON; all debug/logging to stderr.

**Ask First:**
- Adding custom Gradle plugin introspection if standard task parsing is insufficient.
- Optional flags beyond `--tool gradle --file <path>` (e.g., `--filter`, `--include-private-tasks`).

**Never:**
- Do not execute `./gradlew` or invoke Gradle runtime; parse only static output/files.
- Do not implement Maven or SBT task parsing (Story 2.1 and other stories cover those).
- Do not use reflection-based JSON libraries (use uPickle only).

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Parse Gradle tasks output | `parse-gradle-tasks` with stdin containing `./gradlew tasks` output (multiple task sections with build tasks, test tasks, etc.) | JSON array of task objects with name (deduplicated); standard aliases like "clean", "build", "test" recognized | Malformed task output (missing colons, unusual formatting) → gracefully parse best-effort or empty array |
| Parse settings.gradle | `parse-modules --tool gradle --file settings.gradle` with `include 'core'`, `include 'web:api'` directives | JSON array of module objects with name and path; nested modules resolved (e.g., web:api → web/api) | Missing file → error_code `FILE_NOT_FOUND`; invalid Gradle syntax → error_code `PARSE_ERROR` |
| Gradle with no modules | `parse-modules` on simple single-module project (settings.gradle missing or empty) | Success with empty modules array | No error; success with empty list |
| Gradle with composite builds | `settings.gradle` with `includeBuild '../sibling-project'` directives | Parse include directives; return module paths including composite includes | Relative paths resolved from settings.gradle directory |

</frozen-after-approval>

## Code Map

- `engine/src/main/scala/cumulus/Main.scala:60-100` -- CLI router: add pattern-match cases for `parse-gradle-tasks` and `parse-modules --tool gradle`
- `engine/src/main/scala/cumulus/build/GradleModels.scala` -- **NEW**: Case classes for `GradleTask` and `GradleModule` with uPickle derivation
- `engine/src/main/scala/cumulus/build/GradleParser.scala` -- **NEW**: Parse Gradle task output and settings.gradle via text parsing; extract module declarations
- `engine/src/test/scala/cumulus/build/GradleParserTest.scala` -- **NEW**: Unit tests covering task extraction, module discovery, error cases

## Tasks & Acceptance

**Execution:**
- [ ] `engine/src/main/scala/cumulus/build/GradleModels.scala` -- CREATE: Define `GradleTask` (name: String), `GradleModule` (name: String, path: String), and response wrappers with uPickle derivation.
- [ ] `engine/src/main/scala/cumulus/build/GradleParser.scala` -- CREATE: Implement `parseTasks(stdin: String): CumulusResponse[Seq[GradleTask]]` using regex to extract task names from `./gradlew tasks` output; deduplicate via Set. Implement `parseModules(settingsPath: String): CumulusResponse[Seq[GradleModule]]` to parse `include '...'` directives from settings.gradle.
- [ ] `engine/src/main/scala/cumulus/Main.scala` -- EDIT: Add `parse-gradle-tasks` (reads stdin) and `parse-modules --tool gradle` (requires --file) subcommands.
- [ ] `engine/src/test/scala/cumulus/build/GradleParserTest.scala` -- CREATE: Unit tests for task parsing (various Gradle output formats), module extraction (nested modules, composite builds, missing files), and error handling.

**Acceptance Criteria:**
- Given `./gradlew tasks` output from a typical Gradle project, when `parse-gradle-tasks` is invoked with that output via stdin, then stdout returns `{"success":true,"data":{"tasks":[...]},"error":null,"error_code":null}` with at least 5 distinct task names (build, test, clean, compile, run, etc.).
- Given a `settings.gradle` with `include 'core'` and `include 'web'` directives, when `parse-modules --tool gradle --file settings.gradle` is executed, then stdout returns module names and paths in declaration order.
- Given a missing settings.gradle file, when `parse-modules --tool gradle --file nonexistent.gradle` is executed, then stdout returns `{"success":false,...,"error_code":"FILE_NOT_FOUND"}`.
- Given malformed Gradle output or invalid syntax, error responses use `PARSE_ERROR` code appropriately.
- Given the above, `sbt test` passes all tests, and `sbt nativeImage` produces a working binary without reflection errors.

## Design Notes

**Gradle Task Output Parsing:** Gradle's `./gradlew tasks` output has a consistent format with sections like:

```
Build tasks
-----------
assemble - Assemble main and test classes
build - Assemble and test this project
...

Test tasks
----------
test - Run the tests
...
```

Parse by splitting on section headers (lines ending with dashes) and extracting lines with format `taskName - description`. Deduplicate task names via `Set[String]`.

**Settings.gradle Module Discovery:** Extract `include '...'` and `include '...'` patterns (with or without configuration blocks). Nested modules use colon separators (e.g., `include 'web:api'` → path `web/api`).

## Verification

**Commands:**
- `cd engine && sbt test` -- expected: all tests pass.
- `cd engine && sbt nativeImage` -- expected: binary builds successfully.
- `echo "./gradlew tasks output..." | ./engine/target/native-image/cumulus-engine parse-gradle-tasks` -- expected: valid JSON with task array.
- `./engine/target/native-image/cumulus-engine parse-modules --tool gradle --file settings.gradle` -- expected: valid JSON with modules array.
