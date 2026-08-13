---
title: 'Epic 2 Story 2.1: Maven Goals & Submodule Parser (scala-xml)'
type: 'feature'
created: '2026-08-12'
status: 'done'
review_loop_iteration: 0
baseline_commit: 'ef480c99d62972f27c6cff00affb995cf25ce920'
context: ['_bmad-output/implementation-artifacts/epic-2-context.md']
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Maven projects need their build goals and submodule structure extracted from `pom.xml` without reflection-based XML libraries. The Neovim plugin currently lacks build-system awareness, preventing fast plugin-command suggestions and multi-module navigation.

**Approach:** Implement `parse-pom` and `parse-modules --tool maven` subcommands using scala-xml's type-safe node selectors (`\` and `\\`). Extract Maven lifecycle goals (clean, compile, test, install), plugin-specific goals (Spring Boot, Quarkus, Surefire, Failsafe, Exec), and module declarations with relative paths for multi-module projects.

## Boundaries & Constraints

**Always:**
- Use scala-xml only for XML parsing (no regex, no SAX streaming, no Jackson/Gson).
- Response envelope: `CumulusResponse[T]` with zero runtime reflection (uPickle macros only).
- Subcommands: `parse-pom --file <path>` and `parse-modules --tool maven --file <path>`.
- All JSON output conforms to `SPEC-031` schema (fields: success, data, error, error_code).
- stdout reserved for JSON; all debug/logging to stderr.

**Ask First:**
- Changes to Maven goal list or plugin priorities if they diverge from standard Maven/Spring/Quarkus conventions.
- Adding optional CLI flags (e.g., `--include-profiles`, `--skip-inactive-modules`) beyond the specified subcommands.

**Never:**
- Do not implement Gradle or SBT parsers in this story (Story 2.2 and 2.3 cover those).
- Do not attempt to execute `mvn` or `mvnw`; parse only static files.
- Do not use Java's DOM or SAX XML APIs (use scala-xml exclusively).
- Do not create business logic in Lua or modify Neovim integration (that's Epic 6).

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Parse Maven POM | `parse-pom --file pom.xml` with Maven 3 POM in simple (non-multi-module) project | JSON array of goal objects with name and source (lifecycle or plugin); Spring Boot, Quarkus plugins resolved | Missing file → error_code `FILE_NOT_FOUND`; invalid XML → error_code `PARSE_ERROR` |
| Parse POM with namespaces | `parse-pom --file pom.xml` where POM declares xmlns (standard Maven namespace) | Parser correctly resolves qualified elements (`<m:build>`, etc.); all goals still extracted | Gracefully handle both namespaced and non-namespaced POMs |
| Extract modules | `parse-modules --tool maven --file pom.xml` with `<modules>` block declaring 3+ submodules | JSON array of module objects with name and relative path (e.g., `{"name":"core","path":"./core"}`, `{"name":"web","path":"./web"}`) | Missing or empty `<modules>` → success with empty array; invalid path format → INTERNAL_ERROR |
| Circular submodule refs | `parse-modules` on POM with interdependent modules (A→B→C→A, or B→A where A also lists B) | Return module list as declared in POM; DAG solver (Story 2.3) handles cycle detection/ordering | No blocking error; success with modules in declaration order (warnings logged to stderr) |
| Multi-module parent | `parse-pom --file pom.xml` on aggregator parent POM | Lifecycle goals extracted from parent; plugin goals from parent and child modules if plugin is declared in parent | If parent POM has no plugins but children do, only parent-level goals returned (child goals fetched separately via recursive calls) |

</frozen-after-approval>

## Code Map

- `engine/src/main/scala/cumulus/Main.scala:28-30` -- CLI router: pattern-match on `args(0)` for new subcommands `parse-pom` and `parse-modules`. Forward to parser module.
- `engine/src/main/scala/cumulus/protocol/Envelope.scala:16-50` -- `CumulusResponse[T]` envelope and `CumulusError` enum already proven in Epic 1; reuse helpers.
- `engine/build.sbt:15-20` -- Verify `scala-xml` 2.2.0 dependency (already present from Epic 1 setup).
- `engine/src/main/scala/cumulus/build/MavenParser.scala` -- **NEW**: Parse POM via scala-xml, extract lifecycle + plugin goals, return case classes derived via uPickle.
- `engine/src/main/scala/cumulus/build/MavenModels.scala` -- **NEW**: Case classes for `PomGoal` (name, source: `"lifecycle" | "plugin"`), `PomModule` (name, path), and aggregated response types.

## Tasks & Acceptance

**Execution:**
- [x] `engine/src/main/scala/cumulus/build/MavenModels.scala` -- CREATE: Define `PomGoal`, `PomModule`, and response wrapper case classes; derive uPickle ReadWriter for all.
- [x] `engine/src/main/scala/cumulus/build/MavenParser.scala` -- CREATE: Implement `parseGoals(pomPath: String): CumulusResponse[Seq[PomGoal]]` using scala-xml `(pom \ "build" \ "plugins" \ "plugin")` traversal and lifecycle goal enumeration. Handle both namespaced and non-namespaced POMs.
- [x] `engine/src/main/scala/cumulus/build/MavenParser.scala` -- CREATE: Implement `parseModules(pomPath: String): CumulusResponse[Seq[PomModule]]` using `(pom \ "modules" \ "module")` traversal.
- [x] `engine/src/main/scala/cumulus/Main.scala` -- EDIT: Add pattern-match cases for `parse-pom` and `parse-modules --tool maven`. Delegate to parser; handle error codes (FILE_NOT_FOUND, PARSE_ERROR, INTERNAL_ERROR).
- [x] `engine/src/test/scala/cumulus/build/MavenParserTest.scala` -- CREATE: Unit tests for both functions using real sample POMs (simple, multi-module, with namespaces, missing elements).

**Acceptance Criteria:**
- Given a Maven `pom.xml` with declared plugins (Spring Boot, Quarkus, Surefire, Exec), when `parse-pom --file pom.xml` is executed, then stdout returns `{"success":true,"data":[...goals...],"error":null,"error_code":null}` with at least 4 distinct goals (clean, compile, test, install from lifecycle, plus 1+ from plugins).
- Given a multi-module `pom.xml` with `<modules><module>core</module><module>web</module></modules>`, when `parse-modules --tool maven --file pom.xml` is executed, then stdout returns module names and relative paths in declaration order.
- Given a missing `pom.xml` file, when `parse-pom --file nonexistent.xml` is executed, then stdout returns `{"success":false,"data":null,"error":"...","error_code":"FILE_NOT_FOUND"}`.
- Given a malformed XML file, when `parse-pom --file broken.xml` is executed, then stdout returns `{"success":false,...,"error_code":"PARSE_ERROR"}`.
- Given the above, `sbt test` in `engine/` passes all tests, and `sbt nativeImage` produces a working binary.

## Design Notes

**XML Namespace Handling:** Maven POMs declare `xmlns="http://maven.apache.org/POM/4.0.0"` by default. The scala-xml library's `\` operator works with unqualified names in the default namespace if we extract the text content directly from matched nodes. For namespaced elements, use `(pom \ "{http://maven.apache.org/POM/4.0.0}build")` or rely on the parser's automatic namespace stripping.

**Lifecycle Goals:** Maven has six standard phases in the default lifecycle: `validate`, `compile`, `test`, `package`, `install`, `deploy`. This story includes the most-used subset: `clean` (from clean lifecycle), `compile`, `test`, `package`, `install` (from default). Plugin-specific goals are inferred from declared plugins:
- `org.springframework.boot:spring-boot-maven-plugin` → `spring-boot:run`, `spring-boot:build-image`
- `io.quarkus:quarkus-maven-plugin` → `quarkus:dev`, `quarkus:build`
- `org.apache.maven.plugins:maven-surefire-plugin` → `surefire:test`
- `org.codehaus.mojo:exec-maven-plugin` → `exec:java`, `exec:exec`

**Recursive Module Discovery:** If a parent POM references modules, and you want to discover transitive multi-level submodules, a future story (not 2.1 scope) can recursively call `parseModules` on child `pom.xml` files. Story 2.1 returns only direct submodules declared in the given POM.

## Verification

**Commands:**
- `cd engine && sbt test` -- expected: all tests pass, no reflection-related failures.
- `cd engine && sbt nativeImage` -- expected: binary compiles without GraalVM reflection errors.
- `./engine/target/native-image/cumulus-engine parse-pom --file /path/to/test-pom.xml` -- expected: valid JSON output with goals array.
- `./engine/target/native-image/cumulus-engine parse-modules --tool maven --file /path/to/test-pom.xml` -- expected: valid JSON output with modules array.
- `./engine/target/native-image/cumulus-engine parse-pom --file /nonexistent.xml` -- expected: `{"success":false,...,"error_code":"FILE_NOT_FOUND"}`.

**Manual checks (if no CLI):**
- Inspect generated `target/classes/cumulus/build/MavenModels.class` and `MavenParser.class` to confirm scala-xml imports resolve and no runtime reflection gadgets are present.

## Suggested Review Order

**CLI Routing & Response Serialization**

- Entry point: parse-pom and parse-modules subcommands added with argument parsing and polymorphic response handling
  [`Main.scala:34-50`](../../../engine/src/main/scala/cumulus/Main.scala#L34)

- CLI pattern matching routes subcommands to parser functions with error handling for missing arguments
  [`Main.scala:68-90`](../../../engine/src/main/scala/cumulus/Main.scala#L68)

**Parser Implementation**

- Maven goal extraction with hardcoded plugin goal mappings (Spring Boot, Quarkus, Surefire, Exec)
  [`MavenParser.scala:45-75`](../../../engine/src/main/scala/cumulus/build/MavenParser.scala#L45)

- Module extraction using scala-xml `\\` operator with path normalization
  [`MavenParser.scala:90-110`](../../../engine/src/main/scala/cumulus/build/MavenParser.scala#L90)

- Error handling with standardized error codes (FILE_NOT_FOUND, PARSE_ERROR, INTERNAL_ERROR)
  [`MavenParser.scala:120-145`](../../../engine/src/main/scala/cumulus/build/MavenParser.scala#L120)

**Data Model & Response Types**

- Case classes for parsed goals and modules with uPickle derivation
  [`MavenModels.scala`](../../../engine/src/main/scala/cumulus/build/MavenModels.scala#L1)

**Test Coverage**

- 12 comprehensive tests covering happy path, edge cases, error handling, and namespace support
  [`MavenParserTest.scala:14-130`](../../../engine/src/test/scala/cumulus/build/MavenParserTest.scala#L14)
